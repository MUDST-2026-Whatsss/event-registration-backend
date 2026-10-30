package MUDST_2026_Whatsss.event_registration.registration;

import MUDST_2026_Whatsss.event_registration.PostgresIntegrationTest;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.service.AdminEventService;
import MUDST_2026_Whatsss.event_registration.registration.service.RegistrationService;
import MUDST_2026_Whatsss.event_registration.registration.web.dto.CreateRegistrationRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real sockets against a running server. The data is committed so the poller thread can see it,
 * so every row a test creates is deleted afterwards to keep it out of the other suites.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "event.realtime.seat-poll-interval=PT0.2S")
class SeatUpdatesIT extends PostgresIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @LocalServerPort private int port;
    @Autowired private RegistrationService registrationService;
    @Autowired private AdminEventService adminEventService;
    @Autowired private EventRepository eventRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    private RegistrationFixtures fixtures;
    private AuthenticatedUser admin;
    private WebSocketSession socket;
    private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
    private final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
    private final List<UUID> createdEvents = new ArrayList<>();
    private final List<UUID> createdUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fixtures = new RegistrationFixtures(jdbcTemplate, adminEventService, eventRepository);
        admin = fixtures.admin();
        createdUsers.add(admin.userId());
    }

    @AfterEach
    void closeSocketAndDeleteData() throws Exception {
        if (socket != null && socket.isOpen()) {
            socket.close();
        }
        for (UUID eventId : createdEvents) {
            jdbcTemplate.update("""
                    delete from registration_status_history
                    where registration_id in (select registration_id from registrations where event_id = ?)""", eventId);
            for (String table : List.of("registrations", "event_admin_assignments", "event_reviews", "events")) {
                jdbcTemplate.update("delete from " + table + " where event_id = ?", eventId);
            }
        }
        for (UUID userId : createdUsers) {
            jdbcTemplate.update("delete from participants where user_id = ?", userId);
            jdbcTemplate.update("delete from auth_users where user_id = ?", userId);
        }
    }

    @Test
    void pageReceivesTheCountAtOnceAndAgainAfterSomeoneRegisters() throws Exception {
        UUID eventId = publishedEvent("Live seats", 3);
        connect(ALLOWED_ORIGIN);

        subscribe(eventId);
        JsonNode initial = nextUpdate();
        assertThat(initial.path("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(initial.path("remainingSeats").asInt()).isEqualTo(3);
        assertThat(initial.path("full").asBoolean()).isFalse();

        registrationService.register(eventId, consent(), participant("live"));

        JsonNode afterRegistration = nextUpdate();
        assertThat(afterRegistration.path("remainingSeats").asInt()).isEqualTo(2);
        // Unchanged counts are not resent on every poll.
        assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void hiddenSeatCountOnlyRevealsWhetherTheEventIsFull() throws Exception {
        UUID eventId = publishedEvent("Hidden live seats", 1);
        var event = eventRepository.findById(eventId).orElseThrow();
        event.setShowRemainingSeats(false);
        eventRepository.saveAndFlush(event);
        connect(ALLOWED_ORIGIN);

        subscribe(eventId);
        JsonNode initial = nextUpdate();
        assertThat(initial.has("remainingSeats")).isFalse();
        assertThat(initial.path("full").asBoolean()).isFalse();

        registrationService.register(eventId, consent(), participant("last"));

        JsonNode full = nextUpdate();
        assertThat(full.has("remainingSeats")).isFalse();
        assertThat(full.path("full").asBoolean()).isTrue();
    }

    @Test
    void unknownOrUnpublishedEventClosesTheSocket() throws Exception {
        UUID draftId = adminEventService.create(fixtures.request("Draft live seats", 5), admin).eventId();
        createdEvents.add(draftId);
        connect(ALLOWED_ORIGIN);

        subscribe(draftId);

        assertThat(closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
        assertThat(received).isEmpty();
    }

    @Test
    void otherSitesCannotOpenTheSocket() {
        assertThatThrownBy(() -> connect("https://evil.example"))
                .isInstanceOf(ExecutionException.class);
    }

    private UUID publishedEvent(String title, int capacity) {
        UUID eventId = fixtures.publishedEvent(admin, title, capacity);
        createdEvents.add(eventId);
        return eventId;
    }

    private AuthenticatedUser participant(String label) {
        AuthenticatedUser participant = fixtures.participant(label, label, null);
        createdUsers.add(participant.userId());
        return participant;
    }

    private void connect(String origin) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        socket = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                received.add(message.getPayload());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                closed.complete(status);
            }
        }, headers, URI.create("ws://localhost:" + port + "/api/v1/ws/seats")).get(5, TimeUnit.SECONDS);
    }

    private void subscribe(UUID eventId) throws Exception {
        socket.sendMessage(new TextMessage("{\"subscribe\":\"" + eventId + "\"}"));
    }

    private JsonNode nextUpdate() throws Exception {
        String payload = received.poll(5, TimeUnit.SECONDS);
        assertThat(payload).as("a seat update within 5 seconds").isNotNull();
        return objectMapper.readTree(payload);
    }

    private static CreateRegistrationRequest consent() {
        return new CreateRegistrationRequest(null, null, null, true);
    }
}
