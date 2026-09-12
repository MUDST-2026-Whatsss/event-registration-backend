package MUDST_2026_Whatsss.event_registration.auth.service;

/**
 * The caller details that auth operations record for auditing.
 *
 * @param ipAddress resolved client IP, may be null when unavailable
 * @param userAgent truncated to the column width by {@link #of}
 */
public record RequestContext(String ipAddress, String userAgent) {

    /** Matches the {@code user_agent} column width in {@code auth_sessions}. */
    private static final int MAX_USER_AGENT_LENGTH = 500;

    public static RequestContext of(String ipAddress, String userAgent) {
        String trimmed = userAgent == null || userAgent.isBlank()
                ? null
                : userAgent.substring(0, Math.min(userAgent.length(), MAX_USER_AGENT_LENGTH));
        return new RequestContext(ipAddress, trimmed);
    }
}
