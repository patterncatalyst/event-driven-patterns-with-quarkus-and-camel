package com.patterncatalyst.shipping.projections;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.OrderStatus;
import com.patterncatalyst.shipping.ShipmentStatus;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/** The negative witness: without the guard the last event applied wins, and an old status replaces a newer one. */
@QuarkusTest
@TestProfile(GuardOffProfile.class)
class GuardOffTest {

    @Inject
    OrderViewProjector projector;

    @Test
    void withoutTheGuardAnEarlierStatusOverwritesALaterOne() {
        String id = "O-" + UUID.randomUUID();
        projector.delivered(new Events.ShipmentDelivered(Records.shipment(id, ShipmentStatus.DELIVERED)), Instant.now());
        projector.inTransit(new Events.ShipmentInTransit(Records.shipment(id, ShipmentStatus.IN_TRANSIT)), Instant.now());
        assertEquals("IN_TRANSIT", Records.row(id).shipmentStatus);
        assertEquals(OrderStatus.SHIPPED, Records.row(id).status());
    }
}
