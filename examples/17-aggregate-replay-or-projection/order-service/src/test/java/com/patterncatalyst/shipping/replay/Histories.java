package com.patterncatalyst.shipping.replay;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Test helpers. The events table is append-only, so a test writes its own orders on days of its own (a year no
 * other test uses) and compares only those rows.
 */
final class Histories {

    private Histories() {
    }

    private static void insert(Connection c, String order, int version, String type, String payload, String at)
            throws SQLException {
        try (PreparedStatement st = c.prepareStatement("""
                INSERT INTO orders.events (event_id, stream_type, stream_id, version, type, payload, occurred_at)
                VALUES (gen_random_uuid(), 'Order', ?, ?, ?, ?::jsonb, ?::timestamptz)
                """)) {
            st.setString(1, order);
            st.setInt(2, version);
            st.setString(3, type);
            st.setString(4, payload);
            st.setString(5, at);
            st.executeUpdate();
        }
    }

    static void placed(Connection c, String order) throws SQLException {
        insert(c, order, 1, "OrderPlaced", "{\"kind\":\"OrderPlaced\",\"orderId\":\"" + order + "\"}", "2000-01-01T00:00:00Z");
    }

    static void paid(Connection c, String order) throws SQLException {
        insert(c, order, 2, "OrderPaid", "{\"kind\":\"OrderPaid\",\"paymentId\":\"P-" + order + "\"}", "2000-01-01T00:00:00Z");
    }

    /** Version 3: the shipment, at the given instant. */
    static void shipped(Connection c, String order, String carrier, String at) throws SQLException {
        insert(c, order, 3, "OrderShipped",
                "{\"kind\":\"OrderShipped\",\"shipmentId\":\"S-" + order + "\",\"carrier\":\"" + carrier + "\"}", at);
    }

    static void delivered(Connection c, String order) throws SQLException {
        insert(c, order, 4, "OrderDelivered", "{\"kind\":\"OrderDelivered\"}", "2000-01-01T00:00:00Z");
    }

    /** Version 3: a cancellation instead of a shipment. */
    static void cancelled(Connection c, String order) throws SQLException {
        insert(c, order, 3, "OrderCancelled", "{\"kind\":\"OrderCancelled\",\"reason\":\"changed mind\"}", "2000-01-01T00:00:00Z");
    }

    /** Placed, paid and shipped, each committed on its own. */
    static void shippedOrder(Connection c, String order, String carrier, String at) throws SQLException {
        placed(c, order);
        paid(c, order);
        shipped(c, order, carrier, at);
    }

    /** The report lines of one year, as {@code carrier|day -> shipments}. */
    static Map<String, Long> ofYear(List<ShipmentsPerDay> rows, int year) {
        Map<String, Long> out = new TreeMap<>();
        for (ShipmentsPerDay row : rows) {
            if (row.day().getYear() == year) {
                out.put(row.carrier() + "|" + row.day(), row.shipments());
            }
        }
        return out;
    }

    static long total(Map<String, Long> rows) {
        return rows.values().stream().mapToLong(Long::longValue).sum();
    }
}
