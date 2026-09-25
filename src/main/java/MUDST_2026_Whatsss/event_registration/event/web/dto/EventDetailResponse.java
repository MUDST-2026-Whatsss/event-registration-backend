package MUDST_2026_Whatsss.event_registration.event.web.dto;

import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.domain.LocationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Public event detail. The online access URL remains private until registration permits it. */
public record EventDetailResponse(
        UUID eventId,
        String slug,
        String title,
        String summary,
        String description,
        EventCategoryResponse category,
        EventType eventType,
        BigDecimal price,
        String currency,
        String refundPolicy,
        LocationType locationType,
        String locationName,
        String address,
        String imageUrl,
        String timezone,
        Instant startAt,
        Instant endAt,
        Instant registrationStartAt,
        Instant registrationEndAt,
        Instant cancellationDeadlineAt,
        String rules,
        String contactEmail,
        String eligibility,
        boolean allowCancellation,
        boolean showRemainingSeats) {
}
