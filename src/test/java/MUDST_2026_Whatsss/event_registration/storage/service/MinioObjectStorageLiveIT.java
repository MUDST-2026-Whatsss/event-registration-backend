package MUDST_2026_Whatsss.event_registration.storage.service;

import MUDST_2026_Whatsss.event_registration.storage.config.StorageProperties;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/** Optional round-trip test against the Compose MinIO instance. */
class MinioObjectStorageLiveIT {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_MINIO_IT", matches = "true")
    void writesReadsAndDeletesAnObject() {
        StorageProperties properties = propertiesFromEnvironment();
        MinioClient client = MinioClient.builder()
                .endpoint(properties.getEndpoint().toString())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
        MinioObjectStorage storage = new MinioObjectStorage(client, properties);
        String objectKey = "live-tests/" + UUID.randomUUID() + ".txt";
        byte[] expected = "minio-round-trip".getBytes(StandardCharsets.UTF_8);
        boolean uploaded = false;

        try {
            storage.put(objectKey, expected, "text/plain");
            uploaded = true;
            StoredContent actual = storage.get(objectKey);

            assertThat(actual.bytes()).isEqualTo(expected);
            assertThat(actual.contentType()).isEqualTo("text/plain");

            storage.delete(objectKey);
            uploaded = false;
            assertThatExceptionOfType(ApiException.class)
                    .isThrownBy(() -> storage.get(objectKey))
                    .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEDIA_NOT_FOUND));
        } finally {
            if (uploaded) {
                storage.delete(objectKey);
            }
        }
    }

    private static StorageProperties propertiesFromEnvironment() {
        StorageProperties properties = new StorageProperties();
        properties.setEndpoint(URI.create(required("STORAGE_ENDPOINT")));
        properties.setAccessKey(required("STORAGE_ACCESS_KEY"));
        properties.setSecretKey(required("STORAGE_SECRET_KEY"));
        properties.setBucket(required("STORAGE_BUCKET"));
        return properties;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the live MinIO test");
        }
        return value;
    }
}
