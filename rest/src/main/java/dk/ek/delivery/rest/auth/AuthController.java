package dk.ek.delivery.rest.auth;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Login and logout. Login plays the role of the OAuth2 token endpoint: it issues JWTs.
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JdbcClient db;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final TokenDenylist denylist;
    private final long ttlMinutes;

    public AuthController(JdbcClient db, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                          TokenDenylist denylist, @Value("${jwt.ttl-minutes}") long ttlMinutes) {
        this.db = db;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.denylist = denylist;
        this.ttlMinutes = ttlMinutes;
    }

    // POST /api/v1/auth/login -> checks username + password and returns a JWT access token
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        // 1. Find the stored BCrypt hash of the user's password.
        Optional<String> passwordHash = db.sql("SELECT password_hash FROM users WHERE username = ?")
                .param(request.username())
                .query(String.class)
                .optional();

        // 2. Compare the password with the hash. Same error for "no user" and "wrong password".
        if (passwordHash.isEmpty() || !passwordEncoder.matches(request.password(), passwordHash.get())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong username or password");
        }

        // 3. Build the claims (the data inside the JWT).
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())           // jti: unique id, used for revocation
                .subject(request.username())                // sub: who the token belongs to
                .issuedAt(now)                              // iat: when it was created
                .expiresAt(now.plusSeconds(ttlMinutes * 60)) // exp: when it stops working
                .build();

        // 4. Sign the JWT with HMAC-SHA256 and our secret key.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        // 5. Answer in the standard OAuth2 token response format.
        return Map.of(
                "access_token", token,
                "token_type", "Bearer",
                "expires_in", ttlMinutes * 60);
    }

    // POST /api/v1/auth/logout -> revokes the token that was used to call this endpoint
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        denylist.revoke(jwt.getId(), jwt.getExpiresAt());
        return ResponseEntity.noContent().build();
    }
}
