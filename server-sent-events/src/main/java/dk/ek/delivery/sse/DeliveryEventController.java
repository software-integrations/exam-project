package dk.ek.delivery.sse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

// The HTTP endpoints: one SSE stream (server -> client) and one POST that creates the events.
@RestController
@RequestMapping("/api/v1/deliveries")
public class DeliveryEventController {

    // How long one stream may stay open (10 minutes). The browser reconnects by itself afterwards.
    private static final long STREAM_TIMEOUT_MS = 10 * 60 * 1000;

    private final TrackingService tracking;
    private final EventPublisher publisher;

    public DeliveryEventController(TrackingService tracking, EventPublisher publisher) {
        this.tracking = tracking;
        this.publisher = publisher;
    }

    // GET /api/v1/deliveries/1/events -> an open stream of events (Content-Type: text/event-stream)
    // The browser sends "Last-Event-ID" by itself when it reconnects after a lost connection.
    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable long id,
                             @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId)
            throws IOException {
        Delivery delivery = tracking.getStatus(id); // 404 if the delivery does not exist
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);

        // 1. The first event is always the current state from the database.
        emitter.send(SseEmitter.event().name("status").data(delivery, MediaType.APPLICATION_JSON));

        // 2. Reconnect: replay the events the client missed while it was away.
        if (lastEventId != null) {
            for (TrackingUpdate missed : tracking.findUpdatesAfter(id, lastEventId)) {
                EventPublisher.sendLocation(emitter, missed);
            }
        }

        // 3. A delivered delivery gets no more events, so the stream is closed at once.
        //    Otherwise the client is remembered and gets every new event live.
        if (delivery.status().equals("DELIVERED")) {
            emitter.complete();
        } else {
            publisher.subscribe(id, emitter);
        }
        return emitter;
    }

    // POST /api/v1/deliveries/1/locations -> the courier reports a position.
    // The position is stored and pushed to everyone who watches the stream.
    // "consumes" = only JSON bodies are accepted (415 otherwise). This is the CSRF protection:
    // an HTML form on another website cannot send application/json.
    @PostMapping(value = "/{id}/locations", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TrackingUpdate reportLocation(@PathVariable long id, @RequestBody LocationRequest request) {
        if (request.lat() == null || request.lng() == null) {
            throw new IllegalArgumentException("lat and lng are required");
        }
        // Business rule: a delivered delivery cannot move any more (409 Conflict).
        if (tracking.getStatus(id).status().equals("DELIVERED")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery " + id + " is already delivered");
        }
        TrackingUpdate update = tracking.recordLocation(id, request.lat(), request.lng());
        publisher.publish(update);
        return update;
    }

    // Unknown delivery -> 404 Not Found
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // Invalid input -> 400 Bad Request
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
