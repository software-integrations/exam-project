package dk.ek.delivery.sse;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.util.List;

// The business logic and all SQL of the tracking scenario (the same logic as in the gRPC API).
// Every value is passed as a "?" parameter (prepared statement) -> no SQL injection.
@Service
public class TrackingService {

    private final JdbcClient db;

    public TrackingService(JdbcClient db) {
        this.db = db;
    }

    // Reads one delivery from the database.
    public Delivery getStatus(long id) {
        return db.sql("SELECT * FROM deliveries WHERE id = ?")
                .param(id)
                .query(Delivery.class)
                .optional()
                .orElseThrow(() -> new NotFoundException("Delivery " + id + " not found"));
    }

    // Stores a new courier position and works out the new status of the delivery.
    public TrackingUpdate recordLocation(long id, double lat, double lng) {
        // 1. Validate the input.
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("lat must be between -90 and 90 and lng between -180 and 180");
        }
        Delivery delivery = getStatus(id);
        if (delivery.status().equals("DELIVERED")) {
            throw new IllegalArgumentException("Delivery " + id + " is already delivered");
        }

        // 2. Save the position. The database generates the id of the new row.
        KeyHolder key = new GeneratedKeyHolder();
        db.sql("INSERT INTO location_updates (delivery_id, lat, lng) VALUES (?, ?, ?)")
                .params(id, lat, lng)
                .update(key, "id");

        // 3. Work out the distance to the destination and the new status.
        TrackingUpdate update = toUpdate(key.getKey().longValue(), delivery, lat, lng);

        // 4. Save the new status and the latest position on the delivery.
        db.sql("UPDATE deliveries SET status = ?, last_lat = ?, last_lng = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
                .params(update.status(), lat, lng, id)
                .update();
        return update;
    }

    // All updates of a delivery that are newer than the given id.
    // Used to replay the events a client missed while it was disconnected.
    public List<TrackingUpdate> findUpdatesAfter(long deliveryId, long lastSeenId) {
        Delivery delivery = getStatus(deliveryId);
        return db.sql("SELECT id, lat, lng FROM location_updates WHERE delivery_id = ? AND id > ? ORDER BY id")
                .params(deliveryId, lastSeenId)
                .query((row, n) -> toUpdate(row.getLong("id"), delivery, row.getDouble("lat"), row.getDouble("lng")))
                .list();
    }

    // Business rules: closer than 50 m = DELIVERED, closer than 1 km = ARRIVING, else IN_TRANSIT.
    private static TrackingUpdate toUpdate(long locationId, Delivery delivery, double lat, double lng) {
        double distanceKm = Geo.distanceKm(lat, lng, delivery.destLat(), delivery.destLng());
        double rounded = Math.round(distanceKm * 100) / 100.0; // 2 decimals

        if (distanceKm < 0.05) {
            return new TrackingUpdate(locationId, delivery.id(), "DELIVERED", rounded, "The courier has arrived. Delivery completed");
        }
        if (distanceKm < 1.0) {
            return new TrackingUpdate(locationId, delivery.id(), "ARRIVING", rounded, "The courier is less than 1 km away");
        }
        return new TrackingUpdate(locationId, delivery.id(), "IN_TRANSIT", rounded,
                "The courier is " + rounded + " km away");
    }
}
