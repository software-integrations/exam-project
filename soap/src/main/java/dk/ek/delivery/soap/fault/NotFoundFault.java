package dk.ek.delivery.soap.fault;

import dk.ek.delivery.soap.PickupPointService;
import jakarta.xml.ws.WebFault;

// SOAP fault: the requested pickup point or reservation does not exist.
// @WebFault makes CXF describe it in the WSDL and send it as a <soap:Fault>.
@WebFault(name = "NotFoundFault", targetNamespace = PickupPointService.NAMESPACE)
public class NotFoundFault extends Exception {

    public NotFoundFault(String message) {
        super(message);
    }
}
