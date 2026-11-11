package com.patterncatalyst.shipping.cdc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** The rows the embedded engine hands over, without a database: same documents, same tombstone. */
@QuarkusTest
class EmbeddedRowTest {

    private static final Schema KEY = SchemaBuilder.struct().field("item_sku", Schema.STRING_SCHEMA).build();

    @Inject
    ProducerTemplate template;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    private void send(String operation, String sku, Map<String, Object> row) {
        template.sendBodyAndHeaders("direct:embedded-row", row,
                Map.of("CamelDebeziumOperation", operation, "CamelDebeziumKey", new Struct(KEY).put("item_sku", sku)));
    }

    private List<String> awaitDocuments(String sku, int count) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                .until(() -> Changes.documents(bootstrapServers, sku).size() >= count);
        return Changes.documents(bootstrapServers, sku);
    }

    @Test
    void anUpdatedRowBecomesTheSameDocumentAsOnTheConnectPath() {
        send("u", "SKU-E1", Map.of("item_sku", "SKU-E1", "qty_on_hand", 8, "last_changed", "2026-11-11T10:00:00Z"));
        assertEquals(List.of("{\"sku\":\"SKU-E1\",\"quantityOnHand\":8}"), awaitDocuments("SKU-E1", 1));
    }

    @Test
    void aDeleteHasNoBodyAndBecomesATombstoneKeyedFromTheHeader() {
        send("d", "SKU-E2", null);
        assertEquals(List.of("null"), awaitDocuments("SKU-E2", 1));
    }
}
