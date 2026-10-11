package MUDST_2026_Whatsss.event_registration.registration.domain;

import java.util.Set;

/** Mirrors {@code ck_registrations_status}; only the active states consume event capacity. */
public enum RegistrationStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PAYMENT_EXPIRED,
    CANCELLED_BY_USER,
    CANCELLED_BY_ADMIN,
    CANCELLED_EVENT,
    CHECKED_IN,
    NO_SHOW,
    REFUND_PENDING,
    REFUNDED;

    /** Kept in step with {@code EventRepository.countActiveRegistrations}. */
    public static final Set<RegistrationStatus> ACTIVE = Set.of(PENDING_PAYMENT, CONFIRMED, CHECKED_IN);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
