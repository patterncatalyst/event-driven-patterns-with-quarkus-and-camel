package com.patterncatalyst.shipping.analysis;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** Replay in its default mode reads only the disposition snapshot built by the backfill endpoint: zero model
 * calls, no matter how many exceptions are on record. */
@QuarkusTest
class ReplayFromSnapshotTest {

    @Inject
    MockAnalyst analyst;

    @Test
    void replayRederivesTheSnapshotDispositionsWithNoModelCall() throws Exception {
        analyst.reset();
        analyst.answer(new ExceptionAnalysis(Reason.RECIPIENT_ABSENT, "nobody home"));
        String id = Samples.id();
        Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + id, Samples.reported(id, 1, "Nobody home"));
        await().atMost(Duration.ofSeconds(60)).until(() -> Kafka.find(Topics.SHIPPING_EXCEPTION_ANALYSES, id) != null);

        // a same-version backfill: no extra model call, only the snapshot upsert the replay below reads
        analyst.reset();
        given().queryParam("promptVersion", DeliveryExceptionAnalyst.PROMPT_VERSION).post("/admin/reanalyse")
                .then().statusCode(200);
        assertEquals(0, analyst.callCount());

        var response = given().post("/demo/replay").then().statusCode(200).extract();
        assertEquals("recorded", response.jsonPath().getString("mode"));
        assertEquals(0, analyst.callCount());

        List<String> dispositions = response.jsonPath()
                .getList("dispositions.findAll { it.exceptionId == '" + id + "' }.disposition", String.class);
        assertEquals(List.of("RESHIP"), dispositions); // RECIPIENT_ABSENT, attempt 1, under the attempt limit
    }
}
