package dk.ek.delivery.soap.fault;

import dk.ek.delivery.soap.PickupPointService;
import jakarta.xml.ws.WebFault;

// SOAP fault: business rule broken, the pickup point has no free lockers.
@WebFault(name = "PickupPointFullFault", targetNamespace = PickupPointService.NAMESPACE)
public class PickupPointFullFault extends Exception {

    public PickupPointFullFault(String message) {
        super(message);
    }
}
