package MUDST_2026_Whatsss.event_registration.storage.service;

import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import MUDST_2026_Whatsss.event_registration.storage.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

/** MinIO implementation backed by a private bucket. */
@Service
@ConditionalOnProperty(name = "event.storage.enabled", havingValue = "true")
public class MinioObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorage.class);

    private final MinioClient client;
    private final String bucket;
    private volatile boolean bucketReady;

    public MinioObjectStorage(MinioClient client, StorageProperties properties) {
        this.client = client;
        this.bucket = properties.getBucket();
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        ensureBucket();
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .contentType(contentType)
                    .stream(input, (long) content.length, -1L)
                    .build());
        } catch (Exception ex) {
            throw unavailable("write", objectKey, ex);
        }
    }

    @Override
    public StoredContent get(String objectKey) {
        ensureBucket();
        try (GetObjectResponse response = client.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build())) {
            String contentType = response.headers().get("Content-Type");
            return new StoredContent(response.readAllBytes(), contentType);
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equals(ex.errorResponse().code())
                    || "NoSuchObject".equals(ex.errorResponse().code())) {
                throw new ApiException(ErrorCode.MEDIA_NOT_FOUND);
            }
            throw unavailable("read", objectKey, ex);
        } catch (Exception ex) {
            throw unavailable("read", objectKey, ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        ensureBucket();
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            throw unavailable("delete", objectKey, ex);
        }
    }

    /** Create the private bucket lazily so API startup does not race MinIO container startup. */
    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                bucketReady = true;
            } catch (Exception ex) {
                throw unavailable("initialize bucket", bucket, ex);
            }
        }
    }

    private ApiException unavailable(String operation, String objectKey, Exception ex) {
        log.error("Object storage {} failed for {}", operation, objectKey, ex);
        return new ApiException(ErrorCode.STORAGE_UNAVAILABLE);
    }
}
