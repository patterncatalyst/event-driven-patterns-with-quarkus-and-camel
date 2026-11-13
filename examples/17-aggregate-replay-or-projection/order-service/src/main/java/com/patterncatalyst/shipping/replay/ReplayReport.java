package com.patterncatalyst.shipping.replay;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.patterncatalyst.shipping.OrderStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Answers "how many orders did each carrier ship each day" by rebuilding every Order aggregate from its stream, one
 * stream at a time, and counting the ones that were shipped.
 */
@ApplicationScoped
public class ReplayReport {

    /** The answer, how many events were read for it, and how long it took. */
    public record Result(List<ShipmentsPerDay> rows, long eventsRead, long millis) {
    }

    @Inject
    EventStore store;

    @Transactional
    public Result run() {
        long started = System.nanoTime();
        Map<CarrierDay, Long> counts = new TreeMap<>();
        long[] eventsRead = {0};
        String[] current = {null};
        OrderAggregate[] order = {null};
        try (var events = store.replayStream()) {
            events.forEach(event -> {
                eventsRead[0]++;
                if (!event.streamId().equals(current[0])) {
                    count(order[0], counts);
                    current[0] = event.streamId();
                    order[0] = OrderAggregate.start();
                }
                order[0].apply(event.payload(), event.occurredAt());
            });
        }
        count(order[0], counts);
        return new Result(ShipmentsPerDay.of(counts), eventsRead[0], (System.nanoTime() - started) / 1_000_000);
    }

    private static void count(OrderAggregate order, Map<CarrierDay, Long> counts) {
        if (order != null && (order.status() == OrderStatus.SHIPPED || order.status() == OrderStatus.DELIVERED)) {
            counts.merge(new CarrierDay(order.carrier().name(), order.shippedOn()), 1L, Long::sum);
        }
    }
}
