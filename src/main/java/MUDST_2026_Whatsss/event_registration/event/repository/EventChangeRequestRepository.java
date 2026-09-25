package MUDST_2026_Whatsss.event_registration.event.repository;

import MUDST_2026_Whatsss.event_registration.event.domain.EventChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventChangeRequestRepository extends JpaRepository<EventChangeRequest, UUID> {

    boolean existsByEvent_EventIdAndStatus(UUID eventId, String status);

    List<EventChangeRequest> findByEvent_EventIdOrderByCreatedAtDesc(UUID eventId);
}
