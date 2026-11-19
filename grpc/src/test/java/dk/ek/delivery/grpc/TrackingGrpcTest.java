package dk.ek.delivery.grpc;

import dk.ek.delivery.grpc.proto.DeliveryStatusRequest;
import dk.ek.delivery.grpc.proto.DeliveryStatusResponse;
import dk.ek.delivery.grpc.proto.LocationUpdate;
import dk.ek.delivery.grpc.proto.TrackingServiceGrpc;
import dk.ek.delivery.grpc.proto.TrackingUpdate;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The test suite of the gRPC API: positive and negative tests.
// It starts the real application (gRPC server on port 9191 with an in-memory database)
// and calls it with a real gRPC client, like Postman would.
@SpringBootTest(properties = {"grpc.port=9191", "spring.datasource.url=jdbc:h2:mem:grpc-test"})
class TrackingGrpcTest {

    private static ManagedChannel channel;

    // Open one connection to the server for all tests ("plaintext" = no TLS).
    @BeforeAll
    static void connect() {
        channel = ManagedChannelBuilder.forAddress("localhost", 9191).usePlaintext().build();
    }

    @AfterAll
    static void disconnect() {
        channel.shutdownNow();
    }

    // ---------- Unary RPC ----------

    // Positive: an existing delivery is returned.
    @Test
    void unaryReturnsDeliveryStatus() {
        // The blocking stub waits for the one response.
        var client = TrackingServiceGrpc.newBlockingStub(channel);

        DeliveryStatusResponse response = client.getDeliveryStatus(
                DeliveryStatusRequest.newBuilder().setDeliveryId(3).build());

        assertEquals(3, response.getDeliveryId());
        assertEquals("Clara Hansen", response.getRecipient());
        assertEquals("DELIVERED", response.getStatus());
        assertTrue(response.hasLastLat());
    }

    // Negative: an unknown id gives the status code NOT_FOUND.
    @Test
    void unaryUnknownDeliveryGivesNotFound() {
        var client = TrackingServiceGrpc.newBlockingStub(channel);

        StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
                () -> client.getDeliveryStatus(DeliveryStatusRequest.newBuilder().setDeliveryId(999).build()));

        assertEquals(Status.Code.NOT_FOUND, error.getStatus().getCode());
        assertEquals("Delivery 999 not found", error.getStatus().getDescription());
    }

    // ---------- Bidirectional streaming RPC ----------

    // Positive: every location we send is answered with a tracking update.
    @Test
    void streamAnswersEveryLocation() throws InterruptedException {
        // Delivery 2 has its destination at 56.1520, 10.2030.
        StreamResult result = stream(
                location(2, 56.1000, 10.1000),   // far away
                location(2, 56.1500, 10.2000));  // about 300 m away

        assertNull(result.error);
        assertEquals(2, result.updates.size());
        assertEquals("IN_TRANSIT", result.updates.get(0).getStatus());
        assertTrue(result.updates.get(0).getDistanceKm() > 1);
        assertEquals("ARRIVING", result.updates.get(1).getStatus());
        assertTrue(result.updates.get(1).getDistanceKm() < 1);
    }

    // Negative: impossible coordinates end the stream with INVALID_ARGUMENT.
    @Test
    void streamInvalidCoordinatesGivesInvalidArgument() throws InterruptedException {
        StreamResult result = stream(location(1, 123.0, 12.5));

        assertTrue(result.updates.isEmpty());
        assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(result.error).getCode());
    }

    // Negative: an unknown delivery ends the stream with NOT_FOUND.
    @Test
    void streamUnknownDeliveryGivesNotFound() throws InterruptedException {
        StreamResult result = stream(location(999, 55.0, 12.0));

        assertTrue(result.updates.isEmpty());
        assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(result.error).getCode());
    }

    // Negative: a delivery that is already delivered cannot be tracked.
    @Test
    void streamDeliveredDeliveryGivesInvalidArgument() throws InterruptedException {
        StreamResult result = stream(location(3, 55.3959, 10.3883));

        assertFalse(result.error == null);
        assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(result.error).getCode());
    }

    // ---------- Helpers ----------

    // What the server sent back on a stream: the updates, and the error (null if none).
    private static class StreamResult {
        final List<TrackingUpdate> updates = new ArrayList<>();
        Throwable error;
    }

    private static LocationUpdate location(long deliveryId, double lat, double lng) {
        return LocationUpdate.newBuilder().setDeliveryId(deliveryId).setLat(lat).setLng(lng).build();
    }

    // Opens the TrackDelivery stream, sends the locations and collects the server's answers.
    private static StreamResult stream(LocationUpdate... locations) throws InterruptedException {
        StreamResult result = new StreamResult();
        CountDownLatch finished = new CountDownLatch(1);

        // The async stub is needed for streaming. This observer receives the server's messages.
        StreamObserver<LocationUpdate> requests = TrackingServiceGrpc.newStub(channel)
                .trackDelivery(new StreamObserver<>() {
                    @Override
                    public void onNext(TrackingUpdate update) {
                        result.updates.add(update);
                    }

                    @Override
                    public void onError(Throwable error) {
                        result.error = error;
                        finished.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        finished.countDown();
                    }
                });

        // Send our messages and tell the server we are done.
        for (LocationUpdate location : locations) {
            requests.onNext(location);
        }
        requests.onCompleted();

        // Wait until the server has closed the stream.
        assertTrue(finished.await(5, TimeUnit.SECONDS), "the stream did not finish in time");
        return result;
    }
}
