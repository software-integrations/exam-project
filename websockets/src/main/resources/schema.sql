-- Recreate all tables on every start so the data is always the same.
DROP TABLE IF EXISTS location_updates;
DROP TABLE IF EXISTS deliveries;

-- A delivery on its way to a destination (dest_lat, dest_lng).
-- last_lat / last_lng is the courier's latest known position (empty until the first update).
CREATE TABLE deliveries (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient  VARCHAR(100) NOT NULL,
    address    VARCHAR(200) NOT NULL,
    dest_lat   DOUBLE       NOT NULL,
    dest_lng   DOUBLE       NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    last_lat   DOUBLE,
    last_lng   DOUBLE,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Every position the courier has sent (the history of a delivery).
CREATE TABLE location_updates (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    delivery_id BIGINT    NOT NULL REFERENCES deliveries (id),
    lat         DOUBLE    NOT NULL,
    lng         DOUBLE    NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
