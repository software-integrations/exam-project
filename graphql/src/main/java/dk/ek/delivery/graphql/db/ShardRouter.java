package dk.ek.delivery.graphql.db;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

// Hash-based sharding: decides which shard a customer (and its parcels) belongs to.
// The shard key is the customer's email.
@Component
public class ShardRouter {

    private final Databases databases;

    public ShardRouter(Databases databases) {
        this.databases = databases;
    }

    // shard = hash(email) modulo number of shards -> always 0, 1 or 2.
    // CRC32 is a simple, stable hash: the same email always gives the same number.
    public int shardIndexFor(String email) {
        CRC32 hash = new CRC32();
        hash.update(email.toLowerCase().getBytes(StandardCharsets.UTF_8));
        return (int) (hash.getValue() % Databases.SHARD_COUNT);
    }

    // The database connection of the shard that owns this email.
    public JdbcClient shardFor(String email) {
        return databases.shards.get(shardIndexFor(email));
    }
}
