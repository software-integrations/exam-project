package dk.ek.delivery.soap;

import dk.ek.delivery.soap.model.PickupPoint;
import dk.ek.delivery.soap.model.Reservation;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// All SQL of the SOAP API. Every value is passed as a "?" parameter (prepared statement),
// so user input can never change the SQL itself -> no SQL injection.
@Repository
public class PickupPointRepository {

    // Selects a pickup point and calculates its free lockers:
    // capacity minus the number of ACTIVE reservations.
    private static final String SELECT_PICKUP_POINT = """
            SELECT p.*, p.capacity - (SELECT COUNT(*) FROM reservations r
                                      WHERE r.pickup_point_id = p.id AND r.status = 'ACTIVE') AS free_lockers
            FROM pickup_points p
            """;

    // Turns one database row into a PickupPoint object.
    private static final RowMapper<PickupPoint> PICKUP_POINT = (row, n) -> {
        PickupPoint p = new PickupPoint();
        p.id = row.getLong("id");
        p.name = row.getString("name");
        p.city = row.getString("city");
        p.address = row.getString("address");
        p.capacity = row.getInt("capacity");
        p.freeLockers = row.getInt("free_lockers");
        return p;
    };

    // Turns one database row into a Reservation object.
    private static final RowMapper<Reservation> RESERVATION = (row, n) -> {
        Reservation r = new Reservation();
        r.id = row.getLong("id");
        r.pickupPointId = row.getLong("pickup_point_id");
        r.trackingNumber = row.getString("tracking_number");
        r.status = row.getString("status");
        return r;
    };

    private final JdbcClient db;

    public PickupPointRepository(JdbcClient db) {
        this.db = db;
    }

    public Optional<PickupPoint> findPickupPoint(long id) {
        return db.sql(SELECT_PICKUP_POINT + "WHERE p.id = ?")
                .param(id)
                .query(PICKUP_POINT)
                .optional();
    }

    public List<PickupPoint> findPickupPointsByCity(String city) {
        return db.sql(SELECT_PICKUP_POINT + "WHERE LOWER(p.city) = LOWER(?) ORDER BY p.id")
                .param(city)
                .query(PICKUP_POINT)
                .list();
    }

    public Optional<Reservation> findReservation(long id) {
        return db.sql("SELECT * FROM reservations WHERE id = ?")
                .param(id)
                .query(RESERVATION)
                .optional();
    }

    // Inserts a reservation and returns the id the database generated.
    public long insertReservation(long pickupPointId, String trackingNumber) {
        KeyHolder key = new GeneratedKeyHolder();
        db.sql("INSERT INTO reservations (pickup_point_id, tracking_number, status) VALUES (?, ?, 'ACTIVE')")
                .params(pickupPointId, trackingNumber)
                .update(key, "id");
        return key.getKey().longValue();
    }

    // Marks an ACTIVE reservation as CANCELLED. Returns true if a row was changed.
    public boolean cancelReservation(long id) {
        int changedRows = db.sql("UPDATE reservations SET status = 'CANCELLED' WHERE id = ? AND status = 'ACTIVE'")
                .param(id)
                .update();
        return changedRows == 1;
    }
}
