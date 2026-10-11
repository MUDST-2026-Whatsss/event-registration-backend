package MUDST_2026_Whatsss.event_registration.registration;

import MUDST_2026_Whatsss.event_registration.PostgresIntegrationTest;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.service.AdminEventService;
import MUDST_2026_Whatsss.event_registration.event.web.EventController;
import MUDST_2026_Whatsss.event_registration.event.web.dto.CancelEventRequest;
import MUDST_2026_Whatsss.event_registration.registration.domain.RegistrationStatus;
import MUDST_2026_Whatsss.event_registration.registration.service.RegistrationService;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.CreateRegistrationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationIT extends PostgresIntegrationTest {

    @Autowired private RegistrationService service;
    @Autowired private AdminEventService adminEventService;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventController eventController;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;

    private RegistrationFixtures fixtures;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        fixtures = new RegistrationFixtures(jdbcTemplate, adminEventService, eventRepository);
        admin = fixtures.admin();
    }

    @Test
    void participantRegistersAndSeesSeatCountDrop() {
        UUID eventId = fixtures.publishedEvent(admin, "Registration happy path", 3);
        AuthenticatedUser alice = fixtures.participant("alice", "Alice", "Example");

        var registration = service.register(eventId, consent(), alice);

        assertThat(registration.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registration.registrationNumber()).matches("^REG-[A-Z0-9]{10}$");
        assertThat(registration.attendeeName()).isEqualTo("Alice Example");
        assertThat(registration.attendeeEmail()).isEqualTo(alice.email());
        assertThat(registration.cancellable()).isTrue();
        assertThat(registration.event().remainingSeats()).isEqualTo(2);
        assertThat(service.mine(alice)).extracting(item -> item.registrationId())
                .containsExactly(registration.registrationId());
        Integer history = jdbcTemplate.queryForObject(
                "select count(*) from registration_status_history where registration_id = ?",
                Integer.class, registration.registrationId());
        assertThat(history).isEqualTo(1);
    }

    @Test
    void duplicateRegistrationIsRejected() {
        UUID eventId = fixtures.publishedEvent(admin, "Duplicate registration", 3);
        AuthenticatedUser bob = fixtures.participant("bob", "Bob", null);
        service.register(eventId, consent(), bob);

        assertError(() -> service.register(eventId, consent(), bob), ErrorCode.ALREADY_REGISTERED);
    }

    @Test
    void maximumParticipantsIsEnforced() {
        UUID eventId = fixtures.publishedEvent(admin, "Capacity limit", 2);
        service.register(eventId, consent(), fixtures.participant("p1", "One", null));
        var second = service.register(eventId, consent(), fixtures.participant("p2", "Two", null));

        assertThat(second.event().remainingSeats()).isZero();
        assertThat(second.event().full()).isTrue();
        assertError(() -> service.register(eventId, consent(), fixtures.participant("p3", "Three", null)),
                ErrorCode.EVENT_FULL);
    }

    @Test
    void cancellingFreesTheSeatAndReRegisteringReusesTheRow() {
        UUID eventId = fixtures.publishedEvent(admin, "Cancel and return", 1);
        AuthenticatedUser carol = fixtures.participant("carol", "Carol", null);
        var first = service.register(eventId, consent(), carol);

        var cancelled = service.cancel(first.registrationId(), carol);
        assertThat(cancelled.status()).isEqualTo(RegistrationStatus.CANCELLED_BY_USER);
        assertThat(cancelled.event().remainingSeats()).isEqualTo(1);

        var again = service.register(eventId, consent(), carol);
        assertThat(again.registrationId()).isEqualTo(first.registrationId());
        assertThat(again.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(again.cancelledAt()).isNull();
    }

    @Test
    void anotherParticipantCannotSeeOrCancelARegistration() {
        UUID eventId = fixtures.publishedEvent(admin, "Ownership check", 5);
        var owned = service.register(eventId, consent(), fixtures.participant("owner", "Owner", null));
        AuthenticatedUser stranger = fixtures.participant("stranger", "Stranger", null);

        assertError(() -> service.get(owned.registrationId(), stranger), ErrorCode.REGISTRATION_NOT_FOUND);
        assertError(() -> service.cancel(owned.registrationId(), stranger), ErrorCode.REGISTRATION_NOT_FOUND);
    }

    @Test
    void registrationOutsideTheWindowOrForUnpublishedEventsIsRejected() {
        UUID closed = fixtures.publishedEvent(admin, "Closed window", 5);
        var event = eventRepository.findById(closed).orElseThrow();
        event.setRegistrationStartAt(Instant.now().plusSeconds(3600));
        eventRepository.saveAndFlush(event);
        UUID draft = adminEventService.create(fixtures.request("Draft only", 5), admin).eventId();
        AuthenticatedUser dave = fixtures.participant("dave", "Dave", null);

        assertError(() -> service.register(closed, consent(), dave), ErrorCode.REGISTRATION_CLOSED);
        assertError(() -> service.register(draft, consent(), dave), ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void paidEventsAreNotRegistrableYet() {
        UUID eventId = fixtures.publishedEvent(admin, "Paid event", 5);
        var event = eventRepository.findById(eventId).orElseThrow();
        event.setEventType(EventType.PAID);
        event.setPrice(new BigDecimal("100.00"));
        eventRepository.saveAndFlush(event);

        assertError(() -> service.register(eventId, consent(), fixtures.participant("erin", "Erin", null)),
                ErrorCode.PAID_REGISTRATION_UNAVAILABLE);
    }

    @Test
    void assignedAdminListsParticipantsButOtherAdminsCannot() {
        UUID eventId = fixtures.publishedEvent(admin, "Participant list", 5);
        service.register(eventId, consent(), fixtures.participant("frank", "Frank", "Lee"));
        service.register(eventId, consent(), fixtures.participant("grace", "Grace", "Kim"));

        var page = service.listForEvent(eventId, "", PageRequest.of(0, 50), admin);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(item -> item.attendeeName())
                .containsExactlyInAnyOrder("Frank Lee", "Grace Kim");
        assertThat(service.listForEvent(eventId, "grace", PageRequest.of(0, 50), admin)
                .getTotalElements()).isEqualTo(1);

        assertError(() -> service.listForEvent(eventId, "", PageRequest.of(0, 50), fixtures.admin()),
                ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void cancellingTheEventCancelsEveryActiveRegistration() {
        UUID eventId = fixtures.publishedEvent(admin, "Event cancellation cascade", 5);
        AuthenticatedUser heidi = fixtures.participant("heidi", "Heidi", null);
        service.register(eventId, consent(), heidi);
        long version = eventRepository.findById(eventId).orElseThrow().getVersion();

        adminEventService.cancel(eventId, new CancelEventRequest("Venue closed", version), admin);

        var mine = service.mine(heidi);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).status()).isEqualTo(RegistrationStatus.CANCELLED_EVENT);
        assertThat(mine.get(0).cancellationReason()).isEqualTo("Venue closed");
        assertThat(mine.get(0).cancellable()).isFalse();
    }

    @Test
    void publicCatalogueShowsRemainingSeatsAndHidesFinishedEvents() {
        UUID upcoming = fixtures.publishedEvent(admin, "Upcoming catalogue event", 10);
        UUID finished = fixtures.publishedEvent(admin, "Finished catalogue event", 10);
        service.register(upcoming, consent(), fixtures.participant("ivan", "Ivan", null));
        jdbcTemplate.update("""
                update events set registration_start_at = now() - interval '3 days',
                    registration_end_at = now() - interval '2 days',
                    start_at = now() - interval '2 days', end_at = now() - interval '1 day',
                    cancellation_deadline_at = null
                where event_id = ?""", finished);

        var catalogue = eventController.listPublished();

        assertThat(catalogue).extracting(item -> item.eventId()).contains(upcoming).doesNotContain(finished);
        var summary = catalogue.stream().filter(item -> item.eventId().equals(upcoming)).findFirst().orElseThrow();
        assertThat(summary.maximumParticipants()).isEqualTo(10);
        assertThat(summary.remainingSeats()).isEqualTo(9);
        assertThat(summary.full()).isFalse();
    }

    @Test
    void hiddenSeatCountIsNullButFullFlagIsStillExposed() {
        UUID eventId = fixtures.publishedEvent(admin, "Hidden seat count", 1);
        var event = eventRepository.findById(eventId).orElseThrow();
        event.setShowRemainingSeats(false);
        eventRepository.saveAndFlush(event);
        service.register(eventId, consent(), fixtures.participant("judy", "Judy", null));

        var detail = eventController.getBySlug(event.getSlug());

        assertThat(detail.remainingSeats()).isNull();
        assertThat(detail.full()).isTrue();
    }

    @Test
    void draftEventCanBeDeletedButPublishedEventCannot() {
        var draft = adminEventService.create(fixtures.request("Deletable draft", 5), admin);
        adminEventService.delete(draft.eventId(), draft.version(), admin);
        assertThat(eventRepository.findById(draft.eventId())).isEmpty();

        UUID published = fixtures.publishedEvent(admin, "Published keeps history", 5);
        long version = eventRepository.findById(published).orElseThrow().getVersion();
        assertError(() -> adminEventService.delete(published, version, admin), ErrorCode.INVALID_EVENT_STATE);
        assertThat(eventRepository.findById(published).orElseThrow().getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void guestsCannotRegisterOrListRegistrations() throws Exception {
        UUID eventId = fixtures.publishedEvent(admin, "Guest blocked", 5);

        mockMvc.perform(post("/api/v1/events/{eventId}/registrations", eventId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentAccepted\":true}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/registrations"))
                .andExpect(status().isUnauthorized());
    }

    private static CreateRegistrationRequest consent() {
        return new CreateRegistrationRequest(null, null, null, true);
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
