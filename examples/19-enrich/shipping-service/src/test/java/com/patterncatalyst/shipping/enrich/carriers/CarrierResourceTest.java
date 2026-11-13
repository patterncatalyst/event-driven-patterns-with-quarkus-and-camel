package com.patterncatalyst.shipping.enrich.carriers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CarrierResourceTest {

    @Test
    @Order(1)
    void seededReferenceHasModeAndTemplate() {
        given().get("/carriers/FASTLINE").then().statusCode(200)
                .body("carrier", equalTo("FASTLINE"))
                .body("mode", equalTo("ROAD"))
                .body("trackingUrlTemplate", equalTo("https://track.fastline.example/{trackingNumber}"));
        given().get("/carriers/AIRCO").then().statusCode(200)
                .body("mode", equalTo("AIR"));
    }

    @Test
    @Order(2)
    void unknownCarrierIsNotFound() {
        given().get("/carriers/NOSUCH").then().statusCode(404);
    }

    @Test
    @Order(3)
    void putChangesTheTemplateAndTheNextGetSeesIt() {
        given().contentType(ContentType.JSON).body("{\"trackingUrlTemplate\":\"https://track.fastline.example/v2/{trackingNumber}\"}")
                .put("/carriers/FASTLINE").then().statusCode(200);
        given().get("/carriers/FASTLINE").then().statusCode(200)
                .body("trackingUrlTemplate", equalTo("https://track.fastline.example/v2/{trackingNumber}"));
    }
}
