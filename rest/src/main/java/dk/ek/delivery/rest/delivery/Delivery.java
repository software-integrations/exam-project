package dk.ek.delivery.rest.delivery;

import java.time.LocalDateTime;

// One row of the "deliveries" table. courierId is null until a courier is assigned.
public record Delivery(long id, long customerId, Long courierId, String address, String status,
                       LocalDateTime createdAt) {
}
