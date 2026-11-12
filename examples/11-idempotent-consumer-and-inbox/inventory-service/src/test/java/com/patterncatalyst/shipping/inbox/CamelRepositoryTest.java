package com.patterncatalyst.shipping.inbox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** Camel's Idempotent Consumer with the JPA repository, joined to the route's transaction. */
@QuarkusTest
@TestProfile(CamelRepositoryProfile.class)
class CamelRepositoryTest {

    @Inject
    EntityManager em;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    long keys(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> (Long) em.createQuery(
                "select count(m) from MessageProcessed m where m.messageId = :id")
                .setParameter("id", id.toString()).getSingleResult());
    }

    @Test
    void theSameEventIdIsAppliedOnce() throws Exception {
        String sku = Fixtures.newSku();
        UUID duplicated = UUID.randomUUID();
        Fixtures.send(bootstrapServers, duplicated, Fixtures.order("O-7", sku, 3));
        Fixtures.send(bootstrapServers, duplicated, Fixtures.order("O-7", sku, 3));
        Fixtures.send(bootstrapServers, UUID.randomUUID(), Fixtures.order("O-8", sku, 2));
        Fixtures.awaitStock(sku, 95);
        Fixtures.settle();
        assertEquals(95, Fixtures.stock(sku));
        assertEquals(1, keys(duplicated));
    }

    @Test
    void aFailedEffectRollsBackTheKeyWithIt() throws Exception {
        String sku = Fixtures.newSku();
        UUID failing = UUID.randomUUID();
        Fixtures.send(bootstrapServers, failing, Fixtures.order("O-FAIL", sku, 4));
        Fixtures.send(bootstrapServers, UUID.randomUUID(), Fixtures.order("O-9", sku, 1));
        Fixtures.awaitStock(sku, 99);
        Fixtures.settle();
        assertEquals(99, Fixtures.stock(sku));
        assertEquals(0, keys(failing));
    }
}
