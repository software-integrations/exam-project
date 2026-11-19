package dk.ek.delivery.websockets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

// Handles the messages of every WebSocket connection. Messages are JSON text frames.
// The "action" field of a message says which operation the client wants:
//   getDeliveryStatus -> one request, one answer        (the unary RPC of the gRPC API)
//   trackDelivery     -> many messages, one answer each (the bidirectional streaming RPC)
@Component
public class TrackingHandler extends TextWebSocketHandler {

    private final TrackingService trackingService;
    private final ObjectMapper json = new ObjectMapper();

    public TrackingHandler(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    // Called every time the client sends a text frame on an open connection.
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        try {
            JsonNode request = json.readTree(message.getPayload());
            String action = request.path("action").asText();

            if (action.equals("getDeliveryStatus")) {
                getDeliveryStatus(session, request);
            } else if (action.equals("trackDelivery")) {
                trackDelivery(session, request);
            } else {
                // The client's text is never copied into the answer (XSS protection).
                sendError(session, "INVALID_ARGUMENT", "action must be getDeliveryStatus or trackDelivery");
            }
        } catch (JsonProcessingException e) {
            sendError(session, "INVALID_ARGUMENT", "The message is not valid JSON");
        } catch (IllegalArgumentException e) {
            sendError(session, "INVALID_ARGUMENT", e.getMessage());
        } catch (NotFoundException e) {
            sendError(session, "NOT_FOUND", e.getMessage());
        }
        // After an error the connection stays open, so the client can just send the next message.
    }

    // Unary: {"action":"getDeliveryStatus","deliveryId":1} -> one "deliveryStatus" answer.
    private void getDeliveryStatus(WebSocketSession session, JsonNode request) throws IOException {
        Delivery delivery = trackingService.getStatus(deliveryId(request));

        // Answer = the type of the message + all fields of the delivery.
        ObjectNode answer = json.createObjectNode().put("type", "deliveryStatus");
        answer.setAll((ObjectNode) json.valueToTree(delivery));
        send(session, answer);
    }

    // Streaming: the courier sends {"action":"trackDelivery","deliveryId":1,"lat":..,"lng":..}
    // again and again on the same connection. Every position gets a "trackingUpdate" answer.
    private void trackDelivery(WebSocketSession session, JsonNode request) throws IOException {
        if (!request.path("lat").isNumber() || !request.path("lng").isNumber()) {
            throw new IllegalArgumentException("lat and lng must be numbers");
        }
        TrackingUpdate update = trackingService.recordLocation(deliveryId(request),
                request.get("lat").asDouble(), request.get("lng").asDouble());

        ObjectNode answer = json.createObjectNode().put("type", "trackingUpdate");
        answer.setAll((ObjectNode) json.valueToTree(update));
        send(session, answer);
    }

    // Reads the deliveryId field and checks that it is a whole number.
    private long deliveryId(JsonNode request) {
        if (!request.path("deliveryId").isIntegralNumber()) {
            throw new IllegalArgumentException("deliveryId must be a whole number");
        }
        return request.get("deliveryId").asLong();
    }

    // Error answer: {"type":"error","code":"NOT_FOUND","message":"..."}
    private void sendError(WebSocketSession session, String code, String message) throws IOException {
        send(session, json.createObjectNode().put("type", "error").put("code", code).put("message", message));
    }

    // Sends one JSON text frame to the client.
    private void send(WebSocketSession session, ObjectNode answer) throws IOException {
        session.sendMessage(new TextMessage(json.writeValueAsString(answer)));
    }
}
