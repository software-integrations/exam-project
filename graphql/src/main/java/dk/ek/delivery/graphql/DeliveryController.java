package dk.ek.delivery.graphql;

import dk.ek.delivery.graphql.auth.AuthService;
import dk.ek.delivery.graphql.etl.EtlResult;
import dk.ek.delivery.graphql.etl.EtlService;
import dk.ek.delivery.graphql.model.Customer;
import dk.ek.delivery.graphql.model.Parcel;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Controller;

import java.util.List;

// The resolvers: one Java method for each field of Query and Mutation in schema.graphqls.
// The method name must match the field name in the schema.
@Controller
public class DeliveryController {

    private final CustomerService customerService;
    private final AuthService authService;
    private final EtlService etlService;

    public DeliveryController(CustomerService customerService, AuthService authService, EtlService etlService) {
        this.customerService = customerService;
        this.authService = authService;
        this.etlService = etlService;
    }

    // ---------- Queries ----------

    // query { customer(email: "...") { name } }
    @QueryMapping
    public Customer customer(@Argument String email) {
        return customerService.findCustomer(email).orElse(null);
    }

    // query { customers { name shard } }
    @QueryMapping
    public List<Customer> customers() {
        return customerService.findAllCustomers();
    }

    // Resolves the "parcels" field of the Customer type.
    // It only runs when the client asks for parcels -> no overfetching.
    @SchemaMapping(typeName = "Customer", field = "parcels")
    public List<Parcel> parcels(Customer customer) {
        return customerService.findParcels(customer);
    }

    // ---------- Authentication mutations ----------

    // mutation { login(username: "...", password: "...") } -> returns the bearer token
    @MutationMapping
    public String login(@Argument String username, @Argument String password) {
        return authService.login(username, password);
    }

    // mutation { logout } -> deletes the token from the Authorization header
    @MutationMapping
    public boolean logout(@ContextValue(required = false) String username,
                          @ContextValue(required = false) String token) {
        requireLogin(username);
        authService.logout(token);
        return true;
    }

    // ---------- Mutations for authenticated users only ----------

    @MutationMapping
    public Customer registerCustomer(@Argument String name, @Argument String email, @Argument String city,
                                     @ContextValue(required = false) String username) {
        requireLogin(username);
        return customerService.registerCustomer(name, email, city);
    }

    @MutationMapping
    public Parcel createParcel(@Argument String customerEmail, @Argument String description,
                               @Argument double weightKg,
                               @ContextValue(required = false) String username) {
        requireLogin(username);
        return customerService.createParcel(customerEmail, description, weightKg);
    }

    @MutationMapping
    public EtlResult runEtl(@ContextValue(required = false) String username) {
        requireLogin(username);
        return etlService.run();
    }

    // The AuthInterceptor only sets "username" when the bearer token is valid.
    private void requireLogin(String username) {
        if (username == null) {
            throw new ApiException(ErrorType.UNAUTHORIZED, "You must log in and send a valid bearer token");
        }
    }
}
