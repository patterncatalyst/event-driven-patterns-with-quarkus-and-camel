package com.patterncatalyst.shipping.orchestration.order;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The workflow orchestrator, with Dev Services Kafka and PostgreSQL. The participants are played by hand: the test
 * reads each command and sends the CloudEvent reply a participant would, naming the workflow instance.
 */
@QuarkusTest
class FlowSagaTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Inject
    OrderService orders;

    private void awaitStatus(String id, String status) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertEquals(status, Orders.status(id)));
    }

    @Test
    void theHappyPathEndsPaid() throws Exception {
        String id = Orders.id();
        Orders.start("flow", id);

        var reserve = Wire.command(bootstrap, Topics.INVENTORY_COMMANDS, id, "ReserveStock");
        assertNotNull(Wire.header(reserve, "flowinstanceid"));
        Wire.reply(bootstrap, Topics.INVENTORY_RESERVED, "InventoryReserved", id, "RESERVED", reserve);
        var take = Wire.command(bootstrap, Topics.PAYMENTS_COMMANDS, id, "TakePayment");
        Wire.reply(bootstrap, Topics.PAYMENTS_PROCESSED, "PaymentProcessed", id, "PAID", take);

        awaitStatus(id, "PAID");
        assertEquals(0, Wire.records(bootstrap, Topics.INVENTORY_COMMANDS, id, 2, Duration.ofSeconds(2)).stream()
                .filter(r -> "ReleaseStock".equals(Wire.header(r, "type"))).count());
    }

    @Test
    void aDeclinedPaymentReleasesTheStockAndCancels() throws Exception {
        String id = Orders.id();
        Orders.start("flow", id);

        var reserve = Wire.command(bootstrap, Topics.INVENTORY_COMMANDS, id, "ReserveStock");
        Wire.reply(bootstrap, Topics.INVENTORY_RESERVED, "InventoryReserved", id, "RESERVED", reserve);
        var take = Wire.command(bootstrap, Topics.PAYMENTS_COMMANDS, id, "TakePayment");
        Wire.reply(bootstrap, Topics.PAYMENTS_FAILED, "PaymentFailed", id, "DECLINED", take);

        awaitStatus(id, "CANCELLED");
        Wire.command(bootstrap, Topics.INVENTORY_COMMANDS, id, "ReleaseStock");
        assertEquals(0, Wire.records(bootstrap, Topics.PAYMENTS_COMMANDS, id, 2, Duration.ofSeconds(2)).stream()
                .filter(r -> "RefundPayment".equals(Wire.header(r, "type"))).count(), "nothing was taken, so nothing is refunded");
    }

    @Test
    void insufficientStockCancelsWithoutPaymentOrRelease() throws Exception {
        String id = Orders.id();
        Orders.start("flow", id);

        var reserve = Wire.command(bootstrap, Topics.INVENTORY_COMMANDS, id, "ReserveStock");
        Wire.reply(bootstrap, Topics.INVENTORY_INSUFFICIENT, "InventoryInsufficient", id, "INSUFFICIENT", reserve);

        awaitStatus(id, "CANCELLED");
        assertEquals(0, Wire.records(bootstrap, Topics.PAYMENTS_COMMANDS, id, 1, Duration.ofSeconds(2)).size());
        assertEquals(1, Wire.records(bootstrap, Topics.INVENTORY_COMMANDS, id, 2, Duration.ofSeconds(2)).size());
    }

    @Test
    void aListenTimeoutRefundsThenReleasesThenCancels() throws Exception {
        String id = Orders.id();
        Orders.start("flow", id);

        var reserve = Wire.command(bootstrap, Topics.INVENTORY_COMMANDS, id, "ReserveStock");
        Wire.reply(bootstrap, Topics.INVENTORY_RESERVED, "InventoryReserved", id, "RESERVED", reserve);
        Wire.command(bootstrap, Topics.PAYMENTS_COMMANDS, id, "TakePayment");

        awaitStatus(id, "CANCELLED");
        var refund = Wire.command(bootstrap, Topics.PAYMENTS_COMMANDS, id, "RefundPayment");
        var release = Wire.command(bootstrap, Topics.INVENTORY_COMMANDS, id, "ReleaseStock");
        assertTrue(refund.timestamp() <= release.timestamp(), "the refund is sent before the release");
    }

    @Test
    void anInstanceRowExistsWhileWaitingAndIsGoneAfterwards() throws Exception {
        String id = Orders.id();
        Orders.start("flow", id);

        var reserve = Wire.command(bootstrap, Topics.INVENTORY_COMMANDS, id, "ReserveStock");
        String instance = Wire.header(reserve, "flowinstanceid");
        Wire.reply(bootstrap, Topics.INVENTORY_RESERVED, "InventoryReserved", id, "RESERVED", reserve);
        var take = Wire.command(bootstrap, Topics.PAYMENTS_COMMANDS, id, "TakePayment");
        assertEquals(1, Orders.instanceRows(instance), "the waiting instance is in PostgreSQL");

        Wire.reply(bootstrap, Topics.PAYMENTS_PROCESSED, "PaymentProcessed", id, "PAID", take);
        awaitStatus(id, "PAID");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(0, Orders.instanceRows(instance)));
    }

    @Test
    void aReplyWithoutCloudEventAttributesDoesNotWakeAnInstance() throws Exception {
        String id = Orders.id();
        Orders.start("flow", id);

        var reserve = Wire.command(bootstrap, Topics.INVENTORY_COMMANDS, id, "ReserveStock");
        // A reply as the Camel orchestrator's participants send it: a saga header, no CloudEvent attributes.
        Wire.send(bootstrap, Topics.INVENTORY_RESERVED, id, "{\"orderId\":\"" + id + "\",\"outcome\":\"RESERVED\"}",
                Map.of("type", "InventoryReserved", "orderId", id, "Long-Running-Action", "http://coordinator/" + id));
        assertEquals(0, Wire.records(bootstrap, Topics.PAYMENTS_COMMANDS, id, 1, Duration.ofSeconds(3)).size());

        Wire.reply(bootstrap, Topics.INVENTORY_RESERVED, "InventoryReserved", id, "RESERVED", reserve);
        Wire.command(bootstrap, Topics.PAYMENTS_COMMANDS, id, "TakePayment");
    }

    @Test
    void aReplyWakesOnlyTheInstanceItNames() throws Exception {
        String declined = Orders.id();
        String accepted = Orders.id();
        Orders.start("flow", declined);
        Orders.start("flow", accepted);

        var reserveDeclined = Wire.command(bootstrap, Topics.INVENTORY_COMMANDS, declined, "ReserveStock");
        var reserveAccepted = Wire.command(bootstrap, Topics.INVENTORY_COMMANDS, accepted, "ReserveStock");
        Wire.reply(bootstrap, Topics.INVENTORY_RESERVED, "InventoryReserved", declined, "RESERVED", reserveDeclined);
        Wire.reply(bootstrap, Topics.INVENTORY_RESERVED, "InventoryReserved", accepted, "RESERVED", reserveAccepted);
        var takeDeclined = Wire.command(bootstrap, Topics.PAYMENTS_COMMANDS, declined, "TakePayment");
        var takeAccepted = Wire.command(bootstrap, Topics.PAYMENTS_COMMANDS, accepted, "TakePayment");

        Wire.reply(bootstrap, Topics.PAYMENTS_PROCESSED, "PaymentProcessed", accepted, "PAID", takeAccepted);
        awaitStatus(accepted, "PAID");
        assertEquals("PLACED", Orders.status(declined), "the other instance is still waiting");

        Wire.reply(bootstrap, Topics.PAYMENTS_FAILED, "PaymentFailed", declined, "DECLINED", takeDeclined);
        awaitStatus(declined, "CANCELLED");
        assertEquals("PAID", Orders.status(accepted));
    }
}
