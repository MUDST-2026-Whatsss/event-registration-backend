package MUDST_2026_Whatsss.event_registration.event.repository;

import MUDST_2026_Whatsss.event_registration.event.domain.EventCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventCategoryRepository extends JpaRepository<EventCategory, UUID> {

    List<EventCategory> findByActiveTrueOrderByDisplayOrderAscNameThAsc();

    Optional<EventCategory> findByCodeAndActiveTrue(String code);
}
