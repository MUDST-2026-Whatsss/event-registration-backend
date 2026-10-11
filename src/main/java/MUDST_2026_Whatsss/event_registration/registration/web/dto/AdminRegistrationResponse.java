package MUDST_2026_Whatsss.event_registration.registration.web.dto;

import MUDST_2026_Whatsss.event_registration.registration.domain.RegistrationStatus;

import java.time.Instant;
import java.util.UUID;

/** One row of an event's participant list for its administrators. */
public record AdminRegistrationResponse(
        UUID registrationId,
        String registrationNumber,
        RegistrationStatus status,
        String attendeeName,
        String attendeeEmail,
        String attendeePhone,
        Instant registeredAt,
        Instant confirmedAt,
        Instant cancelledAt,
        String cancellationReason,
        Instant checkedInAt) {
}
