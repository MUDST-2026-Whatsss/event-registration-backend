package MUDST_2026_Whatsss.event_registration.registration.web.dto;

import MUDST_2026_Whatsss.event_registration.registration.domain.RegistrationStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminRecentRegistrationResponse(
        UUID registrationId,
        UUID eventId,
        String eventTitle,
        String registrationNumber,
        RegistrationStatus status,
        String attendeeName,
        String attendeeEmail,
        Instant registeredAt) {
}
