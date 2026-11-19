package dk.ek.delivery.rest.delivery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// JSON body for POST and PUT. The annotations validate the input (400 if invalid).
public record DeliveryRequest(
        @NotNull Long customerId,
        Long courierId,
        @NotBlank @Size(max = 200) String address,
        @NotNull @Pattern(regexp = "PENDING|IN_TRANSIT|DELIVERED|CANCELLED") String status) {
}
