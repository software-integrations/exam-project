package dk.ek.delivery.sse;

// The answer to one location update: the new status and the distance left.
// "id" is the id of the row in location_updates. It is used as the SSE event id.
public record TrackingUpdate(long id, long deliveryId, String status, double distanceKm, String message) {
}
