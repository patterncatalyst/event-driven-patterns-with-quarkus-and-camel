package com.patterncatalyst.shipping.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationWriterTest {

    static final String GROUP = "test-default";

    /** Tests start no collector, so the global registry has no backend; a simple registry records the meters. */
    static final SimpleMeterRegistry SIMPLE = new SimpleMeterRegistry();

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    NotificationWriter writer;

    @Inject
    MeterRegistry registry;

    @Inject
    CamelContext camel;

    @BeforeEach
    void attachRegistry() {
        CompositeMeterRegistry composite = (CompositeMeterRegistry) registry;
        if (!composite.getRegistries().contains(SIMPLE)) {
            composite.add(SIMPLE);
        }
    }

    @Test
    @Order(1)
    void everyRecordIsStoredOnceByTheSingleConsumerAndItsAgeIsMeasured() throws Exception {
        long tenSecondsAgo = System.currentTimeMillis() - 10_000;

        Fixtures.seed(bootstrapServers, 30, tenSecondsAgo);

        Fixtures.awaitExactly(GROUP, 30);
        assertEquals(30, Fixtures.rows(GROUP));
        assertEquals(30, Fixtures.rowsWithAttempts(GROUP, 1));
        assertEquals(1, Fixtures.distinctConsumers(GROUP));

        Timer age = SIMPLE.find("edp.notification.event.age").timer();
        assertEquals(30, age.count());
        assertTrue(age.mean(TimeUnit.SECONDS) >= 10, "event age is measured from the record timestamp");
    }

    @Test
    @Order(2)
    void aRecordDeliveredAgainUpdatesItsRowAndCountsTheAttempt() {
        var request = new NotificationRequest("O-1", "C-1", "Test");

        QuarkusTransaction.requiringNew().run(() -> writer.write(request, "edp.redelivered", 0, 7L, System.currentTimeMillis()));
        assertEquals(1, NotificationRow.count("topic = ?1 and attempts = 1", "edp.redelivered"));

        QuarkusTransaction.requiringNew().run(() -> writer.write(request, "edp.redelivered", 0, 7L, System.currentTimeMillis()));
        QuarkusTransaction.requiringNew().run(() -> writer.write(request, "edp.redelivered", 0, 7L, System.currentTimeMillis()));

        assertEquals(1, NotificationRow.count("topic", "edp.redelivered"));
        assertEquals(1, NotificationRow.count("topic = ?1 and attempts = 3", "edp.redelivered"));
    }

    @Test
    @Order(3)
    void clientMetricsAreBoundForTheConsumerAndRemovedWhenTheRouteStops() throws Exception {
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> consumerMeters() > 0);
        assertEquals(1, SIMPLE.find("kafka.consumer.fetch.manager.fetch.total").functionCounters().size());

        camel.getRouteController().stopRoute("notification-consume");
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> consumerMeters() == 0);

        camel.getRouteController().startRoute("notification-consume");
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> consumerMeters() > 0);
    }

    private long consumerMeters() {
        return SIMPLE.getMeters().stream().filter(m -> m.getId().getName().startsWith("kafka.consumer")).count();
    }
}
