package com.patterncatalyst.shipping.metrics;

import static io.restassured.RestAssured.given;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class OrderResourceTest {

    private static Map<String, Object> body(String id) {
        return Map.of("id", id, "customerId", "CU-26", "itemSku", "SKU-26", "quantity", 1, "amount", 10.00);
    }

    @Test
    void anOrderIsAcceptedOnceAndItsRepeatIsRefused() {
        String id = "O-" + UUID.randomUUID();
        given().contentType(ContentType.JSON).body(body(id)).post("/orders").then().statusCode(201);
        given().contentType(ContentType.JSON).body(body(id)).post("/orders").then().statusCode(409);
    }
}
