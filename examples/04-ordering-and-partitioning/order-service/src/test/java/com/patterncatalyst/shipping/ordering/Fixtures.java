package com.patterncatalyst.shipping.ordering;

import java.time.Duration;
import java.util.List;

import org.awaitility.Awaitility;

import com.patterncatalyst.shipping.OrderStatus;

import io.quarkus.narayana.jta.QuarkusTransaction;

/** Test helpers shared by the applier and resequencer tests. */
final class Fixtures {

    private Fixtures() {
    }

    static String json(String orderId, String type, int sequence, long streamSequence) {
        return """
                {"orderId":"%s","type":"%s","sequence":%d,"streamSequence":%d,"carrier":"FASTLINE"}
                """.formatted(orderId, type, sequence, streamSequence).strip();
    }

    static List<String> appliedTypes(String orderId) {
        return QuarkusTransaction.requiringNew().call(() ->
                StatusHistoryRow.<StatusHistoryRow>list("orderId = ?1 order by id", orderId).stream()
                        .map(h -> h.eventType).toList());
    }

    static OrderStatus statusOf(String orderId) {
        return QuarkusTransaction.requiringNew().call(() ->
                ((OrderStatusRow) OrderStatusRow.findById(orderId)).status);
    }

    static void awaitApplied(String orderId, int count) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                .until(() -> appliedTypes(orderId).size() >= count);
    }
}
