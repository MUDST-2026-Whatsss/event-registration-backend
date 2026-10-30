package MUDST_2026_Whatsss.event_registration.registration.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Attendee details for a registration made without signing in. There is no profile to fall back
 * on, so name and email are required; the email is also how the registration is later found and
 * linked to an account.
 */
public record GuestRegistrationRequest(
        @NotBlank(message = "Name is required.")
        @Size(max = 255, message = "Name must be at most 255 characters.")
        String attendeeName,

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 320, message = "Email must be at most 320 characters.")
        String attendeeEmail,

        @Size(max = 30, message = "Phone number must be at most 30 characters.")
        String attendeePhone,

        @NotNull @AssertTrue Boolean consentAccepted) {
}
