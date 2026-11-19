package com.patterncatalyst.shipping.analysis;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.Shipment;
import com.patterncatalyst.shipping.ShipmentStatus;
import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.common.DevServicesContext;
import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Exercises the real model. A {@code @QuarkusIntegrationTest} starts the packaged jar: test sources, including
 * {@link MockAnalyst}, are not on that jar's classpath, so DeliveryExceptionAnalyst there is the real
 * LangChain4j-backed AI service talking to whatever quarkus.langchain4j.ollama.base-url names (the compose network
 * address in the live stack, localhost:11434 on a developer machine). Needs a reachable Ollama with the pinned
 * model already pulled (scripts/ollama-model.sh pull, then a running ollama container or the compose stack already
 * up). Tagged live and excluded from the default mvn -B verify run (see the aggregator pom); runs only with
 * mvn -B verify -Plive.
 */
@QuarkusIntegrationTest
@Tag("live")
class StabilityHarnessIT implements DevServicesContext.ContextAware {

    private String bootstrapServers;

    @Override
    public void setIntegrationTestContext(DevServicesContext context) {
        bootstrapServers = context.devServicesProperties().get("kafka.bootstrap.servers");
    }

    @Test
    void theCorpusNoteIsAnalysedWithTheExpectedReasonByTheRealModel() throws Exception {
        assertNotNull(bootstrapServers, "Kafka Dev Services did not report a bootstrap address to the live IT");
        String id = "E-LIVE-" + Instant.now().toEpochMilli();
        var shipment = new Shipment("S-" + id, "O-" + id, Carrier.FASTLINE, "TRK-" + id, ShipmentStatus.SCHEDULED);
        var reported = new DeliveryExceptionReported(id, shipment, 1,
                "Box crushed at depot, contents leaking through the corner.", Instant.now().minusSeconds(3600));
        LiveKafka.send(bootstrapServers, Topics.SHIPPING_DELIVERY_EXCEPTIONS, "O-" + id, reported);

        await().atMost(Duration.ofSeconds(120)).untilAsserted(() -> {
            JsonNode analysis = LiveKafka.find(bootstrapServers, Topics.SHIPPING_EXCEPTION_ANALYSES, id);
            assertNotNull(analysis, "no analysis yet for " + id);
            assertEquals("DAMAGED", analysis.get("reason").asText());
        });
    }
}
