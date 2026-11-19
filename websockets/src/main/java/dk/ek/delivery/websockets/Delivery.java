package dk.ek.delivery.websockets;

// One row of the "deliveries" table. lastLat / lastLng are null until the courier sends a position.
public record Delivery(long id, String recipient, String address, double destLat, double destLng,
                       String status, Double lastLat, Double lastLng, String updatedAt) {
}
