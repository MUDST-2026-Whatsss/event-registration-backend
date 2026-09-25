package MUDST_2026_Whatsss.event_registration.event.repository;

import MUDST_2026_Whatsss.event_registration.event.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
