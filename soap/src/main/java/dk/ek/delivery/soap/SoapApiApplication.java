package dk.ek.delivery.soap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Starts the SOAP API (embedded web server on port 8082).
@SpringBootApplication
public class SoapApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SoapApiApplication.class, args);
    }
}
