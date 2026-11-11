package com.patterncatalyst.shipping.queues;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/** The consumer-group path on the same jobs: a transient failure is retried in place, a bounce is logged once. */
@QuarkusTest
@TestProfile(GroupModeProfile.class)
class GroupDeliveryTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    void anOrdinaryJobIsSentOnce() {
        Jobs.send(bootstrapServers, "G-1", "C-1");
        Jobs.awaitRows("group", "G-1", 1);
        assertEquals(List.of("sent"), Jobs.outcomes("group", "G-1"));
    }

    @Test
    void aTransientFailureIsRetriedAndSentOnTheSecondAttempt() {
        Jobs.send(bootstrapServers, "G-2", "C-RETRY-1");
        Jobs.awaitRows("group", "G-2", 2);
        assertEquals(List.of("failed", "sent"), Jobs.outcomes("group", "G-2"));
        assertEquals(List.of(1, 2), Jobs.deliveryCounts("group", "G-2"));
    }

    @Test
    void aBounceIsRejectedOnceAndNotRetried() {
        Jobs.send(bootstrapServers, "G-3", "C-BOUNCE-1");
        Jobs.awaitRows("group", "G-3", 1);
        assertEquals(List.of("rejected"), Jobs.outcomes("group", "G-3"));
    }
}
