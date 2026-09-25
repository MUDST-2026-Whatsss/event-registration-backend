package MUDST_2026_Whatsss.event_registration.event.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChangeRequestReviewResponse(
        UUID requestId,
        String status,
        String reason,
        String reviewComment,
        Instant createdAt,
        Instant reviewedAt,
        long version,
        EventReviewResponse.UserSummary requestedBy,
        EventReviewResponse.UserSummary reviewedBy,
        AdminEventResponse event,
        List<Item> items) {

    public record Item(String fieldName, JsonNode oldValue, JsonNode newValue, int displayOrder) {
    }
}
