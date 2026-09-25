package MUDST_2026_Whatsss.event_registration.event.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelEventRequest(
        @NotBlank(message = "Cancellation reason is required.")
        @Size(max = 500, message = "Cancellation reason must be at most 500 characters.")
        String reason,
        long version) {
}
