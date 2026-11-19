package dk.ek.delivery.rest.customer;

import dk.ek.delivery.rest.common.Link;
import dk.ek.delivery.rest.common.PagedResponse;
import dk.ek.delivery.rest.common.Resource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Endpoints for the "customers" resource. "v1" in the URL is the API version.
@RestController
@RequestMapping(CustomerController.PATH)
public class CustomerController {

    public static final String PATH = "/api/v1/customers";

    private final CustomerRepository customers;

    public CustomerController(CustomerRepository customers) {
        this.customers = customers;
    }

    // GET /api/v1/customers?search=anna&page=1&size=10 -> several customers
    @GetMapping
    public PagedResponse<Resource<Customer>> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        List<Resource<Customer>> items = customers.search(search, page, size).stream()
                .map(this::toResource)
                .toList();
        return PagedResponse.of(items, page, size, customers.count(search), PATH, search);
    }

    // GET /api/v1/customers/1 -> one customer, or 404 if it does not exist
    @GetMapping("/{id}")
    public Resource<Customer> getById(@PathVariable long id) {
        Customer customer = customers.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer " + id + " not found"));
        return toResource(customer);
    }

    // HATEOAS: adds the links that tell the client what it can do next.
    private Resource<Customer> toResource(Customer customer) {
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", Link.get(PATH + "/" + customer.id()));
        links.put("collection", Link.get(PATH));
        return new Resource<>(customer, links);
    }
}
