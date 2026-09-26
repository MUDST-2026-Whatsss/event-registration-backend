package MUDST_2026_Whatsss.event_registration.auth.service;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthPermission;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthRole;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import MUDST_2026_Whatsss.event_registration.auth.domain.Participant;
import MUDST_2026_Whatsss.event_registration.auth.domain.UserStatus;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthRoleRepository;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthPermissionRepository;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthUserRepository;
import MUDST_2026_Whatsss.event_registration.auth.repository.ParticipantRepository;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.auth.security.RoleCodes;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.AdminRoleResponse;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.AdminPermissionResponse;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.AdminUserResponse;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.AdminUserStatsResponse;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.ReplaceUserRolesRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.UpdateUserStatusRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.CreateRoleRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.UpdateRoleRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.ReplaceRolePermissionsRequest;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import MUDST_2026_Whatsss.event_registration.event.domain.AuditLog;
import MUDST_2026_Whatsss.event_registration.event.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private static final String SESSION_REASON_ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    private static final String SESSION_REASON_ROLES_CHANGED = "ROLES_CHANGED";
    private static final String SESSION_REASON_ROLE_POLICY_CHANGED = "ROLE_POLICY_CHANGED";

    private final AuthUserRepository userRepository;
    private final AuthRoleRepository roleRepository;
    private final AuthPermissionRepository permissionRepository;
    private final ParticipantRepository participantRepository;
    private final AuditLogRepository auditRepository;
    private final SessionService sessionService;

    public AdminUserService(
            AuthUserRepository userRepository,
            AuthRoleRepository roleRepository,
            AuthPermissionRepository permissionRepository,
            ParticipantRepository participantRepository,
            AuditLogRepository auditRepository,
            SessionService sessionService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.participantRepository = participantRepository;
        this.auditRepository = auditRepository;
        this.sessionService = sessionService;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> users(
            String query, UserStatus status, String roleCode, Pageable pageable) {
        String normalizedQuery = blankToEmpty(query);
        String normalizedRole = upperOrEmpty(roleCode);
        Page<AuthUser> users = userRepository.findForAdministration(
                normalizedQuery, status, normalizedRole, pageable);
        Map<UUID, Participant> profiles = participantRepository
                .findByUserIdIn(users.getContent().stream().map(AuthUser::getUserId).toList())
                .stream().collect(Collectors.toMap(Participant::getUserId, Function.identity()));
        return users.map(user -> toResponse(user, profiles.get(user.getUserId())));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse user(UUID userId) {
        AuthUser user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        return toResponse(user, participantRepository.findByUserId(userId).orElse(null));
    }

    @Transactional(readOnly = true)
    public AdminUserStatsResponse stats() {
        return new AdminUserStatsResponse(
                userRepository.countActiveByRoleCodes(List.of(RoleCodes.ADMIN, RoleCodes.SUPER_ADMIN)),
                userRepository.count(),
                userRepository.countByStatus(UserStatus.DISABLED));
    }

    @Transactional(readOnly = true)
    public List<AdminRoleResponse> roles() {
        return roleRepository.findAllWithPermissions().stream()
                .map(this::toRoleResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminPermissionResponse> permissions() {
        return permissionRepository.findAllByOrderByPermissionCode().stream()
                .map(permission -> new AdminPermissionResponse(
                        permission.getPermissionId(), permission.getPermissionCode(),
                        permission.getDescription()))
                .toList();
    }

    @Transactional
    public AdminRoleResponse createRole(CreateRoleRequest request, AuthenticatedUser principal) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (roleRepository.existsByRoleCode(code)) {
            throw new ApiException(ErrorCode.ROLE_CODE_ALREADY_EXISTS);
        }
        validateRoleFields(request.scopeType(), request.status());
        Set<AuthPermission> permissions = resolvePermissions(request.permissionCodes());
        Instant now = Instant.now();
        AuthRole role = roleRepository.saveAndFlush(AuthRole.builder()
                .roleCode(code)
                .roleName(request.name().trim())
                .description(blankToNull(request.description()))
                .scopeType(request.scopeType().trim().toUpperCase(Locale.ROOT))
                .status(request.status().trim().toUpperCase(Locale.ROOT))
                .system(false)
                .permissions(permissions)
                .createdAt(now)
                .updatedAt(now)
                .build());
        auditRole(actor(principal), "ROLE_CREATED", role,
                Map.of("permissionCount", permissions.size()));
        return toRoleResponse(role);
    }

    @Transactional
    public AdminRoleResponse updateRole(
            UUID roleId, UpdateRoleRequest request, AuthenticatedUser principal) {
        validateRoleFields(request.scopeType(), request.status());
        AuthRole role = lockedRole(roleId);
        String newStatus = request.status().trim().toUpperCase(Locale.ROOT);
        if (role.isSystem() && !AuthRole.STATUS_ACTIVE.equals(newStatus)) {
            throw new ApiException(ErrorCode.INVALID_USER_STATE,
                    "System roles cannot be disabled.");
        }
        if (!AuthRole.STATUS_ACTIVE.equals(newStatus)
                && userRepository.countByRoleCode(role.getRoleCode()) > 0) {
            throw new ApiException(ErrorCode.INVALID_USER_STATE,
                    "A role assigned to users cannot be disabled.");
        }
        role.setRoleName(request.name().trim());
        role.setDescription(blankToNull(request.description()));
        role.setScopeType(request.scopeType().trim().toUpperCase(Locale.ROOT));
        role.setStatus(newStatus);
        role.setUpdatedAt(Instant.now());
        roleRepository.saveAndFlush(role);
        revokeSessionsForRole(role.getRoleCode());
        auditRole(actor(principal), "ROLE_UPDATED", role,
                Map.of("status", newStatus, "scopeType", role.getScopeType()));
        return toRoleResponse(role);
    }

    @Transactional
    public AdminRoleResponse replaceRolePermissions(
            UUID roleId, ReplaceRolePermissionsRequest request, AuthenticatedUser principal) {
        AuthRole role = lockedRole(roleId);
        Set<AuthPermission> permissions = resolvePermissions(request.permissionCodes());
        role.setPermissions(permissions);
        role.setUpdatedAt(Instant.now());
        roleRepository.saveAndFlush(role);
        revokeSessionsForRole(role.getRoleCode());
        auditRole(actor(principal), "ROLE_PERMISSIONS_REPLACED", role,
                Map.of("permissions", permissions.stream()
                        .map(AuthPermission::getPermissionCode).sorted().toList()));
        return toRoleResponse(role);
    }

    @Transactional
    public AdminUserResponse updateStatus(
            UUID userId, UpdateUserStatusRequest request, AuthenticatedUser principal) {
        if (request.status() != UserStatus.ACTIVE && request.status() != UserStatus.DISABLED) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "User Management can only activate or disable an account.");
        }
        AuthUser target = lockedUser(userId, request.version());
        UserStatus oldStatus = target.getStatus();
        if (oldStatus == request.status()) {
            return toResponse(target, participantRepository.findByUserId(userId).orElse(null));
        }
        if (principal.userId().equals(userId) && request.status() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_USER_STATE,
                    "You cannot disable your own account.");
        }
        if (request.status() != UserStatus.ACTIVE) {
            protectFinalSuperAdmin(target);
        }

        target.setStatus(request.status());
        target.setUpdatedAt(Instant.now());
        userRepository.saveAndFlush(target);
        if (request.status() == UserStatus.DISABLED) {
            sessionService.revokeAllForUser(userId, SESSION_REASON_ACCOUNT_DISABLED);
        }
        AuthUser actor = actor(principal);
        audit(actor, "USER_STATUS_CHANGED", target,
                Map.of("oldStatus", oldStatus.name(), "newStatus", request.status().name()));
        return toResponse(target, participantRepository.findByUserId(userId).orElse(null));
    }

    @Transactional
    public AdminUserResponse replaceRoles(
            UUID userId, ReplaceUserRolesRequest request, AuthenticatedUser principal) {
        List<String> roleCodes = request.roleCodes().stream()
                .map(String::trim).map(value -> value.toUpperCase(Locale.ROOT)).distinct().sorted().toList();
        List<AuthRole> roles = roleRepository.findAllActiveByRoleCodeIn(roleCodes);
        if (roles.size() != roleCodes.size()) {
            throw new ApiException(ErrorCode.ROLE_NOT_FOUND);
        }

        AuthUser target = lockedUser(userId, request.version());
        Set<String> oldRoles = target.getRoles().stream().map(AuthRole::getRoleCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        boolean removesSuperAdmin = oldRoles.contains(RoleCodes.SUPER_ADMIN)
                && !roleCodes.contains(RoleCodes.SUPER_ADMIN);
        if (removesSuperAdmin && principal.userId().equals(userId)) {
            throw new ApiException(ErrorCode.INVALID_USER_STATE,
                    "You cannot remove your own super-admin role.");
        }
        if (removesSuperAdmin) {
            protectFinalSuperAdmin(target);
        }

        target.setRoles(new LinkedHashSet<>(roles));
        target.setUpdatedAt(Instant.now());
        userRepository.saveAndFlush(target);
        sessionService.revokeAllForUser(userId, SESSION_REASON_ROLES_CHANGED);
        AuthUser actor = actor(principal);
        audit(actor, "USER_ROLES_REPLACED", target,
                Map.of("oldRoles", oldRoles, "newRoles", roleCodes));
        return toResponse(target, participantRepository.findByUserId(userId).orElse(null));
    }

    private AuthUser lockedUser(UUID userId, long expectedVersion) {
        AuthUser user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (user.getVersion() != expectedVersion) {
            throw new ApiException(ErrorCode.USER_VERSION_CONFLICT);
        }
        return user;
    }

    private void protectFinalSuperAdmin(AuthUser target) {
        boolean activeSuperAdmin = target.getStatus() == UserStatus.ACTIVE
                && target.getRoles().stream().anyMatch(role -> RoleCodes.SUPER_ADMIN.equals(role.getRoleCode()));
        if (activeSuperAdmin && userRepository.countActiveByRoleCode(RoleCodes.SUPER_ADMIN) <= 1) {
            throw new ApiException(ErrorCode.INVALID_USER_STATE,
                    "The final active super administrator cannot be disabled or demoted.");
        }
    }

    private AuthUser actor(AuthenticatedUser principal) {
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    private void audit(AuthUser actor, String action, AuthUser target, Map<String, Object> metadata) {
        auditRepository.save(AuditLog.builder()
                .actor(actor).action(action).targetType("USER").targetId(target.getUserId())
                .targetLabel(target.getEmail()).metadata(metadata).build());
    }

    private void auditRole(AuthUser actor, String action, AuthRole role, Map<String, Object> metadata) {
        auditRepository.save(AuditLog.builder()
                .actor(actor).action(action).targetType("ROLE").targetId(role.getRoleId())
                .targetLabel(role.getRoleName()).metadata(metadata).build());
    }

    private void revokeSessionsForRole(String roleCode) {
        userRepository.findAllByRoleCode(roleCode).forEach(user ->
                sessionService.revokeAllForUser(
                        user.getUserId(), SESSION_REASON_ROLE_POLICY_CHANGED));
    }

    private AuthRole lockedRole(UUID roleId) {
        return roleRepository.findByIdForUpdate(roleId)
                .orElseThrow(() -> new ApiException(ErrorCode.ROLE_NOT_FOUND));
    }

    private Set<AuthPermission> resolvePermissions(Set<String> requestedCodes) {
        List<String> codes = requestedCodes.stream().map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT)).distinct().sorted().toList();
        List<AuthPermission> permissions = permissionRepository.findByPermissionCodeIn(codes);
        if (permissions.size() != codes.size()) {
            throw new ApiException(ErrorCode.PERMISSION_NOT_FOUND);
        }
        return new LinkedHashSet<>(permissions);
    }

    private static void validateRoleFields(String scopeType, String status) {
        String normalizedScope = scopeType.trim().toUpperCase(Locale.ROOT);
        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("SELF", "ASSIGNED_EVENTS", "GLOBAL").contains(normalizedScope)
                || !Set.of("ACTIVE", "INACTIVE").contains(normalizedStatus)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Role scope or status is invalid.");
        }
    }

    private AdminRoleResponse toRoleResponse(AuthRole role) {
        return new AdminRoleResponse(
                role.getRoleId(), role.getRoleCode(), role.getRoleName(), role.getDescription(),
                role.getStatus(), role.getScopeType(), role.isSystem(),
                role.getPermissions().stream().map(AuthPermission::getPermissionCode).sorted().toList(),
                userRepository.countByRoleCode(role.getRoleCode()), role.getCreatedAt());
    }

    private AdminUserResponse toResponse(AuthUser user, Participant participant) {
        List<AdminUserResponse.RoleSummary> roles = user.getRoles().stream()
                .sorted((left, right) -> left.getRoleCode().compareTo(right.getRoleCode()))
                .map(role -> new AdminUserResponse.RoleSummary(role.getRoleCode(), role.getRoleName()))
                .toList();
        List<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(AuthPermission::getPermissionCode).distinct().sorted().toList();
        return new AdminUserResponse(
                user.getUserId(), user.getEmail(), displayName(user, participant), user.getStatus(),
                roles, permissions, user.getLastLoginAt(), user.getCreatedAt(), user.getVersion());
    }

    private static String displayName(AuthUser user, Participant participant) {
        if (participant == null) return user.getEmail();
        String name = String.join(" ",
                participant.getFirstName() == null ? "" : participant.getFirstName().trim(),
                participant.getLastName() == null ? "" : participant.getLastName().trim()).trim();
        return name.isBlank() ? user.getEmail() : name;
    }

    private static String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String upperOrEmpty(String value) {
        return blankToEmpty(value).toUpperCase(Locale.ROOT);
    }
}
