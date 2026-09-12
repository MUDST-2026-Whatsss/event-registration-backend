package MUDST_2026_Whatsss.event_registration.auth.repository;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByTokenHash(String tokenHash);

    List<AuthSession> findAllByUserId(UUID userId);

    /**
     * Serializes refresh-token rotation for one token. Without the database row lock, two
     * concurrent requests can both observe the predecessor as active and issue two successors.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.tokenHash = :tokenHash")
    Optional<AuthSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /**
     * Kills every session in a token family. Called when a refresh token is replayed: the presented
     * token was already rotated away, so either it leaked or the family is being used from two
     * places, and the safe response is to invalidate the whole chain rather than guess which holder
     * is legitimate.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthSession s
               set s.revokedAt = :now, s.revokedReason = :reason
             where s.tokenFamilyId = :familyId
               and s.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") UUID familyId,
                     @Param("now") Instant now,
                     @Param("reason") String reason);

    /** Used after a password change so that stolen sessions cannot outlive the new credential. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthSession s
               set s.revokedAt = :now, s.revokedReason = :reason
             where s.userId = :userId
               and s.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") UUID userId,
                         @Param("now") Instant now,
                         @Param("reason") String reason);
}
