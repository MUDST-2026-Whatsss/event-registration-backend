package MUDST_2026_Whatsss.event_registration.auth.service;

import MUDST_2026_Whatsss.event_registration.auth.domain.LoginAttempt;
import MUDST_2026_Whatsss.event_registration.auth.repository.LoginAttemptRepository;
import MUDST_2026_Whatsss.event_registration.auth.security.IdentifierHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Transactional writer kept separate so commit failures reach the best-effort caller. */
@Service
public class LoginAuditWriter {

    private final LoginAttemptRepository repository;
    private final IdentifierHasher identifierHasher;

    public LoginAuditWriter(LoginAttemptRepository repository, IdentifierHasher identifierHasher) {
        this.repository = repository;
        this.identifierHasher = identifierHasher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(UUID userId,
                      String normalizedEmail,
                      String outcome,
                      String failureReason,
                      RequestContext context) {
        repository.saveAndFlush(LoginAttempt.builder()
                .userId(userId)
                .identifierHash(identifierHasher.hash(normalizedEmail))
                .outcome(outcome)
                .failureReason(failureReason)
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
    }
}
