package MUDST_2026_Whatsss.event_registration.auth.repository;

import MUDST_2026_Whatsss.event_registration.auth.domain.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    Optional<Participant> findByUserId(UUID userId);

    List<Participant> findByUserIdIn(List<UUID> userIds);
}
