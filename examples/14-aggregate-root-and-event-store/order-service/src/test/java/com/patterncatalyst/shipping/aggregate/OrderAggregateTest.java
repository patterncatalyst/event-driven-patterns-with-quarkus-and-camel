package com.patterncatalyst.shipping.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.OrderStatus;

/** The aggregate on its own: no Quarkus, no database. */
class OrderAggregateTest {

    private static final String ID = "O-U1";

    private static OrderAggregate aggregate(Command... commands) {
        var history = new ArrayList<StoredEvent>();
        var order = OrderAggregate.rehydrate(history);
        for (Command command : commands) {
            for (OrderEvent event : order.decide(command)) {
                history.add(stored(history.size() + 1, event));
            }
        }
        return OrderAggregate.rehydrate(history);
    }

    private static StoredEvent stored(int version, OrderEvent event) {
        var row = new StoredEvent();
        row.version = version;
        row.payload = event;
        return row;
    }

    @Test
    void anOrderFollowsTheLifecycle() {
        var order = aggregate(Streams.place(ID), Streams.pay(ID), Streams.ship(ID));
        assertEquals(OrderStatus.SHIPPED, order.status());
        assertEquals(3, order.version());
    }

    @Test
    void anOrderThatDoesNotExistRefusesEverythingButPlace() {
        var order = aggregate();
        assertThrows(CommandRefused.class, () -> order.decide(Streams.pay(ID)));
        assertThrows(CommandRefused.class, () -> order.decide(Streams.ship(ID)));
        assertThrows(CommandRefused.class, () -> order.decide(Streams.cancel(ID)));
    }

    @Test
    void anOrderIsPlacedOnlyOnce() {
        var order = aggregate(Streams.place(ID));
        assertEquals("order exists", assertThrows(CommandRefused.class, () -> order.decide(Streams.place(ID))).getMessage());
    }

    @Test
    void shippingNeedsPayment() {
        var order = aggregate(Streams.place(ID));
        assertEquals("PLACED to SHIPPED", assertThrows(CommandRefused.class, () -> order.decide(Streams.ship(ID))).getMessage());
    }

    @Test
    void cancellationIsRefusedAfterShipping() {
        var order = aggregate(Streams.place(ID), Streams.pay(ID), Streams.ship(ID));
        assertEquals("SHIPPED to CANCELLED", assertThrows(CommandRefused.class, () -> order.decide(Streams.cancel(ID))).getMessage());
    }

    @Test
    void aCancelledOrderTakesNoPayment() {
        var order = aggregate(Streams.place(ID), Streams.cancel(ID));
        assertEquals("CANCELLED to PAID", assertThrows(CommandRefused.class, () -> order.decide(Streams.pay(ID))).getMessage());
    }

    @Test
    void theSamePaymentTwiceGivesNoEvent() {
        var order = aggregate(Streams.place(ID), Streams.pay(ID));
        assertTrue(order.decide(Streams.pay(ID)).isEmpty());
        assertEquals(2, order.version());
    }

    @Test
    void aSecondPaymentIdIsRefused() {
        var order = aggregate(Streams.place(ID), Streams.pay(ID));
        var other = new Command.MarkPaid(ID, "P-OTHER", new BigDecimal("25.00"));
        assertEquals("already paid by P-" + ID, assertThrows(CommandRefused.class, () -> order.decide(other)).getMessage());
    }

    @Test
    void aPaymentThatDiffersFromTheOrderAmountIsRefused() {
        var order = aggregate(Streams.place(ID));
        var underpay = new Command.MarkPaid(ID, "P-1", new BigDecimal("20.00"));
        assertEquals("amount 20.00 differs from order amount 25.00",
                assertThrows(CommandRefused.class, () -> order.decide(underpay)).getMessage());
    }

    @Test
    void aRefusedCommandLeavesTheAggregateUnchanged() {
        var order = aggregate(Streams.place(ID), Streams.pay(ID), Streams.ship(ID));
        assertThrows(CommandRefused.class, () -> order.decide(Streams.cancel(ID)));
        assertEquals(OrderStatus.SHIPPED, order.status());
        assertEquals(3, order.version());
    }

    @Test
    void rehydratingFromTheStreamEqualsApplyingInOrder() {
        List<OrderEvent> events = new ArrayList<>();
        var live = OrderAggregate.rehydrate(List.of());
        for (Command command : List.of(Streams.place(ID), Streams.pay(ID), Streams.cancel(ID))) {
            events.addAll(live.decide(command));
        }
        var stream = new ArrayList<StoredEvent>();
        events.forEach(event -> stream.add(stored(stream.size() + 1, event)));
        var rebuilt = OrderAggregate.rehydrate(stream);
        assertEquals(live.status(), rebuilt.status());
        assertEquals(3, rebuilt.version());
    }
}
