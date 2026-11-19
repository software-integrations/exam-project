package dk.ek.delivery.graphql.auth;

import dk.ek.delivery.graphql.ApiException;
import dk.ek.delivery.graphql.db.Databases;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

// Bearer-token authentication. The token is a random string (an "opaque" token).
// Hardening: the database only stores the SHA-256 hash of the token. If the database
// leaks, the attacker gets hashes, and a hash cannot be used to log in.
@Service
public class AuthService {

    private final JdbcClient db;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public AuthService(Databases databases) {
        this.db = databases.auth;
    }

    // Creates an API user. The password is stored as a BCrypt hash.
    public void createUser(String username, String password) {
        db.sql("INSERT INTO users (username, password_hash) VALUES (?, ?)")
                .params(username, passwordEncoder.encode(password))
                .update();
    }

    // Checks the password and returns a new token. The client must keep the token.
    public String login(String username, String password) {
        Optional<String> passwordHash = db.sql("SELECT password_hash FROM users WHERE username = ?")
                .param(username)
                .query(String.class)
                .optional();
        if (passwordHash.isEmpty() || !passwordEncoder.matches(password, passwordHash.get())) {
            throw new ApiException(ErrorType.UNAUTHORIZED, "Wrong username or password");
        }

        // 32 random bytes from a cryptographically secure generator -> impossible to guess.
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        // Store only the hash of the token, valid for one hour.
        db.sql("INSERT INTO tokens (token_hash, username, expires_at) VALUES (?, ?, ?)")
                .params(sha256(token), username, LocalDateTime.now().plusHours(1))
                .update();
        return token;
    }

    // Deletes the token, so it stops working immediately.
    public void logout(String token) {
        db.sql("DELETE FROM tokens WHERE token_hash = ?")
                .param(sha256(token))
                .update();
    }

    // Finds the user that owns a token: hash the incoming token and look up the hash.
    // Empty if the token is unknown or expired.
    public Optional<String> findUsername(String token) {
        return db.sql("SELECT username FROM tokens WHERE token_hash = ? AND expires_at > ?")
                .params(sha256(token), LocalDateTime.now())
                .query(String.class)
                .optional();
    }

    // SHA-256 hash as 64 hex characters. One-way: the token cannot be calculated from the hash.
    private static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
