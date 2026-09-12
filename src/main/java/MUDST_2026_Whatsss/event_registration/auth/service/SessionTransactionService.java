package MUDST_2026_Whatsss.event_registration.auth.service;

import MUDST_2026_Whatsss.event_registration.auth.config.AuthProperties;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthSession;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthSessionRepository;
import MUDST_2026_Whatsss.event_registration.auth.security.TokenHasher;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Database transaction boundary for refresh-token rotation and reuse response.
 *
 * <p>The boundary deliberately lives in a separate bean. This makes Spring's proxy semantics
 * explicit, avoids self-lookups through {@code ApplicationContext}, and ensures the row lock is
 * released before a replay response revokes the complete token family.
 */
@Service
public class SessionTransactionService {

    private final AuthSessionRepository sessionRepository;
    private final TokenHasher tokenHasher;
    private final AuthProperties properties;

    public SessionTransactionService(AuthSessionRepository sessionRepository,
                                     TokenHasher tokenHasher,
                                     AuthProperties properties) {
        this.sessionRepository = sessionRepository;
        this.tokenHasher = tokenHasher;
        this.properties = properties;
    }

    /**
     * Locks the predecessor row and either rotates it exactly once or reports a replay.
     * {@code REQUIRES_NEW} prevents an outer authentication transaction from retaining the lock.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RotationResult rotate(String presentedHash, RequestContext context, Instant now) {
        AuthSession existing = sessionRepository.findByTokenHashForUpdate(presentedHash)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (existing.isRevoked()) {
            return RotationResult.reused(existing.getUserId(), existing.getTokenFamilyId());
        }
        if (existing.isExpired(now)) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        existing.revoke(now, SessionService.REASON_ROTATED);
        sessionRepository.save(existing);

        String rawToken = tokenHasher.generateToken();
        AuthSession successor = sessionRepository.saveAndFlush(AuthSession.builder()
                .userId(existing.getUserId())
                .tokenHash(tokenHasher.hash(rawToken))
                .tokenFamilyId(existing.getTokenFamilyId())
                .parentSessionId(existing.getSessionId())
                .userAgent(context.userAgent())
                .ipAddress(context.ipAddress())
                .expiresAt(now.plus(properties.getRefreshTokenTtl()))
                .createdAt(now)
                .build());

        return RotationResult.issued(new IssuedSession(successor, rawToken));
    }

    /** Commits the security response independently of the API transaction that will throw. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeFamily(UUID familyId, Instant now) {
        return sessionRepository.revokeFamily(
                familyId, now, SessionService.REASON_REUSE_DETECTED);
    }

    public record RotationResult(IssuedSession issuedSession, UUID userId, UUID reusedFamilyId) {
        static RotationResult issued(IssuedSession session) {
            return new RotationResult(session, null, null);
        }

        static RotationResult reused(UUID userId, UUID familyId) {
            return new RotationResult(null, userId, familyId);
        }

        boolean isReuse() {
            return reusedFamilyId != null;
        }
    }
}
