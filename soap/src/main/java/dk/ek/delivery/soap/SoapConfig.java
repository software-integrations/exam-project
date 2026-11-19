package dk.ek.delivery.soap;

import jakarta.xml.ws.Endpoint;
import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Publishes the SOAP service on a URL.
@Configuration
public class SoapConfig {

    // Endpoint: http://localhost:8082/services/pickup-points
    // WSDL:     http://localhost:8082/services/pickup-points?wsdl
    // CXF creates the WSDL at runtime from the annotations on PickupPointService.
    @Bean
    public Endpoint pickupPointEndpoint(Bus bus, PickupPointServiceImpl service) {
        EndpointImpl endpoint = new EndpointImpl(bus, service);
        endpoint.publish("/pickup-points");
        return endpoint;
    }
}
