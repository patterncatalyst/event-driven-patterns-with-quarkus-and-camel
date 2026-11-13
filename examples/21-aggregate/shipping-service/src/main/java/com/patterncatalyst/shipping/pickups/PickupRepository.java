package com.patterncatalyst.shipping.pickups;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import javax.sql.DataSource;
import org.apache.camel.processor.aggregate.jdbc.PostgresAggregationRepository;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * Where the open batches live: the tables {@code shipping.pickups} and {@code shipping.pickups_completed}, written
 * through the Agroal datasource Quarkus configures. Camel's repository takes a Spring transaction manager over that
 * datasource; no Spring context is involved.
 */
@ApplicationScoped
public class PickupRepository {

    @Produces
    @Named("pickupsRepository")
    PostgresAggregationRepository pickups(DataSource dataSource) {
        return new PostgresAggregationRepository(new DataSourceTransactionManager(dataSource), "shipping.pickups", dataSource);
    }
}
