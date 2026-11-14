package com.patterncatalyst.shipping.join;

import javax.sql.DataSource;

import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.processor.aggregate.jdbc.PostgresAggregationRepository;
import org.apache.camel.spi.AggregationRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Where the Camel aggregator keeps open groups: the shipping.readiness table, written through the transaction manager
 * each time the group changes. Demo code: AGGREGATION_REPOSITORY=memory keeps the groups in the JVM instead, so a restart
 * loses them.
 */
@ApplicationScoped
public class ReadinessRepository {

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "readiness.repository", defaultValue = "postgres")
    String kind;

    @Produces
    AggregationRepository readinessRepository() {
        if ("memory".equals(kind)) {
            return new MemoryAggregationRepository();
        }
        return new PostgresAggregationRepository(new DataSourceTransactionManager(dataSource), "shipping.readiness", dataSource);
    }
}
