package MUDST_2026_Whatsss.event_registration.event.web.dto;

public record AdminEventStatsResponse(
        long total,
        long draft,
        long pendingReview,
        long published,
        long rejected,
        long cancelled) {
}
