package dk.ek.delivery.websockets;

// The answer to one courier position: the new status and the distance left to the destination.
public record TrackingUpdate(long deliveryId, String status, double distanceKm, String message) {
}
