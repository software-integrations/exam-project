package dk.ek.delivery.graphql.etl;

import dk.ek.delivery.graphql.db.Databases;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

// The ETL process: Extract from the 3 shards, Transform, Load into the warehouse.
@Service
public class EtlService {

    private final Databases databases;
    private final JdbcClient warehouse;

    public EtlService(Databases databases) {
        this.databases = databases;
        this.warehouse = databases.warehouse;
    }

    public EtlResult run() {
        int customersExtracted = 0;
        int parcelsExtracted = 0;

        for (int shard = 0; shard < Databases.SHARD_COUNT; shard++) {
            JdbcClient source = databases.shards.get(shard);

            // ---------- Customers ----------
            // EXTRACT: read all customers of this shard.
            List<Map<String, Object>> customers = source.sql("SELECT * FROM customers").query().listOfRows();
            for (Map<String, Object> customer : customers) {
                // TRANSFORM: clean up the text so the warehouse data is consistent.
                String email = customer.get("EMAIL").toString().trim().toLowerCase();
                String name = customer.get("NAME").toString().trim();
                String city = customer.get("CITY").toString().trim().toUpperCase();

                // LOAD: MERGE = "update the row if it exists, otherwise insert it" (upsert).
                // The KEY is (source_shard, source_id). Shard 0 and shard 1 can both have a
                // customer with id 1; together with the shard number the key is unique.
                // Running the ETL again updates the same rows -> no duplicates.
                warehouse.sql("""
                                MERGE INTO customers (source_shard, source_id, email, name, city)
                                KEY (source_shard, source_id)
                                VALUES (?, ?, ?, ?, ?)
                                """)
                        .params(shard, customer.get("ID"), email, name, city)
                        .update();
            }
            customersExtracted += customers.size();

            // ---------- Parcels ----------
            // EXTRACT: read all parcels of this shard.
            List<Map<String, Object>> parcels = source.sql("SELECT * FROM parcels").query().listOfRows();
            for (Map<String, Object> parcel : parcels) {
                // TRANSFORM 1: replace the shard's customer id with the warehouse's new customer_key.
                long customerKey = warehouse.sql("SELECT customer_key FROM customers WHERE source_shard = ? AND source_id = ?")
                        .params(shard, parcel.get("CUSTOMER_ID"))
                        .query(Long.class)
                        .single();
                // TRANSFORM 2: calculate a new column that does not exist in the shards.
                double weightKg = ((Number) parcel.get("WEIGHT_KG")).doubleValue();
                String weightClass = weightKg < 1 ? "LIGHT" : weightKg < 10 ? "MEDIUM" : "HEAVY";

                // LOAD: the same upsert idea as for customers.
                warehouse.sql("""
                                MERGE INTO parcels (source_shard, source_id, customer_key, description,
                                                    weight_kg, weight_class, status, created_at)
                                KEY (source_shard, source_id)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                                """)
                        .params(shard, parcel.get("ID"), customerKey, parcel.get("DESCRIPTION"),
                                weightKg, weightClass, parcel.get("STATUS"), parcel.get("CREATED_AT"))
                        .update();
            }
            parcelsExtracted += parcels.size();
        }

        // Count what is in the warehouse now. These numbers stay the same on a second run.
        int warehouseCustomers = warehouse.sql("SELECT COUNT(*) FROM customers").query(Integer.class).single();
        int warehouseParcels = warehouse.sql("SELECT COUNT(*) FROM parcels").query(Integer.class).single();
        return new EtlResult(customersExtracted, parcelsExtracted, warehouseCustomers, warehouseParcels);
    }
}
