package MUDST_2026_Whatsss.event_registration.auth;

import MUDST_2026_Whatsss.event_registration.PostgresIntegrationTest;
import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminStatsAccessIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void onlySuperAdminsCanReadPlatformStats() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/stats")
                        .with(authentication(authenticated("USER", List.of("ROLE_USER", "REGISTRATION_CREATE_SELF")))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/stats")
                        .with(authentication(authenticated("ADMIN", List.of("ROLE_ADMIN", "EVENT_CREATE")))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/stats")
                        .with(authentication(authenticated("SUPER_ADMIN", List.of("ROLE_SUPER_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isNumber());
    }

    private static UsernamePasswordAuthenticationToken authenticated(String role, List<String> authorities) {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), role.toLowerCase() + "@example.test",
                role, List.of(role), authorities);
        return UsernamePasswordAuthenticationToken.authenticated(user, null,
                authorities.stream().map(SimpleGrantedAuthority::new).toList());
    }
}
