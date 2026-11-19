# WebSockets API – live delivery tracking

- [x] Implement the same unary and bidirectional streaming operations as the [gRPC API](../grpc/README.md).
- [x] Provide a test suite in Postman or a similar tool with positive and negative cases.

See the [shared exam requirements](../README.md#shared-requirements).

---

## 1. Scenario

A courier drives a parcel to a customer. The courier's app keeps one WebSocket connection open to the server.

| Operation | Same as in gRPC | What it does |
|---|---|---|
| `getDeliveryStatus` | unary RPC `GetDeliveryStatus` | One question, one answer: "what is the status of delivery 1?" |
| `trackDelivery` | bidirectional streaming RPC `TrackDelivery` | The courier sends GPS positions again and again. The server stores each position and answers each one with the new status and the distance left. |

The status is decided by the distance to the destination:

| Distance to destination | Status |
|---|---|
| 1 km or more | `IN_TRANSIT` |
| less than 1 km | `ARRIVING` |
| less than 50 m | `DELIVERED` |

## 2. How to run

```bash
cd websockets
mvn spring-boot:run
```

- Endpoint: `ws://localhost:8085/ws/tracking`
- Database: H2 file `./data/websockets` (user `sa`, empty password). It is recreated on every start, so the data is always the same 3 deliveries.

| id | Recipient | Status at start |
|---|---|---|
| 1 | Anna Jensen | `IN_TRANSIT` |
| 2 | Bo Nielsen | `PENDING` |
| 3 | Clara Hansen | `DELIVERED` |

## 3. File map

| File | Job |
|---|---|
| `WebSocketsApiApplication.java` | Starts the application. |
| `WebSocketConfig.java` | Connects the URL `/ws/tracking` to the handler and sets the allowed origin. |
| `TrackingHandler.java` | **The WebSocket part.** Receives every message, picks the operation, sends the answer. |
| `TrackingService.java` | **The business part.** SQL + rules. Knows nothing about WebSockets. |
| `Delivery.java`, `TrackingUpdate.java` | The data that is sent back as JSON. |
| `Geo.java` | Calculates the distance between two GPS points. |
| `NotFoundException.java` | Thrown when a delivery id does not exist. |
| `schema.sql`, `data.sql` | Tables and example data. |
| `src/test/.../TrackingWebSocketTest.java` | The test suite (positive and negative). |

## 4. How it works

### The message format

WebSockets only move frames (text or bytes). There is no built-in idea of "operations" like in gRPC, so we made our own small protocol: every message is a JSON object, and the `action` field says which operation the client wants.

**Client → server**

```json
{"action": "getDeliveryStatus", "deliveryId": 1}
{"action": "trackDelivery", "deliveryId": 1, "lat": 55.68, "lng": 12.56}
```

**Server → client**

```json
{"type": "deliveryStatus", "id": 1, "recipient": "Anna Jensen", "address": "...", "destLat": 55.69, "destLng": 12.55, "status": "IN_TRANSIT", "lastLat": 55.6761, "lastLng": 12.5683, "updatedAt": "..."}
{"type": "trackingUpdate", "deliveryId": 1, "status": "IN_TRANSIT", "distanceKm": 1.28, "message": "The courier is 1.28 km away"}
{"type": "error", "code": "NOT_FOUND", "message": "Delivery 999 not found"}
```

Error codes: `NOT_FOUND` (unknown delivery) and `INVALID_ARGUMENT` (bad JSON, unknown action, bad coordinates, delivery already delivered). The names are the same as the gRPC status codes.

### The flow of one message

1. **Handshake** – the client sends a normal HTTP `GET /ws/tracking` with `Upgrade: websocket`. Spring checks the `Origin` header (`WebSocketConfig.java`) and answers `101 Switching Protocols`. From now on the TCP connection is a WebSocket.
2. **Message arrives** – Spring calls `handleTextMessage` in `TrackingHandler.java`.
3. **Parse** – the text is parsed as JSON and the `action` field is read.
4. **Dispatch** – an `if / else if` calls `getDeliveryStatus(...)` or `trackDelivery(...)`.
5. **Business logic** – `TrackingService.java` reads or writes the database.
6. **Answer** – the result is turned into JSON and sent with `session.sendMessage(...)`.
7. **Errors** – exceptions are caught in `handleTextMessage` and sent as an `error` message. The connection stays open.

### Unary: `getDeliveryStatus`

`TrackingHandler.getDeliveryStatus` → `TrackingService.getStatus(id)` → `SELECT * FROM deliveries WHERE id = ?` → one `deliveryStatus` message.

### Bidirectional streaming: `trackDelivery`

`TrackingHandler.trackDelivery` → `TrackingService.recordLocation(id, lat, lng)`:

1. Validate the coordinates.
2. Check that the delivery exists and is not delivered.
3. `INSERT` the position into `location_updates`.
4. Calculate the distance to the destination (`Geo.distanceKm`) and choose the status.
5. `UPDATE` the delivery with the new status and position.

The "stream" is simply the open connection: the client can send as many `trackDelivery` messages as it wants, and the server sends one `trackingUpdate` back for each. Both sides can send at any time – that is what bidirectional means.

## 5. Requirement → where in the code

| Requirement | Where |
|---|---|
| Unary operation (same as gRPC) | `TrackingHandler.getDeliveryStatus` |
| Bidirectional streaming operation (same as gRPC) | `TrackingHandler.trackDelivery` |
| Interacts with database-stored information | `TrackingService.java` (`SELECT`, `INSERT`, `UPDATE`) |
| Prevents SQL injection | `TrackingService.java` – only `?` parameters |
| Prevents XSS | `TrackingHandler.java` – typed input, client text is never echoed |
| Prevents CSRF | `WebSocketConfig.java` – `setAllowedOrigins(...)` |
| Test suite, positive and negative | `src/test/.../TrackingWebSocketTest.java` + Postman steps below |

## 6. Security

### SQL injection

All SQL is in `TrackingService.java` and uses **prepared statements**: the SQL text has `?` placeholders and the values are sent separately with `.param(...)` / `.params(...)`. The database never treats a value as SQL code. On top of that, the only inputs are numbers (`deliveryId`, `lat`, `lng`), and the handler rejects anything that is not a number.

### XSS

- The API only accepts numbers from the client. No client text is ever stored in the database.
- Error messages are fixed texts written by us. The client's raw message is **never copied into an answer**, so a `<script>` in a bad message cannot come back to a browser (see the test `invalidJson_returnsError_andConnectionStaysOpen`).
- Answers are JSON, built with Jackson, which escapes special characters.

### CSRF (cross-site WebSocket hijacking)

The Same-Origin Policy and CORS do **not** apply to WebSockets: any web page can try to open a WebSocket to any server. An evil page could open a connection from the victim's browser. This attack is called *cross-site WebSocket hijacking* – it is CSRF for WebSockets.

The defence is to check the `Origin` header in the handshake. Browsers always send it and JavaScript cannot fake it. In `WebSocketConfig.java`:

```java
.setAllowedOrigins("http://localhost:8085");
```

- Handshake from our own site → `101 Switching Protocols`.
- Handshake from another site (for example `http://evil.example`) → `403 Forbidden`.
- No `Origin` header (Postman, the courier's native app) → accepted. Such clients are not browsers, so they cannot be used for CSRF.

Also, the API uses no cookies, so there is no login for an attacker to "borrow".

## 7. Tests

### Automated test suite (JUnit)

```bash
cd websockets
mvn test
```

The tests start the real API on a random port and use a real WebSocket client.

| Test | Kind | Expects |
|---|---|---|
| `getDeliveryStatus_returnsTheDelivery` | positive | `deliveryStatus` for delivery 2 |
| `getDeliveryStatus_unknownId_returnsNotFound` | negative | `error` / `NOT_FOUND` |
| `trackDelivery_streamsSeveralUpdatesOnOneConnection` | positive | 3 updates on one connection: `IN_TRANSIT` → `ARRIVING` → `DELIVERED` |
| `trackDelivery_invalidCoordinates_returnsInvalidArgument` | negative | `error` / `INVALID_ARGUMENT` |
| `trackDelivery_alreadyDelivered_returnsInvalidArgument` | negative | `error` / `INVALID_ARGUMENT` |
| `invalidJson_returnsError_andConnectionStaysOpen` | negative | error, no echo of the input, next message still works |
| `unknownAction_returnsInvalidArgument` | negative | `error` / `INVALID_ARGUMENT` |
| `handshakeFromForeignOrigin_isRejected` | negative (security) | handshake fails |

### Postman (manual)

Postman cannot export WebSocket requests in a collection file. Do the steps below and **take a screenshot of each step for the PDF document** in the hand-in.

1. Start the API (`mvn spring-boot:run`). Restart it before a test run so the data is fresh.
2. In Postman: **New → WebSocket**. URL: `ws://localhost:8085/ws/tracking`. Click **Connect**. Expected: "Connected".
3. Send each message below (message type **JSON** or **Text**) and compare with the expected answer.

| # | Kind | Send | Expected answer |
|---|---|---|---|
| 1 | positive, unary | `{"action":"getDeliveryStatus","deliveryId":1}` | `"type":"deliveryStatus"`, `"status":"IN_TRANSIT"` |
| 2 | negative, unary | `{"action":"getDeliveryStatus","deliveryId":999}` | `"type":"error"`, `"code":"NOT_FOUND"` |
| 3 | positive, stream | `{"action":"trackDelivery","deliveryId":1,"lat":55.6761,"lng":12.5683}` | `"status":"IN_TRANSIT"`, `"distanceKm":1.92` |
| 4 | positive, stream | `{"action":"trackDelivery","deliveryId":1,"lat":55.6950,"lng":12.5500}` | `"status":"ARRIVING"`, `"distanceKm":0.56` |
| 5 | positive, stream | `{"action":"trackDelivery","deliveryId":1,"lat":55.6900,"lng":12.5500}` | `"status":"DELIVERED"`, `"distanceKm":0.0` |
| 6 | negative, stream | `{"action":"trackDelivery","deliveryId":1,"lat":55.69,"lng":12.55}` | `"code":"INVALID_ARGUMENT"` (already delivered) |
| 7 | negative, stream | `{"action":"trackDelivery","deliveryId":2,"lat":200,"lng":12.55}` | `"code":"INVALID_ARGUMENT"` (bad coordinates) |
| 8 | negative | `hello <script>` | `"code":"INVALID_ARGUMENT"`, "The message is not valid JSON" |
| 9 | negative | `{"action":"deleteEverything"}` | `"code":"INVALID_ARGUMENT"` |
| 10 | negative, security | Disconnect. Add the header `Origin: http://evil.example` and click **Connect**. | Connection fails with `403` |

Note that steps 3–5 use **the same open connection** – that is the stream.

## 8. Exam questions

### Explain the WebSockets implementation of the gRPC API's unary RPC. Compare both implementations.

Show `TrackingHandler.handleTextMessage` and `getDeliveryStatus`.

The client sends `{"action":"getDeliveryStatus","deliveryId":1}`. The handler parses the JSON, sees the action, calls `TrackingService.getStatus`, and sends one `deliveryStatus` message back.

| | gRPC | WebSockets |
|---|---|---|
| Contract | `.proto` file. Client and server code is generated from it. | No contract. We invented a JSON format with an `action` field and documented it in this README. |
| Choosing the operation | The method name is part of the HTTP/2 request. The generated code calls the right Java method. | We read `action` ourselves and use `if / else`. |
| Request ↔ response | Built in: one call = one request + one response. | Not built in. WebSockets only know "messages". The client must assume that the next message is the answer (a real system would add a request id). |
| Data format | Binary Protobuf: small, fast, typed. | JSON text: bigger, but readable in Postman and usable from a browser. |
| Types / validation | Types come from the `.proto` file. | We check by hand (`isIntegralNumber()`, `isNumber()`). |
| Errors | Status codes (`NOT_FOUND`, `INVALID_ARGUMENT`) through `responseObserver.onError`. | Our own `error` message with a `code` field. We reused the gRPC names. |
| Transport | HTTP/2. | One TCP connection, opened with an HTTP/1.1 upgrade. |

The business logic (`TrackingService`) is the same in both APIs. Only the transport layer differs.

### Explain the WebSockets implementation of the gRPC API's bidirectional streaming RPC. Compare both implementations.

Show `TrackingHandler.trackDelivery` and `TrackingService.recordLocation`.

A WebSocket connection is bidirectional by nature: both sides can send at any time. So the "stream" is just the open connection. The courier sends many `trackDelivery` messages; the server stores each position and answers each with a `trackingUpdate`.

| | gRPC | WebSockets |
|---|---|---|
| The stream | Declared in the contract: `rpc TrackDelivery (stream LocationUpdate) returns (stream TrackingUpdate)`. One stream = one call of one method. | The whole connection is the stream. All operations share it. |
| Server code | A `StreamObserver`: `onNext` (a message came), `onError`, `onCompleted`. | `handleTextMessage` is called for each message. |
| End of stream | Explicit: the client calls `onCompleted`, the server answers `onCompleted`. | No "end of stream". The client just stops sending or closes the connection. |
| After an error | `onError` **ends the stream**. The client must start a new call. | We send an `error` message and **the connection stays open**. |
| Many streams at once | HTTP/2 multiplexing: many calls on one connection. | One connection = one channel. Multiplexing must be built by hand. |
| Clients | Needs generated stubs. Browsers cannot use it directly (need gRPC-Web). | Works in every browser (`new WebSocket(url)`). |

### Explain the differences between constant polling, long polling, and WebSockets

- **Constant (short) polling** – the client asks "anything new?" every few seconds with a normal HTTP request. The server answers at once, usually "no". Simple, but most requests are wasted, and news arrives with a delay (up to one interval).
- **Long polling** – the client asks, and the server **holds the request open** until there is news (or a timeout). Then the client asks again right away. Fewer wasted requests and almost real-time, but every message still costs a full HTTP request with headers, and the server must hold many open requests.
- **WebSockets** – one handshake, then **one connection that stays open**. Both sides can send whenever they want, with very small overhead per message. Real-time and efficient, but the server must keep state for each connection, which makes scaling and load balancing harder.

Polling is client → server only (the server can only answer). WebSockets are truly two-way.

### Explain what WebSockets consist of, including use cases, advantages and disadvantages

**What it consists of**

- A protocol (RFC 6455) on top of TCP. URLs start with `ws://` or `wss://` (with TLS).
- **Handshake**: it starts as a normal HTTP/1.1 `GET` with the headers `Upgrade: websocket`, `Connection: Upgrade` and `Sec-WebSocket-Key`. The server answers `101 Switching Protocols` with `Sec-WebSocket-Accept`. Because it starts as HTTP, it uses ports 80/443 and passes most firewalls.
- **Frames**: after the handshake, data is sent in small frames: text frames, binary frames, and control frames (`ping`, `pong`, `close`). A frame has only a few bytes of header – no HTTP headers per message.
- **Full-duplex**: both sides can send at the same time, on one long-lived connection.
- In the browser: `new WebSocket(url)` with the events `onopen`, `onmessage`, `onerror`, `onclose` and the method `send()`.

**Use cases**: chat, live tracking (this project), multiplayer games, collaborative editing, live dashboards, trading prices – anything where both sides send often and delay matters.

**Advantages**

- Real-time, two-way communication.
- Very low overhead per message.
- The server can push without being asked.
- Supported by all browsers; text and binary data.

**Disadvantages**

- Stateful: every client holds a connection open. That costs memory and makes horizontal scaling harder (sticky sessions or a shared message broker).
- No built-in request/response, status codes, contract, or caching – you design your own message protocol.
- No automatic reconnect (SSE has that built in).
- Same-Origin Policy/CORS do not apply, so you must check the `Origin` header yourself (cross-site WebSocket hijacking).
- Some proxies close idle connections, so you need ping/pong (heartbeats).
- Overkill if only the server sends data – then SSE is simpler.
