# Software Integrations – Exam Project: Delivery System

Six independent APIs and one ETL process, all about a **parcel delivery company**.
Everything is written in **Java 21** with **Spring Boot** and plain SQL (`JdbcClient`),
and kept as simple as possible. Every folder is its own Maven project and has its own guide.

## The APIs

| # | API | Folder / guide | Business scenario | Address |
|---|---|---|---|---|
| 1 | REST | [rest](rest/README.md) | Dispatch: customers, couriers, deliveries | `http://localhost:8081/api/v1` |
| 2 | SOAP | [soap](soap/README.md) | Pickup points and locker reservations | `http://localhost:8082/services/pickup-points?wsdl` |
| 3 | GraphQL | [graphql](graphql/README.md) | Customers and parcels, stored in 3 shards | `http://localhost:8083/graphql` |
| – | ETL | [graphql/ETL.md](graphql/ETL.md) | Shards → warehouse | mutation `runEtl` |
| 4 | gRPC | [grpc](grpc/README.md) | Live tracking of a delivery | `localhost:9090` |
| 5 | WebSockets | [websockets](websockets/README.md) | Same two operations as gRPC | `ws://localhost:8085/ws/tracking` |
| 6 | Server-Sent Events | [server-sent-events](server-sent-events/README.md) | Live tracking stream for the receiver | `http://localhost:8086/api/v1/deliveries/1/events` |

Suggested reading order: REST → SOAP → GraphQL → ETL → gRPC → WebSockets → SSE.
The last three share one business scenario, so read them together.

## How to run

You need Java 21+ and Maven. Only the REST API needs Docker (for Redis).

```bash
cd rest && docker compose up -d && mvn spring-boot:run     # REST   (8081) + Redis (6379)
cd soap && mvn spring-boot:run                             # SOAP   (8082)
cd graphql && mvn spring-boot:run                          # GraphQL + ETL (8083)
cd grpc && mvn spring-boot:run                             # gRPC   (9090)
cd websockets && mvn spring-boot:run                       # WebSockets (8085)
cd server-sent-events && mvn spring-boot:run               # SSE    (8086)
```

- Start each API from its **own folder** (the database files are created in `./data`).
- The databases are **reset on every start**, so the tests always begin from the same data.
- Login where needed (REST, GraphQL): `dispatcher` / `password123`.

## Shared requirements

- [x] **Six independent APIs.** Each folder is a separate project with its own database.
- [x] **Database-stored information.** Every API uses an H2 SQL database (file in `./data`).
      REST also uses Redis; GraphQL uses five databases (3 shards, warehouse, auth).
- [x] **SQL injection** is prevented the same way everywhere: SQL is only written with `?`
      parameters (prepared statements). User input is never glued into SQL text.
- [x] **XSS** is prevented by validating input and HTML-escaping free text before it is stored.
      The APIs return JSON/XML/Protobuf, never HTML.
- [x] **CSRF** is prevented because no API uses cookies or sessions. Details differ per API
      (bearer header, JSON-only content type, WebSocket origin check) – see each guide.
- [x] **Original work.** The scenario (delivery system) is different from the class exercises
      and the first mandatory assignment (library).

## ETL requirements (in [graphql](graphql/ETL.md))

- [x] Three independent shards with equal structure and two related entity types (customers, parcels).
- [x] Hash-based distribution: `CRC32(email) % 3`.
- [x] Reads and writes are routed automatically to the right shard.
- [x] ETL extracts from all three shards.
- [x] ETL transforms the data and resolves id conflicts (new keys + `source_shard`/`source_id`).
- [x] ETL loads into a separate warehouse database.
- [x] Re-running the ETL creates no duplicates (`MERGE ... KEY`).

## Tests

| API | Test suite |
|---|---|
| REST | `rest/postman/rest.postman_collection.json` |
| SOAP | `soap/postman/soap.postman_collection.json` |
| GraphQL + ETL | `graphql/postman/graphql.postman_collection.json` |
| SSE | `server-sent-events/postman/sse.postman_collection.json` |
| gRPC | JUnit tests (`cd grpc && mvn test`) + Postman steps in the guide (Postman cannot export gRPC collections → screenshots in a PDF) |
| WebSockets | JUnit tests (`cd websockets && mvn test`) + Postman steps in the guide (Postman cannot export WebSocket collections → screenshots in a PDF) |

Run a Postman collection on a freshly started API, from top to bottom.

## Delivery

Zip the six API folders (without `target/` and `data/`) and this README. Do not include links.
