package MUDST_2026_Whatsss.event_registration.auth.ratelimit;

import MUDST_2026_Whatsss.event_registration.auth.config.AuthProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * In-memory token buckets for brute-force and request-flood protection.
 *
 * <p>Three independent limits apply, because each stops a different attack:
 * <ul>
 *   <li><b>per-IP login</b> — one host grinding many accounts (credential stuffing)</li>
 *   <li><b>per-account login</b> — many hosts grinding one account, which a per-IP limit misses
 *       entirely; this is the limit a botnet runs into</li>
 *   <li><b>global per-IP</b> — a cheap ceiling on overall request volume from a single source</li>
 * </ul>
 *
 * <p>Buckets live in a Caffeine cache that expires idle keys, which bounds memory: without it, a
 * flood of distinct spoofed keys would itself become a memory-exhaustion vector.
 *
 * <p>State is per-process. Behind more than one instance the effective limit multiplies by the
 * instance count, so a distributed backend is required before horizontal scaling — this is the
 * limitation AUTH_API.md already records. The account lockout in the database is what remains
 * authoritative across instances.
 */
@Service
public class RateLimiterService {

    /** Idle buckets are dropped after this long; comfortably longer than any configured window. */
    private static final Duration BUCKET_IDLE_TTL = Duration.ofMinutes(30);
    private static final int MAX_TRACKED_KEYS = 100_000;

    private final AuthProperties.RateLimit config;
    private final Cache<String, Bucket> loginByIp;
    private final Cache<String, Bucket> loginByAccount;
    private final Cache<String, Bucket> globalByIp;

    public RateLimiterService(AuthProperties properties) {
        this.config = properties.getRateLimit();
        this.loginByIp = newBucketCache();
        this.loginByAccount = newBucketCache();
        this.globalByIp = newBucketCache();
    }

    private static Cache<String, Bucket> newBucketCache() {
        return Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterAccess(BUCKET_IDLE_TTL)
                .build();
    }

    public RateLimitDecision checkLoginByIp(String ip) {
        return consume(loginByIp, ip, config.getLoginPerIp());
    }

    public RateLimitDecision checkLoginByAccount(String normalizedEmail) {
        return consume(loginByAccount, normalizedEmail, config.getLoginPerAccount());
    }

    public RateLimitDecision checkGlobalByIp(String ip) {
        return consume(globalByIp, ip, config.getGlobalPerIp());
    }

    private RateLimitDecision consume(Cache<String, Bucket> cache, String key, int capacity) {
        if (!config.isEnabled() || key == null || key.isBlank()) {
            return RateLimitDecision.allowed(capacity);
        }
        Bucket bucket = cache.get(key, ignored -> newBucket(capacity));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return RateLimitDecision.allowed(probe.getRemainingTokens());
        }
        return RateLimitDecision.denied(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    /**
     * A bucket that refills its whole capacity once per window, rather than trickling tokens back.
     * That gives a predictable "N attempts per minute" rule which is easy to reason about and to
     * document, at the cost of allowing a burst at the start of each window.
     */
    private Bucket newBucket(int capacity) {
        Bandwidth limit = Bandwidth.classic(
                capacity, Refill.intervally(capacity, config.getWindow()));
        return Bucket.builder().addLimit(limit).build();
    }

    /** Drops all counters. Intended for tests, which must not inherit each other's buckets. */
    public void reset() {
        loginByIp.invalidateAll();
        loginByAccount.invalidateAll();
        globalByIp.invalidateAll();
    }
}
