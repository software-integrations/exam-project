package dk.ek.delivery.rest.customer;

// One row of the "customers" table.
public record Customer(long id, String name, String email, String city) {
}
