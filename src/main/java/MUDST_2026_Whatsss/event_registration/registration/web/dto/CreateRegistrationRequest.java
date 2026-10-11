package MUDST_2026_Whatsss.event_registration.registration.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Attendee snapshot for a new registration. Blank contact fields fall back to the caller's
 * profile, so a signed-in participant can register with only {@code consentAccepted}.
 */
public record CreateRegistrationRequest(
        @Size(max = 255) String attendeeName,
        @Email @Size(max = 320) String attendeeEmail,
        @Size(max = 30) String attendeePhone,
        @NotNull @AssertTrue Boolean consentAccepted) {
}
