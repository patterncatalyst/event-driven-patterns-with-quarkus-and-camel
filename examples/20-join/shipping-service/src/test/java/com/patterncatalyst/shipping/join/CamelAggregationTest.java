package com.patterncatalyst.shipping.join;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** The Camel aggregator on Dev Services Kafka and PostgreSQL: the predicate, the timeout and the stored group. */
@QuarkusTest
@QuarkusTestResource(StateDir.class)
class CamelAggregationTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Inject
    DataSource dataSource;

    private java.util.List<Readiness> camel(String orderId) throws Exception {
        return Kafka.forOrder(Kafka.readiness(bootstrap, Topics.SHIPPING_ORDER_READINESS_CAMEL), orderId);
    }

    private long rows(String table, String orderId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT count(*) FROM shipping." + table + " WHERE id = ?")) {
            statement.setString(1, orderId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    @Test
    void bothFactsCompleteTheGroupInEitherOrder() throws Exception {
        Kafka.reserve(bootstrap, "O-C1", "09:00");
        Kafka.pay(bootstrap, "O-C1", "09:01");
        Kafka.pay(bootstrap, "O-C2", "09:00");
        Kafka.reserve(bootstrap, "O-C2", "09:01");
        Kafka.await(() -> unchecked(() -> !camel("O-C1").isEmpty() && !camel("O-C2").isEmpty()));
        assertEquals(Readiness.READY_TO_SHIP, camel("O-C1").get(0).type());
        assertEquals(Readiness.READY_TO_SHIP, camel("O-C2").get(0).type());
    }

    @Test
    void aReservationThatNeverGetsItsPaymentLeavesAsOverdueAfterTheTimeout() throws Exception {
        Kafka.reserve(bootstrap, "O-C3", "09:00");
        Kafka.await(() -> unchecked(() -> !camel("O-C3").isEmpty()));
        var out = camel("O-C3");
        assertEquals(1, out.size());
        assertEquals(Readiness.PAYMENT_OVERDUE, out.get(0).type());
    }

    @Test
    void aDuplicateReservationDoesNotCompleteTheGroup() throws Exception {
        Kafka.reserve(bootstrap, "O-C4", "09:00");
        Kafka.reserve(bootstrap, "O-C4", "09:00");
        Kafka.await(() -> unchecked(() -> !camel("O-C4").isEmpty()));
        var out = camel("O-C4");
        assertEquals(1, out.size());
        assertEquals(Readiness.PAYMENT_OVERDUE, out.get(0).type(), "two reservations are not a reservation and a payment");
    }

    @Test
    void theOpenGroupIsARowUntilItCompletes() throws Exception {
        Kafka.reserve(bootstrap, "O-C5", "09:00");
        Kafka.await(() -> unchecked(() -> rows("readiness", "O-C5") == 1));
        Kafka.pay(bootstrap, "O-C5", "09:01");
        Kafka.await(() -> unchecked(() -> !camel("O-C5").isEmpty()));
        assertEquals(Readiness.READY_TO_SHIP, camel("O-C5").get(0).type());
        assertEquals(0, rows("readiness", "O-C5"));
    }

    @Test
    void aPaymentThatArrivesAloneLeavesNothingBehind() throws Exception {
        Kafka.pay(bootstrap, "O-C6", "09:00");
        Kafka.await(() -> unchecked(() -> rows("readiness", "O-C6") == 1));
        Kafka.await(() -> unchecked(() -> rows("readiness", "O-C6") == 0));
        Thread.sleep(1500);
        assertTrue(camel("O-C6").isEmpty(), "a payment with no reservation says nothing");
    }

    private interface Check {
        boolean run() throws Exception;
    }

    private static boolean unchecked(Check check) {
        try {
            return check.run();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
