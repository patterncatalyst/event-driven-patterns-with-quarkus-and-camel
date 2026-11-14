package com.patterncatalyst.shipping.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Order;

import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** The backlog gauge against a real logical replication slot on a PostgreSQL with wal_level=logical. */
@QuarkusTest
class OutboxBacklogTest {

    private static final String PUBLICATION = "edp_backlog_test";

    @Inject
    AgroalDataSource dataSource;

    @Inject
    OrderService orders;

    @ConfigProperty(name = "backlog.slot")
    String slot;

    private OutboxBacklog fromSlot() {
        return new OutboxBacklog(dataSource, slot, "slot");
    }

    private void run(String sql, String... args) throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                s.setString(i + 1, args[i]);
            }
            s.execute();
        }
    }

    private long consume() throws Exception {
        try (Connection c = dataSource.getConnection();
                PreparedStatement s = c.prepareStatement("SELECT count(*) FROM pg_logical_slot_get_binary_changes(?, NULL, NULL, "
                        + "'proto_version', '1', 'publication_names', '" + PUBLICATION + "')")) {
            s.setString(1, slot);
            try (ResultSet r = s.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    @BeforeEach
    void createSlot() throws Exception {
        dropSlot();
        run("DROP PUBLICATION IF EXISTS " + PUBLICATION);
        run("CREATE PUBLICATION " + PUBLICATION + " FOR ALL TABLES");
        run("SELECT pg_create_logical_replication_slot(?, 'pgoutput')", slot);
    }

    @AfterEach
    void dropSlot() throws Exception {
        run("SELECT pg_drop_replication_slot(slot_name) FROM pg_replication_slots WHERE slot_name = ?", slot);
    }

    private void place(int count) {
        for (int i = 0; i < count; i++) {
            orders.place(new Order("O-" + UUID.randomUUID(), "CU-26", "SKU-26", 1, new BigDecimal("10.00")));
        }
    }

    @Test
    void slotLagGrowsWithWritesAndFallsWhenTheSlotConfirms() throws Exception {
        double atRest = fromSlot().read();
        place(10);
        double behind = fromSlot().read();
        assertTrue(behind > atRest, "lag grows while nothing reads the slot: " + atRest + " then " + behind);

        assertTrue(consume() > 0);
        double drained = fromSlot().read();
        assertTrue(drained < behind, "lag falls once the slot's consumer confirms: " + behind + " then " + drained);
    }

    @Test
    void tableSourceStaysAtZeroWhileTheSlotFallsBehind() {
        place(10);
        assertEquals(0, new OutboxBacklog(dataSource, slot, "table").read(),
                "every outbox row is deleted in its own transaction");
        assertTrue(fromSlot().read() > 0);
    }

    @Test
    void anUnknownSlotReadsZero() {
        assertEquals(0, new OutboxBacklog(dataSource, "no_such_slot", "slot").read());
    }

    @Test
    void gaugeIsRegisteredInBytesWithTheSlotAsATag() {
        var registry = new SimpleMeterRegistry();
        fromSlot().bindTo(registry);
        var gauge = registry.find("outbox.backlog").tag("slot", slot).gauge();
        assertNotNull(gauge);
        assertEquals("bytes", gauge.getId().getBaseUnit());
    }
}
