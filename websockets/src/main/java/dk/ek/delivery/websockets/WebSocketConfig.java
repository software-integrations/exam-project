package dk.ek.delivery.websockets;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// Connects the URL ws://localhost:8085/ws/tracking to our TrackingHandler.
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TrackingHandler trackingHandler;

    public WebSocketConfig(TrackingHandler trackingHandler) {
        this.trackingHandler = trackingHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(trackingHandler, "/ws/tracking")
                // CSRF protection ("cross-site WebSocket hijacking"): a browser always sends
                // an Origin header. Only pages from our own site may open a connection;
                // a handshake from any other website is answered with 403 Forbidden.
                // Tools like Postman send no Origin header and are still accepted.
                .setAllowedOrigins("http://localhost:8085");
    }
}
