package MUDST_2026_Whatsss.event_registration.auth.repository;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {

    /**
     * Looks an account up by its generated {@code email_normalized} column so that casing and
     * padding in the submitted address cannot be used to register or sign in as a second account.
     * Roles and permissions are fetched eagerly because every caller immediately builds
     * authorities from them.
     */
    @Query("""
            select distinct u from AuthUser u
            left join fetch u.roles r
            left join fetch r.permissions
            where u.emailNormalized = :normalizedEmail
            """)
    Optional<AuthUser> findByNormalizedEmail(@Param("normalizedEmail") String normalizedEmail);

    @Query("""
            select distinct u from AuthUser u
            left join fetch u.roles r
            left join fetch r.permissions
            where u.userId = :userId
            """)
    Optional<AuthUser> findByIdWithRoles(@Param("userId") UUID userId);

    /** Serializes failed-login counter updates across concurrent requests and API instances. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AuthUser u where u.userId = :userId")
    Optional<AuthUser> findByIdForUpdate(@Param("userId") UUID userId);

    boolean existsByEmailNormalized(String emailNormalized);
}
