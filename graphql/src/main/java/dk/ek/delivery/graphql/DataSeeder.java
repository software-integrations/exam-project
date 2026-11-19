package dk.ek.delivery.graphql;

import dk.ek.delivery.graphql.auth.AuthService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// Inserts example data when the API starts.
// The data goes through CustomerService, so it is spread over the shards by the hash function.
@Component
public class DataSeeder implements CommandLineRunner {

    private final AuthService authService;
    private final CustomerService customerService;

    public DataSeeder(AuthService authService, CustomerService customerService) {
        this.authService = authService;
        this.customerService = customerService;
    }

    @Override
    public void run(String... args) {
        // Login: dispatcher / password123
        authService.createUser("dispatcher", "password123");

        customerService.registerCustomer("Anna Jensen", "anna@example.com", "Copenhagen");
        customerService.registerCustomer("Bo Nielsen", "bo@example.com", "Aarhus");
        customerService.registerCustomer("Clara Hansen", "clara@example.com", "Odense");
        customerService.registerCustomer("David Pedersen", "david@example.com", "Aalborg");
        customerService.registerCustomer("Emma Andersen", "emma@example.com", "Copenhagen");
        customerService.registerCustomer("Karen Thomsen", "karen@example.com", "Esbjerg");
        customerService.registerCustomer("Gry Sorensen", "gry@example.com", "Roskilde");
        customerService.registerCustomer("Oscar Lund", "oscar@example.com", "Copenhagen");
        customerService.registerCustomer("Rasmus Holm", "rasmus@example.com", "Aarhus");

        customerService.createParcel("anna@example.com", "Books", 2.5);
        customerService.createParcel("anna@example.com", "Phone case", 0.2);
        customerService.createParcel("bo@example.com", "Office chair", 14.0);
        customerService.createParcel("clara@example.com", "Shoes", 1.1);
        customerService.createParcel("david@example.com", "Headphones", 0.4);
        customerService.createParcel("emma@example.com", "Coffee machine", 6.3);
        customerService.createParcel("karen@example.com", "Bicycle wheel", 3.0);
        customerService.createParcel("gry@example.com", "Winter jacket", 1.8);
        customerService.createParcel("oscar@example.com", "Dumbbells", 20.0);
        customerService.createParcel("rasmus@example.com", "Postcards", 0.1);
    }
}
