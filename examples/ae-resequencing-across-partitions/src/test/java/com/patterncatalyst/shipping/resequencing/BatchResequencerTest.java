package com.patterncatalyst.shipping.resequencing;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/** The batch form sorts within a batch only. */
@QuarkusTest
@TestProfile(BatchProfile.class)
class BatchResequencerTest {

    @Inject
    ProducerTemplate template;

    @Test
    void eachBatchIsSortedAndAnEventInTheNextBatchComesAfterItsSuccessor() {
        // First batch of six, then a second batch of six: the first order's event 1 is in the second batch.
        Events.send(template, "B-1", 2, 3);
        Events.send(template, "B-2", 1, 2);
        Events.send(template, "B-2", 2, 5);
        Events.send(template, "B-3", 1, 4);
        Events.send(template, "B-3", 2, 8);
        Events.send(template, "B-4", 1, 7);

        Events.send(template, "B-1", 1, 1);
        Events.send(template, "B-1", 3, 6);
        Events.send(template, "B-2", 3, 9);
        Events.send(template, "B-3", 3, 11);
        Events.send(template, "B-4", 2, 10);
        Events.send(template, "B-4", 3, 12);

        List<String> expected = List.of(
                "B-1:2", "B-2:1", "B-2:2", "B-3:1", "B-3:2", "B-4:1",
                "B-1:1", "B-1:3", "B-2:3", "B-3:3", "B-4:2", "B-4:3");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertEquals(expected, Events.appliedOrderSequences("B-")));
        List<String> applied = Events.appliedOrderSequences("B-");
        assertTrue(applied.indexOf("B-1:2") < applied.indexOf("B-1:1"));
    }
}
