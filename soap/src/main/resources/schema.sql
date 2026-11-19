-- Recreate all tables on every start so the data is always the same.
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS pickup_points;

-- A parcel shop / locker wall where customers collect parcels
CREATE TABLE pickup_points (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(100) NOT NULL,
    city     VARCHAR(100) NOT NULL,
    address  VARCHAR(200) NOT NULL,
    capacity INT          NOT NULL
);

-- A locker reserved for one parcel at a pickup point
CREATE TABLE reservations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    pickup_point_id BIGINT      NOT NULL REFERENCES pickup_points (id),
    tracking_number VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL
);
