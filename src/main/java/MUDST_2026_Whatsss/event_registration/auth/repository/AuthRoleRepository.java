package MUDST_2026_Whatsss.event_registration.auth.repository;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
