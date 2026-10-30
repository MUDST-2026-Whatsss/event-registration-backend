package MUDST_2026_Whatsss.event_registration.registration.repository;

import MUDST_2026_Whatsss.event_registration.registration.domain.Registration;
import MUDST_2026_Whatsss.event_registration.registration.domain.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

    /** Ownership follows the participant, so a linked guest registration belongs to its new account. */
    @Query("""
            select r.event.eventId from Registration r
            where r.registrationId = :id
              and r.participantId = (select p.participantId from Participant p where p.userId = :userId)
            """)
    Optional<UUID> findOwnedEventId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            select count(r) > 0 from Registration r
            where r.event.eventId = :eventId
              and r.participantId is null
              and lower(r.attendeeEmail) = :normalizedEmail
              and r.status in :statuses
            """)
    boolean existsGuestRegistration(
            @Param("eventId") UUID eventId,
            @Param("normalizedEmail") String normalizedEmail,
            @Param("statuses") Collection<RegistrationStatus> statuses);

    @EntityGraph(attributePaths = {"event", "event.category"})
    @Query("""
            select r from Registration r
            where r.registrationNumber = :registrationNumber
              and r.registeredAsGuest = true
              and lower(r.attendeeEmail) = :normalizedEmail
            """)
    Optional<Registration> findGuestTicket(
            @Param("registrationNumber") String registrationNumber,
            @Param("normalizedEmail") String normalizedEmail);

    /** Newest first, so if one event has several guest rows the latest is the one linked. */
    @Query("""
            select r from Registration r
            where r.participantId is null
              and lower(r.attendeeEmail) = :normalizedEmail
            order by r.registeredAt desc
            """)
    List<Registration> findUnlinkedGuestRegistrations(@Param("normalizedEmail") String normalizedEmail);

    Optional<Registration> findByEvent_EventIdAndParticipantId(UUID eventId, UUID participantId);

    Optional<Registration> findByEvent_EventIdAndRegistrationNumber(UUID eventId, String registrationNumber);

    List<Registration> findByEvent_EventIdAndStatusIn(
            UUID eventId, Collection<RegistrationStatus> statuses);

    @EntityGraph(attributePaths = {"event", "event.category"})
    @Query("select r from Registration r where r.participantId = :participantId order by r.registeredAt desc")
    List<Registration> findMine(@Param("participantId") UUID participantId);

    @EntityGraph(attributePaths = {"event", "event.category"})
    @Query("select r from Registration r where r.registrationId = :registrationId")
    Optional<Registration> findDetailById(@Param("registrationId") UUID registrationId);

    @Query("""
            select r from Registration r
            where r.event.eventId = :eventId
              and (:query = ''
                   or lower(r.attendeeName) like lower(concat('%', :query, '%'))
                   or lower(r.attendeeEmail) like lower(concat('%', :query, '%'))
                   or lower(r.registrationNumber) like lower(concat('%', :query, '%')))
            """)
    Page<Registration> findForEvent(
            @Param("eventId") UUID eventId, @Param("query") String query, Pageable pageable);

    @EntityGraph(attributePaths = "event")
    @Query("select r from Registration r")
    Page<Registration> findRecent(Pageable pageable);

    @EntityGraph(attributePaths = "event")
    @Query("""
            select r from Registration r
            join EventAdminAssignment a on a.event = r.event
            where a.adminUser.userId = :userId
              and a.status = 'ACTIVE'
            """)
    Page<Registration> findRecentForAssignedEvents(
            @Param("userId") UUID userId, Pageable pageable);

    /** Rows of {@code [status, registered_as_guest, count]} for one event. */
    @Query(value = """
            select status, registered_as_guest, count(*) from registrations
            where event_id = :eventId
            group by status, registered_as_guest
            """, nativeQuery = true)
    List<Object[]> countByStatusAndGuest(@Param("eventId") UUID eventId);

    /** Rows of {@code [ISO day, count]}, oldest first; days are counted in {@code timezone}. */
    @Query(value = """
            select to_char(registered_at at time zone :timezone, 'YYYY-MM-DD') as day, count(*) from registrations
            where event_id = :eventId
            group by day
            order by day
            """, nativeQuery = true)
    List<Object[]> countDailySignups(@Param("eventId") UUID eventId, @Param("timezone") String timezone);

    /**
     * Marks confirmed registrations of events that ended before {@code endedBefore} as
     * {@code NO_SHOW} and records the transition. The status filter makes a second, concurrent run
     * skip rows the first one took.
     *
     * @return the number of registrations marked
     */
    // Bypasses loaded entities, so flush pending changes first and drop the stale copies after.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            with marked as (
                update registrations r
                -- clock_timestamp(), not now(): updated_at must not precede created_at, which the
                -- application set from its own clock.
                set status = 'NO_SHOW', updated_at = greatest(clock_timestamp(), r.created_at),
                    version = r.version + 1
                from events e
                where e.event_id = r.event_id
                  and r.status = 'CONFIRMED'
                  and e.end_at < :endedBefore
                returning r.registration_id
            )
            insert into registration_status_history (registration_id, from_status, to_status, reason)
            select registration_id, 'CONFIRMED', 'NO_SHOW', 'Event ended without a check-in' from marked
            """, nativeQuery = true)
    int markNoShowsForEndedEvents(@Param("endedBefore") Instant endedBefore);

    boolean existsByRegistrationNumber(String registrationNumber);
}
