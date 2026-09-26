package MUDST_2026_Whatsss.event_registration.event;

import MUDST_2026_Whatsss.event_registration.PostgresIntegrationTest;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.domain.EventReview;
import MUDST_2026_Whatsss.event_registration.event.domain.LocationType;
import MUDST_2026_Whatsss.event_registration.event.repository.EventAdminAssignmentRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventCategoryRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventChangeRequestRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventReviewRepository;
import MUDST_2026_Whatsss.event_registration.event.service.AdminEventService;
import MUDST_2026_Whatsss.event_registration.event.service.SuperAdminEventService;
import MUDST_2026_Whatsss.event_registration.event.web.dto.CancelEventRequest;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventChangeRequestCreateRequest;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventWriteRequest;
import MUDST_2026_Whatsss.event_registration.event.web.dto.ReviewDecisionRequest;
import MUDST_2026_Whatsss.event_registration.event.web.dto.ReplaceEventAdminsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
    @Autowired private SuperAdminEventService superAdminService;

    private UUID adminId;
    private AuthenticatedUser principal;
    private AuthenticatedUser superAdmin;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into auth_users (user_id, email, password_hash, status) values (?, ?, ?, ?)",
                adminId, "admin-" + adminId + "@example.test", "test-hash", "ACTIVE");
        principal = new AuthenticatedUser(
                adminId, "admin@example.test", "ADMIN", List.of("ADMIN"),
                List.of("EVENT_CREATE", "EVENT_UPDATE", "EVENT_CANCEL"));
        UUID superAdminId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into auth_users (user_id, email, password_hash, status) values (?, ?, ?, ?)",
                superAdminId, "super-" + superAdminId + "@example.test", "test-hash", "ACTIVE");
        superAdmin = new AuthenticatedUser(
                superAdminId, "super@example.test", "SUPER_ADMIN", List.of("SUPER_ADMIN"),
                List.of());
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

    @Test
    void superAdminApprovesSubmittedEventAndRecordsDecision() {
        var created = service.create(validRequest("Review approval event"), principal);
        var submitted = service.submit(created.eventId(), created.version(), principal);
        var review = reviewRepository.findByEvent_EventIdAndDecision(
                created.eventId(), "PENDING").orElseThrow();

        var approved = superAdminService.approveReview(
                review.getReviewId(), new ReviewDecisionRequest(submitted.version(), "Ready"),
                superAdmin);

        assertThat(approved.decision()).isEqualTo("APPROVED");
        assertThat(approved.event().status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(eventRepository.findById(created.eventId()).orElseThrow().getPublishedAt())
                .isNotNull();
        assertThat(reviewRepository.findById(review.getReviewId()).orElseThrow().getReviewedBy())
                .isNotNull();
    }

    @Test
    void rejectedEventAppearsInReviewAndAdminEventFilters() {
        var created = service.create(validRequest("Rejected event visibility test"), principal);
        var submitted = service.submit(created.eventId(), created.version(), principal);
        var review = reviewRepository.findByEvent_EventIdAndDecision(
                created.eventId(), EventReview.PENDING).orElseThrow();

        var rejected = superAdminService.rejectReview(
                review.getReviewId(),
                new ReviewDecisionRequest(submitted.version(), "Test rejection reason"),
                superAdmin);

        assertThat(rejected.decision()).isEqualTo(EventReview.REJECTED);
        assertThat(rejected.event().status()).isEqualTo(EventStatus.REJECTED);
        assertThat(service.stats(principal).rejected()).isEqualTo(1);
        assertThat(service.list(principal, EventStatus.REJECTED, "", PageRequest.of(0, 20)))
                .extracting(item -> item.eventId())
                .contains(created.eventId());
        assertThat(service.list(superAdmin, EventStatus.REJECTED, "", PageRequest.of(0, 20)))
                .extracting(item -> item.eventId())
                .contains(created.eventId());
        assertThat(superAdminService.reviews(EventReview.REJECTED, PageRequest.of(0, 20)))
                .extracting(item -> item.reviewId())
                .contains(review.getReviewId());
    }

    @Test
    void superAdminApprovesChangeRequestAndAppliesStoredDiff() {
        EventWriteRequest original = validRequest("Before approved change");
        var created = service.create(original, principal);
        var event = eventRepository.findById(created.eventId()).orElseThrow();
        event.setStatus(EventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        eventRepository.saveAndFlush(event);
        var published = service.get(created.eventId(), principal);
        var request = service.requestChanges(
                created.eventId(), published.version(),
                new EventChangeRequestCreateRequest(
                        "Final title", withTitle(original, "After approved change")), principal);

        var approved = superAdminService.approveChangeRequest(
                request.changeRequestId(), new ReviewDecisionRequest(request.version(), "Approved"),
                superAdmin);

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(eventRepository.findById(created.eventId()).orElseThrow().getTitle())
                .isEqualTo("After approved change");
    }

    @Test
    void superAdminAssignsEligibleAdminAndPreservesOwner() {
        var created = service.create(validRequest("Assignment event"), principal);
        UUID assignedAdminId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into auth_users (user_id, email, password_hash, status) values (?, ?, ?, ?)",
                assignedAdminId, "assigned-" + assignedAdminId + "@example.test", "test-hash", "ACTIVE");
        UUID adminRoleId = jdbcTemplate.queryForObject(
                "select role_id from auth_roles where role_code = 'ADMIN'", UUID.class);
        jdbcTemplate.update(
                "insert into auth_user_roles (user_id, role_id) values (?, ?)",
                assignedAdminId, adminRoleId);

        var assignments = superAdminService.replaceEventAdmins(
                created.eventId(), new ReplaceEventAdminsRequest(Set.of(assignedAdminId)),
                superAdmin);

        assertThat(assignments).extracting(item -> item.userId())
                .containsExactlyInAnyOrder(adminId, assignedAdminId);
        assertThat(assignments).filteredOn(item -> item.userId().equals(adminId))
                .allMatch(item -> item.owner());
        assertThat(superAdminService.adminCandidates()).extracting(item -> item.userId())
                .contains(assignedAdminId);
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
