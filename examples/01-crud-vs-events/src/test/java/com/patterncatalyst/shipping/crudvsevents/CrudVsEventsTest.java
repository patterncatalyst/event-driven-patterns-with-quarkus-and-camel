package com.patterncatalyst.shipping.crudvsevents;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
class CrudVsEventsTest {

    @Inject
    DataSource dataSource;

    private static Map<String, Object> order(String id, Object amount) {
        return Map.of("id", id, "customerId", "CU-77", "itemSku", "SKU-42", "quantity", 3, "amount", amount);
    }

    private static String place() {
        String id = "O-" + UUID.randomUUID();
        given().contentType(ContentType.JSON).body(order(id, 59.90)).post("/orders").then().statusCode(201);
        return id;
    }

    private static int change(String id, String to) {
        return given().contentType(ContentType.JSON).body(Map.of("to", to))
                .post("/orders/" + id + "/status").statusCode();
    }

    private static long events(String id) {
        return QuarkusTransaction.requiringNew().call(() -> OrderEvent.count("streamId", id));
    }

    private static long rows(String id) {
        return QuarkusTransaction.requiringNew().call(() -> OrderRow.count("id", id));
    }

    @Test
    void oneRowAndOneEventPerChange() {
        String id = place();
        assertEquals(204, change(id, "PAID"));
        assertEquals(204, change(id, "SHIPPED"));
        assertEquals(1, rows(id));
        assertEquals(3, events(id));
        given().get("/orders/" + id).then().body("status", equalTo("SHIPPED"));
    }

    @Test
    void replayEqualsRow() {
        String id = place();
        change(id, "PAID");
        change(id, "SHIPPED");
        change(id, "DELIVERED");
        String row = given().get("/orders/" + id).asString();
        String replayed = given().get("/orders/" + id + "/replayed").asString();
        assertEquals(row, replayed);
    }

    @Test
    void priorStatusesComeFromEventsOnly() {
        String id = place();
        change(id, "PAID");
        change(id, "SHIPPED");
        given().get("/orders/" + id + "/history").then()
                .body("entries.status", contains("PLACED", "PAID", "SHIPPED"))
                .body("entries.version", contains(1, 2, 3));
        given().get("/orders/" + id).then().body("status", equalTo("SHIPPED"));
    }

    @Test
    void illegalTransitionIsRefusedAndAppendsNothing() {
        String id = place();
        change(id, "PAID");
        assertEquals(409, change(id, "PLACED"));
        assertEquals(409, change(id, "DELIVERED"));
        assertEquals(2, events(id));
        given().get("/orders/" + id).then().body("status", equalTo("PAID"));
    }

    @Test
    void appendOnlyTriggersRefuseUpdateDeleteAndTruncate() throws SQLException {
        String id = place();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            assertThrows(SQLException.class,
                    () -> s.executeUpdate("UPDATE order_event SET type = 'X' WHERE stream_id = '" + id + "'"));
            assertThrows(SQLException.class,
                    () -> s.executeUpdate("DELETE FROM order_event WHERE stream_id = '" + id + "'"));
            assertThrows(SQLException.class, () -> s.execute("TRUNCATE order_event"));
        }
        assertEquals(1, events(id));
    }

    @Test
    void amountIsNormalisedOnceSoRowEventAndReplayAgree() {
        String id = "O-" + UUID.randomUUID();
        given().contentType(ContentType.JSON).body(order(id, 19.999)).post("/orders").then().statusCode(201);
        String row = given().get("/orders/" + id).then()
                .body("order.amount", equalTo(20.00f)).extract().asString();
        assertEquals(row, given().get("/orders/" + id + "/replayed").asString());
    }

    @Test
    void placingTheSameIdTwiceIsRefusedWith409() {
        String id = place();
        given().contentType(ContentType.JSON).body(order(id, 10)).post("/orders").then().statusCode(409);
        assertEquals(1, rows(id));
        assertEquals(1, events(id));
    }

    @Test
    void cancelFromPaidIsAllowedAndRecordsTheReason() {
        String id = place();
        assertEquals(204, change(id, "PAID"));
        given().contentType(ContentType.JSON).body(Map.of("to", "CANCELLED", "reason", "customer request"))
                .post("/orders/" + id + "/status").then().statusCode(204);
        given().get("/orders/" + id).then().body("status", equalTo("CANCELLED"));
        given().get("/orders/" + id + "/replayed").then().body("status", equalTo("CANCELLED"));
        given().get("/orders/" + id + "/history").then()
                .body("entries.status", contains("PLACED", "PAID", "CANCELLED"));
        assertEquals(3, events(id));
    }

    @Test
    void cancelAfterShippedIsRefusedAndAppendsNothing() {
        String id = place();
        change(id, "PAID");
        change(id, "SHIPPED");
        assertEquals(409, change(id, "CANCELLED"));
        assertEquals(3, events(id));
        given().get("/orders/" + id).then().body("status", equalTo("SHIPPED"));
    }

    @Test
    void staleExpectedVersionIsRejectedByTheUniqueConstraint() {
        String id = place();
        given().contentType(ContentType.JSON).body(Map.of("to", "PAID", "expectedVersion", 1))
                .post("/orders/" + id + "/status").then().statusCode(204);
        given().contentType(ContentType.JSON).body(Map.of("to", "SHIPPED", "expectedVersion", 1))
                .post("/orders/" + id + "/status").then().statusCode(409);
        assertEquals(2, events(id));
        given().get("/orders/" + id).then().body("status", equalTo("PAID"));
    }

    @Test
    void loadedEventChangesAreNeverWrittenBack() {
        String id = place();
        QuarkusTransaction.requiringNew().run(() -> {
            OrderEvent event = OrderEvent.find("streamId", id).firstResult();
            event.type = "Altered";
            event.version = 99;
        });
        OrderEvent reloaded = QuarkusTransaction.requiringNew()
                .call(() -> OrderEvent.find("streamId", id).firstResult());
        assertEquals("OrderPlaced", reloaded.type);
        assertEquals(1, reloaded.version);
    }
}
