package com.patterncatalyst.shipping.evolution;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/** A release whose OrderPlaced the registry refuses: the command leaves no event row and no outbox row. */
@QuarkusTest
@TestProfile(BrokenReleaseProfile.class)
class BrokenReleaseTest {

    @Inject
    Database db;

    @Test
    void aRefusedSchemaRollsTheCommandBack() throws Exception {
        Registry.globalRule("BACKWARD_TRANSITIVE");
        // v1 and v2 are registered first: a first-ever v3 would be accepted.
        var serializer = Registry.serializer();
        serializer.serialize("edp.orders.placed", Registry.record(Registry.v1(), null));
        serializer.serialize("edp.orders.placed", Registry.v2Record("EXPRESS"));
        int versions = Registry.versionCount(Registry.ARTIFACT);

        var reply = given().contentType(ContentType.JSON)
                .body("{\"id\":\"O-B1\",\"customerId\":\"C-1\",\"itemSku\":\"SKU-1\",\"quantity\":1,\"amount\":25.00}")
                .post("/orders");

        assertEquals(503, reply.statusCode());
        assertTrue(reply.asString().contains("Incompatible artifact"));
        assertEquals(0, db.count("SELECT count(*) FROM orders.events WHERE stream_id = 'O-B1'"));
        assertEquals(0, db.count("SELECT count(*) FROM orders.outboxevent WHERE aggregateid = 'O-B1'"));
        assertEquals(versions, Registry.versionCount(Registry.ARTIFACT));
    }
}
