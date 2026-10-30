package MUDST_2026_Whatsss.event_registration.registration.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Registration funnel for one event.
 *
 * <p>Drop-offs are attendees who withdrew ({@code CANCELLED_BY_USER}) or let a payment lapse
 * ({@code PAYMENT_EXPIRED}); administrator cancellations are reported apart and a cancelled event
 * is not counted. {@code showRate} is {@code null} until the event has ended, and both rates are
 * {@code null} when there is nothing to divide by.
 */
public record EventAnalyticsResponse(
        long signups,
        long memberSignups,
        long guestSignups,
        long dropOffs,
        Double dropOffRate,
        long cancelledByAdmin,
        long confirmed,
        long checkedIn,
        long noShows,
        Double showRate,
        boolean eventEnded,
        List<DailySignups> dailySignups) {

    /** {@code date} is the calendar day in the event's own timezone. */
    public record DailySignups(LocalDate date, long count) {
    }
}
