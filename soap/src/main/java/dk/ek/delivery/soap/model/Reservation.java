package dk.ek.delivery.soap.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

// A locker reservation as it appears in the SOAP XML.
@XmlAccessorType(XmlAccessType.FIELD)
public class Reservation {
    public long id;
    public long pickupPointId;
    public String trackingNumber;
    public String status;
}
