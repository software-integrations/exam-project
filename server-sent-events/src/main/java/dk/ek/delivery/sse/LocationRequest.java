package dk.ek.delivery.sse;

// JSON body of POST /locations: the courier's position.
public record LocationRequest(Double lat, Double lng) {
}
