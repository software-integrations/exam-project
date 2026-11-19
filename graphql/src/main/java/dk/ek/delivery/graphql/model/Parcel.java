package dk.ek.delivery.graphql.model;

// A parcel that belongs to one customer.
public record Parcel(long id, String description, double weightKg, String status, String createdAt) {
}
