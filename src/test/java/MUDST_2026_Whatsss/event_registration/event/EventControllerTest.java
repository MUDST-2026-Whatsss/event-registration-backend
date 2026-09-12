package MUDST_2026_Whatsss.event_registration.event;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit test for {@link EventController}: loads only the web layer and mocks
 * out {@link EventService}, so it never touches a real database.
 */
@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventService eventService;

    @Test
    void getAllEvents_ReturnsJsonList() throws Exception {
        when(eventService.getAllEvents()).thenReturn(List.of(Event.builder().id(1L).title("Tech Talk").build()));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Tech Talk"));
    }

    @Test
    void getEvent_WhenFound_ReturnsEvent() throws Exception {
        when(eventService.getEvent(1L)).thenReturn(Event.builder().id(1L).title("Tech Talk").build());

        mockMvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Tech Talk"));
    }

    @Test
    void getEvent_WhenNotFound_Returns404() throws Exception {
        when(eventService.getEvent(99L)).thenThrow(new EventNotFoundException(99L));

        mockMvc.perform(get("/api/events/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createEvent_WithValidBody_Returns201() throws Exception {
        Event toCreate = Event.builder().title("New Event").build();
        Event saved = Event.builder().id(1L).title("New Event").status("draft").build();
        when(eventService.createEvent(any(Event.class))).thenReturn(saved);

        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(toCreate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("draft"));
    }

    @Test
    void createEvent_WithoutTitle_Returns400() throws Exception {
        when(eventService.createEvent(any(Event.class)))
                .thenThrow(new IllegalArgumentException("Title is required"));

        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteEvent_ReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/events/1"))
                .andExpect(status().isNoContent());
    }
}
