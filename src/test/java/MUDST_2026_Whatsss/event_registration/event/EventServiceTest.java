package MUDST_2026_Whatsss.event_registration.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository);
    }

    @Test
    void createEvent_WithValidTitle_SavesAndDefaultsStatusToDraft() {
        Event event = Event.builder().title("Tech Talk").build();
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event saved = eventService.createEvent(event);

        assertThat(saved.getStatus()).isEqualTo("draft");
        assertThat(saved.getCreatedAt()).isNotNull();
        verify(eventRepository).save(event);
    }

    @Test
    void createEvent_WithoutTitle_ThrowsIllegalArgumentException() {
        Event event = Event.builder().title("   ").build();

        assertThatThrownBy(() -> eventService.createEvent(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Title");
    }

    @Test
    void createEvent_WithNegativeMaxParticipants_ThrowsIllegalArgumentException() {
        Event event = Event.builder().title("Bad Event").maxParticipants(-5).build();

        assertThatThrownBy(() -> eventService.createEvent(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxParticipants");
    }

    @Test
    void getEvent_WhenFound_ReturnsEvent() {
        Event event = Event.builder().id(1L).title("Found").build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        Event result = eventService.getEvent(1L);

        assertThat(result.getTitle()).isEqualTo("Found");
    }

    @Test
    void getEvent_WhenNotFound_ThrowsEventNotFoundException() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEvent(99L))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void deleteEvent_WhenNotFound_ThrowsEventNotFoundException() {
        when(eventRepository.existsById(42L)).thenReturn(false);

        assertThatThrownBy(() -> eventService.deleteEvent(42L))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void deleteEvent_WhenFound_DeletesById() {
        when(eventRepository.existsById(1L)).thenReturn(true);

        eventService.deleteEvent(1L);

        verify(eventRepository).deleteById(1L);
    }

    @Test
    void getEventsByStatus_DelegatesToRepository() {
        when(eventRepository.findByStatus("published"))
                .thenReturn(List.of(Event.builder().title("A").build()));

        List<Event> results = eventService.getEventsByStatus("published");

        assertThat(results).hasSize(1);
    }
}
