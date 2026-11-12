package com.patterncatalyst.shipping.deadletter;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** failure-strategy=fail: the first bad record stops the channel, the application reports it, later records wait. */
@QuarkusTest
@TestProfile(FailStrategyProfile.class)
class FailStrategyTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Inject
    EntityManager em;

    @Test
    void aBadRecordStopsConsumption() throws Exception {
        String bad = "O-" + UUID.randomUUID();
        String good = "O-" + UUID.randomUUID();
        int before = Records.stock(em, "SKU-13");
        Records.send(bootstrap, FailStrategyProfile.TOPIC, bad, "{\"orderId\":\"" + bad + "\",");
        Records.send(bootstrap, FailStrategyProfile.TOPIC, good, Records.valid(good, "SKU-13", 1));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertEquals(503, given().get("/q/health/ready").statusCode()));
        Thread.sleep(3000);
        assertEquals(before, Records.stock(em, "SKU-13"));
    }
}
