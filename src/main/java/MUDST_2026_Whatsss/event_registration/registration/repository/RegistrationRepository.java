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

    Optional<Registration> findByEvent_EventIdAndParticipantId(UUID eventId, UUID participantId);

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

    boolean existsByRegistrationNumber(String registrationNumber);
}
