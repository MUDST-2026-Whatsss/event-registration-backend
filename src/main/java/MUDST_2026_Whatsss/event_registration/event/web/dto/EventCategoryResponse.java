package MUDST_2026_Whatsss.event_registration.event.web.dto;

import java.util.UUID;

public record EventCategoryResponse(
        UUID eventCategoryId,
        String code,
        String nameTh,
        String nameEn,
        String description) {
}
