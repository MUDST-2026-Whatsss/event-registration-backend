package MUDST_2026_Whatsss.event_registration.event.repository;

import MUDST_2026_Whatsss.event_registration.event.domain.Event;
import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

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

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = "category")
    @Query("""
            select distinct e from Event e
            join EventAdminAssignment a on a.event = e
            where a.adminUser.userId = :userId and a.status = 'ACTIVE'
            """)
    Page<Event> findAssignedTo(@Param("userId") UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("""
            select distinct e from Event e
            join EventAdminAssignment a on a.event = e
            where a.adminUser.userId = :userId and a.status = 'ACTIVE'
              and (:status is null or e.status = :status)
              and (:query = '' or lower(e.title) like lower(concat('%', :query, '%')))
            """)
    Page<Event> findAssignedToFiltered(
            @Param("userId") UUID userId,
            @Param("status") EventStatus status,
            @Param("query") String query,
            Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("""
            select e from Event e
            where (:status is null or e.status = :status)
              and (:query = '' or lower(e.title) like lower(concat('%', :query, '%')))
            """)
    Page<Event> findAllFiltered(
            @Param("status") EventStatus status,
            @Param("query") String query,
            Pageable pageable);

    long countByStatus(EventStatus status);

    @Query("""
            select count(distinct e.eventId) from Event e
            join EventAdminAssignment a on a.event = e
            where a.adminUser.userId = :userId and a.status = 'ACTIVE'
            """)
    long countAssigned(@Param("userId") UUID userId);

    @Query("""
            select count(distinct e.eventId) from Event e
            join EventAdminAssignment a on a.event = e
            where a.adminUser.userId = :userId and a.status = 'ACTIVE' and e.status = :status
            """)
    long countAssignedByStatus(@Param("userId") UUID userId, @Param("status") EventStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e left join fetch e.category where e.eventId = :eventId")
    Optional<Event> findByIdForUpdate(@Param("eventId") UUID eventId);

    @Query(value = """
            select count(*) from registrations
            where event_id = :eventId
              and status in ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN')
            """, nativeQuery = true)
    long countActiveRegistrations(@Param("eventId") UUID eventId);
}
