package dk.ek.delivery.rest.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dk.ek.delivery.rest.auth.TokenDenylist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// Security setup: the API is an OAuth2 resource server that accepts JWT bearer tokens.
@Configuration
public class SecurityConfig {

    // The secret key used to sign and verify JWTs (HMAC-SHA256).
    private final SecretKey key;

    public SecurityConfig(@Value("${jwt.secret}") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF attacks abuse cookies that the browser sends automatically.
                // We use no cookies and no sessions: the token must be put in the
                // Authorization header by the client, so CSRF is not possible.
                .csrf(csrf -> csrf.disable())
                // Stateless (REST constraint): the server keeps no session between requests.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Only login is open. Everything else needs a valid token.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .anyRequest().authenticated())
                // Read "Authorization: Bearer <JWT>" and validate it with the JwtDecoder below.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    // Creates and signs new JWTs (used by the login endpoint).
    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    // Validates incoming JWTs: signature, expiry, and that the token is not revoked.
    @Bean
    public JwtDecoder jwtDecoder(TokenDenylist denylist) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // Extra check: reject tokens whose id (jti) is on the Redis denylist.
        OAuth2TokenValidator<Jwt> notRevoked = jwt -> denylist.isRevoked(jwt.getId())
                ? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token has been revoked", null))
                : OAuth2TokenValidatorResult.success();

        // Expiry check (no extra grace time after "exp") + our revocation check.
        OAuth2TokenValidator<Jwt> notExpired = new JwtTimestampValidator(Duration.ZERO);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(notExpired, notRevoked));
        return decoder;
    }

    // BCrypt: slow, salted password hashing.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
