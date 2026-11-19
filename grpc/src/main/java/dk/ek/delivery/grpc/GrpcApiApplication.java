package dk.ek.delivery.grpc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Starts the gRPC API. Spring creates the database connection and our classes;
// GrpcServer then opens the gRPC port (9090).
@SpringBootApplication
public class GrpcApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrpcApiApplication.class, args);
    }
}
