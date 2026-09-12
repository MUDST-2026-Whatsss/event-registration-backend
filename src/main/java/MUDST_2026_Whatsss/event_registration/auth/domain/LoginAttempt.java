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
 * An audit row in {@code auth_login_attempts}, written for every login regardless of outcome.
 *
 * <p>The email is recorded as a SHA-256 {@code identifier_hash} rather than in clear text: the
 * table is useful for spotting credential-stuffing patterns without becoming a harvestable list of
 * addresses that tried to sign in. Attempts against a non-existent account still get a row, with a
 * null {@code user_id}.
 */
@Entity
@Table(name = "auth_login_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAttempt {

    @Id
    @GeneratedValue
    @Column(name = "login_attempt_id", updatable = false, nullable = false)
    private UUID loginAttemptId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "identifier_hash", nullable = false, length = 64)
    private String identifierHash;

    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;

    /**
     * Required when {@link #outcome} is FAILURE and forbidden when it is SUCCESS, per the
     * {@code ck_auth_login_attempts_failure_reason} constraint.
     */
    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    /** See {@link InetType}: PostgreSQL will not implicitly cast a bound parameter to {@code inet}. */
    @Column(name = "ip_address", columnDefinition = "inet")
    @org.hibernate.annotations.Type(InetType.class)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant attemptedAt = Instant.now();

    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILURE = "FAILURE";
}
