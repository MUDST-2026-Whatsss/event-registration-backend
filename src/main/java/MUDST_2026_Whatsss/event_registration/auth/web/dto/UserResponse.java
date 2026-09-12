package MUDST_2026_Whatsss.event_registration.auth.web.dto;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import MUDST_2026_Whatsss.event_registration.auth.domain.Participant;
import MUDST_2026_Whatsss.event_registration.auth.security.RoleCodes;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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
        List<String> roleCodes = user.getRoles().stream()
                .filter(role -> role.isActive())
                .map(role -> role.getRoleCode())
                .sorted()
                .toList();

        List<String> permissionCodes = user.getRoles().stream()
                .filter(role -> role.isActive())
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
                RoleCodes.primaryRole(roleCodes),
                roleCodes,
                permissionCodes,
                user.getStatus().name(),
                user.getEmailVerifiedAt());
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
