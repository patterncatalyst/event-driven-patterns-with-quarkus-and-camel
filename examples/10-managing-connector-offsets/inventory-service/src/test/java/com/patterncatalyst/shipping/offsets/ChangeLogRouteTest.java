package com.patterncatalyst.shipping.offsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

/** The route turns Debezium change events into change-log rows and ignores the fields it does not keep. */
@QuarkusTest
class ChangeLogRouteTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    /** A schemaless Debezium envelope for the legacy inventory_item table, with the source block the connector adds. */
    static String envelope(String op, String sku, int qty, long lsn) {
        return """
                {"before":null,"after":{"item_sku":"%s","qty_on_hand":%d,"last_changed":"2026-11-11T10:00:00.000000Z"},
                 "source":{"version":"3.7.1.Final","connector":"postgresql","name":"edp","db":"edp","schema":"inventory",
                 "table":"inventory_item","txId":762,"lsn":%d,"snapshot":"false"},"op":"%s","ts_ms":1794407704219}
                """.formatted(sku, qty, lsn, op).replace("\n", "");
    }

    static void send(String bootstrapServers, String sku, String json) {
        try (var producer = new KafkaProducer<>(Map.<String, Object>of("bootstrap.servers", bootstrapServers),
                new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(Topics.INVENTORY_ITEM_CHANGES, sku, json));
        }
    }

    static List<ItemChange> changes(String sku) {
        return QuarkusTransaction.requiringNew().call(() -> ItemChange.<ItemChange>list("sku = ?1 order by id", sku));
    }

    static void awaitRows(String sku, int count) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> changes(sku).size() >= count);
    }

    @Test
    void aSnapshotReadAndAnUpdateAreLoggedInOrderWithTheirLsn() {
        send(bootstrapServers, "T-1", envelope("r", "T-1", 100, 1000));
        send(bootstrapServers, "T-1", envelope("u", "T-1", 90, 2000));
        awaitRows("T-1", 2);
        List<ItemChange> rows = changes("T-1");
        assertEquals(List.of("r", "u"), rows.stream().map(c -> c.op).toList());
        assertEquals(List.of(100, 90), rows.stream().map(c -> c.qty).toList());
        assertEquals(List.of(1000L, 2000L), rows.stream().map(c -> c.lsn).toList());
    }

    @Test
    void eachEventIsLoggedOnce() {
        send(bootstrapServers, "T-2", envelope("u", "T-2", 5, 3000));
        awaitRows("T-2", 1);
        Awaitility.await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).until(() -> true);
        assertEquals(1, changes("T-2").size());
    }
}
