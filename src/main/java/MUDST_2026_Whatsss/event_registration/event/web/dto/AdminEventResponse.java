package MUDST_2026_Whatsss.event_registration.event.web.dto;

import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.domain.LocationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminEventResponse(
        UUID eventId,
        String slug,
        String title,
        String summary,
        String description,
        EventCategoryResponse category,
        EventStatus status,
        EventType eventType,
        BigDecimal price,
        String currency,
        String refundPolicy,
        LocationType locationType,
        String locationName,
        String address,
        String onlineUrl,
        String imageObjectKey,
        String imageUrl,
        String timezone,
        Instant startAt,
        Instant endAt,
        Instant registrationStartAt,
        Instant registrationEndAt,
        Instant cancellationDeadlineAt,
        int maximumParticipants,
        long registrationCount,
        String rules,
        String contactEmail,
        String eligibility,
        boolean allowCancellation,
        boolean showRemainingSeats,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
