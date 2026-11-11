package com.patterncatalyst.shipping.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/** consumersCount=3 runs three consumer threads in one group, and each reports its own client metrics. */
@QuarkusTest
@TestProfile(ConcurrencyTest.ThreeConsumers.class)
class ConcurrencyTest {

    public static class ThreeConsumers implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("camel.component.kafka.consumers-count", "3", "notify.group", "test-three");
        }
    }

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    MeterRegistry registry;

    @Test
    void threeConsumersShareTheNineRecordsPartitionsAndEachHasItsOwnMetrics() throws Exception {
        SimpleMeterRegistry simple = new SimpleMeterRegistry();
        ((CompositeMeterRegistry) registry).add(simple);

        Fixtures.seed(bootstrapServers, 90, System.currentTimeMillis());

        Fixtures.awaitExactly("test-three", 90);
        assertEquals(90, Fixtures.rows("test-three"));
        assertEquals(90, Fixtures.rowsWithAttempts("test-three", 1));
        assertEquals(3, Fixtures.distinctConsumers("test-three"));

        var sequences = simple.find("kafka.consumer.fetch.manager.fetch.total").functionCounters().stream()
                .map(c -> c.getId().getTag("client_seq")).collect(Collectors.toSet());
        assertEquals(3, sequences.size(), "each consumer thread registers its own meters");
    }
}
