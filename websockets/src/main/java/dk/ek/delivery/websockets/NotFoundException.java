package dk.ek.delivery.websockets;

// Thrown when a delivery id does not exist in the database.
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
