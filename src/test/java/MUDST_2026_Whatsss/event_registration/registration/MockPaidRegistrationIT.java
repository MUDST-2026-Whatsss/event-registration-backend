package MUDST_2026_Whatsss.event_registration.registration;

import MUDST_2026_Whatsss.event_registration.PostgresIntegrationTest;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.service.AdminEventService;
import MUDST_2026_Whatsss.event_registration.registration.domain.RegistrationStatus;
import MUDST_2026_Whatsss.event_registration.registration.service.RegistrationService;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.CreateRegistrationRequest;
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

    private void assertError(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
