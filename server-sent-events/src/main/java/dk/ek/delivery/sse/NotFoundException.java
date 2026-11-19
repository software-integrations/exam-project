package dk.ek.delivery.sse;

// Thrown when a delivery id does not exist. The controller turns it into 404 Not Found.
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
