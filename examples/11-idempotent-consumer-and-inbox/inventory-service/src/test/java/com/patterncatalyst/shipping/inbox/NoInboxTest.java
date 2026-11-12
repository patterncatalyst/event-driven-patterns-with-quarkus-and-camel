package com.patterncatalyst.shipping.inbox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/** The comparison: without the inbox a duplicate record is applied twice. */
@QuarkusTest
@TestProfile(NoInboxProfile.class)
class NoInboxTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    void aDuplicateRecordIsAppliedTwice() throws Exception {
        String sku = Fixtures.newSku();
        UUID duplicated = UUID.randomUUID();
        Fixtures.send(bootstrapServers, duplicated, Fixtures.order("O-6", sku, 3));
        Fixtures.send(bootstrapServers, duplicated, Fixtures.order("O-6", sku, 3));
        Fixtures.awaitStock(sku, 94);
        Fixtures.settle();
        assertEquals(94, Fixtures.stock(sku));
        assertEquals(0, Fixtures.inboxRows(duplicated));
    }
}
