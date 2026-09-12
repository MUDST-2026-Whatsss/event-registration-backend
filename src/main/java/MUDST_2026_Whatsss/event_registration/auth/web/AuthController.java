package MUDST_2026_Whatsss.event_registration.auth.web;

import MUDST_2026_Whatsss.event_registration.auth.ratelimit.ClientIpResolver;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthCookieService;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.auth.security.JwtService;
import MUDST_2026_Whatsss.event_registration.auth.service.AuthService;
import MUDST_2026_Whatsss.event_registration.auth.service.RequestContext;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.AuthResponse;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.ChangePasswordRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.LoginRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.MessageResponse;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.RegisterRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.UpdateProfileRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.UserResponse;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The authentication endpoints described in AUTH_API.md.
 *
 * <p>Tokens never appear in a response body: login and refresh return only the user payload and
 * attach the session as {@code Set-Cookie} headers.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService cookieService;
    private final JwtService jwtService;
    private final ClientIpResolver ipResolver;

    public AuthController(AuthService authService,
                          AuthCookieService cookieService,
                          JwtService jwtService,
                          ClientIpResolver ipResolver) {
        this.authService = authService;
        this.cookieService = cookieService;
        this.jwtService = jwtService;
        this.ipResolver = ipResolver;
    }

    /**
     * Primes the {@code XSRF-TOKEN} cookie for the SPA.
     *
     * <p>Reading the token from the request attribute is what causes Spring Security to issue the
     * cookie, so this endpoint exists purely to trigger that before the first mutating call.
     */
    @GetMapping("/csrf")
    public Map<String, String> csrf(@RequestAttribute(name = "_csrf", required = false) CsrfToken token) {
        if (token == null) {
            return Map.of();
        }
        return Map.of(
                "headerName", token.getHeaderName(),
                "token", token.getToken());
    }

    @PostMapping("/register")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        AuthService.LoginResult result = authService.login(request, contextOf(httpRequest));
        return sessionResponse(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest httpRequest) {
        String refreshToken = cookieService.readRefreshToken(httpRequest)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

        AuthService.LoginResult result = authService.refresh(refreshToken, contextOf(httpRequest));
        return sessionResponse(result);
    }

    /**
     * Revokes the refresh token and clears both cookies.
     *
     * <p>Always answers 200, even without a valid session: logout should be idempotent, and a
     * client that has lost its cookies still needs the browser state cleared.
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(HttpServletRequest httpRequest) {
        cookieService.readRefreshToken(httpRequest).ifPresent(authService::logout);

        HttpHeaders headers = new HttpHeaders();
        cookieService.addCookie(headers, cookieService.clearAccessTokenCookie());
        cookieService.addCookie(headers, cookieService.clearRefreshTokenCookie());

        return ResponseEntity.ok()
                .headers(headers)
                .body(new MessageResponse("Signed out."));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return authService.getCurrentUser(requirePrincipal(principal).userId());
    }

    @PatchMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(requirePrincipal(principal).userId(), request);
    }

    /**
     * Changes the password, revoking every session including this one, then clears the cookies so
     * the browser does not keep presenting credentials that no longer resolve.
     */
    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest request) {

        authService.changePassword(requirePrincipal(principal).userId(), request);

        HttpHeaders headers = new HttpHeaders();
        cookieService.addCookie(headers, cookieService.clearAccessTokenCookie());
        cookieService.addCookie(headers, cookieService.clearRefreshTokenCookie());

        return ResponseEntity.ok()
                .headers(headers)
                .body(new MessageResponse("Password updated. Sign in again."));
    }

    private ResponseEntity<AuthResponse> sessionResponse(AuthService.LoginResult result) {
        UserResponse user = result.user();
        String accessToken = jwtService.issueAccessToken(
                user.userId(), user.email(), user.role(), user.roles(), user.permissions());

        ResponseCookie access = cookieService.accessTokenCookie(accessToken);
        ResponseCookie refresh = cookieService.refreshTokenCookie(result.session().rawRefreshToken());

        HttpHeaders headers = new HttpHeaders();
        cookieService.addCookie(headers, access);
        cookieService.addCookie(headers, refresh);

        return ResponseEntity.ok().headers(headers).body(new AuthResponse(user));
    }

    private RequestContext contextOf(HttpServletRequest request) {
        return RequestContext.of(ipResolver.resolve(request), request.getHeader(HttpHeaders.USER_AGENT));
    }

    /** The filter chain should have rejected these already; this is defence in depth. */
    private static AuthenticatedUser requirePrincipal(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return principal;
    }
}
