package MUDST_2026_Whatsss.event_registration.event.service;

import MUDST_2026_Whatsss.event_registration.event.domain.Event;
import MUDST_2026_Whatsss.event_registration.event.domain.EventCategory;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventCategoryResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventDetailResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventSummaryResponse;
import MUDST_2026_Whatsss.event_registration.storage.service.MediaUrlResolver;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    private final MediaUrlResolver mediaUrlResolver;

    public EventMapper(MediaUrlResolver mediaUrlResolver) {
        this.mediaUrlResolver = mediaUrlResolver;
    }

    public EventCategoryResponse toCategoryResponse(EventCategory category) {
        if (category == null) {
            return null;
        }
        return new EventCategoryResponse(
                category.getEventCategoryId(),
                category.getCode(),
                category.getNameTh(),
                category.getNameEn(),
                category.getDescription());
    }

    public EventSummaryResponse toSummaryResponse(Event event) {
        return new EventSummaryResponse(
                event.getEventId(),
                event.getSlug(),
                event.getTitle(),
                event.getSummary(),
                toCategoryResponse(event.getCategory()),
                event.getEventType(),
                event.getPrice(),
                event.getCurrency().trim(),
                event.getLocationType(),
                event.getLocationName(),
                mediaUrlResolver.urlFor(event.getImageObjectKey()),
                event.getTimezone(),
                event.getStartAt(),
                event.getEndAt(),
                event.getRegistrationStartAt(),
                event.getRegistrationEndAt());
    }

    public EventDetailResponse toDetailResponse(Event event) {
        return new EventDetailResponse(
                event.getEventId(),
                event.getSlug(),
                event.getTitle(),
                event.getSummary(),
                event.getDescription(),
                toCategoryResponse(event.getCategory()),
                event.getEventType(),
                event.getPrice(),
                event.getCurrency().trim(),
                event.getRefundPolicy(),
                event.getLocationType(),
                event.getLocationName(),
                event.getAddress(),
                mediaUrlResolver.urlFor(event.getImageObjectKey()),
                event.getTimezone(),
                event.getStartAt(),
                event.getEndAt(),
                event.getRegistrationStartAt(),
                event.getRegistrationEndAt(),
                event.getCancellationDeadlineAt(),
                event.getRules(),
                event.getContactEmail(),
                event.getEligibility(),
                event.isAllowCancellation(),
                event.isShowRemainingSeats());
    }
}
