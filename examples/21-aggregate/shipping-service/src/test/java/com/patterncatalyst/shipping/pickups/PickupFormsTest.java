package com.patterncatalyst.shipping.pickups;

import static io.restassured.RestAssured.get;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.Shipment;
import com.patterncatalyst.shipping.ShipmentStatus;
import com.patterncatalyst.shipping.Topics;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Both forms in one application against Dev Services Kafka and PostgreSQL: the Camel batches by size and timeout, the
 * repository that holds the open batch, and the Kafka Streams list over the same input. Each test uses its own
 * shipment ids and reads the whole output topic, because the topics are shared by the tests.
 */
@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
@QuarkusTestResource(StreamsStateDir.class)
class PickupFormsTest {

    static final LocalDate DAY = LocalDate.now(ZoneOffset.UTC).minusDays(1);

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Inject
    ObjectMapper mapper;

    @Inject
    DataSource dataSource;

    @BeforeEach
    void streamsIsRunning() {
        await().atMost(Duration.ofSeconds(90)).until(() -> get("/q/health/live").statusCode() == 200);
    }

    void send(String id, Carrier carrier, String hhmm) throws Exception {
        var shipment = new Shipment(id, "O-" + id, carrier, "TRK-" + id, ShipmentStatus.SCHEDULED);
        var event = new ShipmentScheduled(shipment, Instant.parse(DAY + "T" + hhmm + ":00Z"));
        companion.produceStrings().fromRecords(
                new ProducerRecord<>(Topics.SHIPPING_SCHEDULED, "O-" + id, mapper.writeValueAsString(event))).awaitCompletion();
    }

    List<PickupList> read(String topic) {
        return companion.consumeStrings().fromTopics(topic, Duration.ofSeconds(3)).awaitCompletion().getRecords().stream()
                .map(r -> {
                    try {
                        return mapper.readValue(r.value(), PickupList.class);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).toList();
    }

    List<PickupList> batchesWith(String prefix) {
        return read(Topics.SHIPPING_PICKUP_LISTS_CAMEL).stream()
                .filter(l -> l.shipmentIds().stream().anyMatch(i -> i.startsWith(prefix))).toList();
    }

    @Test
    void aBatchClosesAtFiveShipmentsAndTheRemainderClosesOnTheTimeout() throws Exception {
        for (int i = 1; i <= 7; i++) {
            send("SZ" + i, Carrier.FASTLINE, "03:0" + i);
        }
        await().atMost(Duration.ofSeconds(30)).until(() -> batchesWith("SZ").size() == 2);

        var batches = batchesWith("SZ");
        assertEquals(List.of("SZ1", "SZ2", "SZ3", "SZ4", "SZ5"), batches.get(0).shipmentIds());
        assertEquals("size", batches.get(0).closedBy());
        assertEquals(List.of("SZ6", "SZ7"), batches.get(1).shipmentIds());
        assertEquals("timeout", batches.get(1).closedBy());
    }

    @Test
    void aBatchHoldsOneCarrierEvenWhenTheArrivalsAlternate() throws Exception {
        send("MX1", Carrier.FASTLINE, "04:01");
        send("MX2", Carrier.AIRCO, "04:02");
        send("MX3", Carrier.FASTLINE, "04:03");
        send("MX4", Carrier.AIRCO, "04:04");
        send("MX5", Carrier.AIRCO, "04:05");
        await().atMost(Duration.ofSeconds(30)).until(() -> batchesWith("MX").size() == 2);

        for (PickupList batch : batchesWith("MX")) {
            assertEquals(batch.carrier() == Carrier.FASTLINE ? List.of("MX1", "MX3") : List.of("MX2", "MX4", "MX5"),
                    batch.shipmentIds());
            assertEquals("timeout", batch.closedBy());
        }
    }

    @Test
    void aBatchIsKeyedByItsCarrier() throws Exception {
        send("KY1", Carrier.AIRCO, "06:01");
        await().atMost(Duration.ofSeconds(30)).until(() -> batchesWith("KY").size() == 1);

        var record = companion.consumeStrings().fromTopics(Topics.SHIPPING_PICKUP_LISTS_CAMEL, Duration.ofSeconds(3))
                .awaitCompletion().getRecords().stream().filter(r -> r.value().contains("KY1")).findFirst().orElseThrow();
        assertEquals("AIRCO", record.key());
    }

    @Test
    void theOpenBatchIsARowInPostgresUntilItCloses() throws Exception {
        send("RP1", Carrier.AIRCO, "05:01");
        send("RP2", Carrier.AIRCO, "05:02");

        await().atMost(Duration.ofSeconds(30)).until(() -> openRows() > 0);
        await().atMost(Duration.ofSeconds(30)).until(() -> batchesWith("RP").size() == 1);
        await().atMost(Duration.ofSeconds(10)).until(() -> openRows() == 0);
    }

    @Test
    void bothFormsGroupTheSameInputAndTheStreamsListIsFinalOnlyAfterTheTick() throws Exception {
        for (int i = 1; i <= 5; i++) {
            send("BF" + i, Carrier.FASTLINE, "20:0" + i);
        }
        send("BF6", Carrier.FASTLINE, "20:16");
        send("BF7", Carrier.FASTLINE, "20:10");
        await().atMost(Duration.ofSeconds(30)).until(() -> ids(batchesWith("BF")).size() == 7);
        assertTrue(read(Topics.SHIPPING_PICKUP_LISTS).stream()
                .noneMatch(l -> l.shipmentIds().stream().anyMatch(i -> i.startsWith("BF"))), "no list before the tick");

        send("BFTICK", Carrier.AIRCO, "20:21");

        await().atMost(Duration.ofSeconds(30)).until(() -> read(Topics.SHIPPING_PICKUP_LISTS).stream()
                .anyMatch(l -> l.shipmentIds().contains("BF1")));
        var list = read(Topics.SHIPPING_PICKUP_LISTS).stream().filter(l -> l.shipmentIds().contains("BF1")).findFirst()
                .orElseThrow();
        assertEquals(Set.of("BF1", "BF2", "BF3", "BF4", "BF5", "BF7"), new HashSet<>(list.shipmentIds()));
        assertEquals(6, list.count());
        assertEquals("window", list.closedBy());
        assertTrue(ids(batchesWith("BF")).containsAll(list.shipmentIds()));
    }

    private static Set<String> ids(List<PickupList> lists) {
        Set<String> ids = new HashSet<>();
        lists.forEach(l -> l.shipmentIds().stream().filter(i -> i.startsWith("BF")).forEach(ids::add));
        return ids;
    }

    private int openRows() throws SQLException {
        try (var c = dataSource.getConnection(); var s = c.createStatement();
                var rs = s.executeQuery("SELECT count(*) FROM shipping.pickups")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
