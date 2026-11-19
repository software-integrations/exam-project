# gRPC API – Live delivery tracking

- [x] At least one unary RPC and one bidirectional streaming RPC
- [x] Protobuf generation
- [x] Same operations as the [WebSockets API](../websockets/README.md), same business scenario as the [SSE API](../server-sent-events/README.md)
- [x] Test suite with positive and negative cases
- [x] Uses a database, prevents SQL injection, XSS and CSRF

## 1. The scenario

A courier drives a parcel to a customer. The courier's app sends its GPS position again and again.
The server stores every position in the database and answers with the distance left and the new status.

| RPC | Type | What it does |
|---|---|---|
| `GetDeliveryStatus` | Unary (1 request → 1 response) | Reads one delivery from the database |
| `TrackDelivery` | Bidirectional streaming (many → many) | The courier streams positions; the server streams back tracking updates |

Status rules (in `TrackingService.recordLocation`):

| Distance to destination | Status |
|---|---|
| 1 km or more | `IN_TRANSIT` |
| less than 1 km | `ARRIVING` |
| less than 50 m | `DELIVERED` |

Seed data (reset on every start):

| id | Recipient | Destination (lat, lng) | Status |
|---|---|---|---|
| 1 | Anna Jensen | 55.6900, 12.5500 | IN_TRANSIT |
| 2 | Bo Nielsen | 56.1520, 10.2030 | PENDING |
| 3 | Clara Hansen | 55.3959, 10.3883 | DELIVERED |

## 2. How to run

```bash
cd grpc
mvn spring-boot:run        # starts the gRPC server on localhost:9090
mvn test                   # runs the test suite
```

No Docker is needed. The database is an H2 file in `grpc/data/` (JDBC URL `jdbc:h2:file:./data/grpc`, user `sa`, empty password).
`mvn` also generates the Java classes from the proto file, so always build with Maven before opening the code in the IDE.

## 3. File map

| File | What it is |
|---|---|
| `pom.xml` | Dependencies + the `protobuf-maven-plugin` that generates Java code from the proto file |
| `src/main/proto/tracking.proto` | **The contract**: service, RPCs and messages |
| `target/generated-sources/protobuf/` | The generated Java classes (not written by us) |
| `GrpcApiApplication.java` | Starts Spring |
| `GrpcServer.java` | Starts the gRPC server on port 9090 and registers our service |
| `TrackingGrpcService.java` | **The gRPC layer**: implements the unary and the streaming RPC |
| `TrackingService.java` | Business logic + SQL (the same class is used in the WebSockets and SSE APIs) |
| `Delivery.java`, `TrackingUpdate.java` | Plain data records |
| `Geo.java` | Distance between two GPS points |
| `NotFoundException.java` | "Delivery does not exist" |
| `schema.sql`, `data.sql` | Tables and seed data |
| `src/test/.../TrackingGrpcTest.java` | The test suite |

## 4. How it works

### 4.1 The proto file (`src/main/proto/tracking.proto`)

- `syntax = "proto3";` – the version of the Protobuf language.
- `package delivery.tracking.v1;` – the namespace. The full service name is `delivery.tracking.v1.TrackingService`. The `v1` is the API version.
- `option java_package` / `java_multiple_files` – only tell the Java generator where to put the classes.
- `service TrackingService { rpc ... }` – the operations. The word `stream` in front of a message means "many of these". `stream` on both sides = bidirectional streaming.
- `message ... { int64 delivery_id = 1; }` – a data structure. Each field has a **type**, a **name** and a **tag number**.
  The tag number (not the name) is what is sent on the wire, so the messages are small and binary. Tags must never be reused or changed.
- `optional double last_lat` – the field may be missing (a delivery with no position yet). The generated code gets a `hasLastLat()` method.

### 4.2 Protobuf generation (`pom.xml`)

1. `os-maven-plugin` finds out the operating system (for example `osx-aarch_64`).
2. `protobuf-maven-plugin` downloads the matching `protoc` compiler and the `protoc-gen-grpc-java` plugin.
3. Goal `compile` generates the message classes (`LocationUpdate`, `TrackingUpdate`, ...).
4. Goal `compile-custom` generates `TrackingServiceGrpc`: the server base class (`TrackingServiceImplBase`) and the client stubs.

We never edit generated code. We only **extend** `TrackingServiceImplBase`.

### 4.3 Starting the server (`GrpcServer.java`)

`ServerBuilder.forPort(9090).addService(trackingGrpcService).addService(ProtoReflectionService.newInstance()).build().start()`

- `addService(trackingGrpcService)` registers our implementation.
- `ProtoReflectionService` is server reflection: a client like Postman can ask the server for its contract.
- `@PostConstruct` / `@PreDestroy` start and stop the server together with Spring.

### 4.4 The unary RPC (`TrackingGrpcService.getDeliveryStatus`)

1. gRPC calls the method with the request message and a `StreamObserver` for the response.
2. `tracking.getStatus(id)` runs `SELECT * FROM deliveries WHERE id = ?`.
3. We build a `DeliveryStatusResponse` with the generated builder.
4. `response.onNext(...)` sends the one response, `response.onCompleted()` closes the call.
5. On an exception: `response.onError(...)` closes the call with a status code (`NOT_FOUND`).

### 4.5 The bidirectional streaming RPC (`TrackingGrpcService.trackDelivery`)

The method looks different: it **receives** the stream to write to (`responses`) and **returns** a `StreamObserver` that gets the client's messages.

- `onNext(location)` – runs for every message from the client. It calls `tracking.recordLocation(...)`, which
  inserts into `location_updates`, calculates the distance, and updates `deliveries`. Then `responses.onNext(...)` sends the answer.
- `onCompleted()` – the client is done; we close our side with `responses.onCompleted()`.
- `onError(...)` – the client disconnected.
- If our code throws, `responses.onError(...)` ends the **whole stream** with a status code. That is how gRPC reports errors: a call ends with exactly one status.

Both sides can send at any time on one HTTP/2 stream. Here the server answers each message, but it does not have to wait for the client.

### 4.6 Errors (`TrackingGrpcService.toGrpcError`)

| Java exception | gRPC status | When |
|---|---|---|
| `NotFoundException` | `NOT_FOUND` | unknown delivery id |
| `IllegalArgumentException` | `INVALID_ARGUMENT` | impossible coordinates, or delivery already delivered |
| anything else | `INTERNAL` | no details are leaked to the client |

## 5. Requirements → where in the code

| Requirement | Where |
|---|---|
| Unary RPC | `tracking.proto` (`GetDeliveryStatus`), `TrackingGrpcService.getDeliveryStatus` |
| Bidirectional streaming RPC | `tracking.proto` (`TrackDelivery`), `TrackingGrpcService.trackDelivery` |
| Protobuf generation | `pom.xml` (`protobuf-maven-plugin`), output in `target/generated-sources/protobuf` |
| Database-stored information | `TrackingService` (H2, tables in `schema.sql`) |
| SQL injection prevention | `TrackingService`: only `?` parameters |
| Tests, positive and negative | `TrackingGrpcTest` + the Postman steps below |
| Same operations as WebSockets | `websockets/` implements `getDeliveryStatus` and `trackDelivery` with the same `TrackingService` |
| Same scenario as SSE | `server-sent-events/` streams the tracking updates of a delivery |

## 6. Security

**SQL injection.** Every SQL statement in `TrackingService` uses `?` placeholders (`db.sql("... WHERE id = ?").param(id)`).
The database receives the SQL text and the values separately, so a value can never become SQL.
On top of that, Protobuf is strongly typed: `delivery_id` is an `int64` and `lat`/`lng` are `double`, so a client cannot even send text in those fields.

**XSS.** XSS needs a browser that renders text as HTML. This API sends binary Protobuf, not HTML, and the client only sends numbers.
The strings in the responses (recipient, address, messages) come from our own seed data and code, never from client input. Coordinates are validated (`-90..90`, `-180..180`).

**CSRF.** CSRF works because a browser automatically attaches cookies to requests made from another site.
This API uses no cookies and no sessions. Also, a browser page or HTML form cannot make a native gRPC call at all (it needs HTTP/2 with the `application/grpc` content type and trailers), so there is nothing to forge.

**Not included (good to mention):** there is no TLS and no authentication, because the assignment does not ask for it here. In production you would use TLS (`useTransportSecurity`) and send a token as call metadata, checked by a `ServerInterceptor`.

## 7. Tests

### 7.1 Automated test suite (`mvn test`)

`TrackingGrpcTest` starts the real server on port 9191 with an in-memory database and calls it with a real gRPC client.

| Test | Kind | Expected |
|---|---|---|
| `unaryReturnsDeliveryStatus` | positive | delivery 3, status `DELIVERED` |
| `unaryUnknownDeliveryGivesNotFound` | negative | status `NOT_FOUND`, "Delivery 999 not found" |
| `streamAnswersEveryLocation` | positive | 2 messages sent → 2 answers: `IN_TRANSIT`, then `ARRIVING` |
| `streamInvalidCoordinatesGivesInvalidArgument` | negative | stream ends with `INVALID_ARGUMENT` |
| `streamUnknownDeliveryGivesNotFound` | negative | stream ends with `NOT_FOUND` |
| `streamDeliveredDeliveryGivesInvalidArgument` | negative | stream ends with `INVALID_ARGUMENT` |

### 7.2 Postman (for the exam and for the screenshots)

Postman cannot export gRPC collections. **Take a screenshot of every step below and put them in a PDF in this folder** (the assignment asks for this).

Setup:

1. Start the API: `mvn spring-boot:run`.
2. Postman → **New** → **gRPC**.
3. Server URL: `localhost:9090`. Keep the lock icon (TLS) **off**.
4. Service definition → **Using server reflection** (or import `src/main/proto/tracking.proto`).
5. Pick the method from the dropdown.

Unary: `TrackingService / GetDeliveryStatus`

| # | Message | Expected |
|---|---|---|
| 1 (positive) | `{ "delivery_id": 1 }` | Status `0 OK`. Anna Jensen, `IN_TRANSIT`, `last_lat` 55.6761 |
| 2 (positive) | `{ "delivery_id": 2 }` | Status `0 OK`. `PENDING`, **no** `last_lat`/`last_lng` (optional fields) |
| 3 (negative) | `{ "delivery_id": 999 }` | Status `5 NOT_FOUND`, "Delivery 999 not found" |

Bidirectional streaming: `TrackingService / TrackDelivery` – click **Invoke**, then **Send** one message at a time.

| # | Message | Expected answer |
|---|---|---|
| 4 (positive) | `{ "delivery_id": 1, "lat": 55.6761, "lng": 12.5683 }` | `IN_TRANSIT`, `distance_km` about 1.92 |
| 5 (positive) | `{ "delivery_id": 1, "lat": 55.6850, "lng": 12.5550 }` | `ARRIVING`, about 0.64 |
| 6 (positive) | `{ "delivery_id": 1, "lat": 55.6900, "lng": 12.5500 }` | `DELIVERED`, 0. Then click **End Streaming** → status `0 OK` |
| 7 (negative) | new call: `{ "delivery_id": 1, "lat": 123, "lng": 12.5 }` | stream ends, `3 INVALID_ARGUMENT` |
| 8 (negative) | new call: `{ "delivery_id": 999, "lat": 55, "lng": 12 }` | stream ends, `5 NOT_FOUND` |
| 9 (negative) | new call: `{ "delivery_id": 3, "lat": 55.3959, "lng": 10.3883 }` | stream ends, `3 INVALID_ARGUMENT`, "Delivery 3 is already delivered" |

After step 6 delivery 1 is `DELIVERED`. Restart the API to reset the data.

## 8. Exam questions with short answers

### About the project

**Explain the proto file.**
See 4.1. It is the contract: one service with two RPCs and four messages. Fields have types and tag numbers. `stream` marks a streaming side. The package ends in `v1` for versioning.

**How is the unary RPC implemented?**
See 4.4. Override `getDeliveryStatus` from the generated base class, read from the database, `onNext` once, `onCompleted`. Errors with `onError` and a status code.

**How is the bidirectional streaming RPC implemented?**
See 4.5. The method returns a `StreamObserver` for incoming messages and gets one for outgoing messages. Each `onNext` stores a position and writes an answer. The stream lives until one side completes or an error ends it.

**Run the tests and explain messages and responses.**
`mvn test`, or the Postman steps in 7.2. Requests and responses are Protobuf messages (Postman shows them as JSON). Every call ends with a status: `OK`, `NOT_FOUND`, `INVALID_ARGUMENT`.

**Where is the integration with the database?**
`TrackingService`: `getStatus` (SELECT) and `recordLocation` (INSERT + UPDATE), called from `TrackingGrpcService`.

**Compare with the WebSockets API.**
Same `TrackingService`, different transport. gRPC: contract in a proto file, generated typed code, binary messages, an error ends the stream with a status code. WebSockets: no built-in contract, we invent JSON messages with an `action` field and parse them by hand, and an error is just another message, so the connection stays open.

### Concepts

**How do gRPC APIs work?**
gRPC is a Remote Procedure Call framework: the client calls a method on a generated *stub* as if it was a local method. The contract is a `.proto` file. Messages are serialized with Protocol Buffers (binary) and sent over HTTP/2. There are four call types: unary, server streaming, client streaming and bidirectional streaming. Each call ends with a status code. Extra data (like tokens) travels as metadata (HTTP/2 headers).

**Advantages.**
Small and fast binary messages; strict, typed contract; code generation for many languages; streaming built in; HTTP/2 multiplexing (many calls on one connection); deadlines and cancellation.

**Disadvantages.**
Not human-readable; browsers cannot call it directly (needs gRPC-Web and a proxy); harder to debug and test than JSON over HTTP; client and server must share the proto file.

**Typical use cases.**
Communication between microservices, low-latency and high-throughput systems, real-time streaming (tracking, telemetry, chat), mobile apps with weak networks, systems with many programming languages.

**How is a gRPC API's syntax specified?**
With Protocol Buffers as the Interface Definition Language, in a `.proto` file: `syntax`, `package`, `service` with `rpc` methods, and `message` types with typed, numbered fields. `protoc` compiles the file into code for the server and the client.

**Why HTTP/2?**
HTTP/1.1 is text based and handles one request at a time per connection. HTTP/2 is binary and multiplexes many independent *streams* over one TCP connection, with header compression (HPACK). A gRPC call is one HTTP/2 stream, which is what makes long-lived, two-way streaming possible. HTTP/3 does the same over QUIC instead of TCP.

**TCP vs UDP vs QUIC.**
TCP: connection with a handshake, reliable, ordered – used by HTTP/1.1 and HTTP/2 (and so by gRPC). One lost packet blocks all streams on the connection (head-of-line blocking).
UDP: no connection, no guarantees, very fast – used for DNS, video calls, games.
QUIC: built on UDP, adds reliability, built-in TLS 1.3, faster connection setup, and independent streams so one lost packet only blocks its own stream – used by HTTP/3.
