package MUDST_2026_Whatsss.event_registration.auth.ratelimit;

import MUDST_2026_Whatsss.event_registration.common.error.ApiErrorResponse;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Applies the global per-IP request ceiling before anything expensive runs.
 *
 * <p>Placed ahead of authentication so that a flood costs no database work — the point of a volume
 * limit is to shed load cheaply. The tighter login-specific limits are applied in the auth service
 * instead, where the target account is known.
 *
 * <p>This is basic flood protection for a single instance, not DDoS mitigation: a real
 * volumetric attack has to be absorbed upstream at the CDN or load balancer, before it reaches
 * application threads at all.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiterService rateLimiter;
    private final ClientIpResolver ipResolver;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiterService rateLimiter,
                           ClientIpResolver ipResolver,
                           ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.ipResolver = ipResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Health and readiness probes must keep answering even while a client is being throttled.
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = ipResolver.resolve(request);
        RateLimitDecision decision = rateLimiter.checkGlobalByIp(clientIp);

        if (!decision.allowed()) {
            log.warn("Rate limit exceeded for {} on {}", clientIp, request.getRequestURI());
            writeTooManyRequests(response, decision);
            return;
        }

        response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remainingTokens()));
        filterChain.doFilter(request, response);
    }

    private void writeTooManyRequests(HttpServletResponse response, RateLimitDecision decision)
            throws IOException {
        response.setStatus(ErrorCode.RATE_LIMITED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));

        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.RATE_LIMITED, ErrorCode.RATE_LIMITED.defaultMessage());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
