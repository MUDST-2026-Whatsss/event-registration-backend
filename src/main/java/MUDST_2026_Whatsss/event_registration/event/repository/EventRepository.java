package MUDST_2026_Whatsss.event_registration.event.repository;

import MUDST_2026_Whatsss.event_registration.event.domain.Event;
import MUDST_2026_Whatsss.event_registration.event.domain.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    @EntityGraph(attributePaths = "category")
    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    /** Public catalogue, soonest first. */
    @EntityGraph(attributePaths = "category")
    List<Event> findByStatusOrderByStartAtAsc(EventStatus status);

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
              and (:eventCategoryId is null or e.category.eventCategoryId = :eventCategoryId)
              and (:query = '' or lower(e.title) like lower(concat('%', :query, '%')))
            """)
    Page<Event> findAssignedToFiltered(
            @Param("userId") UUID userId,
            @Param("status") EventStatus status,
            @Param("eventCategoryId") UUID eventCategoryId,
            @Param("query") String query,
            Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("""
            select e from Event e
            where (:status is null or e.status = :status)
              and (:eventCategoryId is null or e.category.eventCategoryId = :eventCategoryId)
              and (:query = '' or lower(e.title) like lower(concat('%', :query, '%')))
            """)
    Page<Event> findAllFiltered(
            @Param("status") EventStatus status,
            @Param("eventCategoryId") UUID eventCategoryId,
            @Param("query") String query,
            Pageable pageable);

    @Query("""
            select c.eventCategoryId as eventCategoryId,
                   c.code as code,
                   c.nameTh as nameTh,
                   c.nameEn as nameEn,
                   e.status as status,
                   count(e.eventId) as eventCount
            from Event e
            left join e.category c
            group by c.eventCategoryId, c.code, c.nameTh,
                     c.nameEn, c.displayOrder, e.status
            order by c.displayOrder, c.nameEn, e.status
            """)
    List<EventCategoryStatusCount> countAllByCategoryAndStatus();

    @Query("""
            select c.eventCategoryId as eventCategoryId,
                   c.code as code,
                   c.nameTh as nameTh,
                   c.nameEn as nameEn,
                   e.status as status,
                   count(distinct e.eventId) as eventCount
            from Event e
            left join e.category c
            join EventAdminAssignment a on a.event = e
            where a.adminUser.userId = :userId and a.status = 'ACTIVE'
            group by c.eventCategoryId, c.code, c.nameTh,
                     c.nameEn, c.displayOrder, e.status
            order by c.displayOrder, c.nameEn, e.status
            """)
    List<EventCategoryStatusCount> countAssignedByCategoryAndStatus(@Param("userId") UUID userId);

    interface EventCategoryStatusCount {
        UUID getEventCategoryId();
        String getCode();
        String getNameTh();
        String getNameEn();
        EventStatus getStatus();
        long getEventCount();
    }

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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Event e where e.eventId = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e left join fetch e.category where e.eventId = :eventId")
    Optional<Event> findByIdForUpdate(@Param("eventId") UUID eventId);

    @Query(value = """
            select count(*) from registrations
            where event_id = :eventId
              and status in ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN')
            """, nativeQuery = true)
    long countActiveRegistrations(@Param("eventId") UUID eventId);

    /** Batch form of {@link #countActiveRegistrations}; events without registrations are absent. */
    @Query(value = """
            select event_id, count(*) from registrations
            where event_id in (:eventIds)
              and status in ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN')
            group by event_id
            """, nativeQuery = true)
    List<Object[]> countActiveRegistrationsByEvent(@Param("eventIds") Collection<UUID> eventIds);

    default Map<UUID, Long> activeRegistrationCounts(Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : countActiveRegistrationsByEvent(eventIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }
}
