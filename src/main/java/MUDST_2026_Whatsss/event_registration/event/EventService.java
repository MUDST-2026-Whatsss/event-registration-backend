package MUDST_2026_Whatsss.event_registration.event;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    public List<Event> getEventsByStatus(String status) {
        return eventRepository.findByStatus(status);
    }

    public Event getEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    public Event createEvent(Event event) {
        validate(event);
        event.setId(null);
        if (event.getStatus() == null || event.getStatus().isBlank()) {
            event.setStatus("draft");
        }
        if (event.getAllowCancel() == null) {
            event.setAllowCancel(true);
        }
        if (event.getShowSeats() == null) {
            event.setShowSeats(true);
        }
        event.setCreatedAt(LocalDateTime.now());
        return eventRepository.save(event);
    }

    public Event updateEvent(Long id, Event updated) {
        validate(updated);
        Event existing = getEvent(id);

        existing.setTitle(updated.getTitle());
        existing.setDescription(updated.getDescription());
        existing.setCategory(updated.getCategory());
        existing.setLocation(updated.getLocation());
        existing.setEventDate(updated.getEventDate());
        existing.setStartTime(updated.getStartTime());
        existing.setEndTime(updated.getEndTime());
        existing.setMaxParticipants(updated.getMaxParticipants());
        existing.setRegistrationDeadline(updated.getRegistrationDeadline());
        existing.setImage(updated.getImage());
        existing.setRules(updated.getRules());
        existing.setContactEmail(updated.getContactEmail());
        existing.setEligibility(updated.getEligibility());
        existing.setAllowCancel(updated.getAllowCancel());
        existing.setShowSeats(updated.getShowSeats());
        if (updated.getStatus() != null && !updated.getStatus().isBlank()) {
            existing.setStatus(updated.getStatus());
        }

        return eventRepository.save(existing);
    }

    public void deleteEvent(Long id) {
        if (!eventRepository.existsById(id)) {
            throw new EventNotFoundException(id);
        }
        eventRepository.deleteById(id);
    }

    private void validate(Event event) {
        if (event.getTitle() == null || event.getTitle().isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (event.getMaxParticipants() != null && event.getMaxParticipants() < 0) {
            throw new IllegalArgumentException("maxParticipants cannot be negative");
        }
    }
}
