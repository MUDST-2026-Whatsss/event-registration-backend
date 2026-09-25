package MUDST_2026_Whatsss.event_registration.auth.repository;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthRoleRepository extends JpaRepository<AuthRole, UUID> {

    @Query("""
            select distinct r from AuthRole r
            left join fetch r.permissions
            where r.roleCode = :roleCode
              and r.status = 'ACTIVE'
            """)
    Optional<AuthRole> findByRoleCode(@Param("roleCode") String roleCode);

    @Query("""
            select distinct r from AuthRole r
            left join fetch r.permissions
            where r.status = 'ACTIVE'
            order by r.roleName
            """)
    List<AuthRole> findAllActiveWithPermissions();

    @Query("""
            select distinct r from AuthRole r
            left join fetch r.permissions
            order by r.roleName
            """)
    List<AuthRole> findAllWithPermissions();

    @Query("""
            select distinct r from AuthRole r
            left join fetch r.permissions
            where r.roleCode in :roleCodes and r.status = 'ACTIVE'
            """)
    List<AuthRole> findAllActiveByRoleCodeIn(@Param("roleCodes") List<String> roleCodes);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AuthRole r where r.roleId = :roleId")
    Optional<AuthRole> findByIdForUpdate(@Param("roleId") UUID roleId);

    boolean existsByRoleCode(String roleCode);
}
