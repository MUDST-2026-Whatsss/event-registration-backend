package MUDST_2026_Whatsss.event_registration.auth.service;

import MUDST_2026_Whatsss.event_registration.auth.config.AuthProperties;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthRole;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import MUDST_2026_Whatsss.event_registration.auth.domain.Participant;
import MUDST_2026_Whatsss.event_registration.auth.domain.UserStatus;
import MUDST_2026_Whatsss.event_registration.auth.ratelimit.RateLimitDecision;
import MUDST_2026_Whatsss.event_registration.auth.ratelimit.RateLimiterService;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthRoleRepository;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthUserRepository;
import MUDST_2026_Whatsss.event_registration.auth.repository.ParticipantRepository;
import MUDST_2026_Whatsss.event_registration.auth.security.RoleCodes;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.ChangePasswordRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.LoginRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.RegisterRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.UpdateProfileRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.UserResponse;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * The account lifecycle: registration, login, session refresh, profile and password changes.
 *
 * <p>Several behaviours here exist specifically to avoid leaking information:
 * <ul>
 *   <li>unknown email and wrong password produce the identical {@code INVALID_CREDENTIALS} error</li>
 *   <li>a password is verified even when the account does not exist, so response timing does not
 *       reveal which addresses are registered</li>
 *   <li>registering an address that already exists returns a generic conflict rather than
 *       confirming the account</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * A BCrypt hash of a random value nobody holds, compared against when the account is missing so
     * that the failure path costs the same as a real password check.
     *
     * <p>Generated at startup rather than hard-coded: a literal would silently stop costing
     * anything if its cost factor drifted from the configured one, which would reopen the timing
     * side channel it exists to close.
     */
    private final String dummyHash;

    private final AuthUserRepository userRepository;
    private final ParticipantRepository participantRepository;
    private final AuthRoleRepository roleRepository;
    private final SessionService sessionService;
    private final LoginAuditService loginAuditService;
    private final FailedLoginService failedLoginService;
    private final RateLimiterService rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

    public AuthService(AuthUserRepository userRepository,
                       ParticipantRepository participantRepository,
                       AuthRoleRepository roleRepository,
                       SessionService sessionService,
                       LoginAuditService loginAuditService,
                       FailedLoginService failedLoginService,
                       RateLimiterService rateLimiter,
                       PasswordEncoder passwordEncoder,
                       AuthProperties properties) {
        this.userRepository = userRepository;
        this.participantRepository = participantRepository;
        this.roleRepository = roleRepository;
        this.sessionService = sessionService;
        this.loginAuditService = loginAuditService;
        this.failedLoginService = failedLoginService;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Creates an account with the USER role and its participant profile.
     *
     * <p>Production accounts start at {@code PENDING_VERIFICATION}. Local/test profiles may opt
     * into auto-verification so development remains usable before an email provider is connected.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (userRepository.existsByEmailNormalized(normalizedEmail)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        AuthRole userRole = roleRepository.findByRoleCode(RoleCodes.USER)
                .orElseThrow(() -> new IllegalStateException(
                        "Role " + RoleCodes.USER + " is missing; check the auth_roles seed data."));

        Instant now = Instant.now();
        boolean autoVerify = properties.isRegistrationAutoVerify();
        AuthUser user = AuthUser.builder()
                .email(request.email().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(autoVerify ? UserStatus.ACTIVE : UserStatus.PENDING_VERIFICATION)
                .emailVerifiedAt(autoVerify ? now : null)
                .createdAt(now)
                .updatedAt(now)
                .build();
        user.getRoles().add(userRole);

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent registrations for one address; the unique index is the arbiter.
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        Participant participant = participantRepository.save(Participant.builder()
                .userId(user.getUserId())
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .phoneNumber(normalizePhone(request.phoneNumber()))
                .createdAt(now)
                .updatedAt(now)
                .build());

        log.info("Registered account {}", user.getUserId());
        return UserResponse.from(user, participant);
    }

    /**
     * Verifies credentials and starts a session.
     *
     * @throws ApiException with {@code RATE_LIMITED}, {@code ACCOUNT_LOCKED},
     *                      {@code ACCOUNT_NOT_ACTIVE} or {@code INVALID_CREDENTIALS}
     */
    @Transactional
    public LoginResult login(LoginRequest request, RequestContext context) {
        String normalizedEmail = normalizeEmail(request.email());

        enforceLoginRateLimits(normalizedEmail, context);

        AuthUser user = userRepository.findByNormalizedEmail(normalizedEmail).orElse(null);

        if (user == null) {
            // Spend comparable time so a missing account is not detectable by response latency.
            passwordEncoder.matches(request.password(), dummyHash);
            loginAuditService.recordFailure(null, normalizedEmail,
                    LoginAuditService.REASON_UNKNOWN_ACCOUNT, context);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        Instant now = Instant.now();

        if (user.isLocked(now)) {
            loginAuditService.recordFailure(user.getUserId(), normalizedEmail,
                    LoginAuditService.REASON_ACCOUNT_LOCKED, context);
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            failedLoginService.registerFailure(user.getUserId(), now);
            loginAuditService.recordFailure(user.getUserId(), normalizedEmail,
                    LoginAuditService.REASON_BAD_PASSWORD, context);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!user.getStatus().canAuthenticate()) {
            // Checked after the password so that probing cannot enumerate suspended accounts.
            loginAuditService.recordFailure(user.getUserId(), normalizedEmail,
                    LoginAuditService.REASON_ACCOUNT_NOT_ACTIVE, context);
            throw new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        loginAuditService.recordSuccess(user.getUserId(), normalizedEmail, context);

        IssuedSession session = sessionService.createSession(user.getUserId(), context);
        Participant participant = participantRepository.findByUserId(user.getUserId()).orElse(null);

        return new LoginResult(UserResponse.from(user, participant), session);
    }

    /** Rotates the refresh token and re-reads the user so role changes are picked up. */
    @Transactional
    public LoginResult refresh(String rawRefreshToken, RequestContext context) {
        IssuedSession rotated = sessionService.rotate(rawRefreshToken, context);
        UUID userId = rotated.session().getUserId();

        AuthUser user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!user.getStatus().canAuthenticate()) {
            sessionService.revokeAllForUser(userId, SessionService.REASON_LOGOUT);
            throw new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        Participant participant = participantRepository.findByUserId(userId).orElse(null);
        return new LoginResult(UserResponse.from(user, participant), rotated);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        AuthUser user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        Participant participant = participantRepository.findByUserId(userId).orElse(null);
        return UserResponse.from(user, participant);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        AuthUser user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));

        Instant now = Instant.now();
        Participant participant = participantRepository.findByUserId(userId)
                .orElseGet(() -> Participant.builder()
                        .userId(userId)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());

        // Null means "unchanged"; only supplied fields are written.
        if (request.firstName() != null) {
            participant.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null) {
            participant.setLastName(request.lastName().trim());
        }
        if (request.phoneNumber() != null) {
            participant.setPhoneNumber(normalizePhone(request.phoneNumber()));
        }
        participant.setUpdatedAt(now);

        return UserResponse.from(user, participantRepository.save(participant));
    }

    /**
     * Changes the password and revokes every session.
     *
     * <p>Revoking all sessions is the point of the operation as much as the new hash is: a user
     * changing their password after a suspected compromise expects it to evict whoever else is
     * signed in.
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        AuthUser user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        int revoked = sessionService.revokeAllForUser(userId, SessionService.REASON_PASSWORD_CHANGED);
        log.info("Password changed for {}; revoked {} session(s)", userId, revoked);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            sessionService.revokeByRawToken(rawRefreshToken);
        }
    }

    private void enforceLoginRateLimits(String normalizedEmail, RequestContext context) {
        RateLimitDecision byIp = rateLimiter.checkLoginByIp(context.ipAddress());
        if (!byIp.allowed()) {
            loginAuditService.recordFailure(null, normalizedEmail,
                    LoginAuditService.REASON_RATE_LIMITED, context);
            throw new ApiException(ErrorCode.RATE_LIMITED);
        }

        // Also limited per account, which is what a distributed attack from many IPs runs into.
        RateLimitDecision byAccount = rateLimiter.checkLoginByAccount(normalizedEmail);
        if (!byAccount.allowed()) {
            loginAuditService.recordFailure(null, normalizedEmail,
                    LoginAuditService.REASON_RATE_LIMITED, context);
            throw new ApiException(ErrorCode.RATE_LIMITED);
        }
    }

    /** Mirrors the {@code email_normalized} generated column: trimmed and lowercased. */
    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Stores digits only, so that {@code 081-234-5678} and {@code 0812345678} compare equal. */
    private static String normalizePhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        return phoneNumber.replaceAll("[\\s-]", "");
    }

    /** A completed authentication: the user payload plus the session cookie material. */
    public record LoginResult(UserResponse user, IssuedSession session) {
    }
}
