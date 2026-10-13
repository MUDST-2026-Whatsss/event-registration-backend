package MUDST_2026_Whatsss.event_registration.auth.service;

import MUDST_2026_Whatsss.event_registration.auth.config.AuthProperties;
import MUDST_2026_Whatsss.event_registration.auth.domain.ActionTokenType;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthActionToken;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthActionTokenRepository;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthUserRepository;
import MUDST_2026_Whatsss.event_registration.auth.security.TokenHasher;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.ResetPasswordRequest;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/** Issues and consumes password-reset tokens without exposing account existence. */
@Service
public class PasswordResetService {

    private static final Duration REQUEST_COOLDOWN = Duration.ofMinutes(1);

    private final AuthUserRepository userRepository;
    private final AuthActionTokenRepository tokenRepository;
    private final TokenHasher tokenHasher;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final AuthProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityAuditService securityAuditService;

    public PasswordResetService(AuthUserRepository userRepository,
                                AuthActionTokenRepository tokenRepository,
                                TokenHasher tokenHasher,
                                PasswordEncoder passwordEncoder,
                                SessionService sessionService,
                                AuthProperties properties,
                                ApplicationEventPublisher eventPublisher,
                                SecurityAuditService securityAuditService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenHasher = tokenHasher;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.securityAuditService = securityAuditService;
    }

    @Transactional
    public void requestReset(String email) {
        AuthUser user = userRepository.findByNormalizedEmail(normalizeEmail(email)).orElse(null);
        if (user == null || user.getPasswordHash() == null) {
            return;
        }

        Instant now = Instant.now();
        if (tokenRepository.existsByUserIdAndTokenTypeAndUsedAtIsNullAndCreatedAtAfter(
                user.getUserId(), ActionTokenType.PASSWORD_RESET, now.minus(REQUEST_COOLDOWN))) {
            return;
        }

        tokenRepository.invalidateUnused(
                user.getUserId(), ActionTokenType.PASSWORD_RESET, now);

        String rawToken = tokenHasher.generateToken();
        tokenRepository.save(AuthActionToken.builder()
                .userId(user.getUserId())
                .tokenType(ActionTokenType.PASSWORD_RESET)
                .tokenHash(tokenHasher.hash(rawToken))
                .expiresAt(now.plus(properties.getPasswordResetTtl()))
                .createdAt(now)
                .build());

        eventPublisher.publishEvent(
                new PasswordResetRequestedEvent(user.getUserId(), user.getEmail(), rawToken));
        securityAuditService.recordSystem("PASSWORD_RESET_REQUESTED", user, Map.of());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        Instant now = Instant.now();
        AuthActionToken token = tokenRepository.findValidForUpdate(
                        tokenHasher.hash(request.token().trim()), ActionTokenType.PASSWORD_RESET, now)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN));

        AuthUser user = userRepository.findByIdForUpdate(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN));

        tokenRepository.invalidateUnused(user.getUserId(), ActionTokenType.PASSWORD_RESET, now);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(now);
        userRepository.save(user);

        int revoked = sessionService.revokeAllForUser(
                user.getUserId(), SessionService.REASON_PASSWORD_CHANGED);
        securityAuditService.recordSystem(
                "PASSWORD_RESET_COMPLETED", user, Map.of("revokedSessions", revoked));
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
