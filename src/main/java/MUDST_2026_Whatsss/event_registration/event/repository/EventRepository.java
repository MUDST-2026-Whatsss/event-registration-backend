package MUDST_2026_Whatsss.event_registration.event.repository;

import MUDST_2026_Whatsss.event_registration.event.domain.Event;
import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    @EntityGraph(attributePaths = "category")
    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Event> findByEventIdAndStatus(UUID eventId, EventStatus status);

    @EntityGraph(attributePaths = "category")
    Optional<Event> findBySlugAndStatus(String slug, EventStatus status);

    /** Event-admin scope; global scope uses the ordinary pageable repository methods. */
    @EntityGraph(attributePaths = "category")
    Page<Event> findByCreatedBy_UserId(UUID userId, Pageable pageable);
}
