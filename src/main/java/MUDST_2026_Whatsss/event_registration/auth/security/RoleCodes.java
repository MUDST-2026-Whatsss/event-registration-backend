package MUDST_2026_Whatsss.event_registration.auth.security;

import java.util.List;

/**
 * Role codes seeded in {@code auth_roles}, and the precedence used to pick the single
 * {@code role} field the frontend route guard still reads.
 */
public final class RoleCodes {

    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** Most privileged first; {@code primaryRole} is the first of these the account holds. */
    private static final List<String> PRECEDENCE = List.of(SUPER_ADMIN, ADMIN, USER);

    private RoleCodes() {
    }

    /**
     * Collapses a set of role codes into the one the SPA should route on. Falls back to the first
     * assigned role for codes added after this class was written, and to USER when there are none.
     */
    public static String primaryRole(List<String> assignedRoles) {
        for (String candidate : PRECEDENCE) {
            if (assignedRoles.contains(candidate)) {
                return candidate;
            }
        }
        return assignedRoles.isEmpty() ? USER : assignedRoles.get(0);
    }
}
