package com.patterncatalyst.shipping.correlation.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The reservation and its compensation, and a record handled in the trace its header names. */
@QuarkusTest
class ReservationServiceTest {

    @Inject
    ReservationService reservations;

    @Inject
    EntityManager em;

    @Inject
    InMemorySpanExporter exporter;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    private static String sku(int stock) {
        String sku = "SKU-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> StockItem.of(sku, stock).persist());
        return sku;
    }

    private static OrderPlaced order(String sku, int quantity) {
        return new OrderPlaced("O-" + UUID.randomUUID(), "CU-27", sku, quantity, new BigDecimal("25.00"));
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
    void aRecordWithATraceContextIsHandledInThatTraceUnderItsOrderId() throws Exception {
        String sku = sku(10);
        OrderPlaced order = order(sku, 1);
        String traceId = "0af7651916cd43dd8448eb211c80319c";
        send(Topics.ORDERS_PLACED, order.orderId(), UUID.randomUUID(),
                "{\"orderId\":\"%s\",\"customerId\":\"CU-27\",\"itemSku\":\"%s\",\"quantity\":1,\"amount\":25.00}"
                        .formatted(order.orderId(), sku),
                "00-" + traceId + "-b7ad6b7169203331-01");
        await(() -> stock(sku) == 9);
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> exporter.getFinishedSpanItems().stream()
                .anyMatch(s -> s.getName().equals("handle event") && traceId.equals(s.getTraceId())
                        && order.orderId().equals(s.getAttributes().get(AttributeKey.stringKey("order.id")))));
    }

    private void send(String topic, String key, UUID eventId, String json) throws Exception {
        send(topic, key, eventId, json, null);
    }

    private void send(String topic, String key, UUID eventId, String json, String traceparent) throws Exception {
        try (var producer = new KafkaProducer<String, String>(Map.<String, Object>of(
                "bootstrap.servers", bootstrapServers,
                "key.serializer", StringSerializer.class.getName(),
                "value.serializer", StringSerializer.class.getName()))) {
            var record = new ProducerRecord<>(topic, key, json);
            record.headers().add("id", eventId.toString().getBytes(StandardCharsets.UTF_8));
            if (traceparent != null) {
                record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
            }
            producer.send(record).get();
        }
    }

    private static void await(java.util.function.Supplier<Boolean> condition) {
        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(250)).until(condition::get);
    }
}
