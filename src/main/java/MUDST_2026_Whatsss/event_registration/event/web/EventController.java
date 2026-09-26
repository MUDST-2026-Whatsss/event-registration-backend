package MUDST_2026_Whatsss.event_registration.event.web;

import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.service.EventMapper;
import MUDST_2026_Whatsss.event_registration.event.service.EventNotFoundException;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventDetailResponse;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventSummaryResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping
    public List<EventSummaryResponse> listPublished() {
        return eventRepository.findByStatus(EventStatus.PUBLISHED, Pageable.unpaged())
                .stream()
                .map(eventMapper::toSummaryResponse)
                .toList();
    }

    @GetMapping("/{slug}")
    public EventDetailResponse getBySlug(@PathVariable String slug) {
        return eventRepository.findBySlugAndStatus(slug, EventStatus.PUBLISHED)
                .map(eventMapper::toDetailResponse)
                .orElseThrow(EventNotFoundException::new);
    }
}
