package dk.ek.delivery.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

// Turns our ApiException into a GraphQL error in the "errors" list of the response.
// GraphQL always answers HTTP 200; errors are reported inside the JSON body.
@Component
public class ErrorHandler extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
        if (exception instanceof ApiException apiException) {
            return GraphqlErrorBuilder.newError(environment)
                    .errorType(apiException.type)
                    .message(apiException.getMessage())
                    .build();
        }
        // Anything else becomes a generic "INTERNAL_ERROR" without details (no stack traces leak).
        return null;
    }
}
