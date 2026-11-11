package com.patterncatalyst.shipping.queues;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/** The share group path: accept, release for another attempt, reject for good. */
@QuarkusTest
class ShareDeliveryTest {

    static final String GROUP = "notification-share";
    static final long LOCK_MS = 2000;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @BeforeEach
    void groupSettings() throws Exception {
        Jobs.configureShareGroup(bootstrapServers, GROUP, LOCK_MS);
    }

    @Test
    void anOrdinaryJobIsAcceptedOnItsFirstDelivery() {
        Jobs.awaitShareMembers(bootstrapServers, GROUP, 1);
        Jobs.send(bootstrapServers, "S-1", "C-1");
        Jobs.awaitRows("share", "S-1", 1);
        Jobs.settle(Duration.ofMillis(3 * LOCK_MS));
        assertEquals(List.of("sent"), Jobs.outcomes("share", "S-1"));
        assertEquals(List.of(1), Jobs.deliveryCounts("share", "S-1"));
    }

    @Test
    void aReleasedJobIsDeliveredAgainWithAHigherDeliveryCount() {
        Jobs.awaitShareMembers(bootstrapServers, GROUP, 1);
        Jobs.send(bootstrapServers, "S-2", "C-RETRY-1");
        Jobs.awaitRows("share", "S-2", 2);
        Jobs.settle(Duration.ofMillis(3 * LOCK_MS));
        assertEquals(List.of("failed", "sent"), Jobs.outcomes("share", "S-2"));
        assertEquals(List.of(1, 2), Jobs.deliveryCounts("share", "S-2"));
    }

    @Test
    void aRejectedJobIsNotDeliveredAgainAfterTheLockDuration() {
        Jobs.awaitShareMembers(bootstrapServers, GROUP, 1);
        Jobs.send(bootstrapServers, "S-3", "C-BOUNCE-1");
        Jobs.awaitRows("share", "S-3", 1);
        // Wait out three lock durations; the full wait is spent, not cut short at the first quiet poll.
        Jobs.settle(Duration.ofMillis(3 * LOCK_MS));
        assertEquals(List.of("rejected"), Jobs.outcomes("share", "S-3"));
        assertEquals(List.of(1), Jobs.deliveryCounts("share", "S-3"));
    }
}
