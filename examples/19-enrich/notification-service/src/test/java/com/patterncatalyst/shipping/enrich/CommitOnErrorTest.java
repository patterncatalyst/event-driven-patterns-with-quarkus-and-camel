package com.patterncatalyst.shipping.enrich;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/** Without breakOnFirstError a record whose lookup fails is committed, and no later start enriches it. */
@QuarkusTest
@QuarkusTestResource(StateDir.class)
@TestProfile(CommitOnErrorProfile.class)
class CommitOnErrorTest {

    @Test
    void theFailedRecordIsCommittedAndNeverEnriched() throws Exception {
        StubShippingService.reset();
        Kafka.send(Topics.SHIPPING_CARRIERS, "FASTLINE", Samples.fastline(Samples.FASTLINE_TEMPLATE));
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                given().get("/demo/carriers/FASTLINE").then().statusCode(200));

        StubShippingService.DOWN.set(true);
        String id = "O-" + UUID.randomUUID();
        long offset = Kafka.schedule(Topics.SHIPPING_SCHEDULED, id, Carrier.FASTLINE);

        await().atMost(Duration.ofSeconds(40)).until(() ->
                Kafka.committed("notification-enricher-camel", Topics.SHIPPING_SCHEDULED) > offset);
        StubShippingService.DOWN.set(false);
        await().during(Duration.ofSeconds(6)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertFalse(Kafka.read(Topics.SHIPPING_SCHEDULED_ENRICHED_CAMEL).containsKey(id)));
        assertTrue(Kafka.read(Topics.SHIPPING_SCHEDULED_ENRICHED).containsKey(id),
                "the table form enriched it, from the local copy");
    }
}
