package MUDST_2026_Whatsss.event_registration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Application entry point.
 *
 * <p>The {@code event} package is temporarily excluded from scanning. Its {@code Event} entity
 * still maps the pre-V6 table shape — a {@code Long id} where the schema now has {@code event_id
 * uuid} — so Hibernate's schema validation fails at startup and takes the whole application with
 * it, authentication included.
 *
 * <p>The code is left in place rather than deleted. Re-enable it by removing the exclusions below
 * once {@code Event}, {@code EventRepository}, {@code EventService} and the controller have been
 * updated to the V6 columns.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ComponentScan(
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "MUDST_2026_Whatsss\\.event_registration\\.event\\..*"))
@EntityScan(basePackages = "MUDST_2026_Whatsss.event_registration.auth.domain")
@EnableJpaRepositories(basePackages = "MUDST_2026_Whatsss.event_registration.auth.repository")
public class EventRegistrationApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventRegistrationApplication.class, args);
	}

}
