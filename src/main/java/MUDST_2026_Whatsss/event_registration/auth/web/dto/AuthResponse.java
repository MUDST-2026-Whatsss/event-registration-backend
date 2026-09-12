package MUDST_2026_Whatsss.event_registration.auth.web.dto;

/**
 * Body returned by login and refresh.
 *
 * <p>Carries no token: both the access JWT and the refresh token travel in HttpOnly cookies, so
 * putting either here would hand script exactly what those cookies exist to withhold.
 */
public record AuthResponse(UserResponse user) {
}
