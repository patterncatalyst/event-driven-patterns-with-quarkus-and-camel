package com.patterncatalyst.shipping.evolution.notification;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/** The Camel route reads the same two shapes with the deserializer and the registry named in the endpoint. */
@QuarkusTest
@TestProfile(CamelReaderProfile.class)
class OrderPlacedReaderRouteTest {

    @Inject
    RecordedPriorities recorded;

    @Test
    void theRouteResolvesV1AndV2Records() throws Exception {
        Topic.sendV1("O-N3");
        Topic.sendV2("O-N4", "EXPRESS");
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            assertEquals("STANDARD/camel", recorded.of("O-N3"));
            assertEquals("EXPRESS/camel", recorded.of("O-N4"));
        });
    }
}
