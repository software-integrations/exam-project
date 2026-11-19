package dk.ek.delivery.websockets;

// Geography helper.
public class Geo {

    // Distance in km between two GPS points (haversine formula), rounded to 2 decimals.
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusKm = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double distance = 2 * earthRadiusKm * Math.asin(Math.sqrt(a));
        return Math.round(distance * 100) / 100.0;
    }
}
