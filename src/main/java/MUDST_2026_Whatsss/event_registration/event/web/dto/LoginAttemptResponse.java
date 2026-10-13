package MUDST_2026_Whatsss.event_registration.event.web.dto;

import java.time.Instant;
import java.util.UUID;

public record LoginAttemptResponse(
        UUID loginAttemptId,
        UserSummary user,
        String outcome,
        String failureReason,
        String ipAddress,
        String userAgent,
        Instant attemptedAt) {

    public record UserSummary(UUID userId, String email, String displayName) {
    }
}
