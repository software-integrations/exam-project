package dk.ek.delivery.rest.delivery;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// All SQL for deliveries. Only "?" parameters are used -> no SQL injection.
@Repository
public class DeliveryRepository {

    private final JdbcClient db;

    public DeliveryRepository(JdbcClient db) {
        this.db = db;
    }

    // Search: matches address or status. Pagination with LIMIT and OFFSET.
    public List<Delivery> search(String search, int page, int size) {
        String like = "%" + search.toLowerCase() + "%";
        return db.sql("""
                        SELECT * FROM deliveries
                        WHERE LOWER(address) LIKE ? OR LOWER(status) LIKE ?
                        ORDER BY id LIMIT ? OFFSET ?
                        """)
                .params(like, like, size, (page - 1) * size)
                .query(Delivery.class)
                .list();
    }

    // Total number of matches, used to calculate the number of pages.
    public long count(String search) {
        String like = "%" + search.toLowerCase() + "%";
        return db.sql("SELECT COUNT(*) FROM deliveries WHERE LOWER(address) LIKE ? OR LOWER(status) LIKE ?")
                .params(like, like)
                .query(Long.class)
                .single();
    }

    public Optional<Delivery> findById(long id) {
        return db.sql("SELECT * FROM deliveries WHERE id = ?")
                .param(id)
                .query(Delivery.class)
                .optional();
    }

    // Inserts a new delivery and returns the id the database generated.
    public long insert(long customerId, Long courierId, String address, String status) {
        KeyHolder key = new GeneratedKeyHolder();
        db.sql("INSERT INTO deliveries (customer_id, courier_id, address, status) VALUES (?, ?, ?, ?)")
                .params(customerId, courierId, address, status)
                .update(key, "id");
        return key.getKey().longValue();
    }

    public void update(long id, long customerId, Long courierId, String address, String status) {
        db.sql("UPDATE deliveries SET customer_id = ?, courier_id = ?, address = ?, status = ? WHERE id = ?")
                .params(customerId, courierId, address, status, id)
                .update();
    }

    public void delete(long id) {
        db.sql("DELETE FROM deliveries WHERE id = ?")
                .param(id)
                .update();
    }
}
