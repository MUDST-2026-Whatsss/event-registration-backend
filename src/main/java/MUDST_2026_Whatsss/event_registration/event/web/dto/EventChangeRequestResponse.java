package MUDST_2026_Whatsss.event_registration.event.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventChangeRequestResponse(
        UUID changeRequestId,
        UUID eventId,
        String status,
        String reason,
        String reviewComment,
        Instant reviewedAt,
        Instant createdAt,
        long version,
        List<Item> items) {

    public record Item(String fieldName, JsonNode oldValue, JsonNode newValue, int displayOrder) {
    }
}
