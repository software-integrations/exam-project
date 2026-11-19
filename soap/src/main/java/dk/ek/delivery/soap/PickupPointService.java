package dk.ek.delivery.soap;

import dk.ek.delivery.soap.fault.InvalidInputFault;
import dk.ek.delivery.soap.fault.NotFoundFault;
import dk.ek.delivery.soap.fault.PickupPointFullFault;
import dk.ek.delivery.soap.model.PickupPoint;
import dk.ek.delivery.soap.model.Reservation;
import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;

import java.util.List;

// The SOAP contract, written in Java ("code-first").
// CXF reads these annotations and generates the WSDL from them:
//   @WebService -> <portType>,  @WebMethod -> <operation>,
//   @WebParam / @WebResult -> the XML elements of the messages,
//   "throws ...Fault" -> the <fault> elements of the operation.
@WebService(name = "PickupPointPort", targetNamespace = PickupPointService.NAMESPACE)
public interface PickupPointService {

    // The XML namespace of everything in this service.
    String NAMESPACE = "http://delivery.ek.dk/pickup";

    // READ: one pickup point by id.
    @WebMethod
    @WebResult(name = "pickupPoint")
    PickupPoint getPickupPoint(@WebParam(name = "id") long id) throws NotFoundFault;

    // READ: all pickup points in a city.
    @WebMethod
    @WebResult(name = "pickupPoint")
    List<PickupPoint> findPickupPointsByCity(@WebParam(name = "city") String city) throws InvalidInputFault;

    // CHANGE: reserve a locker for a parcel.
    @WebMethod
    @WebResult(name = "reservation")
    Reservation reserveLocker(@WebParam(name = "pickupPointId") long pickupPointId,
                              @WebParam(name = "trackingNumber") String trackingNumber)
            throws NotFoundFault, InvalidInputFault, PickupPointFullFault;

    // CHANGE: cancel a reservation, which frees the locker again.
    @WebMethod
    @WebResult(name = "reservation")
    Reservation cancelReservation(@WebParam(name = "reservationId") long reservationId) throws NotFoundFault;
}
