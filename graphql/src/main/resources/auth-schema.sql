-- Schema of the auth database: API users and their login tokens.
DROP TABLE IF EXISTS tokens;
DROP TABLE IF EXISTS users;

-- Passwords are stored as BCrypt hashes, never as plain text.
CREATE TABLE users (
    username      VARCHAR(50)  PRIMARY KEY,
    password_hash VARCHAR(100) NOT NULL
);

-- Token hardening: only the SHA-256 hash of a token is stored, never the token itself.
CREATE TABLE tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    username   VARCHAR(50) NOT NULL REFERENCES users (username),
    expires_at TIMESTAMP   NOT NULL
);
