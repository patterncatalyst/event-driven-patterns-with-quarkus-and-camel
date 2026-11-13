package com.patterncatalyst.shipping.projections;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;

/** The same records through one Camel route and one group build the same view, with the same guard. */
@QuarkusTest
@TestProfile(CamelProjectorProfile.class)
class CamelProjectorTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Test
    void oneRouteOverSixTopicsBuildsTheView() throws Exception {
        Records.send(bootstrap, Topics.ORDERS_PLACED, "O-C1", "OrderPlaced", Records.placedJson("O-C1", "C-C1"));
        Records.send(bootstrap, Topics.PAYMENTS_PROCESSED, "O-C1", "PaymentProcessed", Records.paidJson("O-C1"));
        Records.send(bootstrap, Topics.SHIPPING_SCHEDULED, "O-C1", "ShipmentScheduled", Records.shipmentJson("O-C1", "SCHEDULED"));
        Records.send(bootstrap, Topics.SHIPPING_DELIVERED, "O-C1", "ShipmentDelivered", Records.shipmentJson("O-C1", "DELIVERED"));
        Records.send(bootstrap, Topics.SHIPPING_IN_TRANSIT, "O-C1", "ShipmentInTransit", Records.shipmentJson("O-C1", "IN_TRANSIT"));
        Records.send(bootstrap, Topics.ORDERS_PLACED, "O-C2", "OrderPlaced", Records.placedJson("O-C2", "C-C1"));
        Records.send(bootstrap, Topics.ORDERS_CANCELLED, "O-C2", "OrderCancelled", Records.cancelledJson("O-C2"));
        Records.await(() -> {
            var one = RestAssured.get("/orders/O-C1");
            var two = RestAssured.get("/orders/O-C2");
            return one.statusCode() == 200 && two.statusCode() == 200
                    && "DELIVERED".equals(one.jsonPath().getString("status"))
                    && "CANCELLED".equals(two.jsonPath().getString("status"));
        });
        assertEquals(2, RestAssured.get("/customers/C-C1/orders").jsonPath().getList("orderId").size());
        assertEquals("DELIVERED", RestAssured.get("/orders/O-C1").jsonPath().getString("shipmentStatus"));
    }
}
