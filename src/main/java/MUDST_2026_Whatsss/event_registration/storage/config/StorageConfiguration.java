package MUDST_2026_Whatsss.event_registration.storage.config;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/** Builds the storage client only when the environment explicitly enables object storage. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {

    @Bean
    @ConditionalOnProperty(name = "event.storage.enabled", havingValue = "true")
    MinioClient minioClient(StorageProperties properties) {
        requireText(properties.getAccessKey(), "STORAGE_ACCESS_KEY");
        requireText(properties.getSecretKey(), "STORAGE_SECRET_KEY");
        requireText(properties.getBucket(), "STORAGE_BUCKET");

        String scheme = properties.getEndpoint().getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalStateException("STORAGE_ENDPOINT must use http or https");
        }

        return MinioClient.builder()
                .endpoint(properties.getEndpoint().toString())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    private static void requireText(String value, String environmentName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(environmentName + " is required when STORAGE_ENABLED=true");
        }
    }
}
