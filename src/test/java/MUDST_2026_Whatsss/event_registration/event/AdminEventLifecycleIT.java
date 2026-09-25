package MUDST_2026_Whatsss.event_registration.event;

import MUDST_2026_Whatsss.event_registration.PostgresIntegrationTest;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.domain.LocationType;
import MUDST_2026_Whatsss.event_registration.event.repository.EventAdminAssignmentRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventCategoryRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventChangeRequestRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventReviewRepository;
import MUDST_2026_Whatsss.event_registration.event.service.AdminEventService;
import MUDST_2026_Whatsss.event_registration.event.web.dto.CancelEventRequest;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventChangeRequestCreateRequest;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventWriteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AdminEventLifecycleIT extends PostgresIntegrationTest {

    @Autowired private AdminEventService service;
    @Autowired private EventCategoryRepository categoryRepository;
    @Autowired private EventAdminAssignmentRepository assignmentRepository;
    @Autowired private EventReviewRepository reviewRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventChangeRequestRepository changeRequestRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID adminId;
    private AuthenticatedUser principal;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into auth_users (user_id, email, password_hash, status) values (?, ?, ?, ?)",
                adminId, "admin-" + adminId + "@example.test", "test-hash", "ACTIVE");
        principal = new AuthenticatedUser(
                adminId, "admin@example.test", "ADMIN", List.of("ADMIN"),
                List.of("EVENT_CREATE", "EVENT_UPDATE", "EVENT_CANCEL"));
    }

    @Test
    void createSubmitWithdrawAndCancelRetainHistory() {
        var created = service.create(validRequest("Admin lifecycle event"), principal);

        assertThat(created.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(assignmentRepository.existsByEvent_EventIdAndAdminUser_UserIdAndStatus(
                created.eventId(), adminId, "ACTIVE")).isTrue();

        var submitted = service.submit(created.eventId(), created.version(), principal);
        assertThat(submitted.status()).isEqualTo(EventStatus.PENDING_REVIEW);
        assertThat(reviewRepository.findByEvent_EventIdAndDecision(
                created.eventId(), "PENDING")).isPresent();

        var withdrawn = service.withdraw(submitted.eventId(), submitted.version(), principal);
        assertThat(withdrawn.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(reviewRepository.findByEvent_EventIdAndDecision(
                created.eventId(), "WITHDRAWN")).isPresent();

        var cancelled = service.cancel(
                withdrawn.eventId(), new CancelEventRequest("Schedule changed", withdrawn.version()), principal);
        assertThat(cancelled.status()).isEqualTo(EventStatus.CANCELLED);
        assertThat(cancelled.cancellationReason()).isEqualTo("Schedule changed");

        Integer auditCount = jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where target_id = ?", Integer.class, created.eventId());
        assertThat(auditCount).isEqualTo(4);
    }

    @Test
    void publishedChangesCreateFieldDiffWithoutMutatingEvent() {
        EventWriteRequest original = validRequest("Published event");
        var created = service.create(original, principal);
        var event = eventRepository.findById(created.eventId()).orElseThrow();
        event.setStatus(EventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        eventRepository.saveAndFlush(event);
        var published = service.get(created.eventId(), principal);

        var result = service.requestChanges(
                created.eventId(),
                published.version(),
                new EventChangeRequestCreateRequest(
                        "Use the final public title", withTitle(original, "Updated public title")),
                principal);

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.items()).extracting(item -> item.fieldName()).containsExactly("title");
        assertThat(eventRepository.findById(created.eventId()).orElseThrow().getTitle())
                .isEqualTo("Published event");
        assertThat(changeRequestRepository.existsByEvent_EventIdAndStatus(
                created.eventId(), "PENDING")).isTrue();
        assertThat(service.changeRequests(created.eventId(), principal)).hasSize(1);
    }

    private EventWriteRequest validRequest(String title) {
        UUID categoryId = categoryRepository.findByCodeAndActiveTrue("TECHNOLOGY")
                .orElseThrow().getEventCategoryId();
        Instant now = Instant.now();
        Instant start = now.plusSeconds(7 * 24 * 60 * 60);
        return new EventWriteRequest(
                title,
                "Test summary",
                "Test description",
                categoryId,
                EventType.FREE,
                BigDecimal.ZERO,
                "THB",
                null,
                LocationType.ONSITE,
                "Main Hall",
                null,
                null,
                null,
                "Asia/Bangkok",
                start,
                start.plusSeconds(3600),
                now,
                start.minusSeconds(3600),
                start.minusSeconds(3600),
                100,
                "Be respectful",
                "events@example.test",
                null,
                true,
                true);
    }

    private EventWriteRequest withTitle(EventWriteRequest request, String title) {
        return new EventWriteRequest(
                title, request.summary(), request.description(), request.eventCategoryId(),
                request.eventType(), request.price(), request.currency(), request.refundPolicy(),
                request.locationType(), request.locationName(), request.address(), request.onlineUrl(),
                request.imageObjectKey(), request.timezone(), request.startAt(), request.endAt(),
                request.registrationStartAt(), request.registrationEndAt(),
                request.cancellationDeadlineAt(), request.maximumParticipants(), request.rules(),
                request.contactEmail(), request.eligibility(), request.allowCancellation(),
                request.showRemainingSeats());
    }
}
