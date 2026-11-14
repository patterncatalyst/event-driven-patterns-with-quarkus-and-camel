package com.patterncatalyst.shipping.enrich;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/** Both forms run in the one application, each from its own consumer group, on the same input. */
@QuarkusTest
@QuarkusTestResource(StateDir.class)
class EnrichmentFormsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CAMEL_GROUP = "notification-enricher-camel";

    @BeforeEach
    void ownerAndTopicAgree() throws Exception {
        StubShippingService.reset();
        Kafka.send(Topics.SHIPPING_CARRIERS, "FASTLINE", Samples.fastline(Samples.FASTLINE_TEMPLATE));
        Kafka.send(Topics.SHIPPING_CARRIERS, "AIRCO", Samples.airco());
        awaitStore("FASTLINE", Samples.FASTLINE_TEMPLATE);
        awaitStore("AIRCO", Samples.AIRCO_TEMPLATE);
    }

    @AfterEach
    void ownerBackUp() {
        StubShippingService.DOWN.set(false);
    }

    private static void awaitStore(String carrier, String template) {
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                given().get("/demo/carriers/" + carrier).then().statusCode(200)
                        .body("trackingUrlTemplate", org.hamcrest.Matchers.equalTo(template)));
    }

    private static String orderId() {
        return "O-" + UUID.randomUUID();
    }

    private static JsonNode awaitOutput(String topic, String orderId) {
        await().atMost(Duration.ofSeconds(60)).until(() -> Kafka.read(topic).containsKey(orderId));
        try {
            return MAPPER.readTree(Kafka.read(topic).get(orderId));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void bothFormsProduceTheSameEnrichedRecord() throws Exception {
        String fastline = orderId();
        String airco = orderId();
        Kafka.schedule(Topics.SHIPPING_SCHEDULED, fastline, Carrier.FASTLINE);
        Kafka.schedule(Topics.SHIPPING_SCHEDULED, airco, Carrier.AIRCO);

        for (String id : new String[] {fastline, airco}) {
            JsonNode viaTable = awaitOutput(Topics.SHIPPING_SCHEDULED_ENRICHED, id);
            JsonNode viaLookup = awaitOutput(Topics.SHIPPING_SCHEDULED_ENRICHED_CAMEL, id);
            assertEquals(viaTable, viaLookup);
        }
        assertEquals("ROAD", awaitOutput(Topics.SHIPPING_SCHEDULED_ENRICHED, fastline).get("carrierMode").asText());
        assertEquals("AIR", awaitOutput(Topics.SHIPPING_SCHEDULED_ENRICHED, airco).get("carrierMode").asText());
    }

    @Test
    void theLookupSeesAChangeAtOnceAndTheTableOnlyOnceItIsPublished() throws Exception {
        String v2 = "https://track.fastline.example/v2/{trackingNumber}";
        StubShippingService.CARRIERS.put("FASTLINE", Samples.fastline(v2));

        String before = orderId();
        Kafka.schedule(Topics.SHIPPING_SCHEDULED, before, Carrier.FASTLINE);
        assertEquals("https://track.fastline.example/v2/TRK-" + before,
                awaitOutput(Topics.SHIPPING_SCHEDULED_ENRICHED_CAMEL, before).get("trackingUrl").asText());
        assertEquals("https://track.fastline.example/TRK-" + before,
                awaitOutput(Topics.SHIPPING_SCHEDULED_ENRICHED, before).get("trackingUrl").asText());

        Kafka.send(Topics.SHIPPING_CARRIERS, "FASTLINE", Samples.fastline(v2));
        awaitStore("FASTLINE", v2);
        String after = orderId();
        Kafka.schedule(Topics.SHIPPING_SCHEDULED, after, Carrier.FASTLINE);
        assertEquals("https://track.fastline.example/v2/TRK-" + after,
                awaitOutput(Topics.SHIPPING_SCHEDULED_ENRICHED, after).get("trackingUrl").asText());

        assertEquals("https://track.fastline.example/TRK-" + before,
                awaitOutput(Topics.SHIPPING_SCHEDULED_ENRICHED, before).get("trackingUrl").asText(),
                "an output already written is not revised");
    }

    @Test
    void aMissingReferenceKeepsTheRecordInTheTableFormAndTheLookupStillEnriches() throws Exception {
        Kafka.send(Topics.SHIPPING_CARRIERS, "AIRCO", null);
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                given().get("/demo/carriers/AIRCO").then().statusCode(404));
        String id = orderId();
        Kafka.schedule(Topics.SHIPPING_SCHEDULED, id, Carrier.AIRCO);

        assertTrue(awaitOutput(Topics.SHIPPING_SCHEDULED_ENRICHED, id).get("carrierMode").isNull());
        assertEquals("AIR", awaitOutput(Topics.SHIPPING_SCHEDULED_ENRICHED_CAMEL, id).get("carrierMode").asText());
    }

    @Test
    void withTheOwnerDownTheTableFormCarriesOnAndTheLookupWaitsWithoutCommitting() throws Exception {
        StubShippingService.DOWN.set(true);
        String id = orderId();
        long offset = Kafka.schedule(Topics.SHIPPING_SCHEDULED, id, Carrier.FASTLINE);

        awaitOutput(Topics.SHIPPING_SCHEDULED_ENRICHED, id);
        await().during(Duration.ofSeconds(8)).atMost(Duration.ofSeconds(12)).untilAsserted(() -> {
            assertFalse(Kafka.read(Topics.SHIPPING_SCHEDULED_ENRICHED_CAMEL).containsKey(id));
            assertTrue(Kafka.committed(CAMEL_GROUP, Topics.SHIPPING_SCHEDULED) <= offset,
                    "the failed record must not be committed past");
        });

        StubShippingService.DOWN.set(false);
        assertEquals("ROAD", awaitOutput(Topics.SHIPPING_SCHEDULED_ENRICHED_CAMEL, id).get("carrierMode").asText());
    }
}
