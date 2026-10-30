package MUDST_2026_Whatsss.event_registration.registration.repository;

import MUDST_2026_Whatsss.event_registration.registration.domain.Registration;
import MUDST_2026_Whatsss.event_registration.registration.domain.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
              and lower(trim(r.attendeeEmail)) = :normalizedEmail
              and r.status in :statuses
            """)
    boolean existsActiveRegistrationByEmail(
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

    boolean existsByRegistrationNumber(String registrationNumber);
}
