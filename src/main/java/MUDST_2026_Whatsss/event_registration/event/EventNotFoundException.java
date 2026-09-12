package MUDST_2026_Whatsss.event_registration.event;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(Long id) {
        super("Event not found with id " + id);
    }
}
