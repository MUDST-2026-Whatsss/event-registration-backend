package MUDST_2026_Whatsss.event_registration.storage.web.dto;

/** Result of a validated event cover upload. Store objectKey with the event draft. */
public record EventImageResponse(
        String objectKey,
        String imageUrl,
        String contentType,
        long sizeBytes,
        int width,
        int height) {
}
