package dk.ek.delivery.grpc;

// Geography helper.
public class Geo {

    // Distance in km between two GPS points (the "haversine" formula:
    // the shortest distance over the surface of the earth).
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusKm = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
