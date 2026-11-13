package com.patterncatalyst.shipping.evolution.notification;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** The v2 reader on a topic that holds records of both releases. */
@QuarkusTest
class OrderPlacedConsumerTest {

    @Inject
    RecordedPriorities recorded;

    @Test
    void v1RecordsReadAsStandardAndV2RecordsKeepTheirPriority() throws Exception {
        Topic.sendV1("O-N1");
        Topic.sendV2("O-N2", "EXPRESS");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals("STANDARD/smallrye", recorded.of("O-N1"));
            assertEquals("EXPRESS/smallrye", recorded.of("O-N2"));
        });
    }
}
