package dk.ek.delivery.soap;

import dk.ek.delivery.soap.fault.InvalidInputFault;
import dk.ek.delivery.soap.fault.NotFoundFault;
import dk.ek.delivery.soap.fault.PickupPointFullFault;
import dk.ek.delivery.soap.model.PickupPoint;
import dk.ek.delivery.soap.model.Reservation;
import jakarta.jws.WebService;
import org.springframework.stereotype.Service;

import java.util.List;

// The implementation of the SOAP operations: validate input, then use the database.
@Service
@WebService(endpointInterface = "dk.ek.delivery.soap.PickupPointService",
        serviceName = "PickupPointService",
        portName = "PickupPointPort",
        targetNamespace = PickupPointService.NAMESPACE)
public class PickupPointServiceImpl implements PickupPointService {

    private final PickupPointRepository repository;

    public PickupPointServiceImpl(PickupPointRepository repository) {
        this.repository = repository;
    }

    @Override
    public PickupPoint getPickupPoint(long id) throws NotFoundFault {
        return repository.findPickupPoint(id)
                .orElseThrow(() -> new NotFoundFault("Pickup point " + id + " not found"));
    }

    @Override
    public List<PickupPoint> findPickupPointsByCity(String city) throws InvalidInputFault {
        // Allow-list validation: only letters, spaces and hyphens (blocks XSS and odd input).
        if (city == null || !city.matches("[\\p{L} -]{1,50}")) {
            throw new InvalidInputFault("city must be 1-50 letters");
        }
        return repository.findPickupPointsByCity(city);
    }

    @Override
    public Reservation reserveLocker(long pickupPointId, String trackingNumber)
            throws NotFoundFault, InvalidInputFault, PickupPointFullFault {
        // Allow-list validation: tracking numbers look like DK-123456.
        if (trackingNumber == null || !trackingNumber.matches("[A-Z0-9-]{6,20}")) {
            throw new InvalidInputFault("trackingNumber must be 6-20 characters: A-Z, 0-9 or -");
        }
        PickupPoint pickupPoint = getPickupPoint(pickupPointId);
        // Business rule: no free lockers -> fault.
        if (pickupPoint.freeLockers <= 0) {
            throw new PickupPointFullFault("Pickup point " + pickupPointId + " has no free lockers");
        }
        long reservationId = repository.insertReservation(pickupPointId, trackingNumber);
        return repository.findReservation(reservationId).orElseThrow();
    }

    @Override
    public Reservation cancelReservation(long reservationId) throws NotFoundFault {
        // cancel returns false when there is no ACTIVE reservation with this id.
        if (!repository.cancelReservation(reservationId)) {
            throw new NotFoundFault("Active reservation " + reservationId + " not found");
        }
        return repository.findReservation(reservationId).orElseThrow();
    }
}
