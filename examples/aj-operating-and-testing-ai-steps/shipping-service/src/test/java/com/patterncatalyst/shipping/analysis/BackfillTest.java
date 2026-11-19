package com.patterncatalyst.shipping.analysis;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.Topics;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/** Backfill calls the model only for an exception whose latest analysis is behind the target prompt version, and
 * rebuilds the disposition snapshot every time, so a same-version run costs nothing but still leaves the snapshot
 * current. The live route is stopped (see {@link NoLiveRouteProfile}): otherwise it would analyse the seeded
 * exception's note the normal way before the backfill call ran, leaving nothing behind the target version. */
@QuarkusTest
@TestProfile(NoLiveRouteProfile.class)
class BackfillTest {

    @Inject
    MockAnalyst analyst;

    @ConfigProperty(name = "analyst.model-digest")
    String modelDigest;

    private static void seedStaleAnalysis(String exceptionId, String orderId, String shipmentId) {
        QuarkusTransaction.requiringNew().run(() -> {
            var row = new AnalysisRecord();
            row.exceptionId = exceptionId;
            row.promptVersion = "v0"; // a stand-in for "whatever version came before the one this prompt is pinned at"
            row.modelDigest = "stale-digest";
            row.orderId = orderId;
            row.shipmentId = shipmentId;
            row.carrier = Carrier.FASTLINE;
            row.attempt = 1;
            row.reason = Reason.UNKNOWN;
            row.evidence = "stale";
            row.model = "stale-model";
            row.occurredAt = Instant.now().minus(1, ChronoUnit.DAYS);
            row.persist();
        });
    }

    @Test
    void backfillReanalysesOnlyWhatIsBehindTheTargetVersionAndRebuildsTheSnapshot() throws Exception {
        analyst.reset();
        analyst.answer(new ExceptionAnalysis(Reason.DAMAGED, "crushed"));
        String id = Samples.id();
        String suffix = id.substring(2);
        String orderId = "O-" + suffix;
        String shipmentId = "S-" + suffix;
        seedStaleAnalysis(id, orderId, shipmentId);
        Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, orderId, Samples.reported(id, 1, "Box crushed at depot"));

        var first = given().queryParam("promptVersion", DeliveryExceptionAnalyst.PROMPT_VERSION).post("/admin/reanalyse")
                .then().statusCode(200).extract();
        assertEquals(1, first.jsonPath().getInt("reanalysed"));
        assertEquals(1, analyst.callCount());

        AnalysisRecord updated = AnalysisRecord.findByKey(id, DeliveryExceptionAnalyst.PROMPT_VERSION, modelDigest);
        assertNotNull(updated, "the backfill should have written a new row at the target version");
        assertEquals(Reason.DAMAGED, updated.reason);

        DispositionSnapshot snapshot = DispositionSnapshot.findById(id);
        assertNotNull(snapshot, "the backfill should have rebuilt the snapshot from the new row");
        assertEquals(Disposition.RESHIP, snapshot.disposition); // DAMAGED on attempt 1

        // a same-version backfill makes 0 calls: the exception is already at the target version
        analyst.reset();
        var second = given().queryParam("promptVersion", DeliveryExceptionAnalyst.PROMPT_VERSION).post("/admin/reanalyse")
                .then().statusCode(200).extract();
        assertEquals(0, second.jsonPath().getInt("reanalysed"));
        assertEquals(0, analyst.callCount());
    }
}
