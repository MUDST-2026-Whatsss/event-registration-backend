package MUDST_2026_Whatsss.event_registration.auth.web.dto;

public record AdminUserStatsResponse(
        long activeAdministrators,
        long totalUsers,
        long disabledAccounts) {
}
