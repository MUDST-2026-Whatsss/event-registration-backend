package MUDST_2026_Whatsss.event_registration.auth.security;

import java.util.List;
import java.util.UUID;

/**
 * The principal placed in the security context by {@link JwtAuthenticationFilter}.
 *
 * <p>Built purely from verified JWT claims, so it is safe to trust inside controllers without
 * re-reading the database — with the caveat noted on {@link JwtService} that the claims are a
 * point-in-time snapshot.
 */
public record AuthenticatedUser(
        UUID userId,
        String email,
        String primaryRole,
        List<String> roles,
        List<String> permissions) {

    public boolean hasRole(String roleCode) {
        return roles.contains(roleCode);
    }

    public boolean hasPermission(String permissionCode) {
        return permissions.contains(permissionCode);
    }
}
