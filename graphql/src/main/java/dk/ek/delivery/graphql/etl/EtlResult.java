package dk.ek.delivery.graphql.etl;

// Summary of one ETL run (matches the EtlResult type in the SDL schema).
public record EtlResult(int customersExtracted, int parcelsExtracted,
                        int warehouseCustomers, int warehouseParcels) {
}
