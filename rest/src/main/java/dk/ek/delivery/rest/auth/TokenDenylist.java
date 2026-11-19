package dk.ek.delivery.rest.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

// Revocation strategy: a denylist of revoked token ids, stored in Redis.
// Redis is a key-value store that is independent of the API's SQL database.
@Component
public class TokenDenylist {

    private final StringRedisTemplate redis;

    public TokenDenylist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // Stores the token id (jti). Redis deletes the key by itself when the token
    // would have expired anyway, so the list never grows forever.
    public void revoke(String tokenId, Instant expiresAt) {
        Duration timeLeft = Duration.between(Instant.now(), expiresAt);
        // A token that has already expired does not need to be stored.
        if (timeLeft.isPositive()) {
            redis.opsForValue().set("revoked:" + tokenId, "1", timeLeft);
        }
    }

    // A token is revoked if its id is in Redis.
    public boolean isRevoked(String tokenId) {
        return Boolean.TRUE.equals(redis.hasKey("revoked:" + tokenId));
    }
}
