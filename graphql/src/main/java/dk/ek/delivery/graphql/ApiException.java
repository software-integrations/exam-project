package dk.ek.delivery.graphql;

import org.springframework.graphql.execution.ErrorType;

// An error we want the client to see, e.g. UNAUTHORIZED, NOT_FOUND or BAD_REQUEST.
public class ApiException extends RuntimeException {

    public final ErrorType type;

    public ApiException(ErrorType type, String message) {
        super(message);
        this.type = type;
    }
}
