package MUDST_2026_Whatsss.event_registration.auth.web.dto;

import MUDST_2026_Whatsss.event_registration.auth.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull UserStatus status,
        @NotNull Long version) {
}
