package com.patterncatalyst.shipping.cdc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;

import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/** The route from Debezium envelopes to InventoryItem documents, against Kafka from Dev Services. */
@QuarkusTest
class DocumentRouteTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    private List<String> awaitDocuments(String sku, int count) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                .until(() -> Changes.documents(bootstrapServers, sku).size() >= count);
        return Changes.documents(bootstrapServers, sku);
    }

    @Test
    void aSnapshotReadBecomesADocumentWithTheDomainNames() throws Exception {
        Changes.send(bootstrapServers, "SKU-T1", Changes.envelope("r", "SKU-T1", null, 5));
        assertEquals(List.of("{\"sku\":\"SKU-T1\",\"quantityOnHand\":5}"), awaitDocuments("SKU-T1", 1));
    }

    @Test
    void anUpdateReplacesTheDocumentAndTheChangesOfOneRowKeepTheirOrder() throws Exception {
        Changes.send(bootstrapServers, "SKU-T2", Changes.envelope("c", "SKU-T2", null, 20));
        Changes.send(bootstrapServers, "SKU-T2", Changes.envelope("u", "SKU-T2", 20, 21));
        Changes.send(bootstrapServers, "SKU-T2", Changes.envelope("u", "SKU-T2", 21, 22));
        Changes.send(bootstrapServers, "SKU-T2", Changes.envelope("u", "SKU-T2", 22, 23));
        assertEquals(List.of(20, 21, 22, 23).stream().map(q -> "{\"sku\":\"SKU-T2\",\"quantityOnHand\":" + q + "}").toList(),
                awaitDocuments("SKU-T2", 4));
    }

    @Test
    void aDeleteBecomesExactlyOneTombstone() throws Exception {
        Changes.send(bootstrapServers, "SKU-T3", Changes.envelope("c", "SKU-T3", null, 30));
        // Under REPLICA IDENTITY DEFAULT the old row carries only the key.
        Changes.send(bootstrapServers, "SKU-T3", Changes.envelope("d", "SKU-T3", null, null)
                .replace("\"qty_on_hand\":null", "\"qty_on_hand\":0"));
        // Debezium's own tombstone for the same key follows the delete event.
        Changes.send(bootstrapServers, "SKU-T3", null);
        awaitDocuments("SKU-T3", 2);
        Thread.sleep(3000);
        assertEquals(List.of("{\"sku\":\"SKU-T3\",\"quantityOnHand\":30}", "null"), Changes.documents(bootstrapServers, "SKU-T3"));
    }

    @Test
    void aRowTheDomainRejectsIsDroppedAndTheNextChangeStillFlows() throws Exception {
        Changes.send(bootstrapServers, "SKU-T4", Changes.envelope("c", "SKU-T4", null, -1));
        Changes.send(bootstrapServers, "SKU-T5", Changes.envelope("c", "SKU-T5", null, 7));
        assertEquals(List.of("{\"sku\":\"SKU-T5\",\"quantityOnHand\":7}"), awaitDocuments("SKU-T5", 1));
        assertEquals(List.of(), Changes.documents(bootstrapServers, "SKU-T4"));
    }
}
