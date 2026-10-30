package MUDST_2026_Whatsss.event_registration.registration.web.dto;

import MUDST_2026_Whatsss.event_registration.event.web.dto.EventSummaryResponse;
import MUDST_2026_Whatsss.event_registration.registration.domain.RegistrationStatus;

import java.time.Instant;
import java.util.UUID;

/** A participant's own view of one registration, with the event it belongs to. */
public record RegistrationResponse(
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
        boolean cancellable,
        boolean registeredAsGuest,
        EventSummaryResponse event) {
}
