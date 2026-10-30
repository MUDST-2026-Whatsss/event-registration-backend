package MUDST_2026_Whatsss.event_registration.realtime;

import java.util.UUID;

/** {@code remainingSeats} is null when the event hides its seat count, as in the event API. */
public record SeatUpdate(UUID eventId, Integer remainingSeats, boolean full) {
}
