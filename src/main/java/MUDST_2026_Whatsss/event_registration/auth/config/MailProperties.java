package MUDST_2026_Whatsss.event_registration.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Sender identity and public frontend URL used in account emails. */
@Component
@ConfigurationProperties(prefix = "event.mail")
@Getter
@Setter
public class MailProperties {

    private String fromName = "Eventsss";

    private String fromEmail = "";

    private String frontendBaseUrl = "http://localhost:5173";
}
