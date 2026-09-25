package MUDST_2026_Whatsss.event_registration.auth.web.dto;

import MUDST_2026_Whatsss.event_registration.auth.domain.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserResponse(
        UUID userId,
        String email,
        String displayName,
        UserStatus status,
        List<RoleSummary> roles,
        List<String> permissions,
        Instant lastLoginAt,
        Instant createdAt,
        long version) {

    public record RoleSummary(String code, String name) {
    }
}
