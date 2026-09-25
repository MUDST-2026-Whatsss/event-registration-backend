package MUDST_2026_Whatsss.event_registration.storage.web;

import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.storage.service.EventImageService;
import MUDST_2026_Whatsss.event_registration.storage.web.dto.EventImageResponse;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/event-images")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@ConditionalOnProperty(name = "event.storage.enabled", havingValue = "true")
public class EventImageController {

    private final EventImageService imageService;

    public EventImageController(EventImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventImageResponse> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(imageService.upload(file, user));
    }

    @DeleteMapping("/{ownerId}/{fileName}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID ownerId,
            @PathVariable @Pattern(regexp = EventImageService.FILE_NAME_REGEX) String fileName,
            @AuthenticationPrincipal AuthenticatedUser user) {
        imageService.delete(ownerId, fileName, user);
        return ResponseEntity.noContent().build();
    }
}
