package MUDST_2026_Whatsss.event_registration.event;

import MUDST_2026_Whatsss.event_registration.event.domain.EventType;
import MUDST_2026_Whatsss.event_registration.event.domain.LocationType;
import MUDST_2026_Whatsss.event_registration.event.web.dto.EventWriteRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventWriteRequestTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void acceptsAValidFreeOnsiteEvent() {
        assertThat(VALIDATOR.validate(validRequest(EventType.FREE, BigDecimal.ZERO))).isEmpty();
    }

    @Test
    void rejectsPriceThatDoesNotMatchEventType() {
        var violations = VALIDATOR.validate(validRequest(EventType.FREE, new BigDecimal("100.00")));

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("priceValid");
    }

    private static EventWriteRequest validRequest(EventType type, BigDecimal price) {
        Instant registrationStart = Instant.parse("2026-09-01T00:00:00Z");
        Instant registrationEnd = Instant.parse("2026-09-30T00:00:00Z");
        Instant eventStart = Instant.parse("2026-10-01T02:00:00Z");
        Instant eventEnd = Instant.parse("2026-10-01T09:00:00Z");
        return new EventWriteRequest(
                "Technology Conference",
                "A participant-safe summary",
                "Description",
                null,
                type,
                price,
                "THB",
                null,
                LocationType.ONSITE,
                "Main Hall",
                "Bangkok",
                null,
                null,
                "Asia/Bangkok",
                eventStart,
                eventEnd,
                registrationStart,
                registrationEnd,
                registrationEnd,
                100,
                null,
                "events@example.test",
                null,
                true,
                true);
    }
}
