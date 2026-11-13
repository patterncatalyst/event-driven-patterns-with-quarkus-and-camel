package com.patterncatalyst.shipping.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.OrderStatus;

class OrderAggregateTest {

    @Test
    void aShipmentRecordsTheCarrierAndTheUtcDay() {
        OrderAggregate order = OrderAggregate.start();
        order.apply(new OrderEvent.OrderPlaced("O-1"), Instant.parse("2000-01-01T00:00:00Z"));
        order.apply(new OrderEvent.OrderPaid("P-1"), Instant.parse("2000-01-01T00:00:00Z"));
        order.apply(new OrderEvent.OrderShipped("S-1", Carrier.AIRCO), Instant.parse("2001-03-04T23:59:59Z"));

        assertEquals(OrderStatus.SHIPPED, order.status());
        assertTrue(order.hasShipped());
        assertEquals(Carrier.AIRCO, order.carrier());
        assertEquals(LocalDate.parse("2001-03-04"), order.shippedOn());
        assertEquals(3, order.version());
    }

    @Test
    void aCancelledOrderRefusesToShip() {
        OrderAggregate order = OrderAggregate.start();
        order.decide(new Command.PlaceOrder("O-2"));
        order.decide(new Command.CancelOrder("O-2", "changed mind"));

        assertThrows(CommandRefused.class, () -> order.decide(new Command.ShipOrder("O-2", Carrier.FASTLINE)));
    }
}
