package MUDST_2026_Whatsss.event_registration.registration;

import MUDST_2026_Whatsss.event_registration.PostgresIntegrationTest;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.service.AdminEventService;
import MUDST_2026_Whatsss.event_registration.registration.domain.RegistrationStatus;
import MUDST_2026_Whatsss.event_registration.registration.service.NoShowSweeper;
import MUDST_2026_Whatsss.event_registration.registration.service.RegistrationService;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.CheckInRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.CreateRegistrationRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.EventAnalyticsResponse;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.GuestRegistrationRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventAnalyticsIT extends PostgresIntegrationTest {

    @Autowired private RegistrationService service;
    @Autowired private NoShowSweeper noShowSweeper;
    @Autowired private AdminEventService adminEventService;
    @Autowired private EventRepository eventRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;

    private RegistrationFixtures fixtures;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        fixtures = new RegistrationFixtures(jdbcTemplate, adminEventService, eventRepository);
        admin = fixtures.admin();
    }

    @Test
    void countsSignupsDropOffsAndShowRateOnceTheEventEnds() {
        UUID eventId = fixtures.publishedEvent(admin, "Analytics funnel", 10);
        var withdrew = service.register(eventId, consent(), fixtures.participant("withdrew", "Withdrew", null));
        var lapsed = service.register(eventId, consent(), fixtures.participant("lapsed", "Lapsed", null));
        var attended = service.register(eventId, consent(), fixtures.participant("attended", "Attended", null));
        var absent = service.register(eventId, consent(), fixtures.participant("absent", "Absent", null));
        var guestAttended = service.registerGuest(eventId, guest("guest-attended@example.test"));
        var removedByAdmin = service.registerGuest(eventId, guest("guest-removed@example.test"));

        service.cancel(withdrew.registrationId(), ownerOf(withdrew.registrationId()));
        setStatus(lapsed.registrationId(), "PAYMENT_EXPIRED");
        setStatus(removedByAdmin.registrationId(), "CANCELLED_BY_ADMIN");
        service.checkIn(eventId, new CheckInRequest(attended.registrationNumber()), admin);
        service.checkIn(eventId, new CheckInRequest(guestAttended.registrationNumber()), admin);

        var during = service.analytics(eventId, admin);
        assertThat(during.signups()).isEqualTo(6);
        assertThat(during.memberSignups()).isEqualTo(4);
        assertThat(during.guestSignups()).isEqualTo(2);
        assertThat(during.dropOffs()).isEqualTo(2);
        assertThat(during.dropOffRate()).isCloseTo(2.0 / 6, within(1e-9));
        assertThat(during.cancelledByAdmin()).isEqualTo(1);
        assertThat(during.confirmed()).isEqualTo(1);
        assertThat(during.checkedIn()).isEqualTo(2);
        assertThat(during.eventEnded()).isFalse();
        assertThat(during.showRate()).isNull();
        assertThat(during.dailySignups()).containsExactly(new EventAnalyticsResponse.DailySignups(
                LocalDate.now(ZoneId.of("Asia/Bangkok")), 6));

        endEvent(eventId, "1 day");
        // Counted as a no-show as soon as the event ends, before the sweep changes the status.
        var ended = service.analytics(eventId, admin);
        assertThat(ended.eventEnded()).isTrue();
        assertThat(ended.confirmed()).isEqualTo(1);
        assertThat(ended.noShows()).isEqualTo(1);
        assertThat(ended.showRate()).isCloseTo(2.0 / 3, within(1e-9));

        assertThat(noShowSweeper.markNoShows()).isEqualTo(1);
        assertThat(noShowSweeper.markNoShows()).isZero();

        var after = service.analytics(eventId, admin);
        assertThat(after.confirmed()).isZero();
        assertThat(after.noShows()).isEqualTo(1);
        assertThat(after.showRate()).isCloseTo(2.0 / 3, within(1e-9));
        String absentStatus = jdbcTemplate.queryForObject(
                "select status from registrations where registration_id = ?", String.class, absent.registrationId());
        assertThat(absentStatus).isEqualTo(RegistrationStatus.NO_SHOW.name());
        Integer history = jdbcTemplate.queryForObject("""
                select count(*) from registration_status_history
                where registration_id = ? and from_status = 'CONFIRMED' and to_status = 'NO_SHOW'""",
                Integer.class, absent.registrationId());
        assertThat(history).isEqualTo(1);
    }

    @Test
    void sweepLeavesEventsThatHaveNotEndedAlone() {
        UUID eventId = fixtures.publishedEvent(admin, "Analytics upcoming", 5);
        var registration = service.register(eventId, consent(), fixtures.participant("early", "Early", null));

        noShowSweeper.markNoShows();

        String status = jdbcTemplate.queryForObject(
                "select status from registrations where registration_id = ?", String.class, registration.registrationId());
        assertThat(status).isEqualTo(RegistrationStatus.CONFIRMED.name());
    }

    @Test
    void sweepWaitsForTheGracePeriodAfterTheEventEnds() {
        UUID eventId = fixtures.publishedEvent(admin, "Analytics overrun", 5);
        var registration = service.register(eventId, consent(), fixtures.participant("overrun", "Overrun", null));
        endEvent(eventId, "30 minutes");

        assertThat(noShowSweeper.markNoShows()).isZero();

        String status = jdbcTemplate.queryForObject(
                "select status from registrations where registration_id = ?", String.class, registration.registrationId());
        assertThat(status).isEqualTo(RegistrationStatus.CONFIRMED.name());
    }

    @Test
    void lateArrivalMarkedNoShowCanStillBeCheckedIn() {
        UUID eventId = fixtures.publishedEvent(admin, "Analytics late arrival", 5);
        var late = service.register(eventId, consent(), fixtures.participant("late", "Late", null));
        endEvent(eventId, "1 day");
        noShowSweeper.markNoShows();

        var checkedIn = service.checkIn(eventId, new CheckInRequest(late.registrationNumber()), admin);

        assertThat(checkedIn.status()).isEqualTo(RegistrationStatus.CHECKED_IN);
        entityManager.flush();
        Integer history = jdbcTemplate.queryForObject("""
                select count(*) from registration_status_history
                where registration_id = ? and from_status = 'NO_SHOW' and to_status = 'CHECKED_IN'""",
                Integer.class, late.registrationId());
        assertThat(history).isEqualTo(1);
        var analytics = service.analytics(eventId, admin);
        assertThat(analytics.noShows()).isZero();
        assertThat(analytics.showRate()).isEqualTo(1.0);
    }

    @Test
    void emptyEventHasNoRatesToReport() {
        UUID eventId = fixtures.publishedEvent(admin, "Analytics empty", 5);

        var analytics = service.analytics(eventId, admin);

        assertThat(analytics.signups()).isZero();
        assertThat(analytics.dropOffRate()).isNull();
        assertThat(analytics.showRate()).isNull();
        assertThat(analytics.dailySignups()).isEmpty();
    }

    @Test
    void onlyTheEventsAdministratorsCanSeeItsAnalytics() throws Exception {
        UUID eventId = fixtures.publishedEvent(admin, "Analytics scope", 5);
        AuthenticatedUser otherAdmin = fixtures.admin();

        assertThatThrownBy(() -> service.analytics(eventId, otherAdmin))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_FOUND));
        mockMvc.perform(get("/api/v1/admin/events/{eventId}/analytics", eventId).with(authentication(token(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signups").value(0))
                .andExpect(jsonPath("$.eventEnded").value(false));
        mockMvc.perform(get("/api/v1/admin/events/{eventId}/analytics", eventId).with(authentication(token(otherAdmin))))
                .andExpect(status().isNotFound());
        AuthenticatedUser participant = fixtures.participant("nosy", "Nosy", null);
        mockMvc.perform(get("/api/v1/admin/events/{eventId}/analytics", eventId).with(authentication(token(participant))))
                .andExpect(status().isForbidden());
    }

    private AuthenticatedUser ownerOf(UUID registrationId) {
        UUID userId = jdbcTemplate.queryForObject("""
                select p.user_id from registrations r join participants p on p.participant_id = r.participant_id
                where r.registration_id = ?""", UUID.class, registrationId);
        return new AuthenticatedUser(userId, "owner@example.test", "USER", java.util.List.of("USER"),
                java.util.List.of("REGISTRATION_CREATE_SELF", "REGISTRATION_READ_SELF", "REGISTRATION_CANCEL_SELF"));
    }

    private void setStatus(UUID registrationId, String status) {
        jdbcTemplate.update("""
                update registrations set status = ?, cancelled_at = registered_at, confirmed_at = null
                where registration_id = ?""", status, registrationId);
    }

    /** Moves the event into the past so that it ended {@code endedAgo} (a Postgres interval) ago. */
    private void endEvent(UUID eventId, String endedAgo) {
        // Plain JDBC bypasses Hibernate: write pending changes first and drop the stale event after.
        entityManager.flush();
        jdbcTemplate.update("""
                update events set registration_start_at = now() - interval '3 days',
                    registration_end_at = now() - cast(? as interval) - interval '2 hours',
                    start_at = now() - cast(? as interval) - interval '1 hour',
                    end_at = now() - cast(? as interval),
                    cancellation_deadline_at = null
                where event_id = ?""", endedAgo, endedAgo, endedAgo, eventId);
        entityManager.clear();
    }

    private static UsernamePasswordAuthenticationToken token(AuthenticatedUser user) {
        return UsernamePasswordAuthenticationToken.authenticated(user, null,
                user.permissions().stream().map(SimpleGrantedAuthority::new).toList());
    }

    private static CreateRegistrationRequest consent() {
        return new CreateRegistrationRequest(null, null, null, true);
    }

    private static GuestRegistrationRequest guest(String email) {
        return new GuestRegistrationRequest("Guest " + email, email, null, true);
    }
}
