package MUDST_2026_Whatsss.event_registration.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/login}.
 *
 * <p>Intentionally not annotated with {@code @Email} or any password-complexity rule: a login must
 * fail with the same generic message whatever is submitted, and a field-level validation error
 * would tell an attacker their guess was merely malformed rather than wrong. Only length is
 * bounded, to cap parsing and BCrypt work.
 */
public record LoginRequest(

        @NotBlank(message = "Email is required.")
        @Size(max = 320, message = "Email must be at most 320 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(max = 128, message = "Password must be at most 128 characters.")
        String password) {
}
