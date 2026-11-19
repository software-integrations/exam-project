package dk.ek.delivery.graphql.auth;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

// Runs before every GraphQL request. It reads the "Authorization: Bearer <token>" header
// and puts the token and the username into the GraphQL context,
// where the controller methods can read them with @ContextValue.
@Component
public class AuthInterceptor implements WebGraphQlInterceptor {

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String header = request.getHeaders().getFirst("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            Map<String, Object> context = new HashMap<>();
            context.put("token", token);
            // Only a valid (known and not expired) token gives a username.
            authService.findUsername(token).ifPresent(username -> context.put("username", username));
            request.configureExecutionInput((input, builder) -> builder.graphQLContext(context).build());
        }
        // Continue with the normal GraphQL execution.
        return chain.next(request);
    }
}
