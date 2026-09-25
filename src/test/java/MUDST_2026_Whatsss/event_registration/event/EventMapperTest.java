package MUDST_2026_Whatsss.event_registration.event;

import MUDST_2026_Whatsss.event_registration.event.domain.Event;
import MUDST_2026_Whatsss.event_registration.event.domain.EventCategory;
import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.domain.LocationType;
import MUDST_2026_Whatsss.event_registration.event.service.EventMapper;
import MUDST_2026_Whatsss.event_registration.storage.config.StorageProperties;
import MUDST_2026_Whatsss.event_registration.storage.service.MediaUrlResolver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventMapperTest {

    private final EventMapper mapper = new EventMapper(new MediaUrlResolver(new StorageProperties()));

    @Test
    void mapsParticipantSafeFieldsAndTrimsFixedWidthCurrency() {
        UUID eventId = UUID.randomUUID();
        EventCategory category = EventCategory.builder()
                .eventCategoryId(UUID.randomUUID())
                .code("TECHNOLOGY")
                .nameTh("เทคโนโลยี")
                .nameEn("Technology")
                .build();
        Event event = Event.builder()
                .eventId(eventId)
                .slug("secure-event")
                .title("Secure Event")
                .category(category)
                .eventType(EventType.PAID)
                .price(new BigDecimal("250.00"))
                .currency("THB")
                .locationType(LocationType.ONLINE)
                .onlineUrl("https://private.example.test/room")
                .imageObjectKey("event-images/11111111-1111-1111-1111-111111111111/"
                        + "22222222-2222-2222-2222-222222222222.webp")
                .timezone("Asia/Bangkok")
                .build();

        var response = mapper.toDetailResponse(event);

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.category().code()).isEqualTo("TECHNOLOGY");
        assertThat(response.currency()).isEqualTo("THB");
        assertThat(response.imageUrl()).isEqualTo("/api/v1/media/event-images/"
                + "11111111-1111-1111-1111-111111111111/"
                + "22222222-2222-2222-2222-222222222222.webp");
        // Online access details are deliberately absent from the public response contract.
        assertThat(response.toString()).doesNotContain("private.example.test");
    }
}
