package MUDST_2026_Whatsss.event_registration.storage.config;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Makes an enabled but unreachable object store visible through Actuator health. */
@Component("objectStorage")
@ConditionalOnProperty(name = "event.storage.enabled", havingValue = "true")
public class MinioStorageHealthIndicator implements HealthIndicator {

    private final MinioClient client;
    private final String bucket;

    public MinioStorageHealthIndicator(MinioClient client, StorageProperties properties) {
        this.client = client;
        this.bucket = properties.getBucket();
    }

    @Override
    public Health health() {
        try {
            boolean bucketExists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            return Health.up().withDetail("bucketInitialized", bucketExists).build();
        } catch (Exception ex) {
            return Health.down().withException(ex).build();
        }
    }
}
