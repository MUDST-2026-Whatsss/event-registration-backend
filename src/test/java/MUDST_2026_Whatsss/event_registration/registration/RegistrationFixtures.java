package MUDST_2026_Whatsss.event_registration.registration;

import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.domain.LocationType;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.service.AdminEventService;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventWriteRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Users and events for registration tests; every call creates uniquely named rows. */
final class RegistrationFixtures {

    static final UUID TECHNOLOGY_CATEGORY = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private final JdbcTemplate jdbcTemplate;
    private final AdminEventService adminEventService;
    private final EventRepository eventRepository;

    RegistrationFixtures(
            JdbcTemplate jdbcTemplate, AdminEventService adminEventService, EventRepository eventRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.adminEventService = adminEventService;
        this.eventRepository = eventRepository;
    }

    AuthenticatedUser admin() {
        UUID id = insertUser("admin");
        return new AuthenticatedUser(id, "admin-" + id + "@example.test", "ADMIN", List.of("ADMIN"),
                List.of("EVENT_CREATE", "EVENT_UPDATE", "EVENT_CANCEL", "REGISTRATION_VIEW_ALL",
                        "REGISTRATION_CHECK_IN"));
    }

    AuthenticatedUser participant(String label, String firstName, String lastName) {
        UUID id = insertUser(label);
        jdbcTemplate.update(
                "insert into participants (user_id, first_name, last_name) values (?, ?, ?)",
                id, firstName, lastName);
        return new AuthenticatedUser(id, label + "-" + id + "@example.test", "USER", List.of("USER"),
                List.of("REGISTRATION_CREATE_SELF", "REGISTRATION_READ_SELF", "REGISTRATION_CANCEL_SELF"));
    }

    /** Creates through the admin service, then publishes directly to skip the review workflow. */
    UUID publishedEvent(AuthenticatedUser owner, String title, int capacity) {
        UUID eventId = adminEventService.create(request(title, capacity), owner).eventId();
        var event = eventRepository.findById(eventId).orElseThrow();
        event.setStatus(EventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        eventRepository.saveAndFlush(event);
        return eventId;
    }

    EventWriteRequest request(String title, int capacity) {
        Instant now = Instant.now();
        Instant start = now.plusSeconds(7 * 24 * 60 * 60);
        return new EventWriteRequest(
                title, "Summary", "Description", TECHNOLOGY_CATEGORY,
                EventType.FREE, BigDecimal.ZERO, "THB", null,
                LocationType.ONSITE, "Main Hall", null, null, null,
                "Asia/Bangkok", start, start.plusSeconds(3600),
                now.minusSeconds(60), start.minusSeconds(3600), start.minusSeconds(3600),
                capacity, null, "events@example.test", null, true, true);
    }

    private UUID insertUser(String label) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into auth_users (user_id, email, password_hash, status) values (?, ?, ?, ?)",
                id, label + "-" + id + "@example.test", "test-hash", "ACTIVE");
        return id;
    }
}
