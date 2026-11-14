package com.patterncatalyst.shipping.saga.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The negative witness: without the check, the money for a cancelled order is taken. */
@QuarkusTest
@TestProfile(GuardOffProfile.class)
class GuardOffTest {

    @Inject
    PaymentService payments;

    @Inject
    EntityManager em;

    @Test
    void withTheGuardOffThePaymentForACancelledOrderIsTaken() {
        String id = Fixtures.newId();
        payments.recordCancelled(new OrderCancelled(id, "TIMED_OUT"));
        payments.take(Fixtures.reserved(id, "25.00"));
        assertEquals("COMPLETED", Fixtures.paymentStatus(id));
        assertEquals(1, Fixtures.outbox(em, "PaymentProcessed", id));
    }
}
