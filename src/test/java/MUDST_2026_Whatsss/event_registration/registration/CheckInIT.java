package MUDST_2026_Whatsss.event_registration.registration;

import MUDST_2026_Whatsss.event_registration.PostgresIntegrationTest;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.service.AdminEventService;
import MUDST_2026_Whatsss.event_registration.registration.domain.RegistrationStatus;
import MUDST_2026_Whatsss.event_registration.registration.service.RegistrationService;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.CheckInRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.CreateRegistrationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CheckInIT extends PostgresIntegrationTest {

    @Autowired private RegistrationService service;
    @Autowired private AdminEventService adminEventService;
    @Autowired private EventRepository eventRepository;
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
    void assignedAdminChecksInAConfirmedRegistration() {
        UUID eventId = fixtures.publishedEvent(admin, "Check-in happy path", 5);
        var registration = service.register(eventId, consent(), fixtures.participant("kim", "Kim", null));

        // Lower case and surrounding spaces are accepted for numbers typed in by staff.
        var checkedIn = service.checkIn(eventId,
                new CheckInRequest("  " + registration.registrationNumber().toLowerCase() + " "), admin);

        assertThat(checkedIn.registrationId()).isEqualTo(registration.registrationId());
        assertThat(checkedIn.status()).isEqualTo(RegistrationStatus.CHECKED_IN);
        assertThat(checkedIn.checkedInAt()).isNotNull();
        assertThat(service.listForEvent(eventId, "", PageRequest.of(0, 50), admin).getContent())
                .extracting(item -> item.status()).containsExactly(RegistrationStatus.CHECKED_IN);
        // A checked-in attendee still holds their seat. The native count also flushes the history row.
        assertThat(eventRepository.countActiveRegistrations(eventId)).isEqualTo(1);
        UUID checkedInBy = jdbcTemplate.queryForObject(
                "select checked_in_by_user_id from registrations where registration_id = ?",
                UUID.class, registration.registrationId());
        assertThat(checkedInBy).isEqualTo(admin.userId());
        Integer history = jdbcTemplate.queryForObject(
                "select count(*) from registration_status_history where registration_id = ? and to_status = 'CHECKED_IN'",
                Integer.class, registration.registrationId());
        assertThat(history).isEqualTo(1);
    }

    @Test
    void secondCheckInIsRejected() {
        UUID eventId = fixtures.publishedEvent(admin, "Double check-in", 5);
        var registration = service.register(eventId, consent(), fixtures.participant("leo", "Leo", null));
        service.checkIn(eventId, new CheckInRequest(registration.registrationNumber()), admin);

        assertError(() -> service.checkIn(eventId, new CheckInRequest(registration.registrationNumber()), admin),
                ErrorCode.ALREADY_CHECKED_IN);
    }

    @Test
    void cancelledRegistrationCannotBeCheckedIn() {
        UUID eventId = fixtures.publishedEvent(admin, "Cancelled check-in", 5);
        AuthenticatedUser mia = fixtures.participant("mia", "Mia", null);
        var registration = service.register(eventId, consent(), mia);
        service.cancel(registration.registrationId(), mia);

        assertError(() -> service.checkIn(eventId, new CheckInRequest(registration.registrationNumber()), admin),
                ErrorCode.REGISTRATION_NOT_CHECKABLE);
    }

    @Test
    void unknownNumberOrNumberFromAnotherEventIsNotFound() {
        UUID eventId = fixtures.publishedEvent(admin, "Check-in event", 5);
        UUID otherEventId = fixtures.publishedEvent(admin, "Other check-in event", 5);
        var other = service.register(otherEventId, consent(), fixtures.participant("noah", "Noah", null));

        assertError(() -> service.checkIn(eventId, new CheckInRequest("REG-UNKNOWN000"), admin),
                ErrorCode.REGISTRATION_NOT_FOUND);
        assertError(() -> service.checkIn(eventId, new CheckInRequest(other.registrationNumber()), admin),
                ErrorCode.REGISTRATION_NOT_FOUND);
    }

    @Test
    void adminNotAssignedToTheEventCannotCheckIn() {
        UUID eventId = fixtures.publishedEvent(admin, "Check-in scope", 5);
        var registration = service.register(eventId, consent(), fixtures.participant("olga", "Olga", null));

        assertError(() -> service.checkIn(eventId, new CheckInRequest(registration.registrationNumber()),
                fixtures.admin()), ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void participantsAndGuestsCannotCallTheCheckInEndpoint() throws Exception {
        UUID eventId = fixtures.publishedEvent(admin, "Check-in access", 5);
        AuthenticatedUser paul = fixtures.participant("paul", "Paul", null);
        var registration = service.register(eventId, consent(), paul);
        String body = "{\"registrationNumber\":\"" + registration.registrationNumber() + "\"}";

        mockMvc.perform(post("/api/v1/admin/events/{eventId}/check-in", eventId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/events/{eventId}/check-in", eventId)
                        .with(authentication(authenticated(paul))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/events/{eventId}/check-in", eventId)
                        .with(authentication(authenticated(admin))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));
    }

    private static UsernamePasswordAuthenticationToken authenticated(AuthenticatedUser user) {
        return UsernamePasswordAuthenticationToken.authenticated(user, null,
                user.permissions().stream().map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    void syncedOfflineCheckInKeepsTheTimeItWasScanned() {
        UUID eventId = fixtures.publishedEvent(admin, "Offline check-in time", 5);
        var registration = service.register(eventId, consent(), fixtures.participant("offline", "Offline", null));
        // Scanned offline right after registering, synced a moment later.
        Instant scannedAt = registration.registeredAt();

        var checkedIn = service.checkIn(eventId, new CheckInRequest(registration.registrationNumber(), scannedAt), admin);

        assertThat(checkedIn.checkedInAt()).isEqualTo(scannedAt);
    }

    @Test
    void checkInTimeInTheFutureOrBeforeRegistrationIsRejected() {
        UUID eventId = fixtures.publishedEvent(admin, "Offline check-in bad time", 5);
        var registration = service.register(eventId, consent(), fixtures.participant("clock", "Clock", null));
        String number = registration.registrationNumber();

        assertError(() -> service.checkIn(eventId,
                new CheckInRequest(number, Instant.now().plusSeconds(3600)), admin), ErrorCode.INVALID_CHECK_IN_TIME);
        assertError(() -> service.checkIn(eventId,
                new CheckInRequest(number, registration.registeredAt().minusSeconds(60)), admin), ErrorCode.INVALID_CHECK_IN_TIME);
        // A device clock a minute ahead is tolerated and capped at the server time.
        var checkedIn = service.checkIn(eventId, new CheckInRequest(number, Instant.now().plusSeconds(60)), admin);
        assertThat(checkedIn.checkedInAt()).isBeforeOrEqualTo(Instant.now());
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
