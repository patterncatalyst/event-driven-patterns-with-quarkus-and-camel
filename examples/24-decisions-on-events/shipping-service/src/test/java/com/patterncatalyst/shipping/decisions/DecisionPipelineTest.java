package com.patterncatalyst.shipping.decisions;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

/** Both stages through Kafka Dev Services, with the analyst replaced by a mock. */
@QuarkusTest
class DecisionPipelineTest {

    @InjectMock
    DeliveryExceptionAnalyst analyst;

    private JsonNode awaitRecord(String topic, String exceptionId) {
        await().atMost(Duration.ofSeconds(60)).until(() -> Kafka.find(topic, exceptionId) != null);
        return Kafka.find(topic, exceptionId);
    }

    private static String decisionOf(JsonNode record) {
        return record.get("decision").asText();
    }

    @Test
    void aNoteBecomesARecordedAnalysisAndThenARecordedDecision() throws Exception {
        when(analyst.analyse(anyString())).thenReturn(new ExceptionAnalysis(Reason.DAMAGED, "crushed"));
        String id = Samples.id();
        Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + id, Samples.reported(id, 1, "Box crushed at depot"));

        JsonNode analysis = awaitRecord(Topics.SHIPPING_EXCEPTION_ANALYSES, id);
        assertEquals("DAMAGED", analysis.get("reason").asText());
        assertEquals("crushed", analysis.get("evidence").asText());
        assertEquals(DeliveryExceptionAnalyst.PROMPT_VERSION, analysis.get("promptVersion").asText());
        assertEquals("FASTLINE", analysis.get("carrier").asText());

        JsonNode decision = awaitRecord(Topics.SHIPPING_EXCEPTION_DECISIONS, id);
        assertEquals("RESHIP", decisionOf(decision));
        assertEquals("Damaged or lost, first attempt", decision.get("rule").asText());
        assertEquals(DecisionService.RULES_VERSION, decision.get("rulesVersion").asText());
    }

    @Test
    void theSameDamageOnALaterAttemptIsRefunded() throws Exception {
        when(analyst.analyse(anyString())).thenReturn(new ExceptionAnalysis(Reason.DAMAGED, "torn wrapping"));
        String first = Samples.id();
        String second = Samples.id();
        Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + first, Samples.reported(first, 1, "Torn wrapping"));
        Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + second, Samples.reported(second, 2, "Torn wrapping"));
        assertEquals("RESHIP", decisionOf(awaitRecord(Topics.SHIPPING_EXCEPTION_DECISIONS, first)));
        assertEquals("REFUND", decisionOf(awaitRecord(Topics.SHIPPING_EXCEPTION_DECISIONS, second)));
    }

    @Test
    void replayDecidesTheRecordedAnalysesAgainWithoutCallingTheModel() throws Exception {
        when(analyst.analyse(anyString())).thenReturn(new ExceptionAnalysis(Reason.REFUSED, "refused"));
        String id = Samples.id();
        Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + id, Samples.reported(id, 1, "Customer refused"));
        JsonNode recorded = awaitRecord(Topics.SHIPPING_EXCEPTION_DECISIONS, id);
        int decisionsBefore = Kafka.read(Topics.SHIPPING_EXCEPTION_DECISIONS).size();
        int analysesBefore = Kafka.read(Topics.SHIPPING_EXCEPTION_ANALYSES).size();
        clearInvocations(analyst);

        List<?> replayed = given().post("/demo/replay").then().statusCode(200).extract().jsonPath()
                .getList("decisions.findAll { it.exceptionId == '" + id + "' }");
        assertEquals(1, replayed.size());
        JsonNode again = Kafka.MAPPER.valueToTree(replayed.get(0));
        assertEquals(recorded, again);
        verifyNoInteractions(analyst);
        assertEquals(decisionsBefore, Kafka.read(Topics.SHIPPING_EXCEPTION_DECISIONS).size());
        assertEquals(analysesBefore, Kafka.read(Topics.SHIPPING_EXCEPTION_ANALYSES).size());
    }

    @Test
    void replayWithAnotherAttemptLimitChangesADecisionAndStillNeverCallsTheModel() throws Exception {
        when(analyst.analyse(anyString())).thenReturn(new ExceptionAnalysis(Reason.RECIPIENT_ABSENT, "nobody home"));
        String id = Samples.id();
        Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + id, Samples.reported(id, 2, "Nobody home"));
        assertEquals("RESHIP", decisionOf(awaitRecord(Topics.SHIPPING_EXCEPTION_DECISIONS, id)));
        clearInvocations(analyst);

        List<String> decisions = given().queryParam("attemptLimit", 2).post("/demo/replay").then().statusCode(200)
                .extract().jsonPath().getList("decisions.findAll { it.exceptionId == '" + id + "' }.decision");
        assertEquals(List.of("REFUND"), decisions);
        verifyNoInteractions(analyst);
    }

    @Test
    void aModelThatIsDownLeavesTheNoteUncommittedUntilItIsBack() throws Exception {
        when(analyst.analyse(anyString())).thenThrow(new IllegalStateException("connection refused"));
        String id = Samples.id();
        long offset = Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + id, Samples.reported(id, 1, "Box crushed"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> verify(analyst, atLeast(4)).analyse(anyString()));
        assertTrue(Kafka.committed("shipping-exception-analysis", Topics.SHIPPING_DELIVERY_EXCEPTIONS) <= offset);
        assertNull(Kafka.find(Topics.SHIPPING_EXCEPTION_ANALYSES, id));

        doReturn(new ExceptionAnalysis(Reason.DAMAGED, "crushed")).when(analyst).analyse(anyString());
        assertEquals("RESHIP", decisionOf(awaitRecord(Topics.SHIPPING_EXCEPTION_DECISIONS, id)));
    }
}
