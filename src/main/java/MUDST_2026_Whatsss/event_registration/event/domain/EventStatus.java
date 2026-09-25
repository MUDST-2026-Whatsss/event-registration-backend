package MUDST_2026_Whatsss.event_registration.event.domain;

/** Lifecycle states enforced by the events table and event services. */
public enum EventStatus {
    DRAFT,
    PENDING_REVIEW,
    PUBLISHED,
    REJECTED,
    CANCELLED,
    COMPLETED
}
