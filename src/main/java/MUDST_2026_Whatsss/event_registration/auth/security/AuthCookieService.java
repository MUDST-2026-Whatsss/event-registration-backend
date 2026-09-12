package MUDST_2026_Whatsss.event_registration.auth.security;

import MUDST_2026_Whatsss.event_registration.auth.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Builds and clears the session cookies.
 *
 * <p>Both cookies are {@code HttpOnly}, so injected JavaScript cannot read them even if an XSS
 * hole exists elsewhere in the SPA. {@code SameSite} keeps them off cross-site form posts, and
 * {@code Secure} is driven by configuration so local HTTP still works while any HTTPS deployment
 * refuses to send them in clear.
 *
 * <p>The refresh cookie is scoped to the refresh and logout paths rather than the whole site, so
 * ordinary API calls never carry it and a leak of one request's headers cannot yield long-lived
 * credentials.
 */
@Service
public class AuthCookieService {

    public static final String ACCESS_TOKEN_COOKIE = "EVENT_ACCESS_TOKEN";
    public static final String REFRESH_TOKEN_COOKIE = "EVENT_REFRESH_TOKEN";

    private static final String ROOT_PATH = "/";
    private static final String REFRESH_PATH = "/api/v1/auth";

    private final AuthProperties properties;

    public AuthCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie accessTokenCookie(String token) {
        return baseCookie(ACCESS_TOKEN_COOKIE, token, ROOT_PATH)
                .maxAge(properties.getAccessTokenTtl())
                .build();
    }

    public ResponseCookie refreshTokenCookie(String token) {
        return baseCookie(REFRESH_TOKEN_COOKIE, token, REFRESH_PATH)
                .maxAge(properties.getRefreshTokenTtl())
                .build();
    }

    /** Expired twins of the session cookies, used to clear the browser state on logout. */
    public ResponseCookie clearAccessTokenCookie() {
        return baseCookie(ACCESS_TOKEN_COOKIE, "", ROOT_PATH).maxAge(Duration.ZERO).build();
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return baseCookie(REFRESH_TOKEN_COOKIE, "", REFRESH_PATH).maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value, String path) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(properties.getCookieSameSite())
                .path(path);
        if (properties.getCookieDomain() != null && !properties.getCookieDomain().isBlank()) {
            builder.domain(properties.getCookieDomain());
        }
        return builder;
    }

    public Optional<String> readAccessToken(HttpServletRequest request) {
        return readCookie(request, ACCESS_TOKEN_COOKIE);
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        return readCookie(request, REFRESH_TOKEN_COOKIE);
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /** Convenience for controllers that emit cookies alongside a JSON body. */
    public void addCookie(HttpHeaders headers, ResponseCookie cookie) {
        headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
