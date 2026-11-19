-- Schema of the warehouse: the separate database the ETL process loads into.
DROP TABLE IF EXISTS parcels;
DROP TABLE IF EXISTS customers;

-- customer_key is a NEW id that is unique in the warehouse.
-- (source_shard, source_id) says where the row came from. It is UNIQUE,
-- so the same source row can never be loaded twice.
CREATE TABLE customers (
    customer_key BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_shard INT          NOT NULL,
    source_id    BIGINT       NOT NULL,
    email        VARCHAR(100) NOT NULL,
    name         VARCHAR(100) NOT NULL,
    city         VARCHAR(100) NOT NULL,
    UNIQUE (source_shard, source_id)
);

-- customer_key points to the NEW customer id, not to the old id from the shard.
-- weight_class is calculated by the ETL process (it does not exist in the shards).
CREATE TABLE parcels (
    parcel_key   BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_shard INT          NOT NULL,
    source_id    BIGINT       NOT NULL,
    customer_key BIGINT       NOT NULL REFERENCES customers (customer_key),
    description  VARCHAR(200) NOT NULL,
    weight_kg    DOUBLE       NOT NULL,
    weight_class VARCHAR(10)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    UNIQUE (source_shard, source_id)
);
