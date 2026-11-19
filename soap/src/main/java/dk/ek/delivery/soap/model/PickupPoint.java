package dk.ek.delivery.soap.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

// A pickup point as it appears in the SOAP XML.
// JAXB turns each public field into an XML element (and into the XSD type in the WSDL).
@XmlAccessorType(XmlAccessType.FIELD)
public class PickupPoint {
    public long id;
    public String name;
    public String city;
    public String address;
    public int capacity;
    public int freeLockers;
}
