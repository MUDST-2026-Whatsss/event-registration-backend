package MUDST_2026_Whatsss.event_registration.auth.domain;

/**
 * Mirrors the {@code ck_users_status} check constraint on {@code auth_users}.
 */
public enum UserStatus {
    PENDING_VERIFICATION,
    INVITED,
    ACTIVE,
    SUSPENDED,
    DISABLED;

    /** Only ACTIVE accounts may hold a session; everything else is refused at login. */
    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
