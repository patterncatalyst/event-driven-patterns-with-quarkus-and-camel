package com.patterncatalyst.shipping.saga.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The payment step: taken, declined above the limit, refused for a cancelled order, and never taken twice. */
@QuarkusTest
class PaymentServiceTest {

    @Inject
    PaymentService payments;

    @Inject
    EntityManager em;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    void aPaymentUnderTheLimitIsTakenAndPublished() {
        String id = Fixtures.newId();
        payments.take(Fixtures.reserved(id, "25.00"));
        assertEquals("COMPLETED", Fixtures.paymentStatus(id));
        assertEquals(1, Fixtures.outbox(em, "PaymentProcessed", id));
        assertEquals(0, Fixtures.outbox(em, "PaymentFailed", id));
    }

    @Test
    void aPaymentAboveTheLimitIsDeclined() {
        String id = Fixtures.newId();
        payments.take(Fixtures.reserved(id, "900.00"));
        assertEquals("FAILED", Fixtures.paymentStatus(id));
        assertEquals(1, Fixtures.outbox(em, "PaymentFailed", id));
        assertEquals(0, Fixtures.outbox(em, "PaymentProcessed", id));
    }

    @Test
    void aPaymentForACancelledOrderIsRefused() {
        String id = Fixtures.newId();
        payments.recordCancelled(new OrderCancelled(id, "TIMED_OUT"));
        payments.take(Fixtures.reserved(id, "25.00"));
        assertEquals("NONE", Fixtures.paymentStatus(id));
        assertEquals(0, Fixtures.outbox(em, "PaymentProcessed", id));
    }

    @Test
    void aRepeatedReservationTakesTheMoneyOnce() {
        String id = Fixtures.newId();
        payments.take(Fixtures.reserved(id, "25.00"));
        payments.take(Fixtures.reserved(id, "25.00"));
        assertEquals(1, Fixtures.outbox(em, "PaymentProcessed", id));
    }

    @Test
    void theRouteTakesThePaymentForAReservedOrder() throws Exception {
        String id = Fixtures.newId();
        Fixtures.send(bootstrapServers, Topics.INVENTORY_RESERVED, id,
                "{\"orderId\":\"%s\",\"sku\":\"SKU-22\",\"quantity\":1,\"amount\":25.00}".formatted(id));
        Fixtures.await(() -> "COMPLETED".equals(Fixtures.paymentStatus(id)));
        assertEquals(1, Fixtures.outbox(em, "PaymentProcessed", id));
    }
}
