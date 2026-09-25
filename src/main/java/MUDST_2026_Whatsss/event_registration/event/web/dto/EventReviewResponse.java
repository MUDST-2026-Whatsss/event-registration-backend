package MUDST_2026_Whatsss.event_registration.event.web.dto;

import java.time.Instant;
import java.util.UUID;

public record EventReviewResponse(
        UUID reviewId,
        long eventVersion,
        String priority,
        String decision,
        String comment,
        Instant submittedAt,
        Instant reviewedAt,
        UserSummary submittedBy,
        UserSummary reviewedBy,
        AdminEventResponse event) {

    public record UserSummary(UUID userId, String email) {
    }
}
