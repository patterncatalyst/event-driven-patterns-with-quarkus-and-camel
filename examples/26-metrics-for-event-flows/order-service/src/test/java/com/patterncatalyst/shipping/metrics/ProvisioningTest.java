package com.patterncatalyst.shipping.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The files Grafana and the collector load: the dashboard parses, every panel reads an exported metric, and the
 * alert rules watch the backlog gauge (or, as demo code, the event age).
 */
class ProvisioningTest {

    private static final Path EXAMPLE = Path.of("..");
    private static final List<String> EXPORTED = List.of("outbox_backlog_bytes", "kafka_consumer_group_lag_ratio",
            "kafka_partition_current_offset_ratio", "order_flow_event_age_milliseconds_bucket",
            "order_flow_record_age_milliseconds_bucket");

    private static JsonNode dashboard() throws Exception {
        return new ObjectMapper().readTree(Files.readString(EXAMPLE.resolve("grafana/dashboards/order-flow.json")));
    }

    private static List<String> expressions() throws Exception {
        var found = new ArrayList<String>();
        for (JsonNode panel : dashboard().get("panels")) {
            for (JsonNode target : panel.get("targets")) {
                found.add(target.get("expr").asText());
            }
        }
        return found;
    }

    @Test
    void theDashboardHasItsUidAndSixPanels() throws Exception {
        assertEquals("edp-order-flow", dashboard().get("uid").asText());
        assertEquals(6, dashboard().get("panels").size());
    }

    @Test
    void everyPanelQueryReadsAnExportedMetricFromPrometheus() throws Exception {
        for (JsonNode panel : dashboard().get("panels")) {
            assertEquals("prometheus", panel.get("datasource").get("uid").asText());
        }
        for (String expr : expressions()) {
            assertTrue(EXPORTED.stream().anyMatch(expr::contains), "no exported metric in " + expr);
        }
    }

    @Test
    void theAlertWatchesTheBacklogGaugeAndTheAgeVariantWatchesTheEventAge() throws Exception {
        String backlog = Files.readString(EXAMPLE.resolve("grafana/alerting/backlog.yaml"));
        String age = Files.readString(EXAMPLE.resolve("grafana/alerting/age.yaml"));
        assertTrue(backlog.contains("expr: max(outbox_backlog_bytes)") && backlog.contains("16384"));
        assertTrue(age.contains("order_flow_event_age_max_milliseconds"));
        for (String file : List.of(backlog, age)) {
            assertTrue(file.contains("uid: edp-outbox-backlog") && file.contains("title: order flow stalled"));
        }
    }

    @Test
    void theCollectorScrapesTheBrokerIntoItsOwnPipeline() throws Exception {
        String config = Files.readString(EXAMPLE.resolve("otelcol/kafka-metrics.yaml"));
        assertTrue(config.contains("kafka_metrics:") && config.contains("metrics/kafka:"));
        assertTrue(config.contains("brokers: [\"kafka:9094\"]"));
    }
}
