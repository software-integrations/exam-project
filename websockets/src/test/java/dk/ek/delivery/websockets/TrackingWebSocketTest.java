package dk.ek.delivery.websockets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The test suite of the WebSockets API: positive and negative tests.
// It starts the real API on a random port and talks to it with a real WebSocket client.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrackingWebSocketTest {

    @LocalServerPort
    int port;

    private final ObjectMapper json = new ObjectMapper();

    // Every answer from the server is put in this queue, so the tests can read them in order.
    private final BlockingQueue<String> answers = new LinkedBlockingQueue<>();

    // Opens a WebSocket connection. "origin" may be null (no Origin header, like Postman).
    private WebSocketSession connect(String origin) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (origin != null) {
            headers.setOrigin(origin);
        }
        TextWebSocketHandler collector = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                answers.add(message.getPayload());
            }
        };
        return new StandardWebSocketClient()
                .execute(collector, headers, URI.create("ws://localhost:" + port + "/ws/tracking"))
                .get(5, TimeUnit.SECONDS);
    }

    // Sends one text frame and waits for the server's answer.
    private JsonNode sendAndReceive(WebSocketSession session, String message) throws Exception {
        session.sendMessage(new TextMessage(message));
        return json.readTree(answers.poll(5, TimeUnit.SECONDS));
    }

    // ---------- Unary operation: getDeliveryStatus ----------

    @Test
    void getDeliveryStatus_returnsTheDelivery() throws Exception {
        try (WebSocketSession session = connect(null)) {
            JsonNode answer = sendAndReceive(session, "{\"action\":\"getDeliveryStatus\",\"deliveryId\":2}");

            assertEquals("deliveryStatus", answer.get("type").asText());
            assertEquals("Bo Nielsen", answer.get("recipient").asText());
            assertEquals("PENDING", answer.get("status").asText());
        }
    }

    @Test
    void getDeliveryStatus_unknownId_returnsNotFound() throws Exception {
        try (WebSocketSession session = connect(null)) {
            JsonNode answer = sendAndReceive(session, "{\"action\":\"getDeliveryStatus\",\"deliveryId\":999}");

            assertEquals("error", answer.get("type").asText());
            assertEquals("NOT_FOUND", answer.get("code").asText());
        }
    }

    // ---------- Bidirectional streaming operation: trackDelivery ----------

    @Test
    void trackDelivery_streamsSeveralUpdatesOnOneConnection() throws Exception {
        try (WebSocketSession session = connect(null)) {
            // Far away -> IN_TRANSIT
            JsonNode first = sendAndReceive(session,
                    "{\"action\":\"trackDelivery\",\"deliveryId\":1,\"lat\":55.6761,\"lng\":12.5683}");
            assertEquals("trackingUpdate", first.get("type").asText());
            assertEquals("IN_TRANSIT", first.get("status").asText());
            assertTrue(first.get("distanceKm").asDouble() > 1.0);

            // Less than 1 km from the destination -> ARRIVING
            JsonNode second = sendAndReceive(session,
                    "{\"action\":\"trackDelivery\",\"deliveryId\":1,\"lat\":55.6950,\"lng\":12.5500}");
            assertEquals("ARRIVING", second.get("status").asText());

            // At the destination -> DELIVERED
            JsonNode third = sendAndReceive(session,
                    "{\"action\":\"trackDelivery\",\"deliveryId\":1,\"lat\":55.6900,\"lng\":12.5500}");
            assertEquals("DELIVERED", third.get("status").asText());
            assertEquals(0.0, third.get("distanceKm").asDouble());
        }
    }

    @Test
    void trackDelivery_invalidCoordinates_returnsInvalidArgument() throws Exception {
        try (WebSocketSession session = connect(null)) {
            JsonNode answer = sendAndReceive(session,
                    "{\"action\":\"trackDelivery\",\"deliveryId\":2,\"lat\":200,\"lng\":12.55}");

            assertEquals("error", answer.get("type").asText());
            assertEquals("INVALID_ARGUMENT", answer.get("code").asText());
        }
    }

    @Test
    void trackDelivery_alreadyDelivered_returnsInvalidArgument() throws Exception {
        try (WebSocketSession session = connect(null)) {
            JsonNode answer = sendAndReceive(session,
                    "{\"action\":\"trackDelivery\",\"deliveryId\":3,\"lat\":55.39,\"lng\":10.38}");

            assertEquals("INVALID_ARGUMENT", answer.get("code").asText());
        }
    }

    // ---------- Bad messages and security ----------

    @Test
    void invalidJson_returnsError_andConnectionStaysOpen() throws Exception {
        try (WebSocketSession session = connect(null)) {
            JsonNode error = sendAndReceive(session, "this is <script>not</script> json");
            assertEquals("INVALID_ARGUMENT", error.get("code").asText());
            // XSS: the client's text is not echoed back.
            assertTrue(!error.get("message").asText().contains("<script>"));

            // The same connection still works after the error.
            JsonNode ok = sendAndReceive(session, "{\"action\":\"getDeliveryStatus\",\"deliveryId\":2}");
            assertEquals("deliveryStatus", ok.get("type").asText());
        }
    }

    @Test
    void unknownAction_returnsInvalidArgument() throws Exception {
        try (WebSocketSession session = connect(null)) {
            JsonNode answer = sendAndReceive(session, "{\"action\":\"deleteEverything\"}");

            assertEquals("INVALID_ARGUMENT", answer.get("code").asText());
        }
    }

    @Test
    void handshakeFromForeignOrigin_isRejected() {
        // A page on another website tries to connect -> the handshake fails (403 Forbidden).
        assertThrows(ExecutionException.class, () -> connect("http://evil.example"));
    }
}
