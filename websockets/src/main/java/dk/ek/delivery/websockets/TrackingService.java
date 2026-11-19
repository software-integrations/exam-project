package dk.ek.delivery.websockets;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

// The business logic and SQL of delivery tracking. It knows nothing about WebSockets.
// All SQL uses "?" parameters (prepared statements) -> no SQL injection.
@Service
public class TrackingService {

    // Turns one database row into a Delivery object.
    private static final RowMapper<Delivery> DELIVERY = (row, n) -> new Delivery(
            row.getLong("id"), row.getString("recipient"), row.getString("address"),
            row.getDouble("dest_lat"), row.getDouble("dest_lng"), row.getString("status"),
            row.getObject("last_lat", Double.class), row.getObject("last_lng", Double.class),
            row.getString("updated_at"));

    private final JdbcClient db;

    public TrackingService(JdbcClient db) {
        this.db = db;
    }

    // Reads one delivery, or throws NotFoundException.
    public Delivery getStatus(long id) {
        return db.sql("SELECT * FROM deliveries WHERE id = ?")
                .param(id)
                .query(DELIVERY)
                .optional()
                .orElseThrow(() -> new NotFoundException("Delivery " + id + " not found"));
    }

    // Stores a new courier position and returns the new status of the delivery.
    public TrackingUpdate recordLocation(long id, double lat, double lng) {
        // 1. Validate the coordinates.
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("lat must be between -90 and 90 and lng between -180 and 180");
        }
        // 2. The delivery must exist and must not be finished.
        Delivery delivery = getStatus(id);
        if (delivery.status().equals("DELIVERED")) {
            throw new IllegalArgumentException("Delivery " + id + " is already delivered");
        }

        // 3. Save the position in the history table.
        db.sql("INSERT INTO location_updates (delivery_id, lat, lng) VALUES (?, ?, ?)")
                .params(id, lat, lng)
                .update();

        // 4. The distance to the destination decides the new status.
        double distanceKm = Geo.distanceKm(lat, lng, delivery.destLat(), delivery.destLng());
        String status;
        String message;
        if (distanceKm < 0.05) {
            status = "DELIVERED";
            message = "The courier has arrived. Delivery completed";
        } else if (distanceKm < 1.0) {
            status = "ARRIVING";
            message = "The courier is less than 1 km away";
        } else {
            status = "IN_TRANSIT";
            message = "The courier is " + distanceKm + " km away";
        }

        // 5. Save the new status and the latest position on the delivery.
        db.sql("UPDATE deliveries SET status = ?, last_lat = ?, last_lng = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
                .params(status, lat, lng, id)
                .update();

        return new TrackingUpdate(id, status, distanceKm, message);
    }
}
