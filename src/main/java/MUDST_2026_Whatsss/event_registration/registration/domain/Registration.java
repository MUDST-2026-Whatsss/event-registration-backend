package MUDST_2026_Whatsss.event_registration.registration.domain;

import MUDST_2026_Whatsss.event_registration.event.domain.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One participant's seat at one event, in {@code registrations}.
 *
 * <p>{@code (event_id, participant_id)} is unique, so registering again after a cancellation
 * reactivates this row instead of inserting a new one. Guest registrations have no participant
 * yet, so each guest attempt is its own row; only one active row per guest email is allowed.
 */
@Entity
@Table(name = "registrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Registration {

    @Id
    @GeneratedValue
    @Column(name = "registration_id", nullable = false, updatable = false)
    private UUID registrationId;

    @Column(name = "registration_number", nullable = false, length = 32, updatable = false)
    private String registrationNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    /** Null for a guest registration until it is linked to the account that signs up with its email. */
    @Column(name = "participant_id")
    private UUID participantId;

    /** Null when a guest submitted the registration without signing in. */
    @Column(name = "registered_by_user_id")
    private UUID registeredByUserId;

    @Column(name = "registered_as_guest", nullable = false, updatable = false)
    private boolean registeredAsGuest;

    @Column(name = "guest_linked_at")
    private Instant guestLinkedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RegistrationStatus status;

    @Column(name = "attendee_name", nullable = false, length = 255)
    private String attendeeName;

    @Column(name = "attendee_email", nullable = false, length = 320)
    private String attendeeEmail;

    @Column(name = "attendee_phone", length = 30)
    private String attendeePhone;

    @Column(name = "consent_accepted_at")
    private Instant consentAcceptedAt;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "checked_in_by_user_id")
    private UUID checkedInByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
