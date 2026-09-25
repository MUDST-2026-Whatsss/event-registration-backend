package MUDST_2026_Whatsss.event_registration.storage.web;

import MUDST_2026_Whatsss.event_registration.storage.service.EventImageService;
import MUDST_2026_Whatsss.event_registration.storage.service.StoredContent;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/** Read-only proxy for immutable public event images stored in the private bucket. */
@RestController
@Validated
@RequestMapping("/api/v1/media/event-images")
@ConditionalOnProperty(name = "event.storage.enabled", havingValue = "true")
public class PublicMediaController {

    private final EventImageService imageService;

    public PublicMediaController(EventImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping("/{ownerId}/{fileName}")
    public ResponseEntity<byte[]> load(
            @PathVariable UUID ownerId,
            @PathVariable @Pattern(regexp = EventImageService.FILE_NAME_REGEX) String fileName) {
        StoredContent content = imageService.load(ownerId, fileName);
        MediaType mediaType = MediaType.parseMediaType(content.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.bytes().length)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(content.bytes());
    }
}
