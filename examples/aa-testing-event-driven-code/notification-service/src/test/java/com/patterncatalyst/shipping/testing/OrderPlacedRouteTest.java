package com.patterncatalyst.shipping.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.OrderStatus;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;

/**
 * The route against a real broker from Dev Services. Every test uses its own order ids, because the topics are shared,
 * and reads the output topic with a consumer group of its own.
 */
@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
class OrderPlacedRouteTest {

    static final Duration WINDOW = Duration.ofSeconds(8);

    @InjectKafkaCompanion
    KafkaCompanion companion;

    void send(String eventId, String orderId) {
        companion.produceStrings().fromRecords(Events.record(eventId, orderId)).awaitCompletion();
    }

    /** Everything that reaches the topic during the whole window, for the order ids the test asks about. */
    List<ConsumerRecord<String, String>> drain(String orderId) {
        return companion.consumeStrings()
                .withGroupId("drain-" + System.nanoTime())
                .withOffsetReset("earliest")
                .fromTopics(OrderPlacedNotificationRoute.NOTIFIED, WINDOW)
                .awaitCompletion().getRecords().stream()
                .filter(r -> r.value().contains(orderId)).toList();
    }

    // Returns as soon as one record for the order arrives. It cannot see a second one.
    @Test
    void firstMatchSeesTheNotification() {
        send("E-3601", "O-3601");
        ConsumerRecord<String, String> first = companion.consumeStrings()
                .fromTopics(OrderPlacedNotificationRoute.NOTIFIED, records -> records.filter(r -> r.value().contains("O-3601")).select().first())
                .awaitCompletion(Duration.ofSeconds(15)).getFirstRecord();
        assertTrue(first.value().contains("O-3601"));
    }

    // Waits out the window, so a second notification would be counted.
    @Test
    void aRepeatedEventYieldsExactlyOneNotification() {
        send("E-3602", "O-3602");
        send("E-3602", "O-3602");
        assertEquals(1, drain("O-3602").size());
    }

    @Test
    void aCancelledOrderGetsNoNotification() {
        StubOrderService.STATUS.put("O-3603", OrderStatus.CANCELLED);
        send("E-3603", "O-3603");
        assertEquals(0, drain("O-3603").size());
    }

    @Test
    void theNotificationIsKeyedByTheOrderId() {
        send("E-3604", "O-3604");
        List<ConsumerRecord<String, String>> mine = drain("O-3604");
        assertEquals(1, mine.size());
        assertEquals("O-3604", mine.getFirst().key());
    }
}
