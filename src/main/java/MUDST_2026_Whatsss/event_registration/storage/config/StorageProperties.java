package MUDST_2026_Whatsss.event_registration.storage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.net.URI;

/** Configuration shared by every S3-compatible object-storage implementation. */
@Getter
@Setter
@ConfigurationProperties(prefix = "event.storage")
public class StorageProperties {

    private boolean enabled;
    private URI endpoint = URI.create("http://localhost:9000");
    private String accessKey;
    private String secretKey;
    private String bucket = "event-registration";
    private String publicBaseUrl;
    private DataSize maxImageSize = DataSize.ofMegabytes(2);
    private int maxImageWidth = 6000;
    private int maxImageHeight = 6000;
}
