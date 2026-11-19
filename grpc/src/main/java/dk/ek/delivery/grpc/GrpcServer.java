package dk.ek.delivery.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Starts and stops the gRPC server together with the Spring application.
@Component
public class GrpcServer {

    private final TrackingGrpcService trackingGrpcService;
    private final int port;
    private Server server;

    public GrpcServer(TrackingGrpcService trackingGrpcService, @Value("${grpc.port}") int port) {
        this.trackingGrpcService = trackingGrpcService;
        this.port = port;
    }

    // Runs once when the application starts.
    @PostConstruct
    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                // Our service with the two RPCs.
                .addService(trackingGrpcService)
                // Reflection: lets tools like Postman ask the server which services it has.
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();
        System.out.println("gRPC server listening on port " + port);
    }

    // Runs once when the application stops.
    @PreDestroy
    public void stop() {
        server.shutdownNow();
    }
}
