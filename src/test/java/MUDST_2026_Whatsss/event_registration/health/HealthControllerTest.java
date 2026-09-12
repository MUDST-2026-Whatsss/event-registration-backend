package MUDST_2026_Whatsss.event_registration.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit test for {@link HealthController}: loads only the web layer (no full
 * application context), so it runs fast and checks the controller in isolation.
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void health_ShouldReturnOkBody() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(content().string("OK"));
    }

    @Test
    void health_ShouldReturnTextPlainContentType() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @Test
    void health_WithWrongHttpMethod_ShouldReturnMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/health"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unknownPath_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
