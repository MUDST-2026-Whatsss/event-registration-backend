package MUDST_2026_Whatsss.event_registration.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * Supplies the {@link ObjectMapper} used for request and response bodies.
 *
 * <p>Spring Boot 4 no longer contributes one automatically, and filters that write errors outside
 * the MVC stack need to inject it directly, so it is declared explicitly here.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                // Serialize Instant as an ISO-8601 string rather than a numeric timestamp, which
                // is what the API documents and what JavaScript's Date parser accepts.
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                // Reject unrecognised fields so a client cannot smuggle extra properties past a
                // DTO's validation annotations.
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** Registers the same mapper with MVC so controllers and filters agree on the JSON shape. */
    @Bean
    public MappingJackson2HttpMessageConverter jacksonHttpMessageConverter(ObjectMapper objectMapper) {
        return new MappingJackson2HttpMessageConverter(objectMapper);
    }
}
