package com.patterncatalyst.shipping.tracing.inventory;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** The default form: the consumer span is a child of the context in the record. */
@QuarkusTest
class ConsumerTraceTest {

    static final AttributeKey<String> ORDER_ID = AttributeKey.stringKey("order.id");

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Inject
    InMemorySpanExporter spans;

    private SpanData reserveSpan(String orderId) {
        await().atMost(30, SECONDS).until(() -> find(orderId) != null);
        return find(orderId);
    }

    private SpanData find(String orderId) {
        List<SpanData> all = spans.getFinishedSpanItems();
        return all.stream().filter(s -> s.getName().equals("reserve stock") && orderId.equals(s.getAttributes().get(ORDER_ID)))
                .findFirst().orElse(null);
    }

    private SpanData consumerSpanOf(SpanData reserve) {
        return spans.getFinishedSpanItems().stream()
                .filter(s -> s.getSpanId().equals(reserve.getParentSpanId())).findFirst().orElseThrow();
    }

    @Test
    void theConsumerSpanIsAChildOfTheContextInTheRecord() {
        String id = "O-" + UUID.randomUUID();
        String trace = Records.hex(16);
        String parent = Records.hex(8);
        Records.stock("SKU-T-" + id, 5);
        Records.placed(bootstrap, id, "SKU-T-" + id, 1, "00-" + trace + "-" + parent + "-01");
        SpanData consumer = consumerSpanOf(reserveSpan(id));
        assertEquals(SpanKind.CONSUMER, consumer.getKind());
        assertEquals(trace, consumer.getTraceId());
        assertEquals(parent, consumer.getParentSpanId());
    }

    @Test
    void theReservationSpanCarriesTheOrderIdInTheSameTrace() {
        String id = "O-" + UUID.randomUUID();
        String trace = Records.hex(16);
        Records.stock("SKU-T-" + id, 5);
        Records.placed(bootstrap, id, "SKU-T-" + id, 1, "00-" + trace + "-" + Records.hex(8) + "-01");
        SpanData reserve = reserveSpan(id);
        assertEquals(trace, reserve.getTraceId());
        assertEquals(consumerSpanOf(reserve).getSpanId(), reserve.getParentSpanId());
    }

    @Test
    void aRecordWithoutContextStartsANewTrace() {
        String id = "O-" + UUID.randomUUID();
        Records.stock("SKU-T-" + id, 5);
        Records.placed(bootstrap, id, "SKU-T-" + id, 1, null);
        SpanData consumer = consumerSpanOf(reserveSpan(id));
        assertFalse(consumer.getParentSpanContext().isValid());
        assertEquals(1, spans.getFinishedSpanItems().stream().filter(s -> s.getTraceId().equals(consumer.getTraceId()))
                .filter(s -> s.getKind() == SpanKind.CONSUMER).count());
    }

    @Test
    void theReservationTakesTheStock() {
        String id = "O-" + UUID.randomUUID();
        Records.stock("SKU-T-" + id, 5);
        Records.placed(bootstrap, id, "SKU-T-" + id, 2, null);
        reserveSpan(id);
        assertEquals(3, Records.onHand("SKU-T-" + id));
    }

    @Test
    void aRecordWithoutContextDoesNotJoinAnotherTrace() {
        String first = "O-" + UUID.randomUUID();
        String second = "O-" + UUID.randomUUID();
        Records.stock("SKU-T-" + first, 5);
        Records.stock("SKU-T-" + second, 5);
        Records.placed(bootstrap, first, "SKU-T-" + first, 1, null);
        Records.placed(bootstrap, second, "SKU-T-" + second, 1, null);
        assertNotEquals(reserveSpan(first).getTraceId(), reserveSpan(second).getTraceId());
    }
}
