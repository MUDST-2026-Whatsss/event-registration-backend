package MUDST_2026_Whatsss.event_registration.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateRoleRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description,
        @NotBlank String scopeType,
        @NotBlank String status,
        @NotEmpty Set<@NotBlank String> permissionCodes) {
}
