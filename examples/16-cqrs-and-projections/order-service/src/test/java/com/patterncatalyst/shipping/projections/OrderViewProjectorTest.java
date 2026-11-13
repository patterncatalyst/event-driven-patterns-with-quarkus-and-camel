package com.patterncatalyst.shipping.projections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.OrderStatus;
import com.patterncatalyst.shipping.ShipmentStatus;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** The guarded upserts, applied directly: what each column holds after events arrive in the wrong order or twice. */
@QuarkusTest
class OrderViewProjectorTest {

    @Inject
    OrderViewProjector projector;

    static String id() {
        return "O-" + UUID.randomUUID();
    }

    @Test
    void aLaterShipmentStatusIsNotOverwrittenByAnEarlierOne() {
        String id = id();
        projector.placed(Records.placed(id, "C-1"), Instant.now());
        projector.delivered(new Events.ShipmentDelivered(Records.shipment(id, ShipmentStatus.DELIVERED)), Instant.now());
        projector.inTransit(new Events.ShipmentInTransit(Records.shipment(id, ShipmentStatus.IN_TRANSIT)), Instant.now());
        projector.scheduled(new Events.ShipmentScheduled(Records.shipment(id, ShipmentStatus.SCHEDULED)), Instant.now());
        assertEquals("DELIVERED", Records.row(id).shipmentStatus);
        assertEquals(OrderStatus.DELIVERED, Records.row(id).status());
    }

    @Test
    void theSameEventAppliedTwiceLeavesTheRowUnchanged() {
        String id = id();
        Instant at = Instant.parse("2026-01-01T10:00:00Z");
        var scheduled = new Events.ShipmentScheduled(Records.shipment(id, ShipmentStatus.SCHEDULED));
        projector.placed(Records.placed(id, "C-2"), at);
        projector.paid(Records.paid(id), at.plusSeconds(1));
        projector.scheduled(scheduled, at.plusSeconds(2));
        var once = Records.row(id);
        projector.placed(Records.placed(id, "C-2"), at);
        projector.paid(Records.paid(id), at.plusSeconds(1));
        projector.scheduled(scheduled, at.plusSeconds(2));
        var twice = Records.row(id);
        assertEquals(once.customerId, twice.customerId);
        assertEquals(once.orderStatus, twice.orderStatus);
        assertEquals(once.paymentStatus, twice.paymentStatus);
        assertEquals(once.shipmentStatus, twice.shipmentStatus);
        assertEquals(once.carrier, twice.carrier);
        assertEquals(once.trackingNumber, twice.trackingNumber);
        assertEquals(once.lastEventAt, twice.lastEventAt);
    }

    @Test
    void anEventBeforeItsOrderCreatesAPartialRowThatTheOrderCompletes() {
        String id = id();
        String customer = "C-" + UUID.randomUUID();
        projector.scheduled(new Events.ShipmentScheduled(Records.shipment(id, ShipmentStatus.SCHEDULED)), Instant.now());
        var partial = Records.row(id);
        assertNull(partial.customerId);
        assertEquals(OrderStatus.SHIPPED, partial.status());
        projector.placed(Records.placed(id, customer), Instant.now());
        var complete = Records.row(id);
        assertEquals(customer, complete.customerId);
        assertEquals("SCHEDULED", complete.shipmentStatus);
        assertEquals(OrderStatus.SHIPPED, complete.status());
    }

    @Test
    void eachColumnComesFromItsOwner() {
        String id = id();
        projector.paid(Records.paid(id), Instant.now());
        projector.placed(Records.placed(id, "C-3"), Instant.now());
        var row = Records.row(id);
        assertEquals("COMPLETED", row.paymentStatus);
        assertEquals("PLACED", row.orderStatus);
        assertEquals(OrderStatus.PAID, row.status());
    }

    @Test
    void cancelledIsTerminal() {
        String id = id();
        projector.cancelled(Records.cancelled(id), Instant.now());
        projector.placed(Records.placed(id, "C-4"), Instant.now());
        projector.paid(Records.paid(id), Instant.now());
        assertEquals("CANCELLED", Records.row(id).orderStatus);
        assertEquals(OrderStatus.CANCELLED, Records.row(id).status());
    }
}
