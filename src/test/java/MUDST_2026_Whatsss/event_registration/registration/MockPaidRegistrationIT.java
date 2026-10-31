package MUDST_2026_Whatsss.event_registration.registration;

import MUDST_2026_Whatsss.event_registration.PostgresIntegrationTest;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.auth.service.AuthService;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.RegisterRequest;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.service.AdminEventService;
import MUDST_2026_Whatsss.event_registration.registration.domain.RegistrationStatus;
import MUDST_2026_Whatsss.event_registration.registration.service.RegistrationService;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.CreateRegistrationRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.GuestRegistrationRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.GuestTicketLookupRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "event.registration.mock-payments-enabled=true")
@AutoConfigureMockMvc
@Transactional
class MockPaidRegistrationIT extends PostgresIntegrationTest {
    @Autowired RegistrationService service;
    @Autowired AdminEventService adminService;
    @Autowired EventRepository events;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    RegistrationFixtures fixtures;
    AuthenticatedUser admin;
    UUID eventId;

    @BeforeEach
    void setUp() {
        fixtures = new RegistrationFixtures(jdbc, adminService, events);
        admin = fixtures.admin();
        eventId = fixtures.publishedEvent(admin, "Paid mock lifecycle", 1);
        var event = events.findById(eventId).orElseThrow();
        event.setEventType(EventType.PAID);
        event.setPrice(new BigDecimal("99.00"));
        events.saveAndFlush(event);
    }

    private CreateRegistrationRequest consent() { return new CreateRegistrationRequest(null, null, null, true); }

    @Test
    void paymentConfirmsPersistedRegistrationExactlyOnceAndAppearsForAdminAndUser() throws Exception {
        var user = fixtures.participant("payer", "Payer", null);
        var pending = service.register(eventId, consent(), user);
        assertThat(pending.status()).isEqualTo(RegistrationStatus.PENDING_PAYMENT);
        assertThat(pending.confirmedAt()).isNull();
        assertThat(service.register(eventId, consent(), user).registrationId()).isEqualTo(pending.registrationId());
        assertError(() -> service.register(eventId, consent(), fixtures.participant("other", "Other", null)), ErrorCode.EVENT_FULL);
        var auth = UsernamePasswordAuthenticationToken.authenticated(user, null,
                user.permissions().stream().map(SimpleGrantedAuthority::new).toList());
        mvc.perform(post("/api/v1/registrations/{id}/mock-payment/confirm", pending.registrationId())
                .with(authentication(auth)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        var confirmed = service.confirmMockPayment(pending.registrationId(), user);
        assertThat(confirmed.confirmedAt()).isNotNull();
        assertThat(confirmed.registrationId()).isEqualTo(pending.registrationId());
        assertThat(service.mine(user)).extracting(r -> r.status()).containsExactly(RegistrationStatus.CONFIRMED);
        assertThat(service.register(eventId, consent(), user).registrationId()).isEqualTo(pending.registrationId());
        assertThat(service.listForEvent(eventId, "", org.springframework.data.domain.PageRequest.of(0, 10), admin)
                .getContent()).extracting(r -> r.status()).containsExactly(RegistrationStatus.CONFIRMED);
        assertThat(events.countActiveRegistrations(eventId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from registration_status_history where registration_id = ?", Integer.class,
                pending.registrationId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select reason from registration_status_history where registration_id = ? and to_status = 'CONFIRMED'", String.class,
                pending.registrationId())).contains("MOCK_PAYMENT_CONFIRMED");
    }

    @Test
    void strangersGuestsAndMissingCsrfCannotConfirmPayment() throws Exception {
        var user = fixtures.participant("owner", "Owner", null);
        var pending = service.register(eventId, consent(), user);
        assertError(() -> service.confirmMockPayment(pending.registrationId(), fixtures.participant("stranger", "Stranger", null)), ErrorCode.REGISTRATION_NOT_FOUND);
        mvc.perform(post("/api/v1/registrations/{id}/mock-payment/confirm", pending.registrationId()).with(csrf()))
                .andExpect(status().isUnauthorized());
        var auth = UsernamePasswordAuthenticationToken.authenticated(user, null,
                user.permissions().stream().map(SimpleGrantedAuthority::new).toList());
        mvc.perform(post("/api/v1/registrations/{id}/mock-payment/confirm", pending.registrationId()).with(authentication(auth)))
                .andExpect(status().isForbidden());
        assertThat(service.get(pending.registrationId(), user).status()).isEqualTo(RegistrationStatus.PENDING_PAYMENT);
    }

    @Test
    void cancelledOrClosedPaymentCannotBeConfirmed() {
        var user = fixtures.participant("cancel", "Cancel", null);
        var pending = service.register(eventId, consent(), user);
        service.cancel(pending.registrationId(), user);
        assertError(() -> service.confirmMockPayment(pending.registrationId(), user), ErrorCode.INVALID_REGISTRATION_STATE);
        var again = service.register(eventId, consent(), user);
        var event = events.findById(eventId).orElseThrow();
        event.setRegistrationEndAt(java.time.Instant.now().minusSeconds(1));
        events.saveAndFlush(event);
        assertError(() -> service.confirmMockPayment(again.registrationId(), user), ErrorCode.REGISTRATION_CLOSED);
    }

    @Test
    void guestPaymentPersistsOnceAndLinksToAnAccountWithTheSameVerifiedEmail() throws Exception {
        String email = "paid-guest-" + UUID.randomUUID() + "@example.test";
        var pending = service.registerGuest(eventId, new GuestRegistrationRequest("Guest Payer", email, "0812345678", true));
        var lookup = new GuestTicketLookupRequest(pending.registrationNumber().toLowerCase(), email.toUpperCase());
        assertThat(pending.status()).isEqualTo(RegistrationStatus.PENDING_PAYMENT);
        assertThat(pending.confirmedAt()).isNull();
        assertThat(pending.registeredAsGuest()).isTrue();
        assertThat(events.countActiveRegistrations(eventId)).isEqualTo(1);
        assertError(() -> service.registerGuest(eventId, new GuestRegistrationRequest("Duplicate", email.toUpperCase(), null, true)),
                ErrorCode.GUEST_ALREADY_REGISTERED);
        assertError(() -> service.registerGuest(eventId, new GuestRegistrationRequest("Other", "other@example.test", null, true)),
                ErrorCode.EVENT_FULL);
        assertThat(jdbc.queryForObject("select participant_id is null from registrations where registration_id = ?",
                Boolean.class, pending.registrationId())).isTrue();

        mvc.perform(post("/api/v1/guest-registrations/mock-payment/confirm").with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"registrationNumber\":\"" + pending.registrationNumber() + "\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.cancellable").value(false));
        assertThat(service.confirmGuestMockPayment(lookup).confirmedAt()).isNotNull();
        assertThat(service.guestTicket(lookup).status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(jdbc.queryForObject("select count(*) from registration_status_history where registration_id = ?",
                Integer.class, pending.registrationId())).isEqualTo(2);
        assertThat(events.countActiveRegistrations(eventId)).isEqualTo(1);
        assertThat(service.listForEvent(eventId, "", org.springframework.data.domain.PageRequest.of(0, 10), admin)
                .getContent()).extracting(r -> r.status()).containsExactly(RegistrationStatus.CONFIRMED);

        UUID userId = authService.register(new RegisterRequest(email, "Password123", "Guest", "Payer", "0812345678")).userId();
        var member = new AuthenticatedUser(userId, email, "USER", List.of("USER"), List.of("REGISTRATION_READ_SELF"));
        assertThat(service.mine(member)).singleElement().satisfies(r -> {
            assertThat(r.registrationId()).isEqualTo(pending.registrationId());
            assertThat(r.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        });
    }

    @Test
    void guestConfirmationRequiresMatchingGuestTicketAndCsrf() throws Exception {
        var pending = service.registerGuest(eventId, new GuestRegistrationRequest("Guest", "guest@example.test", null, true));
        String body = "{\"registrationNumber\":\"" + pending.registrationNumber() + "\",\"email\":\"guest@example.test\"}";
        mvc.perform(post("/api/v1/guest-registrations/mock-payment/confirm")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertError(() -> service.confirmGuestMockPayment(new GuestTicketLookupRequest(pending.registrationNumber(), "wrong@example.test")),
                ErrorCode.REGISTRATION_NOT_FOUND);
        assertError(() -> service.confirmGuestMockPayment(new GuestTicketLookupRequest("REG-WRONG00000", "guest@example.test")),
                ErrorCode.REGISTRATION_NOT_FOUND);
        assertThat(service.guestTicket(new GuestTicketLookupRequest(pending.registrationNumber(), "guest@example.test")).status())
                .isEqualTo(RegistrationStatus.PENDING_PAYMENT);
        var event = events.findById(eventId).orElseThrow();
        event.setRegistrationEndAt(java.time.Instant.now().minusSeconds(1));
        events.saveAndFlush(event);
        assertError(() -> service.confirmGuestMockPayment(new GuestTicketLookupRequest(pending.registrationNumber(), "guest@example.test")),
                ErrorCode.REGISTRATION_CLOSED);
    }

    @Test
    void memberPaymentsCannotBeConfirmedThroughThePublicGuestEndpoint() {
        var member = fixtures.participant("member-payment", "Member", "Payer");
        var pending = service.register(eventId, consent(), member);
        assertError(() -> service.confirmGuestMockPayment(new GuestTicketLookupRequest(pending.registrationNumber(), pending.attendeeEmail())),
                ErrorCode.REGISTRATION_NOT_FOUND);
        assertThat(service.get(pending.registrationId(), member).status()).isEqualTo(RegistrationStatus.PENDING_PAYMENT);
    }

    private void assertError(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
