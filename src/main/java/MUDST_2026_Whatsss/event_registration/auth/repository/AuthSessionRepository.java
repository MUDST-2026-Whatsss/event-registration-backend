package MUDST_2026_Whatsss.event_registration.auth.repository;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(value = """
            select s from AuthSession s
            where (:query = ''
                   or lower(coalesce(s.revokedReason, '')) like lower(concat('%', :query, '%'))
                   or lower(coalesce(s.userAgent, '')) like lower(concat('%', :query, '%'))
                   or exists (select u.userId from AuthUser u where u.userId = s.userId
                              and lower(u.email) like lower(concat('%', :query, '%')))
                   or exists (select p.participantId from Participant p where p.userId = s.userId
                              and (lower(coalesce(p.firstName, '')) like lower(concat('%', :query, '%'))
                                   or lower(coalesce(p.lastName, '')) like lower(concat('%', :query, '%'))
                                   or lower(concat(concat(coalesce(p.firstName, ''), ' '), coalesce(p.lastName, '')))
                                      like lower(concat('%', :query, '%')))))
              and (:state = ''
                   or (:state = 'ACTIVE' and s.revokedAt is null and s.expiresAt > :now)
                   or (:state = 'EXPIRED' and s.revokedAt is null and s.expiresAt <= :now)
                   or s.revokedReason = :state)
              and coalesce(s.revokedAt, s.createdAt) >= :fromActivityAt
              and coalesce(s.revokedAt, s.createdAt) < :toActivityAt
            order by coalesce(s.revokedAt, s.createdAt) desc
            """,
            countQuery = """
            select count(s.sessionId) from AuthSession s
            where (:query = ''
                   or lower(coalesce(s.revokedReason, '')) like lower(concat('%', :query, '%'))
                   or lower(coalesce(s.userAgent, '')) like lower(concat('%', :query, '%'))
                   or exists (select u.userId from AuthUser u where u.userId = s.userId
                              and lower(u.email) like lower(concat('%', :query, '%')))
                   or exists (select p.participantId from Participant p where p.userId = s.userId
                              and (lower(coalesce(p.firstName, '')) like lower(concat('%', :query, '%'))
                                   or lower(coalesce(p.lastName, '')) like lower(concat('%', :query, '%'))
                                   or lower(concat(concat(coalesce(p.firstName, ''), ' '), coalesce(p.lastName, '')))
                                      like lower(concat('%', :query, '%')))))
              and (:state = ''
                   or (:state = 'ACTIVE' and s.revokedAt is null and s.expiresAt > :now)
                   or (:state = 'EXPIRED' and s.revokedAt is null and s.expiresAt <= :now)
                   or s.revokedReason = :state)
              and coalesce(s.revokedAt, s.createdAt) >= :fromActivityAt
              and coalesce(s.revokedAt, s.createdAt) < :toActivityAt
            """)
    Page<AuthSession> findForAdministration(
            @Param("query") String query,
            @Param("state") String state,
            @Param("now") Instant now,
            @Param("fromActivityAt") Instant fromActivityAt,
            @Param("toActivityAt") Instant toActivityAt,
            Pageable pageable);

    @Query("""
            select s from AuthSession s
            where (:query = ''
                   or lower(coalesce(s.revokedReason, '')) like lower(concat('%', :query, '%'))
                   or lower(coalesce(s.userAgent, '')) like lower(concat('%', :query, '%'))
                   or exists (select u.userId from AuthUser u where u.userId = s.userId
                              and lower(u.email) like lower(concat('%', :query, '%')))
                   or exists (select p.participantId from Participant p where p.userId = s.userId
                              and (lower(coalesce(p.firstName, '')) like lower(concat('%', :query, '%'))
                                   or lower(coalesce(p.lastName, '')) like lower(concat('%', :query, '%'))
                                   or lower(concat(concat(coalesce(p.firstName, ''), ' '), coalesce(p.lastName, '')))
                                      like lower(concat('%', :query, '%')))))
              and (:state = ''
                   or (:state = 'ACTIVE' and s.revokedAt is null and s.expiresAt > :now)
                   or (:state = 'EXPIRED' and s.revokedAt is null and s.expiresAt <= :now)
                   or s.revokedReason = :state)
              and coalesce(s.revokedAt, s.createdAt) >= :fromActivityAt
              and coalesce(s.revokedAt, s.createdAt) < :toActivityAt
            order by coalesce(s.revokedAt, s.createdAt) desc
            """)
    List<AuthSession> findAllForAdministration(
            @Param("query") String query,
            @Param("state") String state,
            @Param("now") Instant now,
            @Param("fromActivityAt") Instant fromActivityAt,
            @Param("toActivityAt") Instant toActivityAt);

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
