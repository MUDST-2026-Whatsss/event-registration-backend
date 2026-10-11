package MUDST_2026_Whatsss.event_registration.auth.repository;

import MUDST_2026_Whatsss.event_registration.auth.domain.ActionTokenType;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthActionToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthActionTokenRepository extends JpaRepository<AuthActionToken, UUID> {

    boolean existsByUserIdAndTokenTypeAndUsedAtIsNullAndCreatedAtAfter(
            UUID userId, ActionTokenType tokenType, Instant createdAfter);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token from AuthActionToken token
            where token.tokenHash = :tokenHash
              and token.tokenType = :tokenType
              and token.usedAt is null
              and token.expiresAt > :now
            """)
    Optional<AuthActionToken> findValidForUpdate(
            @Param("tokenHash") String tokenHash,
            @Param("tokenType") ActionTokenType tokenType,
            @Param("now") Instant now);

    @Modifying
    @Query("""
            update AuthActionToken token set token.usedAt = :usedAt
            where token.userId = :userId
              and token.tokenType = :tokenType
              and token.usedAt is null
            """)
    int invalidateUnused(
            @Param("userId") UUID userId,
            @Param("tokenType") ActionTokenType tokenType,
            @Param("usedAt") Instant usedAt);
}
