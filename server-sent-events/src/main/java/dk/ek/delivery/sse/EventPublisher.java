package dk.ek.delivery.sse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Keeps track of the open SSE connections and pushes events to them.
// One SseEmitter = one open HTTP connection to one client.
@Component
public class EventPublisher {

    // delivery id -> the clients that are watching this delivery.
    // Thread-safe collections, because many requests use them at the same time.
    private final Map<Long, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    // Remembers a new client. It is removed again when its connection ends.
    public void subscribe(long deliveryId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.computeIfAbsent(deliveryId, id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));   // stream closed (by us or the client)
        emitter.onTimeout(emitter::complete);                    // open too long -> close it
        emitter.onError(error -> emitters.remove(emitter));     // client disappeared
    }

    // Sends a "location" event to every client that watches this delivery.
    public void publish(TrackingUpdate update) {
        for (SseEmitter emitter : subscribers.getOrDefault(update.deliveryId(), List.of())) {
            try {
                sendLocation(emitter, update);
                // The last event of a delivery: say "delivered" and close the stream.
                if (update.status().equals("DELIVERED")) {
                    emitter.send(SseEmitter.event().name("delivered").data(update, MediaType.APPLICATION_JSON));
                    emitter.complete();
                }
            } catch (IOException | IllegalStateException e) {
                // The client is gone. Closing the emitter also removes it from the list.
                emitter.completeWithError(e);
            }
        }
    }

    // Writes one event to the stream:  id:<id>  event:location  data:<JSON>  + blank line
    public static void sendLocation(SseEmitter emitter, TrackingUpdate update) throws IOException {
        emitter.send(SseEmitter.event()
                .id(String.valueOf(update.id()))
                .name("location")
                .data(update, MediaType.APPLICATION_JSON));
    }
}
