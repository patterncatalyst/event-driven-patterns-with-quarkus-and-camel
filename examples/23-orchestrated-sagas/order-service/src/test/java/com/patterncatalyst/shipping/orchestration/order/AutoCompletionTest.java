package com.patterncatalyst.shipping.orchestration.order;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** With AUTO the saga is complete the moment the route that started it ends: the bad outcome MANUAL prevents. */
@QuarkusTest
class AutoCompletionTest {

    @Inject
    ProducerTemplate camel;

    @Test
    void autoCompletionCompletesBeforeAnyReply() {
        int before = AutoCompletionRoute.COMPLETED.get();
        camel.sendBody("direct:auto-saga", "O-auto");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(before + 1, AutoCompletionRoute.COMPLETED.get()));
        assertEquals(0, AutoCompletionRoute.COMPENSATED.get());
    }
}
