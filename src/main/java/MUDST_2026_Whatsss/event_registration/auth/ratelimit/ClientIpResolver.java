package MUDST_2026_Whatsss.event_registration.auth.ratelimit;

import MUDST_2026_Whatsss.event_registration.auth.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Determines the client IP used as a rate-limit key and written to audit rows.
 *
 * <p>{@code X-Forwarded-For} is honoured only when explicitly enabled, because any client can send
 * that header: trusting it by default would let an attacker rotate a fake IP per request and walk
 * straight past the per-IP limit. Enable it only when a reverse proxy overwrites the header.
 *
 * <p>When trusted, the <em>first</em> entry is taken — the original client, since each hop appends
 * to the right.
 */
@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final int MAX_IP_LENGTH = 45; // an IPv6 address with an IPv4 suffix

    private final AuthProperties.RateLimit config;

    public ClientIpResolver(AuthProperties properties) {
        this.config = properties.getRateLimit();
    }

    public String resolve(HttpServletRequest request) {
        if (config.isTrustForwardedFor()) {
            String header = request.getHeader(FORWARDED_FOR);
            if (header != null && !header.isBlank()) {
                String candidate = header.split(",")[0].trim();
                if (isPlausible(candidate)) {
                    return candidate;
                }
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Cheap sanity check so a junk header cannot become a cache key or reach the {@code inet}
     * column. Full parsing is unnecessary: a malformed value is simply ignored in favour of the
     * socket address.
     */
    private static boolean isPlausible(String value) {
        if (value.isEmpty() || value.length() > MAX_IP_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = Character.isDigit(c)
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F')
                    || c == '.' || c == ':';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
