package MUDST_2026_Whatsss.event_registration.auth.web.dto;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record AdminRoleResponse(
        UUID roleId,
        String code,
        String name,
        String description,
        String status,
        String scopeType,
        boolean system,
        List<String> permissions,
        long userCount,
        Instant createdAt) {
}
