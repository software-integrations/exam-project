# SOAP API – Pickup points

A learning guide for the SOAP API. Read it top to bottom once, then use section 8 to practise for the exam.

## 1. Scenario

Customers can collect parcels at **pickup points** (parcel shops and locker walls).
Partner systems use this SOAP service to find pickup points and to reserve lockers.

- A pickup point has a `capacity` (number of lockers).
- A **reservation** books one locker for one parcel (a tracking number).
- `freeLockers` = capacity minus the number of `ACTIVE` reservations.
- Business rule: you cannot reserve a locker at a pickup point with no free lockers.

Operations:

| Operation | Type | Faults |
|---|---|---|
| `getPickupPoint(id)` | read | `NotFoundFault` |
| `findPickupPointsByCity(city)` | read | `InvalidInputFault` |
| `reserveLocker(pickupPointId, trackingNumber)` | change | `NotFoundFault`, `InvalidInputFault`, `PickupPointFullFault` |
| `cancelReservation(reservationId)` | change | `NotFoundFault` |

## 2. How to run

You need Java 21+ and Maven. No Docker and no login are needed.

```bash
cd soap
mvn spring-boot:run       # starts the API on http://localhost:8082
```

- Endpoint: `http://localhost:8082/services/pickup-points` (all operations are a `POST` to this one URL)
- WSDL: `http://localhost:8082/services/pickup-points?wsdl`
- The data is **reset on every start** (`schema.sql` drops and recreates the tables, `data.sql` fills them).
- The database is an H2 file in `soap/data/`. You can open it in a database tool with the URL
  `jdbc:h2:file:./data/soap;AUTO_SERVER=TRUE`, user `sa`, empty password.

Quick try:

```bash
curl -X POST http://localhost:8082/services/pickup-points \
     -H "Content-Type: text/xml" \
     -d '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:pic="http://delivery.ek.dk/pickup">
           <soapenv:Body><pic:getPickupPoint><id>1</id></pic:getPickupPoint></soapenv:Body>
         </soapenv:Envelope>'
```

## 3. File map

| File | What it does |
|---|---|
| `pom.xml` | The libraries: web, Apache CXF (SOAP framework), JDBC, H2 |
| `src/main/resources/application.properties` | Port, CXF path `/services`, database URL |
| `src/main/resources/schema.sql` | Creates the tables `pickup_points` and `reservations` |
| `src/main/resources/data.sql` | Example data: 4 pickup points, 4 reservations |
| `SoapApiApplication.java` | Starts the application |
| `SoapConfig.java` | Publishes the service on `/pickup-points` |
| `PickupPointService.java` | The **contract**: a Java interface with JAX-WS annotations. The WSDL is generated from it |
| `PickupPointServiceImpl.java` | The implementation: validation, business rules, faults |
| `PickupPointRepository.java` | All the SQL |
| `model/PickupPoint.java`, `model/Reservation.java` | The data classes that become XML |
| `fault/NotFoundFault.java`, `fault/InvalidInputFault.java`, `fault/PickupPointFullFault.java` | The three SOAP faults |
| `postman/soap.postman_collection.json` | The test suite |

All Java files are in `src/main/java/dk/ek/delivery/soap/`.

## 4. How it works

### Code-first

We write Java. The framework (Apache CXF) creates the WSDL and the XML for us.
The opposite is "contract-first": write the WSDL/XSD by hand and generate Java from it.

### The contract: `PickupPointService.java`

```java
@WebService(name = "PickupPointPort", targetNamespace = PickupPointService.NAMESPACE)
public interface PickupPointService {

    @WebMethod
    @WebResult(name = "pickupPoint")
    PickupPoint getPickupPoint(@WebParam(name = "id") long id) throws NotFoundFault;
    ...
}
```

- `@WebService` – this interface is a SOAP service. `NAMESPACE` is `http://delivery.ek.dk/pickup`.
- `@WebMethod` – this method is an operation.
- `@WebParam(name = "id")` – the name of the XML element in the request.
- `@WebResult(name = "pickupPoint")` – the name of the XML element in the response.
- `throws NotFoundFault` – the operation can answer with this fault.

### Publishing: `SoapConfig.java`

`pickupPointEndpoint` creates an `EndpointImpl` and calls `endpoint.publish("/pickup-points")`.
Together with `cxf.path=/services` the URL is `/services/pickup-points`.
CXF answers `?wsdl` on the same URL.

### The path of a request

Example: `reserveLocker(2, "DK-555555")`

1. The client POSTs a SOAP envelope to `/services/pickup-points`.
2. CXF reads the first element in the body (`reserveLocker`) to find the operation.
   It turns the XML into Java values (JAXB) and calls `PickupPointServiceImpl.reserveLocker`.
3. The method validates `trackingNumber` with a regular expression. Invalid → `InvalidInputFault`.
4. It calls `getPickupPoint`. Unknown id → `NotFoundFault`.
5. It checks `freeLockers`. Zero → `PickupPointFullFault`.
6. `PickupPointRepository.insertReservation` inserts the row. `findReservation` reads it back.
7. CXF turns the returned `Reservation` object into XML and wraps it in a SOAP envelope.

Request:

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:pic="http://delivery.ek.dk/pickup">
  <soapenv:Header/>
  <soapenv:Body>
    <pic:reserveLocker>
      <pickupPointId>2</pickupPointId>
      <trackingNumber>DK-555555</trackingNumber>
    </pic:reserveLocker>
  </soapenv:Body>
</soapenv:Envelope>
```

Response (HTTP 200):

```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <ns2:reserveLockerResponse xmlns:ns2="http://delivery.ek.dk/pickup">
      <reservation>
        <id>5</id>
        <pickupPointId>2</pickupPointId>
        <trackingNumber>DK-555555</trackingNumber>
        <status>ACTIVE</status>
      </reservation>
    </ns2:reserveLockerResponse>
  </soap:Body>
</soap:Envelope>
```

### Faults

A fault is a Java exception with the `@WebFault` annotation, for example `fault/NotFoundFault.java`.
When the implementation throws it, CXF sends a `<soap:Fault>` with **HTTP 500**:

```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <soap:Fault>
      <faultcode>soap:Server</faultcode>
      <faultstring>Pickup point 99 not found</faultstring>
      <detail>
        <ns1:NotFoundFault xmlns:ns1="http://delivery.ek.dk/pickup"/>
      </detail>
    </soap:Fault>
  </soap:Body>
</soap:Envelope>
```

- `faultstring` is the message we gave the exception.
- `detail` holds the fault's name. A client program can use it to tell the faults apart.

### The data classes

`model/PickupPoint.java` and `model/Reservation.java` are simple classes with public fields.
`@XmlAccessorType(XmlAccessType.FIELD)` tells JAXB: every field is one XML element.
The same classes become the XSD types inside the WSDL.

### The SQL

`PickupPointRepository.SELECT_PICKUP_POINT` reads a pickup point and calculates `free_lockers`
with a sub-query that counts the `ACTIVE` reservations.
`cancelReservation` runs `UPDATE ... WHERE id = ? AND status = 'ACTIVE'` and returns `true` only if one row changed.
That is why cancelling the same reservation twice gives a `NotFoundFault` the second time.

## 5. Requirements → code

| Requirement | Where |
|---|---|
| At least 2 operations that read data | `getPickupPoint` and `findPickupPointsByCity` in `PickupPointService` / `PickupPointServiceImpl` |
| At least 2 operations that change data | `reserveLocker` (INSERT) and `cancelReservation` (UPDATE) |
| Generate a WSDL | CXF generates it at runtime from the annotations in `PickupPointService.java`. Published by `SoapConfig.pickupPointEndpoint`. URL: `/services/pickup-points?wsdl` |
| At least 2 SOAP faults | `NotFoundFault`, `InvalidInputFault`, `PickupPointFullFault` in the `fault` package. Thrown in `PickupPointServiceImpl` |
| Test suite with positive and negative cases | `postman/soap.postman_collection.json` |
| All APIs use database-stored data | H2 database: `schema.sql`, `data.sql`, `PickupPointRepository` |
| Prevent SQL injection, XSS, CSRF | Section 6 |

## 6. Security

### SQL injection

- Every statement in `PickupPointRepository` uses `?` placeholders (prepared statements).
  The database gets the SQL text and the values separately, so a value can never become SQL code.
- The only string that is added to SQL is the constant `SELECT_PICKUP_POINT`. It contains no user input.
- Second layer: the allow-list validation below rejects input like `x' OR '1'='1` before it reaches the database.
- Numbers (`id`, `pickupPointId`, `reservationId`) are typed as `long`. Text like `abc` is rejected by CXF before our code runs.

### XSS

- **Allow-list validation** in `PickupPointServiceImpl`: we describe what is allowed and reject everything else.
  - `city`: only letters, spaces and hyphens, 1–50 characters (`[\p{L} -]{1,50}`).
  - `trackingNumber`: only `A-Z`, `0-9` and `-`, 6–20 characters (`[A-Z0-9-]{6,20}`).
  - `<`, `>`, quotes and so on are not on the list, so a `<script>` tag can never be stored.
- **XML escaping.** JAXB escapes special characters when it writes the response. Text is always sent as text, never as markup.
- The fault messages are fixed texts. They never repeat the unsafe input.
- Responses are `text/xml`, not HTML.

### CSRF

- A CSRF attack needs a browser that sends **cookies** automatically. This API has no cookies, no sessions and no login. There is nothing for an attacker to "ride on".
- A SOAP call is a `POST` with `Content-Type: text/xml`. That is not a "simple" request in CORS terms.
  A browser must first send a preflight `OPTIONS` request. We have no CORS configuration, so the browser blocks the real request.
- An HTML `<form>` cannot send `text/xml` either.

### Faults as a security tool

- Every expected problem is answered with a **controlled fault** and a short, safe message.
- No stack trace, SQL text, table name or class name is sent to the client.
- Invalid input is rejected early (`InvalidInputFault`), before any database work.
- The WSDL documents the faults, so clients know exactly which errors can happen.

### Other

- CXF does not allow DTDs in incoming XML by default. This blocks XXE (XML external entity) attacks.

## 7. Tests

File: `postman/soap.postman_collection.json`

1. Start the API: `mvn spring-boot:run`.
2. Postman → **Import** → choose the file.
3. Open the collection → **Run** → run all folders **in order**.

Every request is a `POST` with `Content-Type: text/xml` and a SOAP envelope as the body
(the WSDL request is a `GET`). The tests check the HTTP status and look for text in the XML response.
The base URL is the variable `baseUrl` (`http://localhost:8082`).

| Folder | What it covers |
|---|---|
| 1. WSDL | The WSDL is generated and contains the main parts, the 4 operations and the 3 faults |
| 2. Read operations | `getPickupPoint` and `findPickupPointsByCity` (200). Negative: unknown id (`NotFoundFault`), id is not a number (CXF `soap:Client` fault), XSS attempt (`InvalidInputFault`) |
| 3. Change operations | `reserveLocker` (200, saves the new id in the variable `reservationId`), free lockers go down, `cancelReservation` (200). Negative: full pickup point (`PickupPointFullFault`), SQL injection attempt (`InvalidInputFault`), unknown pickup point (`NotFoundFault`), cancel twice (`NotFoundFault`) |

All faults answer with HTTP 500. That is normal for SOAP.

On a fresh start the new reservation gets id 5. The suite can be run again without a restart,
because the last requests cancel the reservation that was created.

## 8. Exam questions with short answers

### Explain how the SOAP API's WSDL file is created (show in code)

- **Code-first.** We do not write the WSDL. Apache CXF generates it at runtime.
- The source is `PickupPointService.java`: `@WebService`, `@WebMethod`, `@WebParam`, `@WebResult` and the `throws` clauses.
- The XML types come from `model/PickupPoint.java` and `model/Reservation.java` (JAXB).
- The names of the service and the port come from `@WebService(serviceName = ..., portName = ...)` on `PickupPointServiceImpl`.
- `SoapConfig.pickupPointEndpoint` publishes the endpoint. From then on CXF serves the WSDL at `/services/pickup-points?wsdl`.
- If we change the Java interface, the WSDL changes on the next start. There is no WSDL file in the project.

### Explain the structure of the WSDL file

Open `?wsdl` in a browser and go from top to bottom:

| WSDL part | What it describes | From our code |
|---|---|---|
| `<wsdl:definitions targetNamespace=...>` | The root element and the namespace | `PickupPointService.NAMESPACE` |
| `<wsdl:types>` | An XSD schema: the data types of all requests, responses and faults | Method parameters, `PickupPoint`, `Reservation`, the fault classes |
| `<wsdl:message>` | One message = one request, one response or one fault. It points to an element in `types` | One per method input, one per output, one per fault |
| `<wsdl:portType name="PickupPointPort">` | The abstract interface: the operations, each with input, output and faults | The interface `PickupPointService` and its 4 methods |
| `<wsdl:binding name="PickupPointServiceSoapBinding">` | **How** to call it: SOAP over HTTP, document/literal style | Created by CXF (default settings) |
| `<wsdl:service name="PickupPointService">` with `<wsdl:port>` | **Where** to call it: the URL in `<soap:address>` | `serviceName` / `portName` on the implementation and `publish("/pickup-points")` |

Easy way to remember: `types` + `message` + `portType` = **what**. `binding` = **how**. `service` = **where**.

### Explain how SOAP API security has been taken care of via faults (show in code)

- Show `PickupPointServiceImpl.findPickupPointsByCity` and `reserveLocker`: the first lines validate the input with an allow-list regular expression and throw `InvalidInputFault`.
- Unsafe input (script tags, SQL fragments) never reaches the database or the response.
- Show `fault/InvalidInputFault.java`: an exception with `@WebFault`. CXF turns it into a `<soap:Fault>`.
- The client gets a short, fixed message. Internal details stay on the server.
- Business rules use faults too: `NotFoundFault` and `PickupPointFullFault`.
- All faults are declared in the contract (`throws`), so they are in the WSDL.

### Run the SOAP API tests and explain request and response messages

A SOAP message is an XML document:

- **Envelope** – the root element. It marks the XML as a SOAP message (SOAP 1.1 namespace `http://schemas.xmlsoap.org/soap/envelope/`).
- **Header** – optional. Extra information such as security tokens or transaction ids. We do not use it.
- **Body** – required. The request: an element named after the operation (`pic:getPickupPoint`) with the parameters inside. The response: `<operation>Response` with the result inside.
- **Fault** – inside the body instead of a normal response, when something went wrong.

HTTP details: always `POST`, always the same URL, `Content-Type: text/xml`. Success is `200`, a fault is `500`.
Compare with REST: there the URL and the HTTP method say what to do. In SOAP the body says it.

### What does a SOAP API consist of? Use cases, advantages, disadvantages

- SOAP is a **protocol** for exchanging XML messages. REST is an architectural style.
- Parts: the SOAP message (envelope, header, body, fault), the **WSDL** (the contract) and **XSD** (the data types).
- It usually runs over HTTP, but it can also use other transports (for example SMTP or JMS).
- It has extensions called WS-* (for example WS-Security for signing and encrypting messages).

Use cases: banks and payments, government and healthcare systems, enterprise and legacy integration (SOA). Places where a strict, formal contract matters.

| Advantages | Disadvantages |
|---|---|
| Strict contract: the WSDL describes everything | Verbose XML: bigger and slower than JSON |
| Strong typing and validation with XSD | More complex, harder to learn |
| Client code can be generated from the WSDL | Tight coupling: clients must follow contract changes |
| Standard error format (faults) | Everything is a POST, so no HTTP caching |
| Built-in standards for security and transactions (WS-*) | Poor fit for browsers and mobile apps |
| Independent of language and transport | |

### How is a SOAP API's syntax specified?

- With **WSDL** (Web Services Description Language) and **XSD** (XML Schema Definition). Both are XML.
- XSD defines the data: which elements exist, their types and their order.
- WSDL defines the service: the operations, their messages, the binding and the address.
- The XSD is usually inside the `<wsdl:types>` part of the WSDL. That is the case in our WSDL.

### How do SOAP APIs handle errors?

- With a **Fault** element inside the body. The HTTP status is `500`.
- SOAP 1.1 fault parts:
  - `faultcode` – who is to blame: `soap:Client` (bad request) or `soap:Server` (the server could not process it).
  - `faultstring` – a message for humans.
  - `detail` – application-specific information. Ours holds the fault name, for example `NotFoundFault`.
- In our API: faults thrown by our own code get `soap:Server` (the CXF default for exceptions). XML that CXF cannot read (for example `<id>abc</id>`) gets `soap:Client`.
- Custom faults are listed in the WSDL, so a client can generate matching exception classes.
- Compare with REST: REST uses many HTTP status codes (400, 404, 409 ...). SOAP uses one status and puts the details in the fault.
