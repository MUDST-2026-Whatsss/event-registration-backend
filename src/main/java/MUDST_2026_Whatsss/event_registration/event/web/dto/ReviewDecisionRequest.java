package MUDST_2026_Whatsss.event_registration.event.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewDecisionRequest(
        @NotNull Long version,
        @Size(max = 2000) String comment) {
}
