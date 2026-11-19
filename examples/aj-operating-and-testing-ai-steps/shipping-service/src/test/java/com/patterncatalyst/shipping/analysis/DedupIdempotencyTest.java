package com.patterncatalyst.shipping.analysis;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * The exactly-once effect: a committed {@link AnalysisRecord} is the idempotency guard, with no inbox table. A
 * fresh note calls the model once; a duplicated delivery of the same note, and a crash-forced redelivery of the
 * same note, each find the committed row and reuse it instead of calling the model again.
 */
@QuarkusTest
@TestProfile(CrashAfterAnalysisProfile.class)
class DedupIdempotencyTest {

    @Inject
    MockAnalyst analyst;

    private static boolean isReused(JsonNode analysis) {
        JsonNode node = analysis.get("derivedFromPromptVersion");
        return node != null && !node.isNull();
    }

    private static JsonNode awaitAnalysis(String exceptionId) {
        await().atMost(Duration.ofSeconds(60)).until(() -> Kafka.find(Topics.SHIPPING_EXCEPTION_ANALYSES, exceptionId) != null);
        return Kafka.find(Topics.SHIPPING_EXCEPTION_ANALYSES, exceptionId);
    }

    @Test
    void aFreshAnalysisCallsTheModelOnceAndIsNotMarkedReused() throws Exception {
        analyst.reset();
        analyst.answer(new ExceptionAnalysis(Reason.DAMAGED, "crushed"));
        String id = Samples.id();
        Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + id, Samples.reported(id, 1, "Box crushed at depot"));

        JsonNode analysis = awaitAnalysis(id);
        assertEquals("DAMAGED", analysis.get("reason").asText());
        assertFalse(isReused(analysis));
        assertEquals(1, analyst.callCount());
    }

    @Test
    void aDuplicateDeliveryOfTheSameNoteReusesTheCommittedRowAndCallsTheModelOnce() throws Exception {
        analyst.reset();
        analyst.answer(new ExceptionAnalysis(Reason.LOST, "never seen again"));
        String id = Samples.id();
        var reported = Samples.reported(id, 1, "Never seen again");
        Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + id, reported);
        awaitAnalysis(id);

        Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + id, reported); // the duplicate: same content, a new offset
        await().atMost(Duration.ofSeconds(60)).until(() -> Kafka.read(Topics.SHIPPING_EXCEPTION_ANALYSES).stream()
                .filter(n -> id.equals(n.get("exceptionId").asText())).count() == 2);

        JsonNode second = Kafka.read(Topics.SHIPPING_EXCEPTION_ANALYSES).stream()
                .filter(n -> id.equals(n.get("exceptionId").asText())).reduce((a, b) -> b).orElseThrow();
        assertEquals("LOST", second.get("reason").asText());
        assertTrue(isReused(second), "the duplicate's record should show it was served from the committed row");
        assertEquals(1, analyst.callCount());
    }

    @Test
    void aCrashAfterTheRowCommitsForcesOneRedeliveryThatReusesTheRowInsteadOfCallingTheModelAgain() throws Exception {
        analyst.reset();
        analyst.answer(new ExceptionAnalysis(Reason.REFUSED, "refused"));
        String id = CrashAfterAnalysisProfile.CRASH_EXCEPTION_ID;
        Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + id, Samples.reported(id, 1, "Customer refused"));

        JsonNode analysis = awaitAnalysis(id);
        assertEquals("REFUSED", analysis.get("reason").asText());
        assertTrue(isReused(analysis), "the published record came from the redelivery, which reused the committed row");
        assertEquals(1, analyst.callCount());
    }
}
