package dk.ek.delivery.sse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Starts the Server-Sent Events API (embedded web server on port 8086).
@SpringBootApplication
public class SseApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SseApiApplication.class, args);
    }
}
