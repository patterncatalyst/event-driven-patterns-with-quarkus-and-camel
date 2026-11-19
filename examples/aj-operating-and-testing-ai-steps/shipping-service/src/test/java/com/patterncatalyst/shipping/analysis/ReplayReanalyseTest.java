package com.patterncatalyst.shipping.analysis;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/** The negative witness: a replay that goes back to the notes calls the model again. */
@QuarkusTest
@TestProfile(ReplayReanalyseProfile.class)
class ReplayReanalyseTest {

    @Inject
    MockAnalyst analyst;

    @Test
    void reanalysingOnReplayCallsTheModelAgain() throws Exception {
        analyst.reset();
        analyst.answer(new ExceptionAnalysis(Reason.LOST, "never seen again"));
        String id = Samples.id();
        Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + id, Samples.reported(id, 1, "Never seen again"));
        await().atMost(Duration.ofSeconds(60)).until(() -> Kafka.find(Topics.SHIPPING_EXCEPTION_ANALYSES, id) != null);
        analyst.reset();

        var response = given().post("/demo/replay").then().statusCode(200).extract();
        assertEquals("reanalyse", response.jsonPath().getString("mode"));
        assertTrue(analyst.callCount() >= 1, "reanalyse mode should have called the model at least once");
    }
}
