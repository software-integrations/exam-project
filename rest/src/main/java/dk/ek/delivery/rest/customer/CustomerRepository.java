package dk.ek.delivery.rest.customer;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// All SQL for customers. Every value is passed as a "?" parameter (prepared statement),
// so user input can never change the SQL itself -> no SQL injection.
@Repository
public class CustomerRepository {

    private final JdbcClient db;

    public CustomerRepository(JdbcClient db) {
        this.db = db;
    }

    // Search: matches name or city. Pagination: LIMIT = page size, OFFSET = rows to skip.
    public List<Customer> search(String search, int page, int size) {
        String like = "%" + search.toLowerCase() + "%";
        return db.sql("""
                        SELECT * FROM customers
                        WHERE LOWER(name) LIKE ? OR LOWER(city) LIKE ?
                        ORDER BY id LIMIT ? OFFSET ?
                        """)
                .params(like, like, size, (page - 1) * size)
                .query(Customer.class)
                .list();
    }

    // Total number of matches, used to calculate the number of pages.
    public long count(String search) {
        String like = "%" + search.toLowerCase() + "%";
        return db.sql("SELECT COUNT(*) FROM customers WHERE LOWER(name) LIKE ? OR LOWER(city) LIKE ?")
                .params(like, like)
                .query(Long.class)
                .single();
    }

    public Optional<Customer> findById(long id) {
        return db.sql("SELECT * FROM customers WHERE id = ?")
                .param(id)
                .query(Customer.class)
                .optional();
    }
}
