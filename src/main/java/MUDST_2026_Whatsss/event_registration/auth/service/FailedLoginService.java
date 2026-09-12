package MUDST_2026_Whatsss.event_registration.auth.service;

import MUDST_2026_Whatsss.event_registration.auth.config.AuthProperties;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Persists account lock counters independently from the login transaction that will roll back. */
@Service
public class FailedLoginService {

    private static final Logger log = LoggerFactory.getLogger(FailedLoginService.class);

    private final AuthUserRepository userRepository;
    private final AuthProperties properties;

    public FailedLoginService(AuthUserRepository userRepository, AuthProperties properties) {
        this.userRepository = userRepository;
        this.properties = properties;
    }

    /** A row lock prevents concurrent bad passwords from losing counter increments. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailure(UUID userId, Instant now) {
        AuthUser user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null) {
            return;
        }

        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        user.setUpdatedAt(now);

        if (attempts >= properties.getLockout().getMaxFailedAttempts()) {
            user.setLockedUntil(now.plus(properties.getLockout().getDuration()));
            user.setFailedLoginAttempts(0);
            log.warn("Locked account {} after {} failed attempts", user.getUserId(), attempts);
        }
        userRepository.save(user);
    }
}
