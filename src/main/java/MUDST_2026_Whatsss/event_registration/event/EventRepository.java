package MUDST_2026_Whatsss.event_registration.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Not registered at runtime right now: {@link Event} is temporarily not a managed entity (see the
 * note on that class), and {@code EventRegistrationApplication} excludes this package from
 * repository scanning so startup does not fail with "Not a managed type".
 *
 * <p>The declaration is left intact so the code still compiles and the intent stays visible. When
 * {@code Event} is remapped to the V6 columns, its id type becomes {@code UUID} rather than
 * {@code Long}, and this signature must change with it.
 */
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByStatus(String status);
}
