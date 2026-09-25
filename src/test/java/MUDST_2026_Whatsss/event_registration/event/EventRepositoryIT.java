package MUDST_2026_Whatsss.event_registration.event;

import MUDST_2026_Whatsss.event_registration.PostgresIntegrationTest;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import MUDST_2026_Whatsss.event_registration.event.domain.Event;
import MUDST_2026_Whatsss.event_registration.event.domain.EventCategory;
import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.domain.LocationType;
import MUDST_2026_Whatsss.event_registration.event.repository.EventCategoryRepository;
import MUDST_2026_Whatsss.event_registration.event.repository.EventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class EventRepositoryIT extends PostgresIntegrationTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventCategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private UUID creatorId;
    private AuthUser creator;
    private EventCategory category;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into auth_users (user_id, email, password_hash, status) values (?, ?, ?, ?)",
                creatorId,
                "event-owner-" + creatorId + "@example.test",
                "test-password-hash",
                "ACTIVE");
        creator = entityManager.getReference(AuthUser.class, creatorId);
        category = categoryRepository.findByCodeAndActiveTrue("TECHNOLOGY").orElseThrow();
    }

    @Test
    void savesAndReadsTheCurrentUuidSchema() {
        Event saved = eventRepository.saveAndFlush(newEvent("current-schema-event", EventStatus.PUBLISHED));
        entityManager.clear();

        Event loaded = eventRepository
                .findByEventIdAndStatus(saved.getEventId(), EventStatus.PUBLISHED)
                .orElseThrow();

        assertThat(loaded.getEventId()).isNotNull();
        assertThat(loaded.getCategory().getCode()).isEqualTo("TECHNOLOGY");
        assertThat(loaded.getCreatedBy().getUserId()).isEqualTo(creatorId);
        assertThat(loaded.getMaximumParticipants()).isEqualTo(100);
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void pageableQueriesRespectLifecycleAndCreatorScope() {
        eventRepository.save(newEvent("published-event", EventStatus.PUBLISHED));
        eventRepository.save(newEvent("draft-event", EventStatus.DRAFT));
        eventRepository.flush();

        var published = eventRepository.findByStatus(EventStatus.PUBLISHED, PageRequest.of(0, 10));
        var owned = eventRepository.findByCreatedBy_UserId(creatorId, PageRequest.of(0, 10));

        assertThat(published.getContent())
                .extracting(Event::getSlug)
                .allMatch(slug -> slug.startsWith("published-event-"));
        assertThat(owned.getContent())
                .extracting(Event::getSlug)
                .anyMatch(slug -> slug.startsWith("published-event-"))
                .anyMatch(slug -> slug.startsWith("draft-event-"));
    }

    private Event newEvent(String slug, EventStatus status) {
        Instant registrationStart = Instant.parse("2026-09-01T00:00:00Z");
        Instant registrationEnd = Instant.parse("2026-09-30T00:00:00Z");
        Instant eventStart = Instant.parse("2026-10-01T02:00:00Z");
        return Event.builder()
                .slug(slug + "-" + UUID.randomUUID().toString().substring(0, 8))
                .title("Current Schema Event")
                .summary("Repository integration test")
                .category(category)
                .createdBy(creator)
                .status(status)
                .eventType(EventType.FREE)
                .price(BigDecimal.ZERO)
                .currency("THB")
                .locationType(LocationType.ONSITE)
                .locationName("Main Hall")
                .timezone("Asia/Bangkok")
                .startAt(eventStart)
                .endAt(eventStart.plusSeconds(7 * 60 * 60))
                .registrationStartAt(registrationStart)
                .registrationEndAt(registrationEnd)
                .cancellationDeadlineAt(registrationEnd)
                .maximumParticipants(100)
                .publishedAt(status == EventStatus.PUBLISHED ? Instant.now() : null)
                .build();
    }
}
