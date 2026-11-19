package dk.ek.delivery.grpc;

import dk.ek.delivery.grpc.proto.DeliveryStatusRequest;
import dk.ek.delivery.grpc.proto.DeliveryStatusResponse;
import dk.ek.delivery.grpc.proto.LocationUpdate;
import dk.ek.delivery.grpc.proto.TrackingServiceGrpc;
import dk.ek.delivery.grpc.proto.TrackingUpdate;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

// The gRPC layer: implements the two RPCs from tracking.proto.
// TrackingServiceImplBase and the message classes are GENERATED from the proto file.
// This class only translates between Protobuf messages and TrackingService (the business logic).
@Component
public class TrackingGrpcService extends TrackingServiceGrpc.TrackingServiceImplBase {

    private final TrackingService tracking;

    public TrackingGrpcService(TrackingService tracking) {
        this.tracking = tracking;
    }

    // UNARY RPC: one request in, one response out.
    @Override
    public void getDeliveryStatus(DeliveryStatusRequest request, StreamObserver<DeliveryStatusResponse> response) {
        try {
            var delivery = tracking.getStatus(request.getDeliveryId());

            // Build the Protobuf response message from the database row.
            DeliveryStatusResponse.Builder reply = DeliveryStatusResponse.newBuilder()
                    .setDeliveryId(delivery.id())
                    .setRecipient(delivery.recipient())
                    .setAddress(delivery.address())
                    .setStatus(delivery.status())
                    .setUpdatedAt(delivery.updatedAt());
            // The position is optional: only set it if the courier has sent one.
            if (delivery.lastLat() != null) {
                reply.setLastLat(delivery.lastLat());
                reply.setLastLng(delivery.lastLng());
            }

            response.onNext(reply.build()); // send the one response
            response.onCompleted();         // and close the call
        } catch (RuntimeException e) {
            response.onError(toGrpcError(e)); // close the call with an error status
        }
    }

    // BIDIRECTIONAL STREAMING RPC: gRPC gives us a stream to WRITE responses to,
    // and we return an object that RECEIVES the client's messages.
    @Override
    public StreamObserver<LocationUpdate> trackDelivery(StreamObserver<TrackingUpdate> responses) {
        return new StreamObserver<>() {

            // True after we have ended the stream with an error.
            private boolean closed = false;

            // Called every time the client sends a location.
            @Override
            public void onNext(LocationUpdate location) {
                if (closed) {
                    return;
                }
                try {
                    // Store the position in the database and calculate the new status.
                    var update = tracking.recordLocation(location.getDeliveryId(), location.getLat(), location.getLng());

                    // Answer immediately on the same stream. The client can keep sending.
                    responses.onNext(TrackingUpdate.newBuilder()
                            .setDeliveryId(update.deliveryId())
                            .setStatus(update.status())
                            .setDistanceKm(update.distanceKm())
                            .setMessage(update.message())
                            .build());
                } catch (RuntimeException e) {
                    // In gRPC an error ends the whole stream.
                    closed = true;
                    responses.onError(toGrpcError(e));
                }
            }

            // Called if the client breaks the connection. Nothing to clean up.
            @Override
            public void onError(Throwable error) {
                closed = true;
            }

            // Called when the client says "I am done sending". We close our side too.
            @Override
            public void onCompleted() {
                if (!closed) {
                    responses.onCompleted();
                }
            }
        };
    }

    // Translates our Java exceptions into gRPC status codes (like HTTP status codes in REST).
    private static StatusRuntimeException toGrpcError(RuntimeException e) {
        if (e instanceof NotFoundException) {
            return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
        }
        if (e instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException();
        }
        // Unknown errors: no details are sent to the client.
        return Status.INTERNAL.withDescription("Internal error").asRuntimeException();
    }
}
