package MUDST_2026_Whatsss.event_registration.event.web.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID auditLogId,
        UserSummary actor,
        String action,
        String targetType,
        UUID targetId,
        String targetLabel,
        String outcome,
        Map<String, Object> metadata,
        Instant createdAt) {

    public record UserSummary(UUID userId, String email, String displayName) {
    }
}
