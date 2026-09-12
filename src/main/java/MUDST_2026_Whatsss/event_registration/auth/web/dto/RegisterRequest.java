package MUDST_2026_Whatsss.event_registration.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/register}.
 *
 * <p>Every field is length-bounded so an oversized value is rejected before it reaches the
 * database or a log line. The password rule matches the strictest one the SPA enforces (lower,
 * upper and a digit) so the two never disagree about what is acceptable.
 */
public record RegisterRequest(

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 320, message = "Email must be at most 320 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must include a lowercase letter, an uppercase letter and a number.")
        String password,

        @NotBlank(message = "First name is required.")
        @Size(max = 100, message = "First name must be at most 100 characters.")
        String firstName,

        @NotBlank(message = "Last name is required.")
        @Size(max = 100, message = "Last name must be at most 100 characters.")
        String lastName,

        /** Thai mobile format, optionally written with spaces or dashes. */
        @Pattern(
                regexp = "^$|^0[\\d\\s-]{8,14}$",
                message = "Enter a valid phone number, for example 081-234-5678.")
        @Size(max = 32, message = "Phone number must be at most 32 characters.")
        String phoneNumber) {
}
