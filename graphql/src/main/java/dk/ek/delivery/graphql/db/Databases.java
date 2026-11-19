package dk.ek.delivery.graphql.db;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

// Opens the five independent databases of this API:
// 3 shards (same tables), 1 warehouse (ETL destination) and 1 auth database.
@Component
public class Databases {

    public static final int SHARD_COUNT = 3;

    // shards.get(0), shards.get(1), shards.get(2)
    public final List<JdbcClient> shards = new ArrayList<>();
    public final JdbcClient warehouse;
    public final JdbcClient auth;

    public Databases() {
        for (int i = 0; i < SHARD_COUNT; i++) {
            shards.add(open("shard" + i, "shard-schema.sql"));
        }
        warehouse = open("warehouse", "warehouse-schema.sql");
        auth = open("auth", "auth-schema.sql");
    }

    // Opens one H2 database file (./data/<name>) and runs its schema script.
    // AUTO_SERVER lets a database tool connect while the API runs.
    private static JdbcClient open(String name, String schemaFile) {
        DataSource dataSource = DataSourceBuilder.create()
                .url("jdbc:h2:file:./data/" + name + ";AUTO_SERVER=TRUE")
                .username("sa")
                .password("")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource(schemaFile)).execute(dataSource);
        return JdbcClient.create(dataSource);
    }
}
