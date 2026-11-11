package com.patterncatalyst.shipping.orderservice;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

/** Negative witness: a full-state event with a 100 KB attachment breaks the producer's request limit. */
@QuarkusTest
@TestProfile(InlineAttachmentProfile.class)
class OversizedEventTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    private static Map<String, Object> body(String id, int attachmentBytes) {
        return Map.of("id", id, "customerId", "CU-77", "itemSku", "SKU-42", "quantity", 3, "amount", 59.90,
                "attachment", "g".repeat(attachmentBytes));
    }

    @Test
    void oversizedFullStateEventIsRejectedByTheProducerAndNothingIsPublished() {
        String id = "O-" + UUID.randomUUID();
        String message = given().contentType(ContentType.JSON).body(body(id, 100 * 1024)).post("/orders")
                .then().statusCode(502).extract().asString();
        assertTrue(message.contains("rejected by the producer"), message);
        assertTrue(TopicReader.keyed(bootstrap, Topics.ORDERS_PLACED, id).isEmpty(), "nothing was published");
        // The order itself committed before the publish: the producer refused only the event.
        assertEquals(1, QuarkusTransaction.requiringNew().call(() -> OrderRow.count("id", id)));
    }

    @Test
    void sameModeUnderTheLimitIsPublishedInline() {
        String id = "O-" + UUID.randomUUID();
        given().contentType(ContentType.JSON).body(body(id, 10 * 1024)).post("/orders").then().statusCode(201);
        var records = TopicReader.keyed(bootstrap, Topics.ORDERS_PLACED, id);
        assertEquals(1, records.size());
        assertTrue(records.get(0).value().length() > 10 * 1024, "the content rides inside the event");
    }
}
