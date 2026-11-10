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

    private static String book() {
        String id = "C-" + UUID.randomUUID();
        given().contentType(ContentType.JSON)
                .body(Map.of("id", id, "origin", "NLRTM", "destination", "SGSIN",
                        "weightKg", 1250.5, "carrier", "FASTLINE"))
                .post("/consignments").then().statusCode(201);
        return id;
    }

    private static int change(String id, String to) {
        return given().contentType(ContentType.JSON).body(Map.of("to", to))
                .post("/consignments/" + id + "/status").statusCode();
    }

    private static long events(String id) {
        return QuarkusTransaction.requiringNew().call(() -> ConsignmentEvent.count("streamId", id));
    }

    private static long rows(String id) {
        return QuarkusTransaction.requiringNew().call(() -> ConsignmentRow.count("id", id));
    }

    @Test
    void oneRowAndOneEventPerChange() {
        String id = book();
        assertEquals(204, change(id, "LOADED"));
        assertEquals(204, change(id, "IN_TRANSIT"));
        assertEquals(1, rows(id));
        assertEquals(3, events(id));
        given().get("/consignments/" + id).then().body("status", equalTo("IN_TRANSIT"));
    }

    @Test
    void replayEqualsRow() {
        String id = book();
        change(id, "LOADED");
        change(id, "IN_TRANSIT");
        change(id, "CLEARED");
        String row = given().get("/consignments/" + id).asString();
        String replayed = given().get("/consignments/" + id + "/replayed").asString();
        assertEquals(row, replayed);
    }

    @Test
    void priorStatusesComeFromEventsOnly() {
        String id = book();
        change(id, "LOADED");
        change(id, "IN_TRANSIT");
        given().get("/consignments/" + id + "/history").then()
                .body("entries.status", contains("BOOKED", "LOADED", "IN_TRANSIT"))
                .body("entries.version", contains(1, 2, 3));
        given().get("/consignments/" + id).then().body("status", equalTo("IN_TRANSIT"));
    }

    @Test
    void illegalTransitionIsRefusedAndAppendsNothing() {
        String id = book();
        change(id, "LOADED");
        assertEquals(409, change(id, "BOOKED"));
        assertEquals(409, change(id, "CLEARED"));
        assertEquals(2, events(id));
        given().get("/consignments/" + id).then().body("status", equalTo("LOADED"));
    }

    @Test
    void appendOnlyTriggersRefuseUpdateDeleteAndTruncate() throws SQLException {
        String id = book();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            assertThrows(SQLException.class,
                    () -> s.executeUpdate("UPDATE consignment_event SET type = 'X' WHERE stream_id = '" + id + "'"));
            assertThrows(SQLException.class,
                    () -> s.executeUpdate("DELETE FROM consignment_event WHERE stream_id = '" + id + "'"));
            assertThrows(SQLException.class, () -> s.execute("TRUNCATE consignment_event"));
        }
        assertEquals(1, events(id));
    }

    @Test
    void weightIsNormalisedOnceSoRowEventAndReplayAgree() {
        String id = "C-" + UUID.randomUUID();
        given().contentType(ContentType.JSON)
                .body(Map.of("id", id, "origin", "NLRTM", "destination", "SGSIN",
                        "weightKg", 1.2345, "carrier", "FASTLINE"))
                .post("/consignments").then().statusCode(201);
        String row = given().get("/consignments/" + id).then()
                .body("consignment.weightKg", equalTo(1.235f)).extract().asString();
        assertEquals(row, given().get("/consignments/" + id + "/replayed").asString());
    }

    @Test
    void bookingTheSameIdTwiceIsRefusedWith409() {
        String id = book();
        given().contentType(ContentType.JSON)
                .body(Map.of("id", id, "origin", "NLRTM", "destination", "SGSIN",
                        "weightKg", 10, "carrier", "AIRCO"))
                .post("/consignments").then().statusCode(409);
        assertEquals(1, rows(id));
        assertEquals(1, events(id));
    }

    @Test
    void staleExpectedVersionIsRejectedByTheUniqueConstraint() {
        String id = book();
        given().contentType(ContentType.JSON).body(Map.of("to", "LOADED", "expectedVersion", 1))
                .post("/consignments/" + id + "/status").then().statusCode(204);
        given().contentType(ContentType.JSON).body(Map.of("to", "IN_TRANSIT", "expectedVersion", 1))
                .post("/consignments/" + id + "/status").then().statusCode(409);
        assertEquals(2, events(id));
        given().get("/consignments/" + id).then().body("status", equalTo("LOADED"));
    }

    @Test
    void loadedEventChangesAreNeverWrittenBack() {
        String id = book();
        QuarkusTransaction.requiringNew().run(() -> {
            ConsignmentEvent event = ConsignmentEvent.find("streamId", id).firstResult();
            event.type = "Altered";
            event.version = 99;
        });
        ConsignmentEvent reloaded = QuarkusTransaction.requiringNew()
                .call(() -> ConsignmentEvent.find("streamId", id).firstResult());
        assertEquals("ConsignmentBooked", reloaded.type);
        assertEquals(1, reloaded.version);
    }
}
