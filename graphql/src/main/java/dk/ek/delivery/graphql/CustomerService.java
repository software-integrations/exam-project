package dk.ek.delivery.graphql;

import dk.ek.delivery.graphql.db.Databases;
import dk.ek.delivery.graphql.db.ShardRouter;
import dk.ek.delivery.graphql.model.Customer;
import dk.ek.delivery.graphql.model.Parcel;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Reads and writes customers and parcels in the shards.
// Every method first asks the ShardRouter which shard to use.
// All SQL uses "?" parameters (prepared statements) -> no SQL injection.
@Service
public class CustomerService {

    // Turns one database row into a Parcel object.
    private static final RowMapper<Parcel> PARCEL = (row, n) -> new Parcel(
            row.getLong("id"), row.getString("description"), row.getDouble("weight_kg"),
            row.getString("status"), row.getString("created_at"));

    private final Databases databases;
    private final ShardRouter router;

    public CustomerService(Databases databases, ShardRouter router) {
        this.databases = databases;
        this.router = router;
    }

    // Turns one database row into a Customer object and remembers the shard it came from.
    private static RowMapper<Customer> customerFrom(int shard) {
        return (row, n) -> new Customer(row.getLong("id"), shard,
                row.getString("email"), row.getString("name"), row.getString("city"));
    }

    // READ from ONE shard: the hash of the email tells us where the customer is.
    public Optional<Customer> findCustomer(String email) {
        int shard = router.shardIndexFor(email);
        return databases.shards.get(shard)
                .sql("SELECT * FROM customers WHERE email = ?")
                .param(email.toLowerCase())
                .query(customerFrom(shard))
                .optional();
    }

    // READ from ALL shards: there is no email to route on, so we ask every shard.
    public List<Customer> findAllCustomers() {
        List<Customer> all = new ArrayList<>();
        for (int shard = 0; shard < Databases.SHARD_COUNT; shard++) {
            all.addAll(databases.shards.get(shard)
                    .sql("SELECT * FROM customers ORDER BY id")
                    .query(customerFrom(shard))
                    .list());
        }
        return all;
    }

    // READ the parcels of a customer from the customer's own shard.
    public List<Parcel> findParcels(Customer customer) {
        return databases.shards.get(customer.shard())
                .sql("SELECT * FROM parcels WHERE customer_id = ? ORDER BY id")
                .param(customer.id())
                .query(PARCEL)
                .list();
    }

    // WRITE to ONE shard: the customer is stored in the shard chosen by the hash.
    public Customer registerCustomer(String name, String email, String city) {
        if (!email.matches("[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}")) {
            throw new ApiException(ErrorType.BAD_REQUEST, "email is not valid");
        }
        if (name.isBlank() || city.isBlank()) {
            throw new ApiException(ErrorType.BAD_REQUEST, "name and city must not be empty");
        }
        if (findCustomer(email).isPresent()) {
            throw new ApiException(ErrorType.BAD_REQUEST, "A customer with this email already exists");
        }
        // HtmlUtils.htmlEscape: XSS protection, "<" becomes "&lt;" before the text is stored.
        router.shardFor(email)
                .sql("INSERT INTO customers (email, name, city) VALUES (?, ?, ?)")
                .params(email.toLowerCase(), HtmlUtils.htmlEscape(name), HtmlUtils.htmlEscape(city))
                .update();
        return findCustomer(email).orElseThrow();
    }

    // WRITE to ONE shard: the parcel goes to the same shard as its customer.
    public Parcel createParcel(String customerEmail, String description, double weightKg) {
        if (description.isBlank() || weightKg <= 0) {
            throw new ApiException(ErrorType.BAD_REQUEST, "description must not be empty and weightKg must be positive");
        }
        Customer customer = findCustomer(customerEmail)
                .orElseThrow(() -> new ApiException(ErrorType.NOT_FOUND, "Customer not found"));

        JdbcClient shard = databases.shards.get(customer.shard());
        KeyHolder key = new GeneratedKeyHolder();
        shard.sql("INSERT INTO parcels (customer_id, description, weight_kg, status) VALUES (?, ?, ?, 'REGISTERED')")
                .params(customer.id(), HtmlUtils.htmlEscape(description), weightKg)
                .update(key, "id");

        return shard.sql("SELECT * FROM parcels WHERE id = ?")
                .param(key.getKey().longValue())
                .query(PARCEL)
                .single();
    }
}
