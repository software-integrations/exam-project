package dk.ek.delivery.rest.delivery;

import dk.ek.delivery.rest.common.Link;
import dk.ek.delivery.rest.common.PagedResponse;
import dk.ek.delivery.rest.common.Resource;
import dk.ek.delivery.rest.common.Sanitizer;
import dk.ek.delivery.rest.courier.CourierController;
import dk.ek.delivery.rest.courier.CourierRepository;
import dk.ek.delivery.rest.customer.CustomerController;
import dk.ek.delivery.rest.customer.CustomerRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Endpoints for the "deliveries" resource: full CRUD.
@RestController
@RequestMapping(DeliveryController.PATH)
public class DeliveryController {

    public static final String PATH = "/api/v1/deliveries";

    private final DeliveryRepository deliveries;
    private final CustomerRepository customers;
    private final CourierRepository couriers;

    public DeliveryController(DeliveryRepository deliveries, CustomerRepository customers,
                              CourierRepository couriers) {
        this.deliveries = deliveries;
        this.customers = customers;
        this.couriers = couriers;
    }

    // GET /api/v1/deliveries?search=pending&page=1&size=10 -> several deliveries
    @GetMapping
    public PagedResponse<Resource<Delivery>> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        List<Resource<Delivery>> items = deliveries.search(search, page, size).stream()
                .map(this::toResource)
                .toList();
        return PagedResponse.of(items, page, size, deliveries.count(search), PATH, search);
    }

    // GET /api/v1/deliveries/1 -> one delivery
    @GetMapping("/{id}")
    public Resource<Delivery> getById(@PathVariable long id) {
        return toResource(find(id));
    }

    // POST /api/v1/deliveries -> creates a delivery. Answers 201 Created + Location header.
    @PostMapping
    public ResponseEntity<Resource<Delivery>> create(@Valid @RequestBody DeliveryRequest request) {
        checkReferences(request);
        long id = deliveries.insert(request.customerId(), request.courierId(),
                Sanitizer.clean(request.address()), request.status());
        return ResponseEntity.created(URI.create(PATH + "/" + id)).body(toResource(find(id)));
    }

    // PUT /api/v1/deliveries/1 -> replaces the delivery with the data in the body
    @PutMapping("/{id}")
    public Resource<Delivery> update(@PathVariable long id, @Valid @RequestBody DeliveryRequest request) {
        checkNotDelivered(find(id));
        checkReferences(request);
        deliveries.update(id, request.customerId(), request.courierId(),
                Sanitizer.clean(request.address()), request.status());
        return toResource(find(id));
    }

    // DELETE /api/v1/deliveries/1 -> removes the delivery. Answers 204 No Content.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        checkNotDelivered(find(id));
        deliveries.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Finds a delivery or answers 404 Not Found.
    private Delivery find(long id) {
        return deliveries.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery " + id + " not found"));
    }

    // Business rule: a delivered delivery is history and cannot be changed (409 Conflict).
    private void checkNotDelivered(Delivery delivery) {
        if (delivery.status().equals("DELIVERED")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A delivered delivery cannot be changed");
        }
    }

    // The customer and the courier in the body must exist (400 Bad Request if not).
    private void checkReferences(DeliveryRequest request) {
        if (customers.findById(request.customerId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer does not exist");
        }
        if (request.courierId() != null && couriers.findById(request.courierId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Courier does not exist");
        }
    }

    // HATEOAS: the links depend on the state of the delivery (contextual links).
    private Resource<Delivery> toResource(Delivery delivery) {
        String self = PATH + "/" + delivery.id();
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", Link.get(self));
        links.put("collection", Link.get(PATH));
        links.put("customer", Link.get(CustomerController.PATH + "/" + delivery.customerId()));
        // Only link to a courier if one is assigned.
        if (delivery.courierId() != null) {
            links.put("courier", Link.get(CourierController.PATH + "/" + delivery.courierId()));
        }
        // Only offer update and delete while the delivery can still be changed.
        if (!delivery.status().equals("DELIVERED")) {
            links.put("update", new Link(self, "PUT"));
            links.put("delete", new Link(self, "DELETE"));
        }
        return new Resource<>(delivery, links);
    }
}
