package MUDST_2026_Whatsss.event_registration.auth.ratelimit;

import java.time.Duration;

/**
 * Outcome of a rate-limit check.
 *
 * @param allowed        whether the caller may proceed
 * @param remainingTokens attempts left in the current window, for the {@code X-RateLimit-Remaining} header
 * @param retryAfter     how long until capacity returns, meaningful only when denied
 */
public record RateLimitDecision(boolean allowed, long remainingTokens, Duration retryAfter) {

    public static RateLimitDecision allowed(long remainingTokens) {
        return new RateLimitDecision(true, remainingTokens, Duration.ZERO);
    }

    public static RateLimitDecision denied(Duration retryAfter) {
        return new RateLimitDecision(false, 0, retryAfter);
    }

    /** Seconds to advertise in {@code Retry-After}; always at least one so clients back off. */
    public long retryAfterSeconds() {
        return Math.max(1, retryAfter.toSeconds());
    }
}
