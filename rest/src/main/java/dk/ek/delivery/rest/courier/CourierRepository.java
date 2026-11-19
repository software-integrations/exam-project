package dk.ek.delivery.rest.courier;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// All SQL for couriers. Only "?" parameters are used -> no SQL injection.
@Repository
public class CourierRepository {

    private final JdbcClient db;

    public CourierRepository(JdbcClient db) {
        this.db = db;
    }

    // Search: matches name or vehicle. Pagination with LIMIT and OFFSET.
    public List<Courier> search(String search, int page, int size) {
        String like = "%" + search.toLowerCase() + "%";
        return db.sql("""
                        SELECT * FROM couriers
                        WHERE LOWER(name) LIKE ? OR LOWER(vehicle) LIKE ?
                        ORDER BY id LIMIT ? OFFSET ?
                        """)
                .params(like, like, size, (page - 1) * size)
                .query(Courier.class)
                .list();
    }

    // Total number of matches, used to calculate the number of pages.
    public long count(String search) {
        String like = "%" + search.toLowerCase() + "%";
        return db.sql("SELECT COUNT(*) FROM couriers WHERE LOWER(name) LIKE ? OR LOWER(vehicle) LIKE ?")
                .params(like, like)
                .query(Long.class)
                .single();
    }

    public Optional<Courier> findById(long id) {
        return db.sql("SELECT * FROM couriers WHERE id = ?")
                .param(id)
                .query(Courier.class)
                .optional();
    }
}
