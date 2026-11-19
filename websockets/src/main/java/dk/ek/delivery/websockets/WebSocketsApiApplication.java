package dk.ek.delivery.websockets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Starts the WebSockets API (embedded web server on port 8085).
@SpringBootApplication
public class WebSocketsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebSocketsApiApplication.class, args);
    }
}
