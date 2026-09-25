package MUDST_2026_Whatsss.event_registration.auth;

import MUDST_2026_Whatsss.event_registration.PostgresIntegrationTest;
import MUDST_2026_Whatsss.event_registration.auth.domain.UserStatus;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.auth.service.AdminUserService;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.CreateRoleRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.UpdateUserStatusRequest;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import MUDST_2026_Whatsss.event_registration.event.service.AdminAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AdminAccessControlIT extends PostgresIntegrationTest {

    @Autowired private AdminUserService userService;
    @Autowired private AdminAuditService auditService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private AuthenticatedUser principal;

    @BeforeEach
    void setUp() {
        UUID actorId = insertUser("operator", false);
        principal = new AuthenticatedUser(
                actorId, "operator@example.test", "SUPER_ADMIN",
                List.of("SUPER_ADMIN"), List.of("USER_VIEW", "ROLE_ASSIGN", "AUDIT_VIEW"));
    }

    @Test
    void listsDatabaseUsersAndDisablingAccountWritesAudit() {
        UUID userId = insertUser("managed", true);

        var page = userService.users("managed", UserStatus.ACTIVE, "USER", PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).displayName()).isEqualTo("Managed Account");

        var disabled = userService.updateStatus(
                userId, new UpdateUserStatusRequest(UserStatus.DISABLED, 0L), principal);
        assertThat(disabled.status()).isEqualTo(UserStatus.DISABLED);
        assertThat(userService.stats().disabledAccounts()).isEqualTo(1);
        var auditPage = auditService.logs("USER_STATUS_CHANGED", "USER", PageRequest.of(0, 10));
        assertThat(auditPage.getContent().get(0).targetId()).isEqualTo(userId);
    }

    @Test
    void finalActiveSuperAdminCannotBeDisabled() {
        UUID superAdminId = insertUser("last-super", false);
        assignRole(superAdminId, "SUPER_ADMIN");

        assertThatThrownBy(() -> userService.updateStatus(
                superAdminId, new UpdateUserStatusRequest(UserStatus.DISABLED, 0L), principal))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_USER_STATE));
    }

    @Test
    void createsCustomRoleWithRealPermissionAndAuditRecord() {
        var role = userService.createRole(new CreateRoleRequest(
                "EVENT_REVIEWER", "Event Reviewer", "Reviews event submissions",
                "GLOBAL", "ACTIVE", Set.of("AUDIT_VIEW")), principal);

        assertThat(role.code()).isEqualTo("EVENT_REVIEWER");
        assertThat(role.permissions()).containsExactly("AUDIT_VIEW");
        assertThat(role.system()).isFalse();
        assertThat(auditService.logs("ROLE_CREATED", "ROLE", PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
    }

    private UUID insertUser(String prefix, boolean participant) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into auth_users (user_id, email, password_hash, status) values (?, ?, ?, ?)",
                userId, prefix + "@example.test", "test-hash", "ACTIVE");
        if (participant) {
            jdbcTemplate.update(
                    "insert into participants (participant_id, user_id, first_name, last_name) values (?, ?, ?, ?)",
                    UUID.randomUUID(), userId, "Managed", "Account");
            assignRole(userId, "USER");
        }
        return userId;
    }

    private void assignRole(UUID userId, String roleCode) {
        UUID roleId = jdbcTemplate.queryForObject(
                "select role_id from auth_roles where role_code = ?", UUID.class, roleCode);
        jdbcTemplate.update("insert into auth_user_roles (user_id, role_id) values (?, ?)", userId, roleId);
    }
}
