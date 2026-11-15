package com.patterncatalyst.shipping.correlation.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Payment;
import com.patterncatalyst.shipping.PaymentStatus;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The transitions of the saga: each reply moves it once and the outbox row follows the state. */
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
        assertTrue(saga.onReserved(UUID.randomUUID(), new InventoryReserved(id, "SKU-27", 1, new BigDecimal("25.00"))));
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
        assertTrue(saga.onInsufficient(UUID.randomUUID(), new InventoryInsufficient(id, "SKU-27", 50)));
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
}
