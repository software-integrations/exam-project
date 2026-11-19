-- Schema of ONE shard. All 3 shards get exactly the same tables.
DROP TABLE IF EXISTS parcels;
DROP TABLE IF EXISTS customers;

-- The id counts from 1 in every shard, so ids are NOT unique across shards.
CREATE TABLE customers (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    name  VARCHAR(100) NOT NULL,
    city  VARCHAR(100) NOT NULL
);

-- A parcel always lives in the same shard as its customer.
CREATE TABLE parcels (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT       NOT NULL REFERENCES customers (id),
    description VARCHAR(200) NOT NULL,
    weight_kg   DOUBLE       NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
