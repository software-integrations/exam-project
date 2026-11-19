package dk.ek.delivery.grpc;

// The result of storing one courier position: new status and distance left.
public record TrackingUpdate(long deliveryId, String status, double distanceKm, String message) {
}
