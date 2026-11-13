package com.patterncatalyst.shipping.replay;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/** The timer drives the projection: a command is projected within a few ticks, and the lag gauge follows. */
@QuarkusTest
@TestProfile(ScheduledProjectionTest.EverySecond.class)
class ScheduledProjectionTest extends ProjectionBase {

    public static class EverySecond implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("projection.poll", "1s");
        }
    }

    @Inject
    MeterRegistry meters;

    /** Awaitility polls on its own thread, which has no request context: read inside a transaction. */
    private long projected() {
        return QuarkusTransaction.requiringNew().call(() -> store.rows().stream().mapToLong(ShipmentsPerDay::shipments).sum());
    }

    @Test
    void aCommandReachesTheProjectionOnItsOwn() {
        batch.reset(); // earlier tests may have left a projection that skipped rows on purpose
        given().post("/orders/O-S1").then().statusCode(201);
        given().post("/orders/O-S1/payment").then().statusCode(201);
        given().queryParam("carrier", "AIRCO").post("/orders/O-S1/shipment").then().statusCode(201);
        long shipped = replay.run().rows().stream().mapToLong(ShipmentsPerDay::shipments).sum();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertEquals(shipped, projected());
            assertEquals(0, batch.lagEvents());
        });
        assertNotNull(meters.find("projection.lag.events").gauge(), "the lag gauge is registered; its value reaches Prometheus only with the OpenTelemetry registry, which verify.sh checks");
    }
}
