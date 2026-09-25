package MUDST_2026_Whatsss.event_registration.event.repository;

import MUDST_2026_Whatsss.event_registration.event.domain.EventAdminAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventAdminAssignmentRepository extends JpaRepository<EventAdminAssignment, UUID> {

    boolean existsByEvent_EventIdAndAdminUser_UserIdAndStatus(
            UUID eventId, UUID adminUserId, String status);
}
