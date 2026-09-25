package MUDST_2026_Whatsss.event_registration.auth.web;

import MUDST_2026_Whatsss.event_registration.auth.domain.UserStatus;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.auth.service.AdminUserService;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.AdminRoleResponse;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.AdminPermissionResponse;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.AdminUserResponse;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.AdminUserStatsResponse;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.ReplaceUserRolesRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.UpdateUserStatusRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.CreateRoleRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.UpdateRoleRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.ReplaceRolePermissionsRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping("/users")
    public Page<AdminUserResponse> users(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String role,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return service.users(query, status, role, pageable);
    }

    @GetMapping("/users/stats")
    public AdminUserStatsResponse stats() {
        return service.stats();
    }

    @GetMapping("/users/{userId}")
    public AdminUserResponse user(@PathVariable UUID userId) {
        return service.user(userId);
    }

    @PatchMapping("/users/{userId}/status")
    public AdminUserResponse updateStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.updateStatus(userId, request, principal);
    }

    @PutMapping("/users/{userId}/roles")
    public AdminUserResponse replaceRoles(
            @PathVariable UUID userId,
            @Valid @RequestBody ReplaceUserRolesRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.replaceRoles(userId, request, principal);
    }

    @GetMapping("/roles")
    public List<AdminRoleResponse> roles() {
        return service.roles();
    }

    @GetMapping("/permissions")
    public List<AdminPermissionResponse> permissions() {
        return service.permissions();
    }

    @PostMapping("/roles")
    public AdminRoleResponse createRole(
            @Valid @RequestBody CreateRoleRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.createRole(request, principal);
    }

    @PatchMapping("/roles/{roleId}")
    public AdminRoleResponse updateRole(
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.updateRole(roleId, request, principal);
    }

    @PutMapping("/roles/{roleId}/permissions")
    public AdminRoleResponse replaceRolePermissions(
            @PathVariable UUID roleId,
            @Valid @RequestBody ReplaceRolePermissionsRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.replaceRolePermissions(roleId, request, principal);
    }
}
