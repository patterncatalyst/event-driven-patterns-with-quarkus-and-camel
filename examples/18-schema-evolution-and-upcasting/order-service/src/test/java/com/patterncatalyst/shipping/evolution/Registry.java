package com.patterncatalyst.shipping.evolution;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.eclipse.microprofile.config.ConfigProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.evolution.avro.OrderPlaced;

import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;

/** The registry as a test sees it: its REST API for rules and versions, and the Avro serde the services use. */
final class Registry {

    record Reply(int status, String body) {
    }

    static final String ARTIFACT = "edp.orders.placed-value";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private Registry() {
    }

    static String url() {
        return ConfigProvider.getConfig().getValue("evolution.registry-url", String.class);
    }

    static Reply call(String method, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url() + path)).header("Content-Type", "application/json");
        request = body == null ? request.method(method, HttpRequest.BodyPublishers.noBody())
                : request.method(method, HttpRequest.BodyPublishers.ofString(body));
        var reply = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(reply.statusCode(), reply.body());
    }

    /** Sets the global COMPATIBILITY rule: POST the first time, PUT afterwards. */
    static void globalRule(String config) throws Exception {
        String body = JSON.writeValueAsString(Map.of("ruleType", "COMPATIBILITY", "config", config));
        if (call("POST", "/admin/rules", body).status() >= 400) {
            call("PUT", "/admin/rules/COMPATIBILITY", body);
        }
    }

    /** A rule on one artifact overrides the global rule. */
    static Reply artifactRule(String artifact, String config) throws Exception {
        return call("POST", "/groups/default/artifacts/" + artifact + "/rules",
                JSON.writeValueAsString(Map.of("ruleType", "COMPATIBILITY", "config", config)));
    }

    static Reply addVersion(String artifact, Schema schema) throws Exception {
        return call("POST", "/groups/default/artifacts/" + artifact + "/versions", JSON.writeValueAsString(
                Map.of("content", Map.of("content", schema.toString(), "contentType", "application/json"))));
    }

    static int versionCount(String artifact) throws Exception {
        return JSON.readTree(call("GET", "/groups/default/artifacts/" + artifact + "/versions", null).body())
                .get("versions").size();
    }

    static AvroKafkaSerializer<Object> serializer() {
        var serializer = new AvroKafkaSerializer<Object>();
        Map<String, Object> config = new HashMap<>();
        config.put("apicurio.registry.url", url());
        config.put("apicurio.registry.auto-register", "true");
        config.put("apicurio.registry.headers.enabled", "false");
        serializer.configure(config, false);
        return serializer;
    }

    static AvroKafkaDeserializer<Object> deserializer() {
        var deserializer = new AvroKafkaDeserializer<Object>();
        Map<String, Object> config = new HashMap<>();
        config.put("apicurio.registry.url", url());
        config.put("apicurio.registry.use-specific-avro-reader", "true");
        deserializer.configure(config, false);
        return deserializer;
    }

    // --- the three schemas and a record for each
    static Schema v1() {
        return schema("order-placed-v1.avsc");
    }

    static Schema v2() {
        return OrderPlaced.getClassSchema();
    }

    static Schema v3() {
        return schema("order-placed-v3.avsc");
    }

    private static Schema schema(String name) {
        try (var in = Registry.class.getResourceAsStream("/avro/" + name)) {
            return new Schema.Parser().parse(in);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static GenericRecord record(Schema schema, String priority) {
        GenericRecord record = new GenericData.Record(schema);
        record.put("orderId", "O-1");
        record.put("customerId", "C-1");
        record.put("itemSku", "SKU-1");
        record.put("quantity", 1);
        record.put("amount", "25.00");
        if (priority != null) {
            record.put("shippingPriority", priority);
        }
        return record;
    }

    static OrderPlaced v2Record(String priority) {
        return OrderPlaced.newBuilder().setOrderId("O-2").setCustomerId("C-1").setItemSku("SKU-1").setQuantity(1)
                .setAmount("25.00").setShippingPriority(priority).build();
    }
}
