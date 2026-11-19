package dk.ek.delivery.rest.common;

import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// One page of a collection, with paging info and HATEOAS links to other pages.
public record PagedResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages,
                               Map<String, Link> _links) {

    // Builds the page. "path" is the collection URL, e.g. /api/v1/customers.
    public static <T> PagedResponse<T> of(List<T> items, int page, int size, long totalItems,
                                          String path, String search) {
        int totalPages = (int) Math.ceil((double) totalItems / size);

        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", pageLink(path, search, page, size));
        links.put("first", pageLink(path, search, 1, size));
        // Contextual links: "prev" and "next" only exist when there is such a page.
        if (page > 1) {
            links.put("prev", pageLink(path, search, page - 1, size));
        }
        if (page < totalPages) {
            links.put("next", pageLink(path, search, page + 1, size));
        }
        if (totalPages > 0) {
            links.put("last", pageLink(path, search, totalPages, size));
        }
        return new PagedResponse<>(items, page, size, totalItems, totalPages, links);
    }

    // Builds a link like /api/v1/customers?page=2&size=5&search=anna
    private static Link pageLink(String path, String search, int page, int size) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath(path)
                .queryParam("page", page)
                .queryParam("size", size);
        if (!search.isBlank()) {
            uri.queryParam("search", search);
        }
        return Link.get(uri.toUriString());
    }
}
