package MUDST_2026_Whatsss.event_registration.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record ReplaceUserRolesRequest(
        @NotEmpty Set<@NotBlank String> roleCodes,
        @NotNull Long version) {
}
