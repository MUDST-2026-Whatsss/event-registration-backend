package MUDST_2026_Whatsss.event_registration.registration.service;

import MUDST_2026_Whatsss.event_registration.registration.repository.RegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns confirmed registrations into {@code NO_SHOW} once their event has ended, so the show rate
 * counts people who never checked in.
 *
 * <p>Every API replica runs this; the update only takes rows still {@code CONFIRMED}, so
 * overlapping runs mark each registration once.
 */
@Component
public class NoShowSweeper {

    private static final Logger log = LoggerFactory.getLogger(NoShowSweeper.class);

    private final RegistrationRepository registrationRepository;

    public NoShowSweeper(RegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    @Scheduled(
            initialDelayString = "${event.registration.no-show-sweep.initial-delay:PT1M}",
            fixedDelayString = "${event.registration.no-show-sweep.interval:PT5M}")
    @Transactional
    public int markNoShows() {
        int marked = registrationRepository.markNoShowsForEndedEvents();
        if (marked > 0) {
            log.info("Marked {} registration(s) as NO_SHOW after their event ended", marked);
        }
        return marked;
    }
}
