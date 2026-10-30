package MUDST_2026_Whatsss.event_registration.realtime;

import MUDST_2026_Whatsss.event_registration.event.domain.Event;
import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import MUDST_2026_Whatsss.event_registration.event.service.EventMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live remaining seats for public event pages at {@code /api/v1/ws/seats}.
 *
 * <p>A page sends {@code {"subscribe": "<eventId>"}} and receives a {@link SeatUpdate} at once and
 * again whenever the count changes. Each API instance polls the counts of the events its own
 * sockets watch, so a registration handled by another replica still reaches every page without a
 * shared message broker.
 */
@Component
public class SeatUpdatesHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SeatUpdatesHandler.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 64 * 1024;

    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    /** Socket id → the socket, wrapped so the poller and the socket's own thread can both send. */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    /** Socket id → the one event it watches. */
    private final Map<String, UUID> subscriptions = new ConcurrentHashMap<>();
    /** Socket id → the last update it was sent, so unchanged counts are not resent. */
    private final Map<String, SeatUpdate> lastSent = new ConcurrentHashMap<>();

    public SeatUpdatesHandler(EventRepository eventRepository, ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Optional<UUID> eventId = subscribedEventId(message.getPayload());
        Optional<SeatUpdate> update = eventId.flatMap(id -> eventRepository.findById(id)
                .filter(event -> event.getStatus() == EventStatus.PUBLISHED)
                .map(event -> seatUpdate(event, eventRepository.countActiveRegistrations(id))));
        if (update.isEmpty()) {
            // Unparseable, unknown or unpublished: the same answer, so drafts cannot be probed.
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Unknown event"));
            return;
        }
        subscriptions.put(session.getId(), eventId.get());
        send(session.getId(), update.get());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        subscriptions.remove(session.getId());
        lastSent.remove(session.getId());
    }

    @Scheduled(fixedDelayString = "${event.realtime.seat-poll-interval:PT2S}")
    public void pushChangedSeatCounts() {
        Set<UUID> watched = new HashSet<>(subscriptions.values());
        if (watched.isEmpty()) {
            return;
        }
        Map<UUID, Long> counts = eventRepository.activeRegistrationCounts(watched);
        Map<UUID, SeatUpdate> updates = new HashMap<>();
        for (Event event : eventRepository.findAllById(watched)) {
            updates.put(event.getEventId(), seatUpdate(event, counts.getOrDefault(event.getEventId(), 0L)));
        }
        subscriptions.forEach((sessionId, eventId) -> {
            SeatUpdate update = updates.get(eventId);
            if (update != null && !update.equals(lastSent.get(sessionId))) {
                send(sessionId, update);
            }
        });
    }

    private Optional<UUID> subscribedEventId(String payload) {
        try {
            JsonNode subscribe = objectMapper.readTree(payload).path("subscribe");
            return subscribe.isTextual() ? Optional.of(UUID.fromString(subscribe.asText())) : Optional.empty();
        } catch (IOException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static SeatUpdate seatUpdate(Event event, long activeRegistrations) {
        return new SeatUpdate(event.getEventId(),
                EventMapper.remainingSeats(event, activeRegistrations),
                activeRegistrations >= event.getMaximumParticipants());
    }

    private void send(String sessionId, SeatUpdate update) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(update)));
            lastSent.put(sessionId, update);
        } catch (IOException | IllegalStateException ex) {
            // A slow or vanished client; the close callback cleans it up.
            log.debug("Dropping seat update for socket {}: {}", sessionId, ex.getMessage());
        }
    }
}
