# Server-Sent Events (SSE) – live delivery tracking

- [x] Implement an SSE stream related to the business scenario of the [gRPC API](../grpc/README.md).
- [x] Provide a test suite in Postman or a similar tool.

See the [shared exam requirements](../README.md#shared-requirements).

---

## 1. The scenario

The gRPC API is used by the **courier**: the courier app sends GPS positions and the server
answers with the status of the delivery.

This SSE API is the other side of the same business situation: the **customer** who waits for
the parcel. The customer's browser opens one stream and the server *pushes* an event every time
the courier moves. The customer never sends anything on the stream – it only listens.

The business rules are the same as in the gRPC and WebSockets APIs (`TrackingService.java`):

| Distance to destination | Status       |
|-------------------------|--------------|
| 1 km or more            | `IN_TRANSIT` |
| less than 1 km          | `ARRIVING`   |
| less than 50 m          | `DELIVERED`  |

The API is independent: it has its own database (`./data/sse`, H2) with the same two tables,
`deliveries` and `location_updates`.

## 2. How to run

```bash
cd server-sent-events
mvn spring-boot:run
```

- Demo page: <http://localhost:8086/>
- Stream: `GET http://localhost:8086/api/v1/deliveries/1/events`
- Create an event: `POST http://localhost:8086/api/v1/deliveries/1/locations` with `{"lat": 55.68, "lng": 12.56}`

The database is reset on every start. Seed data: delivery 1 = `IN_TRANSIT`, 2 = `PENDING`, 3 = `DELIVERED`.

## 3. File map

| File | What it does |
|------|--------------|
| `pom.xml` | Dependencies: Spring Web (contains `SseEmitter`), JDBC, H2 |
| `src/main/resources/application.properties` | Port 8086, database URL |
| `src/main/resources/schema.sql`, `data.sql` | Tables and seed data |
| `SseApiApplication.java` | Starts the application |
| `DeliveryEventController.java` | The two endpoints: the SSE stream (GET) and the location report (POST) |
| `EventPublisher.java` | Remembers the open streams and pushes events to them |
| `TrackingService.java` | Business logic + all SQL |
| `Geo.java` | Distance between two GPS points |
| `Delivery.java`, `TrackingUpdate.java`, `LocationRequest.java` | Data records |
| `NotFoundException.java` | "Delivery does not exist" |
| `src/main/resources/static/index.html` | Browser demo of the `EventSource` JavaScript interface |
| `postman/sse.postman_collection.json` | Test suite |

## 4. How it works

### 4.1 Opening the stream – `DeliveryEventController.events()`

1. The client sends a normal HTTP GET: `GET /api/v1/deliveries/1/events`.
2. `tracking.getStatus(id)` reads the delivery from the database (404 if it does not exist).
3. We create an `SseEmitter`. This object **is** the open connection. Spring answers with
   `Content-Type: text/event-stream` and does *not* close the response.
4. The first event is always `status`: the current state from the database.
5. If the client sent the header `Last-Event-ID`, we replay the events it missed
   (`tracking.findUpdatesAfter`).
6. If the delivery is already `DELIVERED` we close the stream (`emitter.complete()`).
   Otherwise `publisher.subscribe(id, emitter)` remembers the connection.

### 4.2 Creating an event – `DeliveryEventController.reportLocation()`

1. The courier side sends `POST /api/v1/deliveries/1/locations` with `{"lat": ..., "lng": ...}`.
2. `tracking.recordLocation()` validates the input, inserts a row in `location_updates`,
   calculates distance + status and updates the `deliveries` row.
3. `publisher.publish(update)` loops over all open streams of that delivery and calls
   `emitter.send(...)`. Every connected customer gets the event at once.
4. If the new status is `DELIVERED`, an extra `delivered` event is sent and the stream is closed.

### 4.3 Remembering the connections – `EventPublisher`

- `Map<Long, List<SseEmitter>>`: delivery id → the clients watching it.
- `ConcurrentHashMap` + `CopyOnWriteArrayList` because many requests (threads) use the map at the same time.
- `onCompletion`, `onTimeout`, `onError` remove a connection from the list when it ends.

### 4.4 The wire format

SSE is plain text over HTTP. Each event is a few `field:value` lines and ends with a **blank line**:

```
event:status
data:{"id":1,"recipient":"Anna Jensen","status":"IN_TRANSIT", ...}

id:1
event:location
data:{"id":1,"deliveryId":1,"status":"IN_TRANSIT","distanceKm":1.28,"message":"The courier is 1.28 km away"}

id:2
event:location
data:{"id":2,"deliveryId":1,"status":"ARRIVING","distanceKm":0.08,"message":"The courier is less than 1 km away"}

event:delivered
data:{"id":3,"deliveryId":1,"status":"DELIVERED","distanceKm":0.0,"message":"The courier has arrived. Delivery completed"}
```

| Field | Meaning | Where we set it |
|-------|---------|-----------------|
| `event:` | The name (type) of the event. Without it the type is `message`. | `SseEmitter.event().name("location")` |
| `id:` | The id of the event. The browser remembers the last one. | `.id(...)` = the id of the row in `location_updates` |
| `data:` | The payload. We send JSON, but it is just text. | `.data(update, APPLICATION_JSON)` |
| `retry:` | How many ms the browser waits before reconnecting (not used here; default ≈ 3 s). | – |
| blank line | End of the event. | added by Spring |

### 4.5 Reconnect with `Last-Event-ID`

If the connection is lost, the browser reconnects **by itself** and sends the header
`Last-Event-ID: <last id it saw>`. Our event ids are database ids, so the server can run
`SELECT ... WHERE delivery_id = ? AND id > ?` and replay exactly the missed events.
No event is lost. Try it:

```bash
curl -N -H "Last-Event-ID: 1" http://localhost:8086/api/v1/deliveries/1/events
```

## 5. Requirements → code

| Requirement | Where |
|-------------|-------|
| SSE implementation | `DeliveryEventController.events()` (`produces = text/event-stream`, returns `SseEmitter`) + `EventPublisher` |
| Related to the gRPC business scenario | Same deliveries, same rules, same `TrackingService` logic: gRPC = courier sends positions, SSE = customer watches them |
| Interacts with a database | `TrackingService` (reads `deliveries`, inserts `location_updates`, replays from `location_updates`) |
| Prevent SQL injection | `TrackingService`: only `?` parameters |
| Prevent XSS | Only numbers are accepted as input; JSON output; `index.html` uses `textContent` |
| Prevent CSRF | `consumes = application/json` on the POST, no cookies, no CORS |
| Test suite | `postman/sse.postman_collection.json` |

## 6. Security

**SQL injection.** All SQL is in `TrackingService` and uses prepared statements: the SQL text is
fixed and the values are sent separately as `?` parameters, so input can never become SQL.
The path variable is a `long` and the body fields are `Double` – text like `1 OR 1=1` is rejected
with 400 before it reaches any SQL.

**XSS.** The API takes no free text from clients – only numbers (`id`, `lat`, `lng`).
Responses are JSON (`application/json` or JSON inside `text/event-stream`), never HTML.
The demo page writes everything with `textContent`, never `innerHTML`, so even if a text
contained `<script>`, the browser would show it as text and not run it.

**CSRF.** A CSRF attack makes the victim's browser send a request *with the victim's cookies*.
- This API uses **no cookies and no sessions**, so there is nothing for an attacker to ride on.
- The only state-changing endpoint (the POST) accepts **only `application/json`** (`consumes = ...`,
  otherwise 415). An HTML form on another site can only send `application/x-www-form-urlencoded`,
  `multipart/form-data` or `text/plain`. JavaScript on another site *can* try to send JSON, but then
  the browser first sends a CORS preflight (`OPTIONS`). **CORS is not enabled**, so the preflight
  fails and the real request is never sent.
- The GET stream only reads data (safe method).

## 7. Tests

### Postman – `postman/sse.postman_collection.json`

Import the collection and run folders 1 and 2 (restart the API first to reset the data).

| Request | Expected |
|---------|----------|
| Stream of delivered delivery 3 | 200, `text/event-stream`, body contains `event:status`, stream closes |
| Stream of unknown delivery | 404 |
| Report location – in transit | 201, `IN_TRANSIT` |
| Report location – near destination | 201, `ARRIVING` |
| Invalid latitude | 400 |
| Missing `lng` | 400 |
| Unknown delivery | 404 |
| Delivered delivery | 409 |
| Body as `text/plain` | 415 |

Folder 3 holds the **live stream** request for the demo. It never ends, so do not include it in
the collection runner. Send it in one tab (Postman shows the events as they arrive) and send the
POST requests from another tab.

### Demo page – <http://localhost:8086/>

Click **Connect**, then **Send location**. Try `55.6895 / 12.5510` (arriving) and
`55.6900 / 12.5500` (delivered – the stream closes).

### curl

```bash
# Terminal 1: -N = do not buffer, show events as they arrive
curl -N http://localhost:8086/api/v1/deliveries/1/events

# Terminal 2
curl -X POST http://localhost:8086/api/v1/deliveries/1/locations \
     -H "Content-Type: application/json" -d '{"lat":55.68,"lng":12.56}'
```

## 8. Exam questions

### Explain the SSE implementation

- **Endpoint:** `DeliveryEventController.events()` – a normal `@GetMapping` with
  `produces = "text/event-stream"` that returns an `SseEmitter`. Returning an `SseEmitter` tells
  Spring to keep the HTTP response open.
- **Sending:** `emitter.send(SseEmitter.event().id(..).name(..).data(..))` writes one event in the
  text format `id:` / `event:` / `data:` + blank line.
- **Who gets what:** `EventPublisher` keeps a thread-safe map of delivery id → open emitters.
  `publish()` is called from the POST endpoint after the position is stored in the database.
- **Lifecycle:** first event = current status from the DB; then live `location` events; a final
  `delivered` event and `emitter.complete()`. Connections are removed in
  `onCompletion` / `onTimeout` / `onError`.
- **Reconnect:** event id = database id, so `Last-Event-ID` lets us replay missed events from the DB.

### Compare SSE with gRPC and WebSockets

| | SSE | WebSockets | gRPC |
|---|---|---|---|
| Direction | **Server → client only** (client uses normal HTTP requests to send) | Two-way (full duplex) | Unary, server-stream, client-stream, two-way stream |
| Protocol | Plain HTTP (a long response). Works with HTTP/1.1 and HTTP/2 | Starts as HTTP, then **upgrades** to the WebSocket protocol (`ws://`) on the same TCP connection | **HTTP/2** |
| Data format | Text only (UTF-8), usually JSON in `data:` | Text or binary frames, any format you choose | Binary **Protocol Buffers** |
| Contract | None (just conventions) | None (you design your own messages) | Strict `.proto` file, generated code |
| Reconnect | **Built in**: automatic retry + `Last-Event-ID` | You must write it yourself | You must write it yourself (retry policies exist) |
| Browser support | Native `EventSource` | Native `WebSocket` | Not directly – needs gRPC-Web and a proxy |
| Proxies / firewalls | Easy – it is ordinary HTTP | Sometimes blocked (needs upgrade support) | Needs HTTP/2 end to end |
| Typical use | Notifications, live feeds, dashboards, progress | Chat, games, collaborative editing | Service-to-service calls, mobile backends |

In our project: the **courier** needs to *send* a stream of positions and get answers → gRPC
bidirectional streaming (or WebSockets). The **customer** only needs to *receive* updates → SSE is
enough, and it is the simplest: one GET endpoint, no special protocol, reconnect for free.

Limits of SSE: one direction only, text only, and with HTTP/1.1 a browser allows only about
6 open connections per domain (not a problem with HTTP/2).

### Conceptual: What does SSE consist of? Use cases? The EventSource interface?

**What it consists of**

- A normal HTTP GET request where the client says `Accept: text/event-stream`.
- A response with `Content-Type: text/event-stream` that the server **keeps open**.
- A simple text format: events are separated by a blank line; each has the fields
  `data:` (payload), `event:` (name), `id:` (event id) and `retry:` (reconnect delay in ms).
  A line that starts with `:` is a comment (often used as a keep-alive "heartbeat").
- Automatic reconnection by the browser, with the `Last-Event-ID` header.
- One direction: server → client. It is a W3C/WHATWG standard (part of HTML5).

**Use cases** – whenever the server has news and the client only listens: delivery/order tracking,
notifications, news and social feeds, stock tickers, live sports scores, dashboards and monitoring,
progress of a long job, and streaming text from AI chat models.

**The `EventSource` JavaScript interface** (see `static/index.html`)

```js
const source = new EventSource("/api/v1/deliveries/1/events"); // opens the connection

source.onopen = () => { ... };                        // connected
source.onmessage = (event) => { ... };                // events WITHOUT a name ("message")
source.addEventListener("location", (event) => {      // events WITH "event:location"
    const update = JSON.parse(event.data);            // event.data = text after "data:"
    console.log(event.lastEventId);                   // text after "id:"
});
source.onerror = () => { ... };                       // connection lost; the browser retries by itself
source.close();                                       // stop for good (no more reconnects)
```

- `readyState`: `0 CONNECTING`, `1 OPEN`, `2 CLOSED`.
- `new EventSource(url, { withCredentials: true })` sends cookies to another origin (CORS).
- Limits: only GET, no custom request headers (so no `Authorization` header – use cookies or a
  query parameter), text only.
- Important: when the server closes the stream, `EventSource` **reconnects automatically**.
  That is why our page calls `source.close()` when it gets the `delivered` event.
