package MUDST_2026_Whatsss.event_registration.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/change-password}. */
public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required.")
        @Size(max = 128, message = "Current password must be at most 128 characters.")
        String currentPassword,

        @NotBlank(message = "New password is required.")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must include a lowercase letter, an uppercase letter and a number.")
        String newPassword) {
}
