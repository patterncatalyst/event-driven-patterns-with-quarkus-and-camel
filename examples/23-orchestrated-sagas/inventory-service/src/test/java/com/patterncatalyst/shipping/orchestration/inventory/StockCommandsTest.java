package com.patterncatalyst.shipping.orchestration.inventory;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** ReserveStock and ReleaseStock as commands on the topic, with Dev Services Kafka and PostgreSQL. */
@QuarkusTest
class StockCommandsTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Inject
    ReservationService reservations;

    private static String id() {
        return "O-" + UUID.randomUUID();
    }

    private static int stock() {
        return QuarkusTransaction.requiringNew().call(() -> ((InventoryItemEntity) InventoryItemEntity.findById("SKU-23")).quantityOnHand);
    }

    private static String status(String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            ReservationEntity r = ReservationEntity.findById(orderId);
            return r == null ? null : r.status;
        });
    }

    private static String reserve(String id, int quantity) {
        return "{\"orderId\":\"" + id + "\",\"itemSku\":\"SKU-23\",\"quantity\":" + quantity + "}";
    }

    @Test
    void reserveWithoutASagaHeaderRunsPlainAndAnswers() throws Exception {
        String id = id();
        int before = stock();
        Records.command(bootstrap, Topics.INVENTORY_COMMANDS, "ReserveStock", id, reserve(id, 1), Map.of());

        var replies = Records.replies(bootstrap, Topics.INVENTORY_RESERVED, id, Duration.ofSeconds(30));
        assertEquals(1, replies.size());
        assertEquals("InventoryReserved", Records.header(replies.get(0), "type"));
        assertEquals("RESERVED", status(id));
        assertEquals(before - 1, stock());
    }

    @Test
    void theReplyCarriesTheSagaHeaderOfTheCommand() throws Exception {
        String id = id();
        Records.command(bootstrap, Topics.INVENTORY_COMMANDS, "ReserveStock", id, reserve(id, 1),
                Map.of("Long-Running-Action", "http://coordinator/lra-coordinator/" + id));

        var replies = Records.replies(bootstrap, Topics.INVENTORY_RESERVED, id, Duration.ofSeconds(30));
        assertEquals(1, replies.size());
        assertNotNull(Records.header(replies.get(0), "Long-Running-Action"));
    }

    @Test
    void theReplyCarriesTheCloudEventAttributesOfAWorkflowInstance() throws Exception {
        String id = id();
        Records.command(bootstrap, Topics.INVENTORY_COMMANDS, "ReserveStock", id, reserve(id, 1), Map.of("flowinstanceid", "01INSTANCE"));

        var replies = Records.replies(bootstrap, Topics.INVENTORY_RESERVED, id, Duration.ofSeconds(30));
        assertEquals(1, replies.size());
        assertEquals("01INSTANCE", Records.header(replies.get(0), "ce_flowinstanceid"));
        assertEquals("InventoryReserved", Records.header(replies.get(0), "ce_type"));
    }

    @Test
    void notEnoughStockAnswersInsufficientAndTakesNothing() throws Exception {
        String id = id();
        int before = stock();
        Records.command(bootstrap, Topics.INVENTORY_COMMANDS, "ReserveStock", id, reserve(id, 1000), Map.of());

        var replies = Records.replies(bootstrap, Topics.INVENTORY_INSUFFICIENT, id, Duration.ofSeconds(30));
        assertEquals(1, replies.size());
        assertEquals(before, stock());
        assertEquals(null, status(id));
    }

    @Test
    void releaseCommandPutsTheStockBackOnce() throws Exception {
        String id = id();
        int before = stock();
        Records.command(bootstrap, Topics.INVENTORY_COMMANDS, "ReserveStock", id, reserve(id, 2), Map.of());
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertEquals(before - 2, stock()));

        Records.command(bootstrap, Topics.INVENTORY_COMMANDS, "ReleaseStock", id, "{\"orderId\":\"" + id + "\"}", Map.of());
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertEquals("RELEASED", status(id)));
        assertEquals(before, stock());
    }

    @Test
    void releaseIsIdempotent() {
        String id = id();
        int before = stock();
        reservations.reserve(new ReserveStock(id, "SKU-23", 3));
        reservations.release(id);
        reservations.release(id);
        assertEquals(before, stock());
    }

    @Test
    void releaseBeforeReserveLeavesTheStockUntouched() {
        String id = id();
        int before = stock();
        reservations.release(id);
        var late = reservations.reserve(new ReserveStock(id, "SKU-23", 3));
        assertEquals(true, late.isEmpty());
        assertEquals(before, stock());
        assertEquals("RELEASED", status(id));
    }
}
