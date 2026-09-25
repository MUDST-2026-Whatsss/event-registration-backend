package MUDST_2026_Whatsss.event_registration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/** Application entry point. All domain entities and repositories below this package are scanned. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class EventRegistrationApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventRegistrationApplication.class, args);
	}

}
