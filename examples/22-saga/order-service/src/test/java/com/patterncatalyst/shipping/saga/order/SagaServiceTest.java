package com.patterncatalyst.shipping.saga.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Payment;
import com.patterncatalyst.shipping.PaymentStatus;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The transitions of the saga: each reply moves it once, an unexpected reply changes nothing, the outbox row follows the state. */
@QuarkusTest
class SagaServiceTest {

    @Inject
    SagaService saga;

    @Inject
    EntityManager em;

    private String placed() {
        String id = Fixtures.newId();
        saga.place(Fixtures.order(id));
        return id;
    }

    private String reserved() {
        String id = placed();
        assertTrue(saga.onReserved(UUID.randomUUID(), new InventoryReserved(id, "SKU-22", 1, new BigDecimal("25.00"))));
        return id;
    }

    private static PaymentProcessed paid(String id) {
        return new PaymentProcessed(new Payment("P-" + id, id, new BigDecimal("25.00"), PaymentStatus.COMPLETED));
    }

    @Test
    void aHappyPathEndsInPaid() {
        String id = reserved();
        assertEquals(SagaState.AWAITING_PAYMENT, Fixtures.state(id));
        assertTrue(saga.onPaymentProcessed(UUID.randomUUID(), paid(id)));
        assertEquals(SagaState.COMPLETED, Fixtures.state(id));
        assertEquals("PAID", Fixtures.orderStatus(id));
        assertEquals(0, Fixtures.outbox(em, "OrderCancelled", id));
    }

    @Test
    void anOrderIsPlacedWithItsSagaAndItsEvent() {
        String id = placed();
        assertEquals(SagaState.AWAITING_STOCK, Fixtures.state(id));
        assertEquals("PLACED", Fixtures.orderStatus(id));
        assertEquals(1, Fixtures.outbox(em, "OrderPlaced", id));
    }

    @Test
    void insufficientStockCancelsWithoutCompensation() {
        String id = placed();
        assertTrue(saga.onInsufficient(UUID.randomUUID(), new InventoryInsufficient(id, "SKU-22", 50)));
        assertEquals(SagaState.CANCELLED, Fixtures.state(id));
        assertEquals(CancelReason.OUT_OF_STOCK, Fixtures.saga(id).reason);
        assertEquals("CANCELLED", Fixtures.orderStatus(id));
        assertEquals(1, Fixtures.outbox(em, "OrderCancelled", id));
    }

    @Test
    void aDeclinedPaymentCancelsTheOrder() {
        String id = reserved();
        assertTrue(saga.onPaymentFailed(UUID.randomUUID(), new PaymentFailed(id, "DECLINED")));
        assertEquals(SagaState.CANCELLED, Fixtures.state(id));
        assertEquals(CancelReason.PAYMENT_DECLINED, Fixtures.saga(id).reason);
        assertEquals(1, Fixtures.outbox(em, "OrderCancelled", id));
    }

    @Test
    void theSweeperCancelsAnExpiredSagaAndWritesTheEventWithTheChange() {
        String id = reserved();
        Fixtures.expire(id);
        assertTrue(saga.expire() >= 1);
        assertEquals(SagaState.CANCELLED, Fixtures.state(id));
        assertEquals(CancelReason.TIMED_OUT, Fixtures.saga(id).reason);
        assertEquals(1, Fixtures.outbox(em, "OrderCancelled", id));
    }

    @Test
    void aRolledBackSweepLeavesNeitherTheStateChangeNorTheOutboxRow() {
        String id = reserved();
        Fixtures.expire(id);
        QuarkusTransaction.begin();
        saga.expire();
        QuarkusTransaction.rollback();
        assertEquals(SagaState.AWAITING_PAYMENT, Fixtures.state(id));
        assertEquals(0, Fixtures.outbox(em, "OrderCancelled", id));
    }

    @Test
    void aLatePaymentForACancelledOrderChangesNothing() {
        String id = reserved();
        Fixtures.expire(id);
        saga.expire();
        int version = Fixtures.saga(id).version;
        assertFalse(saga.onPaymentProcessed(UUID.randomUUID(), paid(id)));
        assertEquals(SagaState.CANCELLED, Fixtures.state(id));
        assertEquals("CANCELLED", Fixtures.orderStatus(id));
        assertEquals(version, Fixtures.saga(id).version);
    }

    @Test
    void theSameEventIdIsAppliedOnce() {
        String id = placed();
        UUID eventId = UUID.randomUUID();
        InventoryReserved reserved = new InventoryReserved(id, "SKU-22", 1, new BigDecimal("25.00"));
        assertTrue(saga.onReserved(eventId, reserved));
        assertFalse(saga.onReserved(eventId, reserved));
        assertEquals(1, Fixtures.saga(id).version);
    }

    /** A probabilistic catch: 50 rounds make the two transitions overlap often, and one of them must win every time. */
    @Test
    void aSweepAndAPaymentReleasedTogetherHaveOneWinner() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int round = 1; round <= 50; round++) {
                String id = reserved();
                Fixtures.expire(id);
                var barrier = new CyclicBarrier(2);
                Future<Boolean> payment = pool.submit(() -> {
                    barrier.await();
                    return saga.onPaymentProcessed(UUID.randomUUID(), paid(id));
                });
                Future<Integer> sweep = pool.submit(() -> {
                    barrier.await();
                    return saga.expire();
                });
                boolean paymentWon = payment.get();
                sweep.get();
                SagaState end = Fixtures.state(id);
                long cancelled = Fixtures.outbox(em, "OrderCancelled", id);
                if (paymentWon) {
                    assertEquals(SagaState.COMPLETED, end, "round " + round);
                    assertEquals(0, cancelled, "round " + round);
                } else {
                    assertEquals(SagaState.CANCELLED, end, "round " + round);
                    assertEquals(1, cancelled, "round " + round);
                }
                assertEquals(2, Fixtures.saga(id).version, "round " + round);
            }
        }
    }
}
