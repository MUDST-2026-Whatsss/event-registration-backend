package MUDST_2026_Whatsss.event_registration.auth;

import MUDST_2026_Whatsss.event_registration.PostgresIntegrationTest;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthSession;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthRole;
import MUDST_2026_Whatsss.event_registration.auth.domain.AuthUser;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthRoleRepository;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthSessionRepository;
import MUDST_2026_Whatsss.event_registration.auth.repository.AuthUserRepository;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthCookieService;
import MUDST_2026_Whatsss.event_registration.auth.security.JwtService;
import MUDST_2026_Whatsss.event_registration.auth.security.RoleCodes;
import MUDST_2026_Whatsss.event_registration.auth.service.AuthService;
import MUDST_2026_Whatsss.event_registration.auth.service.RequestContext;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.LoginRequest;
import MUDST_2026_Whatsss.event_registration.auth.web.dto.RegisterRequest;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthSecurityIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private AuthSessionRepository sessionRepository;

    @Autowired
    private AuthUserRepository userRepository;

    @Autowired
    private AuthRoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void loginWithoutCsrfIsRejectedBeforeCredentialsAreProcessed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"Wrong123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void successfulLoginSetsHttpOnlyShortLivedCookies() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email
                                + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
                    assertTrue(cookies.stream().anyMatch(cookie ->
                            cookie.contains("EVENT_ACCESS_TOKEN=")
                                    && cookie.contains("Max-Age=900")
                                    && cookie.contains("HttpOnly")
                                    && cookie.contains("SameSite=Lax")));
                    assertTrue(cookies.stream().anyMatch(cookie ->
                            cookie.contains("EVENT_REFRESH_TOKEN=")
                                    && cookie.contains("HttpOnly")
                                    && cookie.contains("SameSite=Lax")));
                });
    }

    @Test
    void multiRoleLoginRequiresSelectionAndIssuesOnlyTheSelectedAuthorities() throws Exception {
        String email = uniqueEmail();
        register(email);

        AuthUser user = userRepository.findByNormalizedEmail(email).orElseThrow();
        AuthRole admin = roleRepository.findByRoleCode(RoleCodes.ADMIN).orElseThrow();
        user.getRoles().add(admin);
        userRepository.saveAndFlush(user);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email
                                + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").doesNotExist())
                .andExpect(jsonPath("$.user.roles.length()").value(2))
                .andReturn();

        var pendingCookie = login.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        var pendingClaims = jwtService.parse(pendingCookie.getValue()).orElseThrow();
        assertNull(pendingClaims.get(JwtService.CLAIM_ROLE));
        assertTrue(pendingClaims.get(JwtService.CLAIM_ROLES, List.class).isEmpty());
        assertTrue(pendingClaims.get(JwtService.CLAIM_PERMISSIONS, List.class).isEmpty());

        MvcResult selection = mockMvc.perform(post("/api/v1/auth/select-role")
                        .cookie(pendingCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.roles.length()").value(2))
                .andReturn();

        var activeCookie = selection.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        var activeClaims = jwtService.parse(activeCookie.getValue()).orElseThrow();
        assertEquals("ADMIN", activeClaims.get(JwtService.CLAIM_ROLE, String.class));
        assertEquals(List.of("ADMIN"), activeClaims.get(JwtService.CLAIM_ROLES, List.class));
        assertTrue(!activeClaims.get(JwtService.CLAIM_PERMISSIONS, List.class).isEmpty());
    }

    @Test
    void concurrentRefreshRotatesOnlyOnceAndRevokesTheFamily() throws Exception {
        String email = uniqueEmail();
        register(email);
        AuthService.LoginResult login = authService.login(
                new LoginRequest(email, "Password123"), RequestContext.of("127.0.0.1", "test"));

        String refreshToken = login.session().rawRefreshToken();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> refresh = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                try {
                    return authService.refresh(
                            refreshToken, RequestContext.of("127.0.0.1", "concurrency-test"));
                } catch (ApiException ex) {
                    return ex.getErrorCode();
                }
            };

            Future<Object> first = executor.submit(refresh);
            Future<Object> second = executor.submit(refresh);
            List<Object> outcomes = List.of(
                    first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream()
                    .filter(AuthService.LoginResult.class::isInstance).count());
            assertEquals(1, outcomes.stream()
                    .filter(ErrorCode.SESSION_REVOKED::equals).count());

            UUID userId = login.user().userId();
            List<AuthSession> family = sessionRepository.findAllByUserId(userId);
            assertEquals(2, family.size());
            assertTrue(family.stream().allMatch(AuthSession::isRevoked));
            assertInstanceOf(AuthService.LoginResult.class,
                    outcomes.stream().filter(AuthService.LoginResult.class::isInstance)
                            .findFirst().orElseThrow());
        } finally {
            executor.shutdownNow();
        }
    }

    private void register(String email) {
        authService.register(new RegisterRequest(
                email, "Password123", "Auth", "Test", "0812345678"));
    }

    private static String uniqueEmail() {
        return "auth-" + UUID.randomUUID() + "@example.com";
    }
}
