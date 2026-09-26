package MUDST_2026_Whatsss.event_registration.auth.service;

import MUDST_2026_Whatsss.event_registration.auth.config.AuthProperties;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthSession;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthSessionRepository;
import MUDST_2026_Whatsss.event_registration.auth.security.TokenHasher;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates, rotates and revokes refresh sessions.
 *
 * <p>Rotation is what limits the damage of a stolen refresh token: each use invalidates the token
 * presented and issues a replacement, so a copied token is good for at most one call. If the old
 * token is presented again — which happens when an attacker uses a token the real user has already
 * rotated, or vice versa — the entire family is revoked and both parties are forced to sign in
 * again. Losing the session is the correct outcome there, since there is no way to tell the
 * legitimate holder from the thief.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    public static final String REASON_ROTATED = "ROTATED";
    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_REUSE_DETECTED = "REUSE_DETECTED";
    public static final String REASON_PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String REASON_REPLACED_BY_LOGIN = "REPLACED_BY_LOGIN";

    private final AuthSessionRepository sessionRepository;
    private final TokenHasher tokenHasher;
    private final AuthProperties properties;
    private final SessionTransactionService transactionService;

    public SessionService(AuthSessionRepository sessionRepository,
                          TokenHasher tokenHasher,
                          AuthProperties properties,
                          SessionTransactionService transactionService) {
        this.sessionRepository = sessionRepository;
        this.tokenHasher = tokenHasher;
        this.properties = properties;
        this.transactionService = transactionService;
    }

    /** Starts a brand-new token family, as happens at login. */
    @Transactional
    public IssuedSession createSession(UUID userId, RequestContext context) {
        return persist(userId, UUID.randomUUID(), null, context);
    }

    /**
     * Validates a presented refresh token and issues its successor.
     *
     * @throws ApiException when the token is unknown, expired, or already rotated
     */
    public IssuedSession rotate(String rawRefreshToken, RequestContext context) {
        Instant now = Instant.now();
        String presentedHash = tokenHasher.hash(rawRefreshToken);

        SessionTransactionService.RotationResult result =
                transactionService.rotate(presentedHash, context, now);

        if (result.isReuse()) {
            // The token was valid once, so this is a replay of a rotated or logged-out session.
            // Assume compromise and tear down the whole family.
            log.warn("Refresh token reuse detected for user {} in family {}",
                    result.userId(), result.reusedFamilyId());
            // Rotation's row lock has already been committed and released. The revocation is a
            // separate committed transaction, so the exception below cannot undo it.
            // Use a fresh timestamp: the successor may have been created while this request was
            // waiting on the predecessor lock, and revoked_at must never predate created_at.
            transactionService.revokeFamily(result.reusedFamilyId(), Instant.now());
            throw new ApiException(ErrorCode.SESSION_REVOKED);
        }

        return result.issuedSession();
    }

    /**
     * Revokes the session behind a refresh token.
     *
     * <p>Silent when the token is unknown: logout is idempotent, and reporting whether a token
     * existed would leak information to anyone probing with guessed values.
     */
    @Transactional
    public void revokeByRawToken(String rawRefreshToken) {
        revokeByRawToken(rawRefreshToken, REASON_LOGOUT);
    }

    /** Replaces only the session represented by this browser's current refresh cookie. */
    @Transactional
    public void revokeByRawToken(String rawRefreshToken, String reason) {
        sessionRepository.findByTokenHash(tokenHasher.hash(rawRefreshToken))
                .ifPresent(session -> {
                    session.revoke(Instant.now(), reason);
                    sessionRepository.save(session);
                });
    }

    @Transactional
    public int revokeAllForUser(UUID userId, String reason) {
        return sessionRepository.revokeAllForUser(userId, Instant.now(), reason);
    }

    private IssuedSession persist(UUID userId, UUID familyId, UUID parentSessionId, RequestContext context) {
        String rawToken = tokenHasher.generateToken();
        Instant now = Instant.now();

        AuthSession session = sessionRepository.save(AuthSession.builder()
                .userId(userId)
                .tokenHash(tokenHasher.hash(rawToken))
                .tokenFamilyId(familyId)
                .parentSessionId(parentSessionId)
                .userAgent(context.userAgent())
                .ipAddress(context.ipAddress())
                .expiresAt(now.plus(properties.getRefreshTokenTtl()))
                .createdAt(now)
                .build());

        return new IssuedSession(session, rawToken);
    }
}
