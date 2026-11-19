package com.patterncatalyst.shipping.analysis;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/** The negative witness: without the keyed lookup, a duplicate delivery of the same note pays for the model
 * twice instead of once. */
@QuarkusTest
@TestProfile(DedupDisabledProfile.class)
class DedupDisabledTest {

    @Inject
    MockAnalyst analyst;

    @Test
    void withDedupOffADuplicateDeliveryCallsTheModelTwice() throws Exception {
        analyst.reset();
        analyst.answer(new ExceptionAnalysis(Reason.DAMAGED, "crushed"));
        String id = Samples.id();
        var reported = Samples.reported(id, 1, "Box crushed at depot");
        Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + id, reported);
        Kafka.send(Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + id, reported);

        await().atMost(Duration.ofSeconds(60)).until(() -> analyst.callCount() >= 2);
        assertEquals(2, analyst.callCount());
    }
}
