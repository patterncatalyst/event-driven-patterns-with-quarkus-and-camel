package com.patterncatalyst.shipping.evolution;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/** The negative witness: without the chain a stored v1 row cannot be read as the current record. */
@QuarkusTest
@TestProfile(UpcastersOffProfile.class)
class UpcastersOffTest {

    @Inject
    Database db;

    @Test
    void aV1RowFailsToLoadWithoutTheChain() throws Exception {
        db.storeV1Order("O-U1");
        assertEquals(500, given().get("/orders/O-U1").statusCode());
    }
}
