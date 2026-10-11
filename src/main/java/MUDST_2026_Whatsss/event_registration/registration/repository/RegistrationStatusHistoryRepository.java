package MUDST_2026_Whatsss.event_registration.registration.repository;

import MUDST_2026_Whatsss.event_registration.registration.domain.RegistrationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegistrationStatusHistoryRepository
        extends JpaRepository<RegistrationStatusHistory, UUID> {

    List<RegistrationStatusHistory> findByRegistrationIdOrderByChangedAt(UUID registrationId);
}
