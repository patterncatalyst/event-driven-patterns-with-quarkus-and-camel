package com.patterncatalyst.shipping.metrics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.inject.Singleton;

/**
 * The outbox backlog: the bytes of write-ahead log the connector's replication slot has not confirmed. The outbox
 * table cannot show it, because every row is deleted in the transaction that inserts it. With backlog.source=table
 * the gauge counts rows instead (demo code), which stays at zero while the relay is stopped.
 */
@Singleton
public class OutboxBacklog implements MeterBinder {

    private static final Logger LOG = Logger.getLogger(OutboxBacklog.class);

    private static final String SLOT_LAG = """
            SELECT coalesce(pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn), 0)
            FROM pg_replication_slots WHERE slot_name = ?""";
    private static final String TABLE_ROWS = "SELECT count(*) FROM orders.outboxevent";

    private final AgroalDataSource dataSource;
    private final String slot;
    private final String source;

    OutboxBacklog(AgroalDataSource dataSource, @ConfigProperty(name = "backlog.slot") String slot,
            @ConfigProperty(name = "backlog.source") String source) {
        this.dataSource = dataSource;
        this.slot = slot;
        this.source = source;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("outbox.backlog", this, OutboxBacklog::read)
                .baseUnit("bytes")
                .description("Write-ahead log the connector has not confirmed")
                .tag("slot", slot)
                .register(registry);
    }

    double read() {
        boolean rows = "table".equals(source);
        try (Connection c = dataSource.getConnection();
                PreparedStatement s = c.prepareStatement(rows ? TABLE_ROWS : SLOT_LAG)) {
            if (!rows) {
                s.setString(1, slot);
            }
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getDouble(1) : 0;
            }
        } catch (Exception e) {
            LOG.warn("backlog read failed", e);
            return Double.NaN;
        }
    }
}
