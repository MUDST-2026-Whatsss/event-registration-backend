package MUDST_2026_Whatsss.event_registration.auth.repository;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuthPermissionRepository extends JpaRepository<AuthPermission, UUID> {

    List<AuthPermission> findAllByOrderByPermissionCode();

    List<AuthPermission> findByPermissionCodeIn(List<String> permissionCodes);
}
