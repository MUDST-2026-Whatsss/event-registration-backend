package MUDST_2026_Whatsss.event_registration.event.repository;

import MUDST_2026_Whatsss.event_registration.event.domain.EventChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.UUID;

public interface EventChangeRequestRepository extends JpaRepository<EventChangeRequest, UUID> {

    boolean existsByEvent_EventIdAndStatus(UUID eventId, String status);

    List<EventChangeRequest> findByEvent_EventIdOrderByCreatedAtDesc(UUID eventId);

    @EntityGraph(attributePaths = {"event", "event.category", "requestedBy", "reviewedBy"})
    Page<EventChangeRequest> findByStatus(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"event", "event.category", "requestedBy", "reviewedBy"})
    Page<EventChangeRequest> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"event", "event.category", "requestedBy", "reviewedBy"})
    @Query("select r from EventChangeRequest r where r.eventChangeRequestId = :requestId")
    java.util.Optional<EventChangeRequest> findDetailById(@Param("requestId") UUID requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from EventChangeRequest r join fetch r.event where r.eventChangeRequestId = :requestId")
    java.util.Optional<EventChangeRequest> findByIdForUpdate(@Param("requestId") UUID requestId);
}
