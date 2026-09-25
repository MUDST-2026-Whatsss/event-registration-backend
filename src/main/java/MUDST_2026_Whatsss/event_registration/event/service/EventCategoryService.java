package MUDST_2026_Whatsss.event_registration.event.service;

import MUDST_2026_Whatsss.event_registration.event.repository.EventCategoryRepository;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventCategoryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EventCategoryService {

    private final EventCategoryRepository repository;
    private final EventMapper mapper;

    public EventCategoryService(EventCategoryRepository repository, EventMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<EventCategoryResponse> listActive() {
        return repository.findByActiveTrueOrderByDisplayOrderAscNameThAsc().stream()
                .map(mapper::toCategoryResponse)
                .toList();
    }
}
