package com.patterncatalyst.shipping.evolution;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/** The current release against a store that holds rows of both shapes. */
@QuarkusTest
class OrderStoreTest {

    @Inject
    Database db;

    private static void place(String id, String priority) {
        String body = "{\"id\":\"" + id + "\",\"customerId\":\"C-1\",\"itemSku\":\"SKU-1\",\"quantity\":1,\"amount\":25.00"
                + (priority == null ? "" : ",\"shippingPriority\":\"" + priority + "\"") + "}";
        given().contentType(ContentType.JSON).body(body).post("/orders").then().statusCode(201);
    }

    @Test
    void aMixedStreamOfV1AndV2RowsRehydrates() throws Exception {
        db.storeV1Order("O-T1");
        place("O-T2", "EXPRESS");
        given().get("/orders/O-T1").then().statusCode(200).body("shippingPriority", org.hamcrest.Matchers.is("STANDARD"))
                .body("schemaVersion", org.hamcrest.Matchers.is(1)).body("status", org.hamcrest.Matchers.is("PLACED"));
        given().get("/orders/O-T2").then().statusCode(200).body("shippingPriority", org.hamcrest.Matchers.is("EXPRESS"))
                .body("schemaVersion", org.hamcrest.Matchers.is(2));
    }

    @Test
    void aCommandOnAV1StreamLeavesTheStoredPayloadByteForByte() throws Exception {
        db.storeV1Order("O-T3");
        String before = db.text("SELECT payload::text FROM orders.events WHERE stream_id = 'O-T3' AND version = 1");
        given().post("/orders/O-T3/cancellation").then().statusCode(200);
        assertEquals(before, db.text("SELECT payload::text FROM orders.events WHERE stream_id = 'O-T3' AND version = 1"));
        assertEquals(2, db.count("SELECT count(*) FROM orders.events WHERE stream_id = 'O-T3'"));
        given().get("/orders/O-T3").then().body("status", org.hamcrest.Matchers.is("CANCELLED"));
    }

    @Test
    void theStoreRefusesToUpdateAStoredPayload() throws Exception {
        db.storeV1Order("O-T4");
        SQLException refused = assertThrows(SQLException.class,
                () -> db.execute("UPDATE orders.events SET payload = '{}'::jsonb WHERE stream_id = 'O-T4'"));
        assertTrue(refused.getMessage().contains("append-only"));
    }

    @Test
    void theOutboxRowHoldsAvroBytesThatStartWithTheSchemaIdAndDecodeToTheCurrentRecord() throws Exception {
        Registry.globalRule("BACKWARD_TRANSITIVE");
        place("O-T5", "EXPRESS");
        byte[] payload;
        try (var connection = db.dataSource.getConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT payload, topic FROM orders.outboxevent WHERE aggregateid = 'O-T5'")) {
            assertTrue(rows.next());
            payload = rows.getBytes(1);
            assertEquals("edp.orders.placed", rows.getString(2));
        }
        assertEquals(0, payload[0]);
        int schemaId = ByteBuffer.wrap(payload).getInt(1);
        var versions = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(Registry.call("GET", "/groups/default/artifacts/" + Registry.ARTIFACT + "/versions", null).body());
        assertTrue(java.util.stream.StreamSupport.stream(versions.get("versions").spliterator(), false)
                .anyMatch(version -> version.get("contentId").asInt() == schemaId));
        var decoded = (com.patterncatalyst.shipping.evolution.avro.OrderPlaced) Registry.deserializer()
                .deserialize("edp.orders.placed", payload);
        assertEquals("O-T5", decoded.getOrderId());
        assertEquals("EXPRESS", decoded.getShippingPriority());
    }
}
