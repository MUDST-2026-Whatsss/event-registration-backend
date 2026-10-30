package MUDST_2026_Whatsss.event_registration.registration;

import MUDST_2026_Whatsss.event_registration.PostgresIntegrationTest;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.auth.service.AuthService;
import MUDST_2026_Whatsss.event_registration.auth.service.RequestContext;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.LoginRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.RegisterRequest;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.service.AdminEventService;
import MUDST_2026_Whatsss.event_registration.registration.domain.RegistrationStatus;
import MUDST_2026_Whatsss.event_registration.registration.service.RegistrationService;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.CheckInRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.CreateRegistrationRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.GuestRegistrationRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.GuestTicketLookupRequest;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.RegistrationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuestRegistrationIT extends PostgresIntegrationTest {

    private static final RequestContext CONTEXT = RequestContext.of("127.0.0.1", "guest-registration-test");

    @Autowired private RegistrationService service;
    @Autowired private AuthService authService;
    @Autowired private AdminEventService adminEventService;
    @Autowired private EventRepository eventRepository;
    @Autowired private PasswordEncoder passwordEncoder;
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
    void guestRegistersWithoutSigningInAndTakesASeat() throws Exception {
        UUID eventId = fixtures.publishedEvent(admin, "Guest happy path", 3);

        mockMvc.perform(post("/api/v1/events/{eventId}/guest-registrations", eventId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"attendeeName":"Gina Guest","attendeeEmail":"gina@example.test",
                                 "attendeePhone":"0812345678","consentAccepted":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationNumber").value(org.hamcrest.Matchers.matchesPattern("^REG-[A-Z0-9]{10}$")))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.registeredAsGuest").value(true))
                .andExpect(jsonPath("$.cancellable").value(false))
                .andExpect(jsonPath("$.event.remainingSeats").value(2));

        // The guest row is kept on record with no account behind it.
        var row = jdbcTemplate.queryForMap(
                "select participant_id, registered_by_user_id, registered_as_guest from registrations where event_id = ?",
                eventId);
        assertThat(row.get("participant_id")).isNull();
        assertThat(row.get("registered_by_user_id")).isNull();
        assertThat(row.get("registered_as_guest")).isEqualTo(true);
    }

    @Test
    void guestRequestsAreValidatedAndNeedCsrf() throws Exception {
        UUID eventId = fixtures.publishedEvent(admin, "Guest validation", 3);

        mockMvc.perform(post("/api/v1/events/{eventId}/guest-registrations", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestJson("csrf@example.test")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/events/{eventId}/guest-registrations", eventId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendeeName\":\"\",\"attendeeEmail\":\"not-an-email\",\"consentAccepted\":true}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/events/{eventId}/guest-registrations", eventId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendeeName\":\"No Consent\",\"attendeeEmail\":\"nc@example.test\",\"consentAccepted\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oneGuestEmailHoldsOneSeatPerEventAndCapacityStillApplies() {
        UUID eventId = fixtures.publishedEvent(admin, "Guest duplicates", 2);
        service.registerGuest(eventId, guest("Dup", "dup@example.test"));

        assertError(() -> service.registerGuest(eventId, guest("Dup Again", "  DUP@Example.test ")),
                ErrorCode.GUEST_ALREADY_REGISTERED);

        service.registerGuest(eventId, guest("Second", "second@example.test"));
        assertError(() -> service.registerGuest(eventId, guest("Third", "third@example.test")),
                ErrorCode.EVENT_FULL);
    }

    @Test
    void guestsCannotRegisterForPaidOrUnpublishedEvents() {
        UUID paid = fixtures.publishedEvent(admin, "Guest paid", 3);
        var event = eventRepository.findById(paid).orElseThrow();
        event.setEventType(EventType.PAID);
        event.setPrice(new BigDecimal("99.00"));
        eventRepository.saveAndFlush(event);
        UUID draft = adminEventService.create(fixtures.request("Guest draft", 3), admin).eventId();

        assertError(() -> service.registerGuest(paid, guest("Payer", "payer@example.test")),
                ErrorCode.GUEST_PAID_REGISTRATION_UNAVAILABLE);
        assertError(() -> service.registerGuest(draft, guest("Early", "early@example.test")),
                ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void guestCannotReuseAMembersAttendeeEmail() {
        UUID eventId = fixtures.publishedEvent(admin, "Member then guest duplicate", 3);
        var member = fixtures.participant("email-owner", "Email", "Owner");
        service.register(eventId, new CreateRegistrationRequest(null, "shared@example.test", null, true), member);

        assertError(() -> service.registerGuest(eventId, guest("Duplicate", "  SHARED@Example.test ")),
                ErrorCode.GUEST_ALREADY_REGISTERED);
        assertThat(eventRepository.countActiveRegistrations(eventId)).isEqualTo(1);
    }

    @Test
    void memberCannotReuseAnUnlinkedGuestsEmailOrAnotherMembersEmail() {
        UUID eventId = fixtures.publishedEvent(admin, "Guest then member duplicate", 3);
        service.registerGuest(eventId, guest("First Guest", "shared@example.test"));
        var member = fixtures.participant("different-account", "Different", "Account");

        assertError(() -> service.register(eventId,
                new CreateRegistrationRequest(null, "  SHARED@Example.test ", null, true), member),
                ErrorCode.ALREADY_REGISTERED);

        service.register(eventId, new CreateRegistrationRequest(null, "member@example.test", null, true), member);
        var other = fixtures.participant("another-account", "Another", "Account");
        assertError(() -> service.register(eventId,
                new CreateRegistrationRequest(null, "MEMBER@example.test", null, true), other),
                ErrorCode.ALREADY_REGISTERED);
        assertThat(eventRepository.countActiveRegistrations(eventId)).isEqualTo(2);
    }

    @Test
    void linkedGuestEmailStillBlocksANewGuestRegistration() {
        UUID eventId = fixtures.publishedEvent(admin, "Linked guest duplicate", 3);
        String email = uniqueEmail();
        var guest = service.registerGuest(eventId, guest("Future Member", email));
        authService.register(new RegisterRequest(email, "Password123", "Future", "Member", "0812345678"));

        assertError(() -> service.registerGuest(eventId, guest("Duplicate", email.toUpperCase())),
                ErrorCode.GUEST_ALREADY_REGISTERED);
        assertThat(jdbcTemplate.queryForObject(
                "select participant_id is not null from registrations where registration_id = ?",
                Boolean.class, guest.registrationId())).isTrue();
        assertThat(eventRepository.countActiveRegistrations(eventId)).isEqualTo(1);
    }

    @Test
    void sameEmailMayRegisterForAnotherEventOrAfterCancellationAndPhonesRemainReusable() {
        UUID first = fixtures.publishedEvent(admin, "Email scope A", 4);
        UUID second = fixtures.publishedEvent(admin, "Email scope B", 4);
        var member = fixtures.participant("cancelled-email-owner", "Email", "Owner");
        var own = service.register(first,
                new CreateRegistrationRequest(null, "shared@example.test", "0812345678", true), member);
        service.registerGuest(second, new GuestRegistrationRequest("Other Event", "SHARED@example.test", "0812345678", true));
        service.registerGuest(first, new GuestRegistrationRequest("Same Phone", "different@example.test", "0812345678", true));
        service.cancel(own.registrationId(), member);
        service.registerGuest(first, guest("Reuse Cancelled Email", "SHARED@example.test"));

        // The cancelled member row cannot be reactivated while the guest holds that email.
        assertError(() -> service.register(first,
                new CreateRegistrationRequest(null, "shared@example.test", null, true), member),
                ErrorCode.ALREADY_REGISTERED);
        assertThat(eventRepository.countActiveRegistrations(first)).isEqualTo(2);
        assertThat(eventRepository.countActiveRegistrations(second)).isEqualTo(1);
    }

    @Test
    void guestFindsTheTicketOnlyWithTheMatchingEmail() throws Exception {
        UUID eventId = fixtures.publishedEvent(admin, "Guest lookup", 3);
        String number = service.registerGuest(eventId, guest("Lookup", "lookup@example.test"))
                .registrationNumber();

        var ticket = service.guestTicket(new GuestTicketLookupRequest(number.toLowerCase(), "LOOKUP@example.test"));
        assertThat(ticket.registrationNumber()).isEqualTo(number);
        assertThat(ticket.attendeeName()).isEqualTo("Lookup");

        mockMvc.perform(post("/api/v1/guest-registrations/lookup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationNumber\":\"" + number + "\",\"email\":\"someone-else@example.test\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REGISTRATION_NOT_FOUND"));
    }

    @Test
    void staffCheckInAGuestByRegistrationNumber() {
        UUID eventId = fixtures.publishedEvent(admin, "Guest check-in", 3);
        String number = service.registerGuest(eventId, guest("Walk In", "walkin@example.test"))
                .registrationNumber();

        var checkedIn = service.checkIn(eventId, new CheckInRequest(number), admin);

        assertThat(checkedIn.status()).isEqualTo(RegistrationStatus.CHECKED_IN);
        assertThat(checkedIn.registeredAsGuest()).isTrue();
    }

    @Test
    void signingUpWithTheGuestEmailMovesTheRegistrationIntoTheAccountHistory() {
        UUID first = fixtures.publishedEvent(admin, "Guest then sign up A", 3);
        UUID second = fixtures.publishedEvent(admin, "Guest then sign up B", 3);
        String email = uniqueEmail();
        var registrationA = service.registerGuest(first, guest("Later Member", email));
        var registrationB = service.registerGuest(second, guest("Later Member", email.toUpperCase()));

        // Test accounts are verified on sign-up, which is what allows the link.
        UUID userId = authService.register(new RegisterRequest(email, "Password123", "Later", "Member", "0812345678"))
                .userId();
        AuthenticatedUser member = member(userId, email);

        assertThat(service.mine(member)).extracting(RegistrationResponse::registrationId)
                .containsExactlyInAnyOrder(registrationA.registrationId(), registrationB.registrationId());
        // The new owner can now manage it like any of their own registrations.
        var cancelled = service.cancel(registrationA.registrationId(), member);
        assertThat(cancelled.status()).isEqualTo(RegistrationStatus.CANCELLED_BY_USER);
        assertThat(cancelled.registeredAsGuest()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select guest_linked_at is not null from registrations where registration_id = ?",
                Boolean.class, registrationB.registrationId())).isTrue();
    }

    @Test
    void anExistingAccountPicksUpGuestRegistrationsOnTheNextSignIn() {
        String email = uniqueEmail();
        UUID userId = authService.register(new RegisterRequest(email, "Password123", "Old", "Member", "0812345678"))
                .userId();
        UUID eventId = fixtures.publishedEvent(admin, "Member registers as guest", 3);
        var guestRegistration = service.registerGuest(eventId, guest("Old Member", email));
        assertThat(service.mine(member(userId, email))).isEmpty();

        authService.login(new LoginRequest(email, "Password123"), CONTEXT);

        assertThat(service.mine(member(userId, email))).extracting(RegistrationResponse::registrationId)
                .containsExactly(guestRegistration.registrationId());
    }

    @Test
    void anUnverifiedAccountDoesNotReceiveGuestRegistrations() {
        String email = uniqueEmail();
        UUID eventId = fixtures.publishedEvent(admin, "Unverified claim", 3);
        service.registerGuest(eventId, guest("Victim", email));
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into auth_users (user_id, email, password_hash, status) values (?, ?, ?, 'ACTIVE')",
                userId, email, passwordEncoder.encode("Password123"));

        authService.login(new LoginRequest(email, "Password123"), CONTEXT);

        assertThat(service.mine(member(userId, email))).isEmpty();
    }

    @Test
    void aGuestRowForAnEventTheAccountAlreadyHoldsStaysUnlinked() {
        String email = uniqueEmail();
        UUID userId = authService.register(new RegisterRequest(email, "Password123", "Double", "Booked", "0812345678"))
                .userId();
        AuthenticatedUser member = member(userId, email);
        UUID eventId = fixtures.publishedEvent(admin, "Already held", 3);
        var own = service.register(eventId, new CreateRegistrationRequest(null, null, null, true), member);
        // Legacy duplicates can predate the cross-flow email check; linking must still be safe.
        UUID guestRegistrationId = jdbcTemplate.queryForObject("""
                insert into registrations (event_id, registration_number, registered_as_guest,
                    status, attendee_name, attendee_email, registered_at, confirmed_at, consent_accepted_at)
                values (?, 'REG-LEGACY0001', true, 'CONFIRMED', 'Double Booked', ?, now(), now(), now())
                returning registration_id""", UUID.class, eventId, email);

        authService.login(new LoginRequest(email, "Password123"), CONTEXT);

        assertThat(service.mine(member)).extracting(RegistrationResponse::registrationId)
                .containsExactly(own.registrationId());
        assertThat(jdbcTemplate.queryForObject(
                "select participant_id is null from registrations where registration_id = ?",
                Boolean.class, guestRegistrationId)).isTrue();
    }

    private static GuestRegistrationRequest guest(String name, String email) {
        return new GuestRegistrationRequest(name, email, null, true);
    }

    private static String guestJson(String email) {
        return "{\"attendeeName\":\"Guest\",\"attendeeEmail\":\"" + email + "\",\"consentAccepted\":true}";
    }

    private static AuthenticatedUser member(UUID userId, String email) {
        return new AuthenticatedUser(userId, email, "USER", List.of("USER"),
                List.of("REGISTRATION_CREATE_SELF", "REGISTRATION_READ_SELF", "REGISTRATION_CANCEL_SELF"));
    }

    private static String uniqueEmail() {
        return "guest-" + UUID.randomUUID() + "@example.test";
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
