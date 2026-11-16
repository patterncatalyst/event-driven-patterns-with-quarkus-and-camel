package com.patterncatalyst.shipping.resequencing;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** The stream form on the global sequence. The tests share one resequencer, so their numbers continue each other. */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StreamResequencerTest {

    @Inject
    ProducerTemplate template;

    @Test
    @Order(1)
    void scrambledArrivalIsAppliedInGlobalOrder() {
        // Global order: 1 A1, 2 B1, 3 A2, 4 B2, 5 A3, 6 B3. Arrival order is scrambled across orders and events.
        Events.send(template, "T-A", 3, 5);
        Events.send(template, "T-B", 2, 4);
        Events.send(template, "T-A", 1, 1);
        Events.send(template, "T-B", 3, 6);
        Events.send(template, "T-B", 1, 2);
        Events.send(template, "T-A", 2, 3);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L), Events.appliedGlobalSequences("T-")));
    }

    @Test
    @Order(2)
    void aGapIsSkippedAtTheTimeoutAndALateEventIsAppliedLast() {
        // 10 never arrives in time: 7, 8 and 9 follow 6 at once, 11 and 12 wait for the timeout.
        Events.send(template, "T-C", 1, 7);
        Events.send(template, "T-D", 1, 8);
        Events.send(template, "T-E", 1, 9);
        Events.send(template, "T-F", 1, 11);
        Events.send(template, "T-G", 1, 12);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 11L, 12L), Events.appliedGlobalSequences("T-")));

        // Without rejectOld the late event is applied when it arrives, after the ones that followed it.
        Events.send(template, "T-H", 1, 10);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 11L, 12L, 10L), Events.appliedGlobalSequences("T-")));
    }
}
