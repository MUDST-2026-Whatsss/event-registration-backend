package MUDST_2026_Whatsss.event_registration.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record ReplaceRolePermissionsRequest(@NotEmpty Set<@NotBlank String> permissionCodes) {
}
