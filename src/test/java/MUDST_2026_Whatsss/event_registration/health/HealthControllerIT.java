package MUDST_2026_Whatsss.event_registration.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test: boots the full Spring application context (same as
 * running the real app) and exercises the endpoint end-to-end through MockMvc.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class HealthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getHealth_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void getHealth_ShouldReturnTextPlainContentType() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @Test
    void postHealth_ShouldReturnMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/health"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unknownPath_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
