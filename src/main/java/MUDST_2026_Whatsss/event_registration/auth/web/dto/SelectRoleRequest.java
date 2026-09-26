package MUDST_2026_Whatsss.event_registration.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Selects the single role whose permissions should be active in the access token. */
public record SelectRoleRequest(
        @NotBlank(message = "Role is required.")
        String role) {
}
