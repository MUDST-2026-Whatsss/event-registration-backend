package MUDST_2026_Whatsss.event_registration.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One issued refresh token in {@code auth_sessions}.
 *
 * <p>Only the SHA-256 {@code token_hash} is stored, so a database leak does not yield usable
 * tokens. Every rotation inserts a new row carrying the same {@code token_family_id} and pointing
 * at its predecessor, which is what makes replay of a already-rotated token detectable: the token
 * still resolves to a row, but that row is revoked, and the whole family is then killed.
 */
@Entity
@Table(name = "auth_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthSession {

    @Id
    @GeneratedValue
    @Column(name = "session_id", updatable = false, nullable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "token_family_id", nullable = false)
    private UUID tokenFamilyId;

    @Column(name = "parent_session_id")
    private UUID parentSessionId;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** See {@link InetType}: PostgreSQL will not implicitly cast a bound parameter to {@code inet}. */
    @Column(name = "ip_address", columnDefinition = "inet")
    @org.hibernate.annotations.Type(InetType.class)
    private String ipAddress;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 100)
    private String revokedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /** A session is usable only while it is neither revoked nor past its expiry. */
    public boolean isActive(Instant now) {
        return !isRevoked() && !isExpired(now);
    }

    public void revoke(Instant now, String reason) {
        if (revokedAt == null) {
            this.revokedAt = now;
            this.revokedReason = reason;
        }
    }
}
