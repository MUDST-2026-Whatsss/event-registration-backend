package MUDST_2026_Whatsss.event_registration.auth.service;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import MUDST_2026_Whatsss.event_registration.event.domain.AuditLog;
import MUDST_2026_Whatsss.event_registration.event.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Writes account-security events into the immutable administrative audit trail. */
@Service
public class SecurityAuditService {

    private final AuditLogRepository auditRepository;

    public SecurityAuditService(AuditLogRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void record(AuthUser actor, String action, AuthUser target, Map<String, Object> metadata) {
        auditRepository.save(AuditLog.builder()
                .actor(actor)
                .action(action)
                .targetType("USER")
                .targetId(target.getUserId())
                .targetLabel(target.getEmail())
                .outcome("SUCCESS")
                .metadata(metadata)
                .build());
    }

    public void recordSystem(String action, AuthUser target, Map<String, Object> metadata) {
        record(null, action, target, metadata);
    }
}
