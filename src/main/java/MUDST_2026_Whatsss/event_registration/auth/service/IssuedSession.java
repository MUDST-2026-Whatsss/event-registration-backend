package MUDST_2026_Whatsss.event_registration.auth.service;

import MUDST_2026_Whatsss.event_registration.auth.domain.AuthSession;

/**
 * A newly created session together with its raw refresh token.
 *
 * <p>The raw value exists only here, on its way into a {@code Set-Cookie} header; the persisted
 * row holds nothing but its digest.
 */
public record IssuedSession(AuthSession session, String rawRefreshToken) {
}
