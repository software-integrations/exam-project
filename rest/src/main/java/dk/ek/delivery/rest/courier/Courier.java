package dk.ek.delivery.rest.courier;

// One row of the "couriers" table.
public record Courier(long id, String name, String vehicle, String phone) {
}
