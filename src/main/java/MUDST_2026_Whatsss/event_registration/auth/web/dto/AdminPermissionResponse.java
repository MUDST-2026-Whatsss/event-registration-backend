package MUDST_2026_Whatsss.event_registration.auth.web.dto;

import java.util.UUID;

public record AdminPermissionResponse(UUID permissionId, String code, String description) {
}
