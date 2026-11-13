package com.patterncatalyst.shipping.evolution;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/** What the previous release leaves behind: a version 1 row, and version 1 of the schema in the registry. */
@QuarkusTest
@TestProfile(PreviousReleaseProfile.class)
class PreviousReleaseTest {

    @Inject
    Database db;

    @Test
    void releaseOneStoresV1RowsAndRegistersTheV1Schema() throws Exception {
        Registry.globalRule("BACKWARD_TRANSITIVE");
        given().contentType(ContentType.JSON)
                .body("{\"id\":\"O-P1\",\"customerId\":\"C-1\",\"itemSku\":\"SKU-1\",\"quantity\":1,\"amount\":25.00}")
                .post("/orders").then().statusCode(201);
        assertEquals(1, db.count("SELECT count(*) FROM orders.events WHERE stream_id = 'O-P1' AND schema_version = 1 "
                + "AND NOT jsonb_exists(payload, 'shippingPriority')"));
        given().get("/orders/O-P1").then().body("shippingPriority", is("STANDARD")).body("schemaVersion", is(1));
        assertTrue(Registry.versionCount(Registry.ARTIFACT) >= 1);
    }
}
