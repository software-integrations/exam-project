package dk.ek.delivery.graphql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

// Starts the GraphQL API (embedded web server on port 8083).
// Spring normally creates ONE database connection for us. We switch that off
// because we open five databases ourselves (see Databases.java).
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class GraphQlApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphQlApiApplication.class, args);
    }
}
