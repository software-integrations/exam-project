package dk.ek.delivery.graphql.model;

// A customer. "shard" is the number of the shard the row was read from.
public record Customer(long id, int shard, String email, String name, String city) {
}
