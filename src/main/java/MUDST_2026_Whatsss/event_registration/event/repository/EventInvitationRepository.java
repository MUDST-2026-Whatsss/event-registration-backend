package MUDST_2026_Whatsss.event_registration.event.repository;

import MUDST_2026_Whatsss.event_registration.event.domain.EventInvitation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventInvitationRepository extends JpaRepository<EventInvitation, UUID> {
    Optional<EventInvitation> findByEvent_EventIdAndUser_UserId(UUID eventId, UUID userId);

    @EntityGraph(attributePaths = {"event", "event.category"})
    @Query("""
            select i from EventInvitation i
            where i.user.userId = :userId and i.acceptedAt is null
              and i.event.status = 'PUBLISHED' and i.event.registrationEndAt > :now
              and not exists (select r.registrationId from Registration r
                  where r.event = i.event and r.registeredByUserId = :userId
                    and r.status in ('CONFIRMED', 'CHECKED_IN', 'PENDING_PAYMENT'))
            order by i.createdAt desc
            """)
    List<EventInvitation> findVisibleForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
