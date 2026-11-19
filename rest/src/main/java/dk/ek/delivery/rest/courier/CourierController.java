package dk.ek.delivery.rest.courier;

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

// Endpoints for the "couriers" resource.
@RestController
@RequestMapping(CourierController.PATH)
public class CourierController {

    public static final String PATH = "/api/v1/couriers";

    private final CourierRepository couriers;

    public CourierController(CourierRepository couriers) {
        this.couriers = couriers;
    }

    // GET /api/v1/couriers?search=van&page=1&size=10 -> several couriers
    @GetMapping
    public PagedResponse<Resource<Courier>> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        List<Resource<Courier>> items = couriers.search(search, page, size).stream()
                .map(this::toResource)
                .toList();
        return PagedResponse.of(items, page, size, couriers.count(search), PATH, search);
    }

    // GET /api/v1/couriers/1 -> one courier, or 404 if it does not exist
    @GetMapping("/{id}")
    public Resource<Courier> getById(@PathVariable long id) {
        Courier courier = couriers.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Courier " + id + " not found"));
        return toResource(courier);
    }

    // HATEOAS: adds the links that tell the client what it can do next.
    private Resource<Courier> toResource(Courier courier) {
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", Link.get(PATH + "/" + courier.id()));
        links.put("collection", Link.get(PATH));
        return new Resource<>(courier, links);
    }
}
