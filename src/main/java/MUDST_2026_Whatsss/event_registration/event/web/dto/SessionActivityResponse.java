package MUDST_2026_Whatsss.event_registration.event.web.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionActivityResponse(
        UUID sessionId,
        UserSummary user,
        String state,
        String ipAddress,
        String userAgent,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant activityAt) {

    public record UserSummary(UUID userId, String email, String displayName) {
    }
}
