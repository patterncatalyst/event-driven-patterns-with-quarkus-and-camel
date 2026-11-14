package com.patterncatalyst.shipping.decisions;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/** The bad outcome: a replay that goes back to the notes calls the model again. */
@QuarkusTest
@TestProfile(ReanalyseProfile.class)
class ReanalyseReplayTest {

    @InjectMock
    DeliveryExceptionAnalyst analyst;

    @Test
    void reanalysingOnReplayCallsTheModelAgain() throws Exception {
        when(analyst.analyse(anyString())).thenReturn(new ExceptionAnalysis(Reason.LOST, "never seen again"));
        String id = Samples.id();
        Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + id, Samples.reported(id, 1, "Never seen again"));
        await().atMost(Duration.ofSeconds(60)).until(() -> Kafka.find(Topics.SHIPPING_EXCEPTION_DECISIONS, id) != null);
        clearInvocations(analyst);

        assertEquals("reanalyse", given().post("/demo/replay").then().statusCode(200).extract().path("mode"));
        verify(analyst, atLeastOnce()).analyse(anyString());
    }
}
