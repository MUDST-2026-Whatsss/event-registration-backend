package MUDST_2026_Whatsss.event_registration.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Tunable auth settings, bound from {@code event.auth.*}.
 *
 * <p>Defaults match AUTH_API.md. The JWT secret has no default on purpose — startup fails rather
 * than silently signing tokens with a well-known key.
 */
@ConfigurationProperties(prefix = "event.auth")
@Validated
@Getter
@Setter
public class AuthProperties {

    /** HS256 signing key, base64 or raw; must decode to at least 32 bytes. */
    @NotBlank
    private String jwtSecret;

    private String jwtIssuer = "event-registration";

    private Duration accessTokenTtl = Duration.ofMinutes(15);

    private Duration refreshTokenTtl = Duration.ofDays(7);

    /** Local/test escape hatch; production registrations must verify their email before login. */
    private boolean registrationAutoVerify = false;

    /** Marks session cookies {@code Secure}. Must stay true anywhere served over HTTPS. */
    private boolean cookieSecure = false;

    private String cookieSameSite = "Lax";

    private String cookieDomain;

    private final Lockout lockout = new Lockout();

    private final RateLimit rateLimit = new RateLimit();

    private final Cors cors = new Cors();

    @Getter
    @Setter
    public static class Lockout {
        /** Consecutive failures before the account is locked. */
        @Min(1)
        private int maxFailedAttempts = 5;

        private Duration duration = Duration.ofMinutes(15);
    }

    @Getter
    @Setter
    public static class RateLimit {
        private boolean enabled = true;

        /** Login attempts allowed per IP per {@link #window}. */
        @Min(1)
        private int loginPerIp = 10;

        /** Login attempts allowed per email address per {@link #window}, across all IPs. */
        @Min(1)
        private int loginPerAccount = 5;

        /** Ceiling on all other authenticated/public API calls per IP per {@link #window}. */
        @Min(1)
        private int globalPerIp = 300;

        private Duration window = Duration.ofMinutes(1);

        /**
         * Trust {@code X-Forwarded-For} when resolving the client IP. Enable only behind a proxy
         * that overwrites the header, otherwise a caller can spoof it and evade the limiter.
         */
        private boolean trustForwardedFor = false;
    }

    @Getter
    @Setter
    public static class Cors {
        /** Exact origins allowed to send credentialed requests; wildcards are not permitted. */
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }
}
