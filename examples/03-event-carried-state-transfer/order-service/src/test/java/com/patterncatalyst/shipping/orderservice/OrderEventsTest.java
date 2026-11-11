package com.patterncatalyst.shipping.orderservice;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class OrderEventsTest {

    @Inject
    ObjectMapper json;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    private static Map<String, Object> body(String id, String attachment) {
        return attachment == null
                ? Map.of("id", id, "customerId", "CU-77", "itemSku", "SKU-42", "quantity", 3, "amount", 59.90)
                : Map.of("id", id, "customerId", "CU-77", "itemSku", "SKU-42", "quantity", 3, "amount", 59.90,
                        "attachment", attachment);
    }

    private static int place(String id, String attachment) {
        return given().contentType(ContentType.JSON).body(body(id, attachment)).post("/orders").statusCode();
    }

    private JsonNode only(String topic, String id) throws Exception {
        var records = TopicReader.keyed(bootstrap, topic, id);
        assertEquals(1, records.size(), "one record keyed " + id);
        return json.readTree(records.get(0).value());
    }

    @Test
    void placedEventCarriesTheWholeOrderAndItsStatus() throws Exception {
        String id = "O-" + UUID.randomUUID();
        assertEquals(201, place(id, null));
        JsonNode event = only(Topics.ORDERS_PLACED, id);
        assertEquals(id, event.get("orderId").asText());
        assertEquals("PLACED", event.get("status").asText());
        assertEquals("CU-77", event.get("order").get("customerId").asText());
        assertEquals("SKU-42", event.get("order").get("itemSku").asText());
        assertEquals(3, event.get("order").get("quantity").asInt());
        assertEquals(0, new java.math.BigDecimal("59.90").compareTo(event.get("order").get("amount").decimalValue()));
        assertFalse(event.has("claim"), "no attachment, no claim");
    }

    @Test
    void cancelledEventCarriesTheOrderWithStatusCancelled() throws Exception {
        String id = "O-" + UUID.randomUUID();
        place(id, null);
        given().post("/orders/" + id + "/cancel").then().statusCode(204);
        JsonNode event = only(Topics.ORDERS_CANCELLED, id);
        assertEquals("CANCELLED", event.get("status").asText());
        assertEquals("SKU-42", event.get("order").get("itemSku").asText());
        given().post("/orders/" + id + "/cancel").then().statusCode(409);
        assertEquals(1, TopicReader.keyed(bootstrap, Topics.ORDERS_CANCELLED, id).size(), "a refused cancel publishes nothing");
    }

    @Test
    void invalidOrderIsRefusedAndNothingIsPublished() {
        String id = "O-" + UUID.randomUUID();
        given().contentType(ContentType.JSON)
                .body(Map.of("id", id, "customerId", "CU-77", "itemSku", "SKU-42", "quantity", 0, "amount", 5))
                .post("/orders").then().statusCode(400);
        assertTrue(TopicReader.keyed(bootstrap, Topics.ORDERS_PLACED, id).isEmpty());
    }

    @Test
    void attachmentOf100KbTravelsAsAClaimAndTheEventStaysSmall() throws Exception {
        String id = "O-" + UUID.randomUUID();
        String attachment = "g".repeat(100 * 1024);
        assertEquals(201, place(id, attachment));
        var records = TopicReader.keyed(bootstrap, Topics.ORDERS_PLACED, id);
        assertEquals(1, records.size());
        assertTrue(records.get(0).value().length() < 1024, "the event is small");
        JsonNode claim = json.readTree(records.get(0).value()).get("claim");
        assertEquals(100 * 1024, claim.get("size").asLong());
        assertFalse(records.get(0).value().contains("ggg"), "the content is not in the event");
        String content = given().get("/claims/" + claim.get("id").asText()).then().statusCode(200).extract().asString();
        assertEquals(attachment, content);
    }

    @Test
    void unknownClaimIs404() {
        given().get("/claims/" + UUID.randomUUID()).then().statusCode(404);
    }

    @Test
    void orderIsReadableOverRest() {
        String id = "O-" + UUID.randomUUID();
        place(id, null);
        given().get("/orders/" + id).then().statusCode(200).body("status", equalTo("PLACED"));
    }
}
