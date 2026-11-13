package com.patterncatalyst.shipping.projections;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.OrderStatus;

/** The status shown to a reader is the furthest fact the row holds. */
class OrderViewStatusTest {

    static OrderView view(String order, String payment, String shipment) {
        var view = new OrderView();
        view.orderStatus = order;
        view.paymentStatus = payment;
        view.shipmentStatus = shipment;
        return view;
    }

    @Test
    void theFurthestFactWins() {
        assertEquals(OrderStatus.PLACED, view("PLACED", null, null).status());
        assertEquals(OrderStatus.PLACED, view(null, null, null).status());
        assertEquals(OrderStatus.PLACED, view("PLACED", "PENDING", null).status());
        assertEquals(OrderStatus.PAID, view("PLACED", "COMPLETED", null).status());
        assertEquals(OrderStatus.PAID, view(null, "COMPLETED", null).status());
        assertEquals(OrderStatus.SHIPPED, view("PLACED", "COMPLETED", "SCHEDULED").status());
        assertEquals(OrderStatus.SHIPPED, view("PLACED", "COMPLETED", "IN_TRANSIT").status());
        assertEquals(OrderStatus.SHIPPED, view(null, null, "SCHEDULED").status());
        assertEquals(OrderStatus.DELIVERED, view("PLACED", "COMPLETED", "DELIVERED").status());
        assertEquals(OrderStatus.DELIVERED, view(null, null, "DELIVERED").status());
    }

    @Test
    void cancelledOverridesEveryOtherFact() {
        assertEquals(OrderStatus.CANCELLED, view("CANCELLED", null, null).status());
        assertEquals(OrderStatus.CANCELLED, view("CANCELLED", "COMPLETED", null).status());
        assertEquals(OrderStatus.CANCELLED, view("CANCELLED", "COMPLETED", "SCHEDULED").status());
    }
}
