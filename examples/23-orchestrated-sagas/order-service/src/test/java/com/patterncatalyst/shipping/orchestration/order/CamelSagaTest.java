package com.patterncatalyst.shipping.orchestration.order;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The Camel orchestrator on Camel's in-memory saga service, with Dev Services Kafka and PostgreSQL. The test routes
 * in SagaDrivers end the saga where the replies route would.
 */
@QuarkusTest
class CamelSagaTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Inject
    SagaOutcomes outcomes;

    @Inject
    ProducerTemplate camel;

    private void run(String route, String id) {
        camel.sendBodyAndHeader(route, id, "orderId", id);
    }

    @Test
    void aSagaThatEndsInPaymentCompletesAndCompensatesNothing() {
        String id = Orders.id();
        Orders.startRow(id);

        run("direct:test-complete", id);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertEquals("PAID", Orders.status(id)));
        assertEquals(1, Wire.records(bootstrap, Topics.INVENTORY_COMMANDS, id, 1, Duration.ofSeconds(20)).size());
        assertEquals(0, Orders.cancelledRows(id), "nothing was compensated");
    }

    @Test
    void theSecondStepJoinsTheSagaAndSendsTakePaymentAfterReserveStock() {
        String id = Orders.id();
        Orders.startRow(id);

        run("direct:test-pay", id);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertEquals("PAID", Orders.status(id)));
        var reserve = Wire.command(bootstrap, Topics.INVENTORY_COMMANDS, id, "ReserveStock");
        var take = Wire.command(bootstrap, Topics.PAYMENTS_COMMANDS, id, "TakePayment");
        assertTrue(reserve.timestamp() <= take.timestamp());
    }

    @Test
    void aFailedSagaCancelsTheOrderOnceAndPublishesOrderCancelled() {
        String id = Orders.id();
        Orders.startRow(id);

        run("direct:test-compensate", id);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertEquals("CANCELLED", Orders.status(id)));
        assertEquals(1, Orders.cancelledRows(id));
    }

    @Test
    void aSagaThatTimesOutIsCompensated() {
        String id = Orders.id();
        Orders.startRow(id);

        run("direct:camel-saga", id);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertEquals("CANCELLED", Orders.status(id)));
    }

    @Test
    void aCompensationRunTwiceChangesTheOrderOnce() {
        String id = Orders.id();
        Orders.startRow(id);

        outcomes.compensated("camel-lra", id);
        outcomes.compensated("camel-lra", id);

        assertEquals("CANCELLED", Orders.status(id));
        assertEquals(1, Orders.cancelledRows(id));
    }
}
