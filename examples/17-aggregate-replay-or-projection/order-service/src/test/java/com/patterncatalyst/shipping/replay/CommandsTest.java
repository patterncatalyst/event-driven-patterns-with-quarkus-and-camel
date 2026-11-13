package com.patterncatalyst.shipping.replay;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** The commands, through the REST API: the aggregate decides, a refusal is 409 and appends nothing. */
@QuarkusTest
class CommandsTest {

    @Inject
    EventStore store;

    private long events(String order) {
        return store.count("streamId", order);
    }

    @Test
    void anOrderIsPlacedPaidAndShipped() {
        given().post("/orders/O-C1").then().statusCode(201);
        given().post("/orders/O-C1/payment").then().statusCode(201);
        given().queryParam("carrier", "FASTLINE").post("/orders/O-C1/shipment").then().statusCode(201);

        assertEquals(3, events("O-C1"));
    }

    @Test
    void anOrderCannotBeShippedBeforeItIsPaid() {
        given().post("/orders/O-C2").then().statusCode(201);

        given().queryParam("carrier", "AIRCO").post("/orders/O-C2/shipment").then().statusCode(409);

        assertEquals(1, events("O-C2"));
    }

    @Test
    void aCancelledOrderCannotBeShippedAndIsNotCounted() {
        given().post("/orders/O-C3").then().statusCode(201);
        given().post("/orders/O-C3/payment").then().statusCode(201);
        given().post("/orders/O-C3/cancellation").then().statusCode(201);

        given().queryParam("carrier", "AIRCO").post("/orders/O-C3/shipment").then().statusCode(409);

        assertEquals(3, events("O-C3"));
    }

    @Test
    void anOrderPlacedTwiceIsRefused() {
        given().post("/orders/O-C4").then().statusCode(201);
        given().post("/orders/O-C4").then().statusCode(409);

        assertEquals(1, events("O-C4"));
    }
}
