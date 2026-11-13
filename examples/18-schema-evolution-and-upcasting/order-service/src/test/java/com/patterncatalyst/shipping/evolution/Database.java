package com.patterncatalyst.shipping.evolution;

import java.sql.SQLException;

import io.agroal.api.AgroalDataSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Plain SQL against the Dev Services database, outside Hibernate, for rows the current code can no longer write. */
@Singleton
class Database {

    @Inject
    AgroalDataSource dataSource;

    void execute(String sql) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    String text(String sql) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    long count(String sql) throws SQLException {
        return Long.parseLong(text(sql));
    }

    /** A stream as the previous release stored it: OrderPlaced at schema_version 1, no shippingPriority. */
    void storeV1Order(String id) throws SQLException {
        execute("INSERT INTO orders.events (event_id, stream_type, stream_id, version, type, schema_version, payload, occurred_at) "
                + "VALUES (gen_random_uuid(), 'Order', '" + id + "', 1, 'OrderPlaced', 1, "
                + "'{\"order\":{\"id\":\"" + id + "\",\"customerId\":\"C-1\",\"itemSku\":\"SKU-1\",\"quantity\":1,\"amount\":25.00}}'::jsonb, now())");
    }
}
