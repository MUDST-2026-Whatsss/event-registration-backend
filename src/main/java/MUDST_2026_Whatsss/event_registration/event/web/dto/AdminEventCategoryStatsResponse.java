package MUDST_2026_Whatsss.event_registration.event.web.dto;

import java.util.UUID;

public record AdminEventCategoryStatsResponse(
        UUID eventCategoryId,
        String code,
        String nameTh,
        String nameEn,
        long total,
        long draft,
        long pendingReview,
        long published,
        long rejected,
        long cancelled,
        long completed) {
}
