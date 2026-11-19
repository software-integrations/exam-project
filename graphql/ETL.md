# Sharding and ETL

The GraphQL API stores its data in **3 shards** and has an **ETL process** that copies
everything into one **warehouse** database.

```
                       hash(email) % 3
 GraphQL API ──────────────┬──────────────┬──────────────┐
                           ▼              ▼              ▼
                       shard0         shard1         shard2        (same tables)
                    customers      customers      customers
                    parcels        parcels        parcels
                           │              │              │
                           └────── ETL (runEtl) ─────────┘
                                          ▼
                                     warehouse
                               customers + parcels (new ids)
```

## 1. The databases

`db/Databases.java` opens five independent H2 database files in `graphql/data/`:

| Database | Schema file | Content |
|---|---|---|
| `shard0`, `shard1`, `shard2` | `shard-schema.sql` | `customers` and `parcels` (related: `parcels.customer_id → customers.id`) |
| `warehouse` | `warehouse-schema.sql` | Consolidated `customers` and `parcels` |
| `auth` | `auth-schema.sql` | API users and token hashes (not sharded) |

Every shard runs the **same** schema file, so the structures are equivalent.
The shards do not know about each other.

## 2. Hash-based sharding (`db/ShardRouter.java`)

```java
public int shardIndexFor(String email) {
    CRC32 hash = new CRC32();
    hash.update(email.toLowerCase().getBytes(StandardCharsets.UTF_8));
    return (int) (hash.getValue() % Databases.SHARD_COUNT);
}
```

- **Shard key**: the customer's email.
- **Hash function**: CRC32 turns the email into a number. It is deterministic: the same email
  always gives the same number.
- **Modulo 3** turns the number into 0, 1 or 2 → the shard.
- A hash spreads the customers evenly, no matter what the emails look like.
- **Parcels** have no own shard key. A parcel is stored in the shard of its customer, so a
  customer and its parcels are always together and can be joined inside one shard.

Why not use the id as shard key? The id is created by the shard *after* we have picked a shard.
We need a key that exists before the insert, so we use the email.

## 3. Routing reads and writes (`CustomerService.java`)

| Operation | Method | Shard |
|---|---|---|
| Write customer | `registerCustomer` | `router.shardFor(email)` |
| Write parcel | `createParcel` | shard of the customer (`customer.shard()`) |
| Read one customer | `findCustomer` | `router.shardIndexFor(email)` → one shard |
| Read parcels | `findParcels` | shard of the customer |
| Read all customers | `findAllCustomers` | loops over all 3 shards and merges ("scatter-gather") |

The caller never chooses a shard. The router does it automatically from the email.

`DataSeeder` inserts the start data through `CustomerService`, so the start data is also
distributed by the hash function (3 customers per shard).

## 4. The ETL process (`etl/EtlService.java`)

Started with the mutation `runEtl` (needs a bearer token). For each shard:

| Step | What | Code |
|---|---|---|
| **Extract** | Read all customers and all parcels of the shard | `SELECT * FROM customers`, `SELECT * FROM parcels` |
| **Transform** | Clean text: email → lower case, city → UPPER CASE, trim spaces | in the customer loop |
| | Replace the shard's `customer_id` with the warehouse's `customer_key` | lookup by `(source_shard, source_id)` |
| | Calculate the new column `weight_class` (LIGHT / MEDIUM / HEAVY) | from `weight_kg` |
| **Load** | Upsert into the warehouse | `MERGE INTO ... KEY (source_shard, source_id)` |

### Identifier conflicts

Every shard counts ids from 1. So customer `1` exists in shard 0, shard 1 **and** shard 2.
They are three different people.

Solution in the warehouse (`warehouse-schema.sql`):

- `source_shard` + `source_id` together say where a row came from. This pair is unique
  (`UNIQUE (source_shard, source_id)`).
- `customer_key` / `parcel_key` is a **new** id made by the warehouse (a surrogate key),
  unique across everything.
- Parcels point to the new `customer_key`, not to the old shard id.

### No duplicates on re-run (idempotent)

`MERGE INTO ... KEY (source_shard, source_id)` means:
"if a row with this key exists → update it, otherwise → insert it".

- First run: all rows are inserted.
- Second run: the same rows are found by their key and updated. Nothing is added.
- New rows in the shards are inserted on the next run; changed rows are updated.
  So no duplicates and no missing information.

## 5. Showcase at the exam

1. Start the API: `mvn spring-boot:run`.
2. Open `http://localhost:8083/h2-console`. User `sa`, empty password. JDBC URLs:
   - `jdbc:h2:file:./data/shard0;AUTO_SERVER=TRUE` (also `shard1`, `shard2`)
   - `jdbc:h2:file:./data/warehouse;AUTO_SERVER=TRUE`

   (The H2 console is a development tool. It would be switched off in production.)
3. Show `SELECT * FROM customers` in each shard: different customers, the **same ids** (1, 2, 3).
4. Show the warehouse: empty.
5. In Postman (or GraphiQL with the header): `login`, then `mutation { runEtl { customersExtracted parcelsExtracted warehouseCustomers warehouseParcels } }`.
6. Show the warehouse: 9 customers, 10 parcels, with `source_shard`, `source_id` and new keys.
7. Run `runEtl` again: the counts are the same → no duplicates.
8. Register a new customer + parcel (`registerCustomer`, `createParcel`), show which shard got it,
   run `runEtl` again → the warehouse has one more of each.

Useful warehouse query:

```sql
SELECT c.customer_key, c.source_shard, c.source_id, c.name, p.description, p.weight_class
FROM customers c JOIN parcels p ON p.customer_key = c.customer_key
ORDER BY c.customer_key;
```

## 6. Checklist

| Check | How it is met |
|---|---|
| Data is distributed correctly across the shards | `ShardRouter.shardIndexFor` – query `{ customers { email shard } }` shows it |
| Operations are routed to the right shard | `CustomerService` always goes through the router; Postman test "read back from its shard" |
| Warehouse consolidates all 3 shards | `EtlService.run` loops over all shards; Postman test "runEtl - first run" |
| Re-run creates no duplicates / loses nothing | `MERGE ... KEY (source_shard, source_id)`; Postman test "second run" |

## 7. Exam questions – short answers

**Explain your implementation of hash-based sharding.** Shard key = email. `CRC32(email) % 3`
picks the shard (`ShardRouter`). Parcels follow their customer. All reads/writes go through the router.

**Explain the consolidation strategy of the ETL.** Loop over the shards; extract everything;
transform (clean text, map to new keys, calculate `weight_class`); load with an upsert keyed on
`(source_shard, source_id)`. New surrogate keys solve the id conflicts; the upsert makes re-runs safe.

**Replication vs partitioning vs sharding.**
- *Replication*: the **same** data is copied to several servers. Gives availability and faster reads.
- *Partitioning*: one table is split into smaller parts, usually inside the **same** database server.
  Makes big tables faster to query and maintain.
- *Sharding*: horizontal partitioning across **different** database servers. Each shard has a part
  of the rows. Gives write scalability and more storage. This project does sharding.
- Use replication for read-heavy systems and fail-over, partitioning for very large tables,
  sharding when one server can no longer handle the writes/data. They are often combined.

**Leader-follower vs leader-leader replication.**
- *Leader-follower*: one leader takes all writes and copies them to the followers. Followers serve
  reads. Simple, no write conflicts, but the leader is a bottleneck / single point for writes
  and followers can lag behind.
- *Leader-leader* (multi-leader): several nodes accept writes and replicate to each other.
  Better write availability, but conflicts are possible and must be resolved.

**Vertical vs horizontal partitioning; horizontal strategies.**
- *Vertical*: split by **columns** (e.g. rarely used or large columns go to another table).
- *Horizontal*: split by **rows** (all columns, different rows in each part).
- Horizontal strategies:
  - **Range** (e.g. A–H, I–P, Q–Z or by date): simple, good for range queries, can become uneven.
  - **Hash** (this project): even distribution, but range queries hit all shards and adding a
    shard moves a lot of data (consistent hashing reduces that).
  - **List** (e.g. by country): easy to understand, uneven if one value is popular.
  - **Directory/lookup**: a table says where each key lives. Flexible, but an extra lookup.

**Weaknesses of this solution (good to mention).** `% 3` is fixed: adding a 4th shard changes the
shard of most emails (re-sharding). Queries without the shard key (`customers`) must ask every shard.
There are no transactions across shards. The ETL does a full extract each time (fine for small data;
an incremental extract with an `updated_at` column would scale better) and does not remove rows
that were deleted in a shard (the API has no delete).
