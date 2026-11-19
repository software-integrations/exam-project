package dk.ek.delivery.soap.fault;

import dk.ek.delivery.soap.PickupPointService;
import jakarta.xml.ws.WebFault;

// SOAP fault: the input did not pass validation (security: unsafe input is rejected).
@WebFault(name = "InvalidInputFault", targetNamespace = PickupPointService.NAMESPACE)
public class InvalidInputFault extends Exception {

    public InvalidInputFault(String message) {
        super(message);
    }
}
