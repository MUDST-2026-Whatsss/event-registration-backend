package MUDST_2026_Whatsss.event_registration.registration.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The registration number read from the participant's QR code or typed in by staff. */
public record CheckInRequest(
        @NotBlank(message = "Registration number is required.")
        @Size(max = 32, message = "Registration number must be at most 32 characters.")
        String registrationNumber) {
}
