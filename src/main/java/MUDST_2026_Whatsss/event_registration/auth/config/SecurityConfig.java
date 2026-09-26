package MUDST_2026_Whatsss.event_registration.auth.config;

import MUDST_2026_Whatsss.event_registration.auth.ratelimit.RateLimitFilter;
import MUDST_2026_Whatsss.event_registration.auth.security.JwtAuthenticationFilter;
import MUDST_2026_Whatsss.event_registration.common.error.ApiErrorResponse;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * The single place that decides which endpoints are public and which require authentication.
 *
 * <p>Deny-by-default: {@code anyRequest().authenticated()} closes the chain, so a new controller is
 * protected the moment it is added and must be opted out of explicitly. Fine-grained rules live in
 * {@code @PreAuthorize} on the methods themselves, enabled by {@link EnableMethodSecurity}.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * BCrypt at cost 12, per AUTH_API.md — deliberately slow so that an offline attack on a stolen
     * hash table stays expensive. Raising it further increases login latency on every request.
     */
    private static final int BCRYPT_STRENGTH = 12;

    private final AuthProperties properties;

    public SecurityConfig(AuthProperties properties) {
        this.properties = properties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RateLimitFilter rateLimitFilter,
                                                   ObjectMapper objectMapper) throws Exception {

        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        // Hand the SPA the raw token value so it can echo it back in the X-XSRF-TOKEN header.
        csrfHandler.setCsrfRequestAttributeName(null);

        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> {
            cookie.path("/")
                    .secure(properties.isCookieSecure())
                    .sameSite(properties.getCookieSameSite());
            if (properties.getCookieDomain() != null
                    && !properties.getCookieDomain().isBlank()) {
                cookie.domain(properties.getCookieDomain());
            }
        });

        http
                .cors(Customizer.withDefaults())

                // CSRF stays ON. Session cookies are sent automatically by the browser, so without
                // it any site could trigger a state-changing call as a signed-in user. The token
                // lives in a readable cookie that only same-origin script can echo back.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfHandler)
                        // A read-only probe with no mutating handler; keep unsupported methods at
                        // 405 instead of making infrastructure synthesize a CSRF token.
                        .ignoringRequestMatchers("/api/health"))

                // No server-side session: the JWT cookie is the whole authentication state.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .xssProtection(xss -> xss
                                .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; frame-ancestors 'none'")))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(
                                "/api/v1/auth/csrf",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()

                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/event-categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/media/event-images/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/**").permitAll()

                        .anyRequest().authenticated())

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, objectMapper, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, ex) -> writeError(
                                response,
                                objectMapper,
                                ex instanceof CsrfException
                                        ? ErrorCode.CSRF_TOKEN_INVALID
                                        : ErrorCode.ACCESS_DENIED)))

                // Shed floods before authentication does any work.
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Errors raised inside the filter chain bypass the controller advice, so mirror its shape. */
    private static void writeError(HttpServletResponse response,
                                   ObjectMapper objectMapper,
                                   ErrorCode errorCode) throws java.io.IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of(errorCode, errorCode.defaultMessage()));
    }

    /**
     * Credentialed CORS requires exact origins — the spec forbids pairing {@code allowCredentials}
     * with a {@code *} wildcard, and allowing any origin to send session cookies would undo the
     * CSRF protection above.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Accept"));
        configuration.setExposedHeaders(List.of("Retry-After", "X-RateLimit-Remaining"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
