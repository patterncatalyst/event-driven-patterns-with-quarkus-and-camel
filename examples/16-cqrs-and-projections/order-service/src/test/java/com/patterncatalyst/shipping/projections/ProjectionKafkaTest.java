package com.patterncatalyst.shipping.projections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.OrderStatus;
import com.patterncatalyst.shipping.Topics;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;

/** From a record on each topic to the row and the answer a reader gets, and what a held-back topic does to it. */
@QuarkusTest
class ProjectionKafkaTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Inject
    MeterRegistry registry;

    String status(String id) {
        var response = RestAssured.get("/orders/" + id);
        return response.statusCode() == 200 ? response.jsonPath().getString("status") : "none";
    }

    void pause(String channel) {
        assertTrue(RestAssured.post("/demo/channels/" + channel + "/pause").jsonPath().getBoolean("paused"));
    }

    void resume(String channel) {
        RestAssured.post("/demo/channels/" + channel + "/resume");
    }

    @Test
    void eachTopicMovesTheOrderForward() throws Exception {
        String id = "O-K1";
        Records.send(bootstrap, Topics.ORDERS_PLACED, id, "OrderPlaced", Records.placedJson(id, "C-K1"));
        Records.await(() -> status(id).equals("PLACED"));
        Records.send(bootstrap, Topics.PAYMENTS_PROCESSED, id, "PaymentProcessed", Records.paidJson(id));
        Records.await(() -> status(id).equals("PAID"));
        Records.send(bootstrap, Topics.SHIPPING_SCHEDULED, id, "ShipmentScheduled", Records.shipmentJson(id, "SCHEDULED"));
        Records.await(() -> status(id).equals("SHIPPED"));
        Records.send(bootstrap, Topics.SHIPPING_IN_TRANSIT, id, "ShipmentInTransit", Records.shipmentJson(id, "IN_TRANSIT"));
        Records.await(() -> RestAssured.get("/orders/" + id).jsonPath().getString("shipmentStatus").equals("IN_TRANSIT"));
        Records.send(bootstrap, Topics.SHIPPING_DELIVERED, id, "ShipmentDelivered", Records.shipmentJson(id, "DELIVERED"));
        Records.await(() -> status(id).equals("DELIVERED"));
        var body = RestAssured.get("/customers/C-K1/orders").jsonPath();
        assertEquals(List.of(id), body.getList("orderId"));
        assertEquals("FASTLINE", RestAssured.get("/orders/" + id).jsonPath().getString("carrier"));
    }

    @Test
    void aCancellationIsReadAsCancelled() throws Exception {
        String id = "O-K2";
        Records.send(bootstrap, Topics.ORDERS_PLACED, id, "OrderPlaced", Records.placedJson(id, "C-K2"));
        Records.send(bootstrap, Topics.ORDERS_CANCELLED, id, "OrderCancelled", Records.cancelledJson(id));
        Records.await(() -> status(id).equals("CANCELLED"));
    }

    @Test
    void aPausedTopicLeavesTheRowStaleAndResumeCatchesUp() throws Exception {
        String id = "O-K3";
        Records.send(bootstrap, Topics.ORDERS_PLACED, id, "OrderPlaced", Records.placedJson(id, "C-K3"));
        Records.send(bootstrap, Topics.SHIPPING_SCHEDULED, id, "ShipmentScheduled", Records.shipmentJson(id, "SCHEDULED"));
        Records.await(() -> status(id).equals("SHIPPED"));
        Instant before = Instant.parse(RestAssured.get("/orders/" + id).jsonPath().getString("asOf"));

        pause("delivered");
        Records.send(bootstrap, Topics.SHIPPING_DELIVERED, id, "ShipmentDelivered", Records.shipmentJson(id, "DELIVERED"));
        Records.await(() -> {
            try {
                return Records.lag(bootstrap, "order-view-delivered", Topics.SHIPPING_DELIVERED) >= 1;
            } catch (Exception e) {
                return false;
            }
        });
        Thread.sleep(1500);
        assertEquals("SHIPPED", status(id));
        assertEquals(before, Instant.parse(RestAssured.get("/orders/" + id).jsonPath().getString("asOf")));

        resume("delivered");
        Records.await(() -> status(id).equals("DELIVERED"));
        assertTrue(Instant.parse(RestAssured.get("/orders/" + id).jsonPath().getString("asOf")).isAfter(before));
        // the paused span is the lag of the event that waited
        assertTrue(registry.find("order.view.lag").timer().max(TimeUnit.MILLISECONDS) >= 1500);
    }

    @Test
    void aShipmentStatusThatArrivesLateDoesNotMoveTheViewBack() throws Exception {
        String id = "O-K4";
        Records.send(bootstrap, Topics.ORDERS_PLACED, id, "OrderPlaced", Records.placedJson(id, "C-K4"));
        pause("in-transit");
        Records.send(bootstrap, Topics.SHIPPING_SCHEDULED, id, "ShipmentScheduled", Records.shipmentJson(id, "SCHEDULED"));
        Records.send(bootstrap, Topics.SHIPPING_DELIVERED, id, "ShipmentDelivered", Records.shipmentJson(id, "DELIVERED"));
        Records.send(bootstrap, Topics.SHIPPING_IN_TRANSIT, id, "ShipmentInTransit", Records.shipmentJson(id, "IN_TRANSIT"));
        Records.await(() -> status(id).equals("DELIVERED"));
        resume("in-transit");
        Records.await(() -> {
            try {
                return Records.lag(bootstrap, "order-view-in-transit", Topics.SHIPPING_IN_TRANSIT) == 0;
            } catch (Exception e) {
                return false;
            }
        });
        assertEquals("DELIVERED", status(id));
        assertEquals(OrderStatus.DELIVERED.name(), RestAssured.get("/orders/" + id).jsonPath().getString("shipmentStatus"));
    }

    @Test
    void theLagTimerRecordsEveryAppliedEvent() throws Exception {
        long before = registry.find("order.view.lag").timer().count();
        String id = "O-K5";
        Records.send(bootstrap, Topics.ORDERS_PLACED, id, "OrderPlaced", Records.placedJson(id, "C-K5"));
        Records.await(() -> status(id).equals("PLACED"));
        var timer = registry.find("order.view.lag").timer();
        assertTrue(timer.count() > before);
        assertTrue(timer.totalTime(TimeUnit.MILLISECONDS) > 0);
    }
}
