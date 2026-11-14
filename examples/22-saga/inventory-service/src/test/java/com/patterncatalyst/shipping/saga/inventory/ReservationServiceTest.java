package com.patterncatalyst.shipping.saga.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The reservation and its compensation: once, commutative, and the same under a duplicate delivery. */
@QuarkusTest
class ReservationServiceTest {

    @Inject
    ReservationService reservations;

    @Inject
    EntityManager em;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    private static String sku(int stock) {
        String sku = "SKU-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> StockItem.of(sku, stock).persist());
        return sku;
    }

    private static OrderPlaced order(String sku, int quantity) {
        return new OrderPlaced("O-" + UUID.randomUUID(), "CU-22", sku, quantity, new BigDecimal("25.00"));
    }

    private static int stock(String sku) {
        return QuarkusTransaction.requiringNew().call(() -> ((StockItem) StockItem.findById(sku)).quantityOnHand);
    }

    private static String state(String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Reservation row = Reservation.findById(orderId);
            return row == null ? "NONE" : row.state;
        });
    }

    private long outbox(String type, String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                "select count(*) from inventory.outboxevent where type = ?1 and aggregateid = ?2")
                .setParameter(1, type).setParameter(2, orderId).getSingleResult()).longValue());
    }

    @Test
    void aReservationHoldsStockAndAReleasePutsItBack() {
        String sku = sku(10);
        OrderPlaced order = order(sku, 3);
        assertTrue(reservations.reserve(UUID.randomUUID(), order));
        assertEquals(7, stock(sku));
        assertEquals(1, outbox("InventoryReserved", order.orderId()));
        assertTrue(reservations.release(UUID.randomUUID(), order.orderId()));
        assertEquals(10, stock(sku));
        assertEquals("RELEASED", state(order.orderId()));
    }

    @Test
    void releasingTwiceReleasesOnce() {
        String sku = sku(10);
        OrderPlaced order = order(sku, 3);
        reservations.reserve(UUID.randomUUID(), order);
        assertTrue(reservations.release(UUID.randomUUID(), order.orderId()));
        assertFalse(reservations.release(UUID.randomUUID(), order.orderId()));
        assertEquals(10, stock(sku));
    }

    @Test
    void aReleaseBeforeTheReservationRefusesTheReservation() {
        String sku = sku(10);
        OrderPlaced order = order(sku, 3);
        assertFalse(reservations.release(UUID.randomUUID(), order.orderId()));
        assertFalse(reservations.reserve(UUID.randomUUID(), order));
        assertEquals(10, stock(sku));
        assertEquals("RELEASED", state(order.orderId()));
        assertEquals(0, outbox("InventoryReserved", order.orderId()));
    }

    @Test
    void notEnoughStockIsAnsweredAndHoldsNothing() {
        String sku = sku(2);
        OrderPlaced order = order(sku, 3);
        assertFalse(reservations.reserve(UUID.randomUUID(), order));
        assertEquals(2, stock(sku));
        assertEquals("INSUFFICIENT", state(order.orderId()));
        assertEquals(1, outbox("InventoryInsufficient", order.orderId()));
        assertFalse(reservations.release(UUID.randomUUID(), order.orderId()));
        assertEquals(2, stock(sku));
    }

    @Test
    void theSameEventIdIsAppliedOnce() {
        String sku = sku(10);
        OrderPlaced order = order(sku, 3);
        UUID reserve = UUID.randomUUID();
        assertTrue(reservations.reserve(reserve, order));
        assertFalse(reservations.reserve(reserve, order));
        assertEquals(7, stock(sku));
        UUID release = UUID.randomUUID();
        assertTrue(reservations.release(release, order.orderId()));
        assertFalse(reservations.release(release, order.orderId()));
        assertEquals(10, stock(sku));
    }

    /** A probabilistic catch: a reservation and a release of one order, released together, end in the same place in either order. */
    @Test
    void aReservationAndAReleaseInAnyOrderEndWithTheStockBack() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int round = 1; round <= 50; round++) {
                String sku = sku(10);
                OrderPlaced order = order(sku, 3);
                var barrier = new CyclicBarrier(2);
                Future<Boolean> reserve = pool.submit(() -> {
                    barrier.await();
                    return reservations.reserve(UUID.randomUUID(), order);
                });
                Future<Boolean> release = pool.submit(() -> {
                    barrier.await();
                    return reservations.release(UUID.randomUUID(), order.orderId());
                });
                reserve.get();
                release.get();
                assertEquals(10, stock(sku), "round " + round);
                assertEquals("RELEASED", state(order.orderId()), "round " + round);
            }
        }
    }

    @Test
    void recordsOnTheTopicsReserveAndRelease() throws Exception {
        String sku = sku(10);
        OrderPlaced order = order(sku, 4);
        send(Topics.ORDERS_PLACED, order.orderId(), UUID.randomUUID(),
                "{\"orderId\":\"%s\",\"customerId\":\"CU-22\",\"itemSku\":\"%s\",\"quantity\":4,\"amount\":25.00}"
                        .formatted(order.orderId(), sku));
        await(() -> stock(sku) == 6);
        UUID duplicated = UUID.randomUUID();
        String failed = "{\"orderId\":\"%s\",\"reason\":\"DECLINED\"}".formatted(order.orderId());
        send(Topics.PAYMENTS_FAILED, order.orderId(), duplicated, failed);
        send(Topics.PAYMENTS_FAILED, order.orderId(), duplicated, failed);
        await(() -> stock(sku) == 10);
        Thread.sleep(2000);
        assertEquals(10, stock(sku));
        assertEquals(1, outbox("InventoryReserved", order.orderId()));
    }

    private void send(String topic, String key, UUID eventId, String json) throws Exception {
        try (var producer = new KafkaProducer<String, String>(Map.<String, Object>of(
                "bootstrap.servers", bootstrapServers,
                "key.serializer", StringSerializer.class.getName(),
                "value.serializer", StringSerializer.class.getName()))) {
            var record = new ProducerRecord<>(topic, key, json);
            record.headers().add("id", eventId.toString().getBytes(StandardCharsets.UTF_8));
            producer.send(record).get();
        }
    }

    private static void await(java.util.function.Supplier<Boolean> condition) {
        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(250)).until(condition::get);
    }
}
