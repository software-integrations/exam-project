package dk.ek.delivery.rest.common;

// One HATEOAS link: where to go (href) and which HTTP method to use.
public record Link(String href, String method) {

    // Shortcut for the most common kind of link.
    public static Link get(String href) {
        return new Link(href, "GET");
    }
}
