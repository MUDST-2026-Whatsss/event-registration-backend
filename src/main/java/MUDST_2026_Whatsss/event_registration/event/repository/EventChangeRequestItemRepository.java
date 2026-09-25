package MUDST_2026_Whatsss.event_registration.event.repository;

import MUDST_2026_Whatsss.event_registration.event.domain.EventChangeRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventChangeRequestItemRepository extends JpaRepository<EventChangeRequestItem, UUID> {

    List<EventChangeRequestItem> findByChangeRequest_EventChangeRequestIdOrderByDisplayOrder(
            UUID eventChangeRequestId);
}
