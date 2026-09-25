package MUDST_2026_Whatsss.event_registration.event.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record ReplaceEventAdminsRequest(@NotNull Set<UUID> adminUserIds) {
}
