package MUDST_2026_Whatsss.event_registration.registration.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Both values must match, so a registration number alone does not reveal the attendee's details. */
public record GuestTicketLookupRequest(
        @NotBlank(message = "Registration number is required.")
        @Size(max = 32, message = "Registration number must be at most 32 characters.")
        String registrationNumber,

        @NotBlank(message = "Email is required.")
        @Size(max = 320, message = "Email must be at most 320 characters.")
        String email) {
}
