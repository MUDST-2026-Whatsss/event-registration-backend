package MUDST_2026_Whatsss.event_registration.event.web;

import MUDST_2026_Whatsss.event_registration.event.domain.Event;
import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.service.EventMapper;
import MUDST_2026_Whatsss.event_registration.event.service.EventNotFoundException;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventDetailResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventSummaryResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Public read-only event endpoints — no authentication required. */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    public EventController(EventRepository eventRepository, EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
    }

    /** Upcoming and in-progress published events; finished ones drop out of the catalogue. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<EventSummaryResponse> listPublished() {
        List<Event> events = eventRepository.findByStatusAndEndAtAfterOrderByStartAtAsc(
                EventStatus.PUBLISHED, Instant.now());
        Map<UUID, Long> counts = eventRepository.activeRegistrationCounts(
                events.stream().map(Event::getEventId).toList());
        return events.stream()
                .map(event -> eventMapper.toSummaryResponse(
                        event, counts.getOrDefault(event.getEventId(), 0L)))
                .toList();
    }

    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    public EventDetailResponse getBySlug(@PathVariable String slug) {
        Event event = eventRepository.findBySlugAndStatus(slug, EventStatus.PUBLISHED)
                .orElseThrow(EventNotFoundException::new);
        return eventMapper.toDetailResponse(
                event, eventRepository.countActiveRegistrations(event.getEventId()));
    }
}
