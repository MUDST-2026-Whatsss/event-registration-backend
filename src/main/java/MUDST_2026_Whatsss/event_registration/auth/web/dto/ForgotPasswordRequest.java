package MUDST_2026_Whatsss.event_registration.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/forgot-password}. */
public record ForgotPasswordRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 320, message = "Email must be at most 320 characters.")
        String email) {
}
