package dk.ek.delivery.rest.common;

import org.springframework.web.util.HtmlUtils;

// XSS protection: text from clients is HTML-escaped before it is stored.
// "<script>" becomes "&lt;script&gt;", so a browser will never run it.
public class Sanitizer {

    public static String clean(String input) {
        return HtmlUtils.htmlEscape(input.trim());
    }
}
