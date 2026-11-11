package com.patterncatalyst.shipping.publishing;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class FlashSaleTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    MeterRegistry registry;

    private long endOffsets() throws Exception {
        try (Admin admin = Admin.create(Map.<String, Object>of("bootstrap.servers", bootstrapServers))) {
            Map<TopicPartition, OffsetSpec> latest = IntStream.range(0, 3)
                    .mapToObj(p -> new TopicPartition(Topics.ORDERS_PLACED, p))
                    .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
            return admin.listOffsets(latest).all().get().values().stream().mapToLong(i -> i.offset()).sum();
        }
    }

    private double counter(String name, String clientId) {
        FunctionCounter c = registry.find(name).tag("client.id", clientId).functionCounter();
        return c == null ? -1 : c.count();
    }

    private double recordsPerRequest(String profile, int orders, int concurrency) {
        String client = "publisher-" + profile;
        double sendBefore = counter("kafka.producer.record.send.total", client);
        double requestBefore = counter("kafka.producer.request.total", client);
        var result = given().queryParam("profile", profile).queryParam("orders", orders)
                .queryParam("concurrency", concurrency)
                .post("/flash-sale").then().statusCode(200).extract().jsonPath();
        assertEquals(orders, result.getInt("orders"));
        assertEquals(0, result.getInt("failed"));
        double sent = counter("kafka.producer.record.send.total", client) - Math.max(sendBefore, 0);
        double requests = counter("kafka.producer.request.total", client) - Math.max(requestBefore, 0);
        assertEquals(orders, sent, "records counted by the producer's own metric");
        return sent / requests;
    }

    @Test
    void everyProfilePublishesEveryOrderOnce() throws Exception {
        long before = endOffsets();
        for (String profile : new String[] { "unbatched", "default", "tuned" }) {
            given().queryParam("profile", profile).queryParam("orders", 300).queryParam("concurrency", 50)
                    .post("/flash-sale").then().statusCode(200);
        }
        assertEquals(before + 900, endOffsets());
    }

    @Test
    void tunedBatchesMoreRecordsPerRequestThanUnbatched() {
        double unbatched = recordsPerRequest("unbatched", 2000, 100);
        double tuned = recordsPerRequest("tuned", 2000, 100);
        assertTrue(unbatched <= 3, "unbatched sends one record per batch: " + unbatched);
        assertTrue(tuned >= 3 * unbatched, "tuned " + tuned + " against unbatched " + unbatched);
    }

    @Test
    void oneCallerAtATimeNeverFillsABatch() {
        double single = recordsPerRequest("tuned", 100, 1);
        assertTrue(single <= 1.5, "one synchronous caller: " + single);
    }

    @Test
    void eachProducerIsMeteredUnderItsOwnClientId() {
        for (String profile : new String[] { "unbatched", "default", "tuned" }) {
            given().queryParam("profile", profile).queryParam("orders", 10).queryParam("concurrency", 2)
                    .post("/flash-sale").then().statusCode(200);
        }
        for (String metric : new String[] { "kafka.producer.record.send.total", "kafka.producer.request.total",
                "kafka.producer.batch.size.avg", "kafka.producer.records.per.request.avg" }) {
            var sequences = registry.find(metric).meters().stream()
                    .filter(m -> m.getId().getTag("client.id") != null && m.getId().getTag("client.id").startsWith("publisher-"))
                    .collect(Collectors.toMap(m -> m.getId().getTag("client.id"), m -> m.getId().getTag("client_seq")));
            assertEquals(3, sequences.size(), metric + " per client id");
            assertEquals(3, sequences.values().stream().distinct().count(), metric + " client_seq values are unique");
        }
    }

    @Test
    void anUnknownProfileIsRejected() {
        given().queryParam("profile", "nope").queryParam("orders", 1).queryParam("concurrency", 1)
                .post("/flash-sale").then().statusCode(400);
    }

    @Test
    void latencyIsRecordedPerProfile() {
        given().queryParam("profile", "default").queryParam("orders", 20).queryParam("concurrency", 5)
                .post("/flash-sale").then().statusCode(200);
        assertTrue(registry.find("edp.publish.latency").tag("profile", "default").timer().count() >= 20);
        assertTrue(Duration.ofNanos((long) registry.find("edp.publish.latency").tag("profile", "default").timer()
                .max(TimeUnit.NANOSECONDS)).toNanos() > 0);
    }
}
