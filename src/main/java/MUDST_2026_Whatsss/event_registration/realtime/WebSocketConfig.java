package MUDST_2026_Whatsss.event_registration.realtime;

import MUDST_2026_Whatsss.event_registration.auth.config.AuthProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Browsers may only open the socket from the same origins CORS allows for the REST API. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SeatUpdatesHandler seatUpdatesHandler;
    private final AuthProperties properties;

    public WebSocketConfig(SeatUpdatesHandler seatUpdatesHandler, AuthProperties properties) {
        this.seatUpdatesHandler = seatUpdatesHandler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(seatUpdatesHandler, "/api/v1/ws/seats")
                .setAllowedOrigins(properties.getCors().getAllowedOrigins().toArray(String[]::new));
    }
}
