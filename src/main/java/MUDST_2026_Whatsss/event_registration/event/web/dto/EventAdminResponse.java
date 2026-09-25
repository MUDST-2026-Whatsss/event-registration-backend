package MUDST_2026_Whatsss.event_registration.event.web.dto;

import java.time.Instant;
import java.util.UUID;

public record EventAdminResponse(
        UUID userId,
        String email,
        String assignmentRole,
        Instant assignedAt,
        boolean owner) {
}
