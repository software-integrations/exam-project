package dk.ek.delivery.rest.auth;

import jakarta.validation.constraints.NotBlank;

// JSON body of the login request.
public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
