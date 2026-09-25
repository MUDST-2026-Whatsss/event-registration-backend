package MUDST_2026_Whatsss.event_registration.event.web;

import MUDST_2026_Whatsss.event_registration.event.service.EventCategoryService;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventCategoryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/event-categories")
public class EventCategoryController {

    private final EventCategoryService service;

    public EventCategoryController(EventCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<EventCategoryResponse> list() {
        return service.listActive();
    }
}
