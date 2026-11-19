package dk.ek.delivery.sse;

import java.time.LocalDateTime;

// One row of the "deliveries" table. lastLat / lastLng are null until the courier reports a position.
public record Delivery(long id, String recipient, String address, double destLat, double destLng,
                       String status, Double lastLat, Double lastLng, LocalDateTime updatedAt) {
}
