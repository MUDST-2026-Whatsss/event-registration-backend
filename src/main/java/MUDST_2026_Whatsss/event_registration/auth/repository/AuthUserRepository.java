package MUDST_2026_Whatsss.event_registration.auth.repository;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import MUDST_2026_Whatsss.event_registration.auth.domain.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
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

    @Query("""
            select distinct u from AuthUser u
            join fetch u.roles r
            where r.roleCode = :roleCode and r.status = 'ACTIVE' and u.status = 'ACTIVE'
            order by u.email
            """)
    List<AuthUser> findActiveByRoleCode(@Param("roleCode") String roleCode);

    @Query(value = """
            select distinct u from AuthUser u
            left join u.roles r
            where (:query = ''
                   or lower(u.email) like lower(concat('%', :query, '%')))
              and (:status is null or u.status = :status)
              and (:roleCode = '' or r.roleCode = :roleCode)
            """,
            countQuery = """
            select count(distinct u.userId) from AuthUser u
            left join u.roles r
            where (:query = ''
                   or lower(u.email) like lower(concat('%', :query, '%')))
              and (:status is null or u.status = :status)
              and (:roleCode = '' or r.roleCode = :roleCode)
            """)
    Page<AuthUser> findForAdministration(
            @Param("query") String query,
            @Param("status") UserStatus status,
            @Param("roleCode") String roleCode,
            Pageable pageable);

    long countByStatus(UserStatus status);

    @Query("""
            select count(distinct u.userId) from AuthUser u
            join u.roles r
            where u.status = 'ACTIVE' and r.roleCode in :roleCodes and r.status = 'ACTIVE'
            """)
    long countActiveByRoleCodes(@Param("roleCodes") List<String> roleCodes);

    @Query("""
            select count(distinct u.userId) from AuthUser u
            join u.roles r
            where u.status = 'ACTIVE' and r.roleCode = :roleCode and r.status = 'ACTIVE'
            """)
    long countActiveByRoleCode(@Param("roleCode") String roleCode);

    @Query("select count(distinct u.userId) from AuthUser u join u.roles r where r.roleCode = :roleCode")
    long countByRoleCode(@Param("roleCode") String roleCode);
}
