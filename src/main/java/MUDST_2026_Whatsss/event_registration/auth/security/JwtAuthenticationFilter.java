package MUDST_2026_Whatsss.event_registration.auth.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the access-token cookie on every request and populates the security context.
 *
 * <p>This is the authentication half of the guard: it only establishes <em>who</em> the caller is,
 * and never decides what they may do. Requests with no cookie, or with a bad one, simply continue
 * unauthenticated so that the authorization rules in {@link
 * MUDST_2026_Whatsss.event_registration.auth.config.SecurityConfig} produce the 401 — that keeps
 * the "is this endpoint public?" decision in exactly one place.
 *
 * <p>Roles are exposed both as {@code ROLE_*} authorities (for {@code hasRole}) and permissions as
 * bare codes (for {@code hasAuthority}), so either style works in {@code @PreAuthorize}.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthCookieService cookieService;

    public JwtAuthenticationFilter(JwtService jwtService, AuthCookieService cookieService) {
        this.jwtService = jwtService;
        this.cookieService = cookieService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // An already-authenticated context means another mechanism ran first; do not override it.
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            cookieService.readAccessToken(request)
                    .flatMap(jwtService::parse)
                    .flatMap(JwtAuthenticationFilter::toPrincipal)
                    .ifPresent(principal -> authenticate(principal, request));
        }

        filterChain.doFilter(request, response);
    }

    private static Optional<AuthenticatedUser> toPrincipal(Claims claims) {
        try {
            UUID userId = UUID.fromString(claims.getSubject());
            return Optional.of(new AuthenticatedUser(
                    userId,
                    claims.get(JwtService.CLAIM_EMAIL, String.class),
                    claims.get(JwtService.CLAIM_ROLE, String.class),
                    stringList(claims.get(JwtService.CLAIM_ROLES)),
                    stringList(claims.get(JwtService.CLAIM_PERMISSIONS))));
        } catch (IllegalArgumentException | NullPointerException ex) {
            // A signed token whose claims are malformed is treated as no token at all.
            return Optional.empty();
        }
    }

    private static List<String> stringList(Object claimValue) {
        if (claimValue instanceof Collection<?> collection) {
            List<String> values = new ArrayList<>(collection.size());
            for (Object element : collection) {
                if (element != null) {
                    values.add(element.toString());
                }
            }
            return List.copyOf(values);
        }
        return List.of();
    }

    private void authenticate(AuthenticatedUser principal, HttpServletRequest request) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        principal.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        principal.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
