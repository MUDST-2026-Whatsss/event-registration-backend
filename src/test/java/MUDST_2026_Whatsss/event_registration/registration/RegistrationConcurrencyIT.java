package MUDST_2026_Whatsss.event_registration.registration;

import MUDST_2026_Whatsss.event_registration.PostgresIntegrationTest;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.service.AdminEventService;
import MUDST_2026_Whatsss.event_registration.registration.service.RegistrationService;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.CreateRegistrationRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.GuestRegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Not {@code @Transactional}: each registration must commit in its own thread so the event-row
 * lock is genuinely contended. The committed event is removed afterwards so it cannot leak into
 * other tests' catalogue queries.
 */
@SpringBootTest
class RegistrationConcurrencyIT extends PostgresIntegrationTest {

    private static final int CONTENDERS = 8;

    @Autowired private RegistrationService service;
    @Autowired private AdminEventService adminEventService;
    @Autowired private EventRepository eventRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void lastSeatIsSoldExactlyOnceUnderConcurrentRequests() throws Exception {
        var fixtures = new RegistrationFixtures(jdbcTemplate, adminEventService, eventRepository);
        UUID eventId = fixtures.publishedEvent(fixtures.admin(), "Concurrent last seat", 1);
        List<AuthenticatedUser> participants = new ArrayList<>();
        for (int i = 0; i < CONTENDERS; i++) {
            participants.add(fixtures.participant("racer" + i, "Racer", String.valueOf(i)));
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS);
        List<Future<ErrorCode>> outcomes = new ArrayList<>();
        try {
            for (AuthenticatedUser participant : participants) {
                outcomes.add(pool.submit(() -> {
                    start.await();
                    try {
                        service.register(eventId, new CreateRegistrationRequest(null, null, null, true),
                                participant);
                        return null;
                    } catch (ApiException exception) {
                        return exception.getErrorCode();
                    }
                }));
            }
            start.countDown();

            int confirmed = 0;
            int full = 0;
            for (Future<ErrorCode> outcome : outcomes) {
                ErrorCode error = outcome.get(30, TimeUnit.SECONDS);
                if (error == null) {
                    confirmed++;
                } else if (error == ErrorCode.EVENT_FULL) {
                    full++;
                }
            }
            assertThat(confirmed).isEqualTo(1);
            assertThat(full).isEqualTo(CONTENDERS - 1);
            assertThat(eventRepository.countActiveRegistrations(eventId)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            deleteEvent(eventId);
        }
    }

    @Test
    void memberAndGuestUsingTheSameEmailCannotTakeTwoSeatsConcurrently() throws Exception {
        var fixtures = new RegistrationFixtures(jdbcTemplate, adminEventService, eventRepository);
        UUID eventId = fixtures.publishedEvent(fixtures.admin(), "Concurrent email duplicate", 5);
        var member = fixtures.participant("email-racer", "Email", "Racer");
        String email = "race-" + UUID.randomUUID() + "@example.test";
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<ErrorCode>> outcomes = new ArrayList<>();
        try {
            outcomes.add(pool.submit(() -> {
                start.await();
                try {
                    service.register(eventId, new CreateRegistrationRequest(null, email, null, true), member);
                    return null;
                } catch (ApiException exception) {
                    return exception.getErrorCode();
                }
            }));
            outcomes.add(pool.submit(() -> {
                start.await();
                try {
                    service.registerGuest(eventId, new GuestRegistrationRequest(
                            "Guest Racer", "  " + email.toUpperCase(java.util.Locale.ROOT) + "  ", null, true));
                    return null;
                } catch (ApiException exception) {
                    return exception.getErrorCode();
                }
            }));
            start.countDown();
            List<ErrorCode> results = new ArrayList<>();
            for (Future<ErrorCode> outcome : outcomes) results.add(outcome.get(30, TimeUnit.SECONDS));
            assertThat(results.stream().filter(java.util.Objects::isNull).count()).isEqualTo(1);
            assertThat(results.stream().filter(java.util.Objects::nonNull).toList())
                    .hasSize(1).allMatch(code -> code == ErrorCode.ALREADY_REGISTERED
                            || code == ErrorCode.GUEST_ALREADY_REGISTERED);
            assertThat(eventRepository.countActiveRegistrations(eventId)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            deleteEvent(eventId);
        }
    }

    private void deleteEvent(UUID eventId) {
        jdbcTemplate.update("""
                delete from registration_status_history where registration_id in
                    (select registration_id from registrations where event_id = ?)""", eventId);
        jdbcTemplate.update("delete from registrations where event_id = ?", eventId);
        jdbcTemplate.update("delete from event_admin_assignments where event_id = ?", eventId);
        jdbcTemplate.update("delete from audit_logs where target_id = ?", eventId);
        jdbcTemplate.update("delete from events where event_id = ?", eventId);
    }
}
