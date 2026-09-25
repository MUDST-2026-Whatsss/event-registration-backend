package MUDST_2026_Whatsss.event_registration.event.repository;

import MUDST_2026_Whatsss.event_registration.event.domain.EventReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventReviewRepository extends JpaRepository<EventReview, UUID> {

    Optional<EventReview> findByEvent_EventIdAndDecision(UUID eventId, String decision);
}
