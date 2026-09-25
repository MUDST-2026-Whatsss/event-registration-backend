package MUDST_2026_Whatsss.event_registration.event.repository;

import MUDST_2026_Whatsss.event_registration.event.domain.EventReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface EventReviewRepository extends JpaRepository<EventReview, UUID> {

    Optional<EventReview> findByEvent_EventIdAndDecision(UUID eventId, String decision);

    @EntityGraph(attributePaths = {"event", "event.category", "submittedBy", "reviewedBy"})
    Page<EventReview> findByDecision(String decision, Pageable pageable);

    @EntityGraph(attributePaths = {"event", "event.category", "submittedBy", "reviewedBy"})
    Page<EventReview> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"event", "event.category", "submittedBy", "reviewedBy"})
    @Query("select r from EventReview r where r.reviewId = :reviewId")
    Optional<EventReview> findDetailById(@Param("reviewId") UUID reviewId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from EventReview r join fetch r.event where r.reviewId = :reviewId")
    Optional<EventReview> findByIdForUpdate(@Param("reviewId") UUID reviewId);
}
