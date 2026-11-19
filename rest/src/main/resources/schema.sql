-- Recreate all tables on every start so the data is always the same.
DROP TABLE IF EXISTS deliveries;
DROP TABLE IF EXISTS couriers;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS users;

-- People who receive parcels
CREATE TABLE customers (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    city  VARCHAR(100) NOT NULL
);

-- People who deliver parcels
CREATE TABLE couriers (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    vehicle VARCHAR(50)  NOT NULL,
    phone   VARCHAR(20)  NOT NULL
);

-- One delivery belongs to one customer and (maybe) one courier
CREATE TABLE deliveries (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT       NOT NULL REFERENCES customers (id),
    courier_id  BIGINT       REFERENCES couriers (id),
    address     VARCHAR(200) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- API users (staff). Passwords are stored as BCrypt hashes, never as plain text.
CREATE TABLE users (
    username      VARCHAR(50)  PRIMARY KEY,
    password_hash VARCHAR(100) NOT NULL
);
