package dk.ek.delivery.rest.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.Map;

// HATEOAS wrapper: the data of one item plus its links.
// JSON result: { ...fields of the item..., "_links": { "self": {...}, ... } }
public class Resource<T> {

    // @JsonUnwrapped puts the item's fields directly in the JSON object.
    @JsonUnwrapped
    public final T data;

    @JsonProperty("_links")
    public final Map<String, Link> links;

    public Resource(T data, Map<String, Link> links) {
        this.data = data;
        this.links = links;
    }
}
