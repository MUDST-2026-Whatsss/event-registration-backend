package MUDST_2026_Whatsss.event_registration.auth.service;

import MUDST_2026_Whatsss.event_registration.auth.domain.LoginAttempt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Records every login outcome in {@code auth_login_attempts}.
 *
 * <p>Runs in its own transaction so the audit row survives when the caller's transaction rolls
 * back — a failed login must still leave a trace, and that is precisely the case where the
 * surrounding work is abandoned.
 */
@Service
public class LoginAuditService {

    private static final Logger log = LoggerFactory.getLogger(LoginAuditService.class);

    /** Failure reasons; these land in a 100-character column and are read by operators. */
    public static final String REASON_UNKNOWN_ACCOUNT = "UNKNOWN_ACCOUNT";
    public static final String REASON_BAD_PASSWORD = "BAD_PASSWORD";
    public static final String REASON_ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String REASON_ACCOUNT_NOT_ACTIVE = "ACCOUNT_NOT_ACTIVE";
    public static final String REASON_RATE_LIMITED = "RATE_LIMITED";

    private final LoginAuditWriter writer;

    public LoginAuditService(LoginAuditWriter writer) {
        this.writer = writer;
    }

    public void recordSuccess(UUID userId, String normalizedEmail, RequestContext context) {
        save(userId, normalizedEmail, LoginAttempt.OUTCOME_SUCCESS, null, context);
    }

    public void recordFailure(UUID userId, String normalizedEmail, String reason, RequestContext context) {
        save(userId, normalizedEmail, LoginAttempt.OUTCOME_FAILURE, reason, context);
    }

    private void save(UUID userId,
                      String normalizedEmail,
                      String outcome,
                      String failureReason,
                      RequestContext context) {
        try {
            // The proxy invocation includes flush and commit, so every persistence failure is
            // observable here and cannot turn a valid authentication into an HTTP 500.
            writer.write(userId, normalizedEmail, outcome, failureReason, context);
        } catch (RuntimeException ex) {
            // Auditing must never turn a valid login into a 500.
            log.error("Failed to record login attempt", ex);
        }
    }
}
