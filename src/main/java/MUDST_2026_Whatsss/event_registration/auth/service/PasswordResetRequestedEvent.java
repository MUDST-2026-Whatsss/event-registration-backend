package MUDST_2026_Whatsss.event_registration.auth.service;

import java.util.UUID;

/** Published only after a reset token has been persisted. */
public record PasswordResetRequestedEvent(UUID userId, String email, String rawToken) {
}
