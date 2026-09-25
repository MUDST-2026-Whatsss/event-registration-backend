package MUDST_2026_Whatsss.event_registration.event.web.dto;

import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.domain.LocationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Participant-safe catalogue projection. Internal lifecycle and creator data are omitted. */
public record EventSummaryResponse(
        UUID eventId,
        String slug,
        String title,
        String summary,
        EventCategoryResponse category,
        EventType eventType,
        BigDecimal price,
        String currency,
        LocationType locationType,
        String locationName,
        String imageUrl,
        String timezone,
        Instant startAt,
        Instant endAt,
        Instant registrationStartAt,
        Instant registrationEndAt) {
}
