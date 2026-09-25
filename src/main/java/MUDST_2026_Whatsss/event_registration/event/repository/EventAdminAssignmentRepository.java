package MUDST_2026_Whatsss.event_registration.event.repository;

import MUDST_2026_Whatsss.event_registration.event.domain.EventAdminAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.UUID;

public interface EventAdminAssignmentRepository extends JpaRepository<EventAdminAssignment, UUID> {

    boolean existsByEvent_EventIdAndAdminUser_UserIdAndStatus(
            UUID eventId, UUID adminUserId, String status);

    @EntityGraph(attributePaths = {"adminUser", "adminUser.roles"})
    List<EventAdminAssignment> findByEvent_EventIdAndStatusOrderByAssignedAt(
            UUID eventId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from EventAdminAssignment a join fetch a.adminUser where a.event.eventId = :eventId and a.status = 'ACTIVE'")
    List<EventAdminAssignment> findActiveByEventForUpdate(@Param("eventId") UUID eventId);
}
