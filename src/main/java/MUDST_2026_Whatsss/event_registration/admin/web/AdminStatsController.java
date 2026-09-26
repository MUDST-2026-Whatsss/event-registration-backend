package MUDST_2026_Whatsss.event_registration.admin.web;

import MUDST_2026_Whatsss.event_registration.auth.repository.AuthUserRepository;
import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stats")
public class AdminStatsController {

    public record AdminStatsResponse(
            long totalEvents,
            long publishedEvents,
            long pendingEvents,
            long draftEvents,
            long rejectedEvents,
            long totalUsers) {
    }

    private final EventRepository eventRepository;
    private final AuthUserRepository authUserRepository;

    public AdminStatsController(EventRepository eventRepository,
                                AuthUserRepository authUserRepository) {
        this.eventRepository = eventRepository;
        this.authUserRepository = authUserRepository;
    }

    @GetMapping
    public AdminStatsResponse getStats() {
        long totalEvents = eventRepository.count();
        long publishedEvents = eventRepository.findByStatus(EventStatus.PUBLISHED, Pageable.unpaged()).getTotalElements();
        long pendingEvents = eventRepository.findByStatus(EventStatus.PENDING_REVIEW, Pageable.unpaged()).getTotalElements();
        long draftEvents = eventRepository.findByStatus(EventStatus.DRAFT, Pageable.unpaged()).getTotalElements();
        long rejectedEvents = eventRepository.findByStatus(EventStatus.REJECTED, Pageable.unpaged()).getTotalElements();
        long totalUsers = authUserRepository.count();

        return new AdminStatsResponse(
                totalEvents,
                publishedEvents,
                pendingEvents,
                draftEvents,
                rejectedEvents,
                totalUsers);
    }
}
