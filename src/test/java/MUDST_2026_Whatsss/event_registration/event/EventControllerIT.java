package MUDST_2026_Whatsss.event_registration.event;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test: boots the full Spring context (against the in-memory H2
 * database configured for tests) and exercises the real create/read/update/
 * delete flow through MockMvc, exactly as a real client would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class EventControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createThenGetEvent_ReturnsPersistedEvent() throws Exception {
        Event newEvent = Event.builder()
                .title("Integration Test Event")
                .category("Technology")
                .maxParticipants(100)
                .build();

        String response = mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(newEvent)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("draft"))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/events/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Integration Test Event"));
    }

    @Test
    void createEvent_WithoutTitle_Returns400() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEvent_WhenNotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/events/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateThenDeleteEvent_WorksEndToEnd() throws Exception {
        Event newEvent = Event.builder().title("To Update").build();
        String createResponse = mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(newEvent)))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResponse).get("id").asLong();

        Event updated = Event.builder().title("Updated Title").status("published").build();
        mockMvc.perform(put("/api/events/" + id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.status").value("published"));

        mockMvc.perform(delete("/api/events/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events/" + id))
                .andExpect(status().isNotFound());
    }
}
