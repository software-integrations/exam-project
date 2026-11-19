# REST API – Delivery management

A learning guide for the REST API. Read it top to bottom once, then use section 8 to practise for the exam.

## 1. Scenario

A delivery company has **customers**, **couriers** and **deliveries**.
A dispatcher (staff member) logs in and manages the deliveries.

- A delivery belongs to one customer.
- A delivery may have a courier (it is `null` until one is assigned).
- A delivery has a status: `PENDING`, `IN_TRANSIT`, `DELIVERED` or `CANCELLED`.
- Business rule: a `DELIVERED` delivery is history. It cannot be updated or deleted.

## 2. How to run

You need Java 21+, Maven and Docker.

```bash
cd rest
docker compose up -d      # starts Redis on port 6379 (needed for token revocation)
mvn spring-boot:run       # starts the API on http://localhost:8081
```

- Login: `dispatcher` / `password123`
- The data is **reset on every start** (`schema.sql` drops and recreates the tables, `data.sql` fills them).
- The database is an H2 file in `rest/data/`. You can open it in a database tool with the URL
  `jdbc:h2:file:./data/rest;AUTO_SERVER=TRUE`, user `sa`, empty password.
- Redis must be running. Every request with a token asks Redis "is this token revoked?".

Quick try:

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"dispatcher","password":"password123"}'

curl http://localhost:8081/api/v1/customers?page=1\&size=5 \
     -H "Authorization: Bearer <access_token from login>"
```

## 3. File map

| File | What it does |
|---|---|
| `pom.xml` | The libraries: web, JDBC, validation, OAuth2 resource server, Redis, H2 |
| `docker-compose.yml` | Starts Redis |
| `src/main/resources/application.properties` | Port, database URL, Redis address, JWT secret and lifetime |
| `src/main/resources/schema.sql` | Creates the tables `customers`, `couriers`, `deliveries`, `users` |
| `src/main/resources/data.sql` | Example data and the user `dispatcher` |
| `RestApiApplication.java` | Starts the application |
| `config/SecurityConfig.java` | Security rules, JWT encoder and decoder, BCrypt |
| `auth/AuthController.java` | `login` (creates a JWT) and `logout` (revokes it) |
| `auth/LoginRequest.java` | JSON body of the login request |
| `auth/TokenDenylist.java` | Stores revoked token ids in Redis |
| `common/Link.java` | One HATEOAS link (`href` + `method`) |
| `common/Resource.java` | One item + its `_links` |
| `common/PagedResponse.java` | One page of items + paging info + paging links |
| `common/Sanitizer.java` | HTML-escapes text (XSS protection) |
| `customer/Customer.java`, `courier/Courier.java`, `delivery/Delivery.java` | The data records (one database row each) |
| `customer/CustomerRepository.java`, `courier/CourierRepository.java`, `delivery/DeliveryRepository.java` | All the SQL |
| `customer/CustomerController.java`, `courier/CourierController.java` | GET list and GET by id |
| `delivery/DeliveryController.java` | GET list, GET by id, POST, PUT, DELETE |
| `delivery/DeliveryRequest.java` | JSON body for POST and PUT, with validation rules |
| `postman/rest.postman_collection.json` | The test suite |

All Java files are in `src/main/java/dk/ek/delivery/rest/`.

## 4. How it works

### Endpoints

| Method | URL | Result |
|---|---|---|
| POST | `/api/v1/auth/login` | 200 + access token |
| POST | `/api/v1/auth/logout` | 204, the token is revoked |
| GET | `/api/v1/customers?search=&page=&size=` | 200, one page of customers |
| GET | `/api/v1/customers/{id}` | 200 or 404 |
| GET | `/api/v1/couriers?search=&page=&size=` | 200, one page of couriers |
| GET | `/api/v1/couriers/{id}` | 200 or 404 |
| GET | `/api/v1/deliveries?search=&page=&size=` | 200, one page of deliveries |
| GET | `/api/v1/deliveries/{id}` | 200 or 404 |
| POST | `/api/v1/deliveries` | 201 + `Location` header |
| PUT | `/api/v1/deliveries/{id}` | 200, 404 or 409 |
| DELETE | `/api/v1/deliveries/{id}` | 204, 404 or 409 |

Only `login` is open. Everything else needs `Authorization: Bearer <token>`.

### The path of a request

Example: `GET /api/v1/deliveries?search=pending&page=1&size=5`

1. **Security filter** (`SecurityConfig.securityFilterChain`). Spring Security reads the
   `Authorization: Bearer ...` header and gives the token to the `JwtDecoder`.
2. **JwtDecoder** (`SecurityConfig.jwtDecoder`). It checks the signature (HS256 with our secret),
   the expiry time, and asks `TokenDenylist.isRevoked` (Redis). If one check fails: `401`.
3. **Controller** (`DeliveryController.list`). Spring reads the query parameters. `@Min` / `@Max`
   validate `page` and `size`. Invalid values give `400`.
4. **Repository** (`DeliveryRepository.search` and `count`). It runs the SQL with `?` parameters.
5. **HATEOAS** (`DeliveryController.toResource`). Every delivery is wrapped in a `Resource` with links.
6. **Paging** (`PagedResponse.of`). It adds `page`, `size`, `totalItems`, `totalPages` and the paging links.
7. Spring turns the object into JSON.

### Search and pagination

In `CustomerRepository.search` (the other two repositories work the same way):

```sql
SELECT * FROM customers
WHERE LOWER(name) LIKE ? OR LOWER(city) LIKE ?
ORDER BY id LIMIT ? OFFSET ?
```

- **Search**: the parameter is `"%" + search.toLowerCase() + "%"`. An empty search gives `%%`, which matches everything.
- **Pagination**: `LIMIT` is the page size. `OFFSET` is `(page - 1) * size` = the rows to skip.
- `count(search)` returns the total number of matches. `PagedResponse.of` uses it to calculate `totalPages`.
- Pages start at 1. Default: `page=1`, `size=10`. Maximum size: 100.

What each resource searches in: customers → name or city. Couriers → name or vehicle. Deliveries → address or status.

### HATEOAS

A single item (`DeliveryController.toResource`):

```json
{
  "id": 4, "customerId": 3, "courierId": null, "address": "Vestergade 22, Odense", "status": "PENDING",
  "_links": {
    "self":       { "href": "/api/v1/deliveries/4", "method": "GET" },
    "collection": { "href": "/api/v1/deliveries",   "method": "GET" },
    "customer":   { "href": "/api/v1/customers/3",  "method": "GET" },
    "update":     { "href": "/api/v1/deliveries/4", "method": "PUT" },
    "delete":     { "href": "/api/v1/deliveries/4", "method": "DELETE" }
  }
}
```

The links are **contextual**: they depend on the state of the item.

- `courier` only exists when a courier is assigned.
- `update` and `delete` only exist when the status is not `DELIVERED`.
- In a page (`PagedResponse.of`): `prev` only exists after page 1, `next` only exists before the last page.

`Resource.java` uses `@JsonUnwrapped`, so the fields of the item and `_links` are in the same JSON object.

### Login, JWT and logout

`AuthController.login`:

1. Reads the BCrypt hash of the user's password from the `users` table.
2. Compares it with `passwordEncoder.matches`. Wrong user and wrong password give the same `401`.
3. Builds the claims: `jti` (random UUID), `sub` (username), `iat` (now), `exp` (now + 15 minutes).
4. Signs the JWT with HS256 (`jwtEncoder.encode`).
5. Answers in the OAuth2 token response format:

```json
{ "access_token": "eyJhbGciOiJIUzI1NiJ9...", "token_type": "Bearer", "expires_in": 900 }
```

`AuthController.logout` gets the validated JWT with `@AuthenticationPrincipal Jwt jwt` and calls
`TokenDenylist.revoke(jti, exp)`. That stores the key `revoked:<jti>` in Redis with a time-to-live
equal to the time the token has left. From now on `SecurityConfig.jwtDecoder` rejects the token with `401`.

### Errors

The controllers throw `ResponseStatusException` with a status code. Because of
`spring.mvc.problemdetails.enabled=true`, errors use the standard "problem details" JSON:

```json
{ "type": "about:blank", "title": "Not Found", "status": 404, "detail": "Courier 999 not found", "instance": "/api/v1/couriers/999" }
```

## 5. Requirements → code

| Requirement | Where |
|---|---|
| 3 GET endpoints that return collections, with search and pagination | `list` in `CustomerController`, `CourierController`, `DeliveryController`. SQL in the `search` and `count` methods of the three repositories |
| 3 GET endpoints that return one item by ID | `getById` in the same three controllers |
| 1 POST, 1 PUT, 1 DELETE | `create`, `update`, `delete` in `DeliveryController` |
| Login and logout endpoints | `login` and `logout` in `AuthController` |
| REST constraints, including HATEOAS with contextual links | `toResource` in the controllers, `PagedResponse.of`, `Resource.java`, `Link.java`. Stateless: `SecurityConfig.securityFilterChain` |
| REST naming convention | The `PATH` constants in the controllers: plural nouns, lowercase, no verbs for resources |
| Versioning | `/api/v1/...` in every `PATH` constant and in `AuthController` |
| OAuth2 with JWT | `SecurityConfig` (`oauth2ResourceServer`, `jwtDecoder`, `jwtEncoder`) and `AuthController.login` |
| Revocation in a key-value store independent of the API database | `TokenDenylist.java` (Redis), checked in `SecurityConfig.jwtDecoder`. Redis runs from `docker-compose.yml`; the API data is in H2 |
| Test suite with positive and negative cases | `postman/rest.postman_collection.json` |
| All APIs use database-stored data | H2 database: `schema.sql`, `data.sql`, the repositories |
| Prevent SQL injection, XSS, CSRF | Section 6 |

## 6. Security

### SQL injection

Every SQL statement uses `?` placeholders (prepared statements). See all methods in the three
repositories and the query in `AuthController.login`. The database gets the SQL text and the values
separately, so a value can never become SQL code.

Example: `search=' OR '1'='1` becomes the plain text value `%' or '1'='1%`. It matches no customer.
The Postman request "Search with SQL injection attempt" proves it: `totalItems` is 0.

No SQL is built by adding user input to a string. The `ORDER BY` is fixed text.

### XSS

- **Escaping on input.** `Sanitizer.clean` HTML-escapes free text before it is stored. It is used in
  `DeliveryController.create` and `update` for `address`. `<script>` is stored as `&lt;script&gt;`.
  A browser shows it as text and does not run it.
- **Validation.** `DeliveryRequest` only accepts four values for `status` (`@Pattern`) and numbers for the ids.
- **JSON only.** Responses are `Content-Type: application/json`, not HTML. Spring Security also adds
  `X-Content-Type-Options: nosniff`, so a browser will not guess that the response is HTML.

### CSRF

A CSRF attack works because the browser sends **cookies** automatically with every request to a site.

This API uses no cookies and no sessions (`SessionCreationPolicy.STATELESS` in `SecurityConfig`).
The token must be put in the `Authorization` header by the client's own code. A browser never adds
that header by itself, so a foreign web page cannot make an authenticated request.
That is why `csrf.disable()` in `SecurityConfig.securityFilterChain` is safe here.

Extra: no CORS is configured, so the browser's same-origin policy blocks scripts from other sites
from reading our responses or sending JSON requests with an `Authorization` header.

### Other

- Passwords are stored as BCrypt hashes (`SecurityConfig.passwordEncoder`, `data.sql`).
- The JWT secret comes from the environment variable `JWT_SECRET`. The value in
  `application.properties` is only a default for local development.
- Tokens live for 15 minutes (`jwt.ttl-minutes`).

## 7. Tests

File: `postman/rest.postman_collection.json`

**The collection needs both Redis and the API running.** Without Redis, every request with a token fails.

1. `docker compose up -d` and `mvn spring-boot:run`. Use a fresh start: some tests expect the original data (for example 12 customers).
2. Postman → **Import** → choose the file.
3. Open the collection → **Run** → run all folders **in order**.

The first request (login) saves the token in the collection variable `token`.
The collection sends it as a bearer token on all other requests.
The base URL is the variable `baseUrl` (`http://localhost:8081`).

| Folder | What it covers |
|---|---|
| 1. Auth | Login (200). Negative: wrong password (401), missing password (400), no token (401 + `WWW-Authenticate` header) |
| 2. Customers | Pagination and its links, search, get by id. Negative: SQL injection attempt, `page=0` (400), unknown id (404) |
| 3. Couriers | List, search together with pagination, get by id. Negative: unknown id (404) |
| 4. Deliveries | Middle page with `prev` and `next`, search, contextual links, POST (201 + `Location`), PUT, DELETE (204). Negative: invalid body (400), unknown customer (400), XSS attempt is escaped, unknown id (404), changing a delivered delivery (409) |
| 5. Logout and revocation | Logout (204). Negative: the same token is now rejected (401) |

To run the suite again, restart the API first (the data resets).

## 8. Exam questions with short answers

### Explain URL naming for the REST API

- URLs name **resources** with **nouns**, not actions: `/customers`, `/couriers`, `/deliveries`.
- Collections are **plural**. One item is the collection plus its id: `/deliveries/4`.
- Lowercase letters, no file extensions, no trailing slash.
- The **HTTP method** is the verb: `GET` reads, `POST` creates, `PUT` replaces, `DELETE` removes. So there is no `/getDelivery` or `/deleteDelivery`.
- Query parameters are used for filtering and paging: `?search=pending&page=2&size=5`.
- `/auth/login` and `/auth/logout` are the exception. They are actions, not data. The naming guide calls this a "controller" resource, and for those a verb is allowed.

### How is the REST API enforcing HATEOAS? Show in code

- Every response contains `_links`. The client follows links instead of building URLs itself.
- One item: `toResource` in each controller builds a `Map<String, Link>` and wraps the item in `Resource`.
- A page: `PagedResponse.of` adds `self`, `first`, `prev`, `next`, `last`.
- The links are contextual. Show the two `if` statements in `DeliveryController.toResource` and the two in `PagedResponse.of`.
- Each `Link` has `href` and `method`, so the client also knows which HTTP method to use.

### Explain how pagination and filtering has been implemented

- Parameters: `search` (default empty), `page` (default 1, minimum 1), `size` (default 10, 1–100). See the `list` methods.
- Filtering: `WHERE LOWER(column) LIKE ?` with the value `%search%` in lowercase. Case-insensitive "contains".
- Pagination: `LIMIT ? OFFSET ?` with `size` and `(page - 1) * size`.
- A second query (`count`) gives the total. `PagedResponse.of` calculates `totalPages = ceil(totalItems / size)`.
- The paging links keep the `search` value, so the client stays in the same search (`PagedResponse.pageLink`).

### Explain how versioning is implemented. Why this scheme?

- **URI path versioning**: the version is in the path, `/api/v1/...`. See the `PATH` constants in the controllers and `@RequestMapping("/api/v1/auth")`.
- A breaking change would get new controllers under `/api/v2`. The `v1` controllers keep working for old clients.
- Why: it is the simplest and most visible. You see the version in every URL, log line and link. It works in a browser and in Postman without special headers. It is easy to route and to cache.
- Alternatives: a query parameter (`?version=1`), a custom header (`X-API-Version: 1`), or the `Accept` header (media type versioning). The header versions keep URLs clean but are harder to see and to test.

### Run the tests and explain headers, parameters, bodies, status codes

Request:

- `Authorization: Bearer <JWT>` – who the client is.
- `Content-Type: application/json` – on POST and PUT, the format of the body.
- Query parameters: `search`, `page`, `size`.
- Bodies: `LoginRequest` (`username`, `password`) and `DeliveryRequest` (`customerId`, `courierId`, `address`, `status`).

Response:

- `Content-Type: application/json`, or `application/problem+json` for errors.
- `Location: /api/v1/deliveries/16` – on `201 Created`, the URL of the new item.
- `WWW-Authenticate: Bearer ...` – on `401`, tells the client which kind of login is needed and why it failed.
- `Cache-Control: no-store` and `X-Content-Type-Options: nosniff` – added by Spring Security.

Status codes used:

| Code | When |
|---|---|
| 200 OK | GET, PUT, login |
| 201 Created | POST delivery |
| 204 No Content | DELETE, logout |
| 400 Bad Request | Invalid body or parameters, customer or courier does not exist |
| 401 Unauthorized | No token, bad token, expired token, revoked token, wrong password |
| 404 Not Found | Unknown id |
| 409 Conflict | Changing or deleting a delivered delivery |

### Explain how OAuth2 authentication was implemented

Be precise here:

- The API is an **OAuth2 resource server**. `oauth2ResourceServer(oauth2 -> oauth2.jwt(...))` in `SecurityConfig` makes Spring Security read the bearer token (RFC 6750) and validate it as a JWT.
- `AuthController.login` is a **minimal token endpoint**. It works like the OAuth2 *resource owner password credentials* grant: the client sends username and password and gets an access token. The answer uses the standard OAuth2 token response fields: `access_token`, `token_type`, `expires_in`.
- There is **no separate authorization server**. Our API issues the tokens and also validates them. In a bigger system the token would come from a dedicated server (for example Keycloak or Microsoft Entra ID), and our API would only validate it.
- Differences from the full standard: the login body is JSON (the standard token endpoint uses form fields and `grant_type=password`), and there are no refresh tokens, scopes or client ids.

### Explain how JWTs are handled

- **Creating** (`AuthController.login`): claims `jti`, `sub`, `iat`, `exp`. Signed with HS256 by `jwtEncoder`.
- **The key** (`SecurityConfig` constructor): one secret from `jwt.secret`. HS256 is symmetric: the same key signs and verifies. That is fine because the same application does both.
- **Validating** (`SecurityConfig.jwtDecoder`): signature → expiry (`JwtTimestampValidator`, no grace time) → not revoked (`TokenDenylist.isRevoked`).
- **Using**: the client sends the token in the `Authorization` header on every request. The server stores no session.
- **Revoking** (`AuthController.logout`): the `jti` goes on the Redis denylist.

### Why did you choose the revocation strategy that you chose?

The strategy is a **denylist of token ids in Redis**, with a time-to-live equal to the token's remaining lifetime.

- Logout works immediately. The token is useless from the next request.
- The list stays small. It only holds tokens that were revoked and are not expired yet. Redis deletes the keys by itself.
- Redis is in memory, so the check on every request is fast.
- Redis is independent of the API database (H2), as the assignment requires.

Alternatives:

| Strategy | Why not |
|---|---|
| Only short-lived tokens | No real revocation. A stolen token works until it expires |
| Allowlist (store every valid token) | Every issued token must be stored. This is the same as server-side sessions |
| Rotate the signing key | Revokes **all** tokens of all users at once |

Cost of our choice: one Redis lookup per request, so the server is no longer purely "self-contained JWT". If Redis is down, requests with a token fail.

### What makes an API RESTful?

It follows the REST constraints:

| Constraint | Meaning | In this API |
|---|---|---|
| Client–server | Client and server are separate and only share the interface | The API only returns data. Any client can use it |
| Stateless | Every request has everything the server needs | No sessions. The JWT is sent on every request (`SessionCreationPolicy.STATELESS`) |
| Cacheable | Responses say if they can be cached | Spring Security marks the responses `Cache-Control: no-store` because they are private data |
| Uniform interface | Resources have URLs, standard methods, self-descriptive messages, HATEOAS | Noun URLs, HTTP verbs, status codes, `Content-Type`, `_links` |
| Layered system | The client does not know if it talks to the server or to a proxy / load balancer | Nothing in the API depends on a direct connection |
| Code on demand (optional) | The server may send code to the client | Not used |

### What transport does a REST API use? What formats?

- REST is an architectural style. In practice it runs on **HTTP** (or HTTPS).
- It is not tied to one format. JSON is the most common. XML, HTML, plain text and binary are also possible.
- The format is agreed with the `Accept` and `Content-Type` headers (content negotiation). This API uses JSON.

### Discuss statelessness in a REST API

- The server keeps no client state between requests. Each request is understood on its own.
- Good: any server instance can answer any request, so it is easy to scale and to recover from a crash.
- Cost: the client sends its credentials every time, so requests are a bit bigger.
- In this API: the JWT carries the identity. The revocation denylist is a small piece of server state. It lives in Redis, outside the API process, so every API instance can share it.

### What is idempotency in HTTP?

An operation is idempotent when doing it many times leaves the server in the same state as doing it once.

| Method | Idempotent | Safe (read-only) |
|---|---|---|
| GET | yes | yes |
| PUT | yes | no |
| DELETE | yes | no |
| POST | **no** | no |

- `PUT /deliveries/4` with the same body twice: same result.
- `DELETE /deliveries/16` twice: the second call answers 404, but the server state is the same (the delivery is gone).
- `POST /deliveries` twice: two deliveries. This is a problem when a client retries after a timeout.
- Strategies for POST: the client sends an **idempotency key** header and the server remembers it and returns the first result again; or use PUT with an id chosen by the client; or check for a natural unique key.

### What does a JWT consist of? What is a claim?

Three Base64URL parts with dots between them: `header.payload.signature`.

- **Header**: the algorithm (`HS256`).
- **Payload**: the claims.
- **Signature**: `HMAC-SHA256(header + "." + payload, secret)`. It proves that nobody changed the token.

A **claim** is one piece of information in the payload. Ours: `jti` (token id), `sub` (the user), `iat` (issued at), `exp` (expires at).
The payload is only encoded, **not encrypted**. Anyone can read it, so never put secrets in it.

### How can a JWT be revoked?

A JWT is valid until `exp`. The server cannot "delete" it at the client. Options: a denylist (ours), an allowlist, very short lifetimes with refresh tokens, or changing the signing key. See the table above.

### Explain OAuth2 and its roles

OAuth2 lets an application get limited access to a user's data without getting the user's password.

- **Resource owner**: the user who owns the data.
- **Client**: the application that wants access.
- **Authorization server**: checks the user and issues tokens.
- **Resource server**: the API with the data. It validates the token.

In this project: the dispatcher is the resource owner, Postman is the client, and our API is both the token issuer and the resource server.
OIDC is a layer on top of OAuth2 for **authentication**. It adds an ID token (a JWT) that says who the user is.

### SOP, CORS and preflight

- **SOP** (same-origin policy): a browser script may only read responses from its own origin (scheme + host + port).
- **CORS**: the server can relax SOP with response headers such as `Access-Control-Allow-Origin`.
- **Preflight**: for "non-simple" requests (for example JSON bodies, `PUT`, `DELETE`, or an `Authorization` header) the browser first sends an `OPTIONS` request to ask for permission.
- This API sets no CORS headers, so browsers block cross-origin scripts from using it. Postman and curl are not browsers and are not affected.
