# GraphQL API (+ sharding and ETL)

This guide explains the GraphQL API. The sharding and the ETL process live in the same
project and have their own guide: **[ETL.md](ETL.md)**.

## 1. Scenario

A parcel company registers **customers** and their **parcels**.
Clients read customers (with or without their parcels) and logged-in staff
register new customers and parcels.

- Tech: Java 21, Spring Boot, **Spring for GraphQL**, H2 databases, plain SQL with `JdbcClient`.
- Endpoint: `POST http://localhost:8083/graphql`
- Login: `dispatcher` / `password123`

## 2. How to run

```bash
cd graphql
mvn spring-boot:run
```

| URL | What |
|---|---|
| `http://localhost:8083/graphql` | The API (POST, JSON) |
| `http://localhost:8083/graphiql` | Browser page to try queries |
| `http://localhost:8083/h2-console` | Database admin page (see ETL.md) |

The data is reset every time the API starts.

## 3. File map

| File | What it does |
|---|---|
| `src/main/resources/graphql/schema.graphqls` | The **SDL schema**: all types, queries and mutations |
| `DeliveryController.java` | The **resolvers**: one Java method per query/mutation |
| `CustomerService.java` | SQL for customers and parcels (uses the shards) |
| `auth/AuthService.java` | Login, logout, token creation and **token hashing** |
| `auth/AuthInterceptor.java` | Reads the `Authorization: Bearer` header on every request |
| `ApiException.java`, `ErrorHandler.java` | Turn our errors into GraphQL errors |
| `db/Databases.java` | Opens the 5 databases (3 shards, warehouse, auth) |
| `db/ShardRouter.java` | The hash function that picks a shard |
| `etl/EtlService.java` | The ETL process |
| `DataSeeder.java` | Example data inserted at startup |
| `postman/graphql.postman_collection.json` | The test suite |

## 4. How it works

### One request, step by step

1. The client sends `POST /graphql` with JSON: `{ "query": "{ customer(email: \"anna@example.com\") { name } }" }`.
2. `AuthInterceptor.intercept` runs first. If there is a bearer token, it puts `token` and
   (only if the token is valid) `username` into the GraphQL context.
3. Spring for GraphQL checks the query against `schema.graphqls`. An unknown field is rejected here.
4. Spring calls the Java method with the same name as the field: `DeliveryController.customer`.
5. The result is returned as JSON, with **only the fields the client asked for**.

### The SDL schema (`schema.graphqls`)

- `type Query` – read operations: `customer(email)` and `customers`.
- `type Mutation` – write operations: `login`, `logout`, `registerCustomer`, `createParcel`, `runEtl`.
- `type Customer`, `type Parcel`, `type EtlResult` – the shapes of the data.
- `String!` – the `!` means "never null". `[Parcel!]!` – a list that is never null, with items that are never null.
- `ID`, `String`, `Int`, `Float`, `Boolean` are the built-in scalar types.

### Queries (`DeliveryController.java`)

| Query | Java method | What happens |
|---|---|---|
| `customer(email)` | `customer` (`@QueryMapping`) | Routed to **one** shard by the email hash. Returns `null` if not found |
| `customers` | `customers` (`@QueryMapping`) | Asks **all** shards and merges the lists |
| `Customer.parcels` | `parcels` (`@SchemaMapping`) | Runs only when the client asks for `parcels` |

`@Argument` connects a method parameter to a GraphQL argument with the same name.

### Mutations

| Mutation | Needs token | What happens |
|---|---|---|
| `login(username, password): String!` | no | Checks the BCrypt password hash, returns a new token |
| `logout: Boolean!` | yes | Deletes the token |
| `registerCustomer(name, email, city)` | yes | Validates, then inserts into the shard chosen by the hash |
| `createParcel(customerEmail, description, weightKg)` | yes | Inserts into the customer's shard |
| `runEtl` | yes | Runs the ETL process (see ETL.md) |

Every protected mutation starts with `requireLogin(username)`. `username` comes from the GraphQL
context (`@ContextValue`). It is `null` when there was no valid token, and then we throw
`ApiException(UNAUTHORIZED)`.

### Bearer-token authentication (`auth/`)

1. `login` → `AuthService.login`:
   - looks up the user's BCrypt password hash and compares,
   - creates 32 random bytes with `SecureRandom` and encodes them as text → the **token**,
   - stores `sha256(token)` in the `tokens` table with an expiry time (1 hour),
   - returns the token to the client. This is the only time the real token exists on the server.
2. The client sends the token with every request: `Authorization: Bearer <token>`.
3. `AuthInterceptor` reads the header. `AuthService.findUsername` hashes the incoming token
   and searches for the hash (`WHERE token_hash = ? AND expires_at > ?`).
4. `logout` deletes the row, so the token stops working immediately.

### Token hardening via hashing

The database stores only the **SHA-256 hash** of the token (`AuthService.sha256`), never the token.

- If someone steals the database, they get hashes. A hash cannot be sent as a token,
  because the server would hash it again and find nothing.
- SHA-256 is one-way: you cannot calculate the token from the hash.
- A fast hash without salt is fine here because the token is 256 random bits (cannot be guessed).
  Passwords are different: humans pick weak passwords, so they need a slow, salted hash (BCrypt).

Other hardening: the token is random (`SecureRandom`), it expires, and it can be revoked (logout).

### Errors

GraphQL answers **HTTP 200** even for errors. The error is in the `errors` list:

```json
{ "errors": [ { "message": "Customer not found", "path": ["createParcel"],
                "extensions": { "classification": "NOT_FOUND" } } ], "data": null }
```

`ErrorHandler` builds this from our `ApiException`. Unknown exceptions become a generic
`INTERNAL_ERROR` without details, so no stack trace leaks.

## 5. Requirements → code

| Requirement | Where |
|---|---|
| SDL schema | `src/main/resources/graphql/schema.graphqls` |
| At least 2 queries | `customer`, `customers` in `DeliveryController` |
| `login` / `logout` mutations | `DeliveryController.login`, `.logout` → `AuthService` |
| Bearer-token authentication | `AuthInterceptor` + `AuthService.findUsername` |
| Token hardened via hashing | `AuthService.sha256`, table `tokens.token_hash` (`auth-schema.sql`) |
| 2 mutations for authenticated users | `registerCustomer`, `createParcel` (+ `runEtl`) with `requireLogin` |
| Database-stored information | H2 databases opened in `db/Databases.java` |
| Test suite, positive and negative | `postman/graphql.postman_collection.json` |
| Sharding + ETL | `db/ShardRouter.java`, `etl/EtlService.java` → see ETL.md |

## 6. Security

| Attack | How it is prevented | Where |
|---|---|---|
| **SQL injection** | All SQL uses `?` parameters (prepared statements). Input is sent as data, never as SQL text | `CustomerService`, `AuthService`, `EtlService` |
| **XSS** | Free text is HTML-escaped before it is stored (`<` → `&lt;`). The API only returns JSON, never HTML. Email is validated with a strict pattern | `CustomerService.registerCustomer`, `.createParcel` |
| **CSRF** | CSRF needs the browser to send credentials automatically (cookies). We use no cookies: the token must be added to the `Authorization` header by code. The endpoint only accepts `POST` with `Content-Type: application/json`, which an HTML form cannot send, and CORS is not enabled | `AuthInterceptor` |

Also: the schema is strongly typed, so wrong types (`weightKg: "abc"`) are rejected before our code runs.

## 7. Tests

Import `postman/graphql.postman_collection.json` in Postman and run the whole collection
on a **freshly started** API (the tests expect the start data).

| Folder | Covers |
|---|---|
| 1. Queries | all customers, one customer with parcels, only-requested-fields, unknown customer, SQL injection, unknown field |
| 2. Authentication | mutation without token, wrong password, login (saves the token in a variable) |
| 3. Mutations | register customer, duplicate, invalid email, XSS, create parcel, unknown customer, bad weight, read back from shard |
| 4. ETL | run ETL, run again → same counts |
| 5. Logout | logout, token no longer works |

Request format: always `POST`, JSON body with `query` (and optional `variables`).
Response format: JSON with `data` and/or `errors`.

## 8. Exam questions – short answers

**Explain the queries (show in code).** `schema.graphqls` → `type Query`; `DeliveryController.customer`
and `.customers`. `customer` is routed to one shard, `customers` reads all three. `parcels` is a
nested field with its own resolver.

**Explain the mutations.** `type Mutation` in the schema; the `@MutationMapping` methods. Show
`requireLogin` and then `CustomerService.registerCustomer` (validation → escape → insert in shard).

**How is bearer-token authentication implemented?** `AuthService.login` creates the token,
`AuthInterceptor` reads the header and resolves the user, `requireLogin` protects the mutations.

**How is the token hardened?** Only `sha256(token)` is stored. Plus: random, expiring, revocable.

**Explain the SDL.** Types, fields, `!`, lists, `Query`/`Mutation` root types, comments with `#`.

**Overfetching and underfetching – how does GraphQL solve them?**
- *Overfetching*: a REST endpoint returns the whole object even if you need one field.
  In GraphQL the client lists the fields: `{ customer(email: "...") { name } }` returns only `name`.
- *Underfetching*: one REST call is not enough, you need more calls (customer, then parcels → "n+1" calls).
  In GraphQL one query gets both: `{ customer(...) { name parcels { description } } }`.

**How do GraphQL APIs work? Operations, advantages, disadvantages.**
- One endpoint, one HTTP method (POST). The client sends a query; the server runs a resolver per field.
- Operations: **query** (read), **mutation** (write), **subscription** (server pushes updates).
- Advantages: exact data in one round trip, strongly typed schema, self-documenting (introspection),
  easy to evolve without versions (add fields, deprecate old ones).
- Disadvantages: HTTP caching is hard (everything is POST to one URL), always HTTP 200 so errors
  must be read from the body, expensive/deep queries must be limited, n+1 problem on the server side
  (our `parcels` resolver runs once per customer), more complex than REST for simple APIs.

**How is a GraphQL API's syntax specified?** With the **SDL** (Schema Definition Language) in a
schema file. It defines object types, fields, scalar types, arguments, nullability (`!`), lists,
and the root types `Query`, `Mutation`, `Subscription`. The server validates every request against it.

**What is a bearer token? How can a token be stored? Why and how do you harden it?**
- Bearer token: whoever *bears* (holds) the token gets access, no extra proof needed. Sent as
  `Authorization: Bearer <token>`.
- Client storage: in memory (safest, lost on reload), `sessionStorage`/`localStorage`
  (readable by XSS), or an `HttpOnly` cookie (safe from XSS, but then CSRF protection is needed).
- Server storage: hashed, as in this project.
- Why harden: a stolen token gives full access. How: hash it at rest, short lifetime,
  revocation, HTTPS only, random and long.
