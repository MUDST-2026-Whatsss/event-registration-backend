package MUDST_2026_Whatsss.event_registration.event.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EventChangeRequestCreateRequest(
        @NotBlank @Size(max = 2000) String reason,
        @NotNull @Valid EventWriteRequest event) {
}
