package MUDST_2026_Whatsss.event_registration.auth.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/v1/auth/me}.
 *
 * <p>A null field means "leave unchanged", which is what makes this a genuine PATCH. Note that
 * email and role are absent by design: changing either is a privilege operation, not a profile
 * edit, so accepting them here would let anyone take over an address or escalate themselves.
 */
public record UpdateProfileRequest(

        @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters.")
        String firstName,

        @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters.")
        String lastName,

        @Pattern(
                regexp = "^$|^0[\\d\\s-]{8,14}$",
                message = "Enter a valid phone number, for example 081-234-5678.")
        @Size(max = 32, message = "Phone number must be at most 32 characters.")
        String phoneNumber) {
}
