package MUDST_2026_Whatsss.event_registration.auth.web.dto;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import MUDST_2026_Whatsss.event_registration.auth.domain.Participant;
import MUDST_2026_Whatsss.event_registration.auth.security.RoleCodes;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The {@code user} object returned by login, refresh and {@code /me}.
 *
 * <p>Role codes are emitted in their canonical database form ({@code USER}, {@code ADMIN},
 * {@code SUPER_ADMIN}); the SPA maps them to its own routing vocabulary. {@code role} is the single
 * collapsed value kept for the existing route guard, while {@code roles} and {@code permissions}
 * are what real authorization decisions should use.
 *
 * <p>{@code name} is derived here rather than stored, so the console header has a ready display
 * string without every caller having to concatenate one.
 */
public record UserResponse(
        UUID userId,
        UUID participantId,
        String email,
        String firstName,
        String lastName,
        String name,
        String phoneNumber,
        String role,
        List<String> roles,
        List<String> permissions,
        String status,
        Instant emailVerifiedAt) {

    public static UserResponse from(AuthUser user, Participant participant) {
        return forActiveRole(user, participant, RoleCodes.primaryRole(activeRoleCodes(user)));
    }

    /**
     * Builds the authentication payload for one active role. When {@code requestedRole} is null,
     * a single-role account is selected automatically while a multi-role account stays pending
     * until the user explicitly chooses a role.
     */
    public static UserResponse forActiveRole(
            AuthUser user, Participant participant, String requestedRole) {
        List<String> roleCodes = activeRoleCodes(user);
        String activeRole = requestedRole == null || requestedRole.isBlank()
                ? (roleCodes.size() == 1 ? roleCodes.get(0) : null)
                : requestedRole.trim().toUpperCase(Locale.ROOT);

        if (activeRole != null && !roleCodes.contains(activeRole)) {
            throw new IllegalArgumentException("Role is not assigned to this account.");
        }

        List<String> permissionCodes = activeRole == null
                ? List.of()
                : user.getRoles().stream()
                        .filter(role -> role.isActive() && activeRole.equals(role.getRoleCode()))
                        .flatMap(role -> role.getPermissions().stream())
                        .map(permission -> permission.getPermissionCode())
                        .distinct()
                        .sorted(Comparator.naturalOrder())
                        .toList();

        String firstName = participant == null ? null : participant.getFirstName();
        String lastName = participant == null ? null : participant.getLastName();

        return new UserResponse(
                user.getUserId(),
                participant == null ? null : participant.getParticipantId(),
                user.getEmail(),
                firstName,
                lastName,
                displayName(firstName, lastName, user.getEmail()),
                participant == null ? null : participant.getPhoneNumber(),
                activeRole,
                roleCodes,
                permissionCodes,
                user.getStatus().name(),
                user.getEmailVerifiedAt());
    }

    private static List<String> activeRoleCodes(AuthUser user) {
        List<String> roleCodes = user.getRoles().stream()
                .filter(role -> role.isActive())
                .map(role -> role.getRoleCode())
                .sorted()
                .toList();
        return roleCodes;
    }

    /** Falls back to the email local part so the UI always has something to show. */
    private static String displayName(String firstName, String lastName, String email) {
        String combined = ((firstName == null ? "" : firstName) + " "
                + (lastName == null ? "" : lastName)).trim();
        if (!combined.isEmpty()) {
            return combined;
        }
        int atIndex = email.indexOf('@');
        return atIndex > 0 ? email.substring(0, atIndex) : email;
    }
}
