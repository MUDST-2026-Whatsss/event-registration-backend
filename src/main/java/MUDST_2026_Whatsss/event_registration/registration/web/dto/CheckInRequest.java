package MUDST_2026_Whatsss.event_registration.registration.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * The registration number read from the participant's QR code or typed in by staff.
 *
 * <p>{@code checkedInAt} is when the code was scanned. Offline check-ins send it when they are
 * synced later, so the record shows the arrival time rather than the sync time; when absent, the
 * server time is used.
 */
public record CheckInRequest(
        @NotBlank(message = "Registration number is required.")
        @Size(max = 32, message = "Registration number must be at most 32 characters.")
        String registrationNumber,
        Instant checkedInAt) {

    public CheckInRequest(String registrationNumber) {
        this(registrationNumber, null);
    }
}
