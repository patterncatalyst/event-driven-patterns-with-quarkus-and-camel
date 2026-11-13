package com.patterncatalyst.shipping.pickups;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.Topics;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.time.Duration;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.WindowStore;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * One pickup list per carrier per 15-minute slot of event time. A slot stays open for 5 more minutes of stream time
 * for records that arrive out of order, then leaves once, as a final result.
 */
@ApplicationScoped
public class PickupTopology {

    static final Duration SLOT = Duration.ofMinutes(15);
    static final Duration GRACE = Duration.ofMinutes(5);

    @Inject
    ObjectMapper mapper;

    // Demo code: PICKUP_EMIT=updates removes the suppression, so every update to a list leaves.
    @ConfigProperty(name = "pickup.emit", defaultValue = "final")
    String emit;

    // Demo code: PICKUP_CHANGELOG=off keeps the open window only in the local store, so a recreate loses it.
    @ConfigProperty(name = "pickup.changelog", defaultValue = "on")
    String changelog;

    @Produces
    public Topology topology() {
        return build(mapper, "updates".equals(emit), !"off".equals(changelog));
    }

    static Topology build(ObjectMapper mapper, boolean updates, boolean changelog) {
        var scheduled = new ObjectMapperSerde<>(ShipmentScheduled.class, mapper);
        var lists = new ObjectMapperSerde<>(PickupList.class, mapper);

        Materialized<String, PickupList, WindowStore<Bytes, byte[]>> store =
                Materialized.<String, PickupList, WindowStore<Bytes, byte[]>>as("pickup-lists")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(lists);
        if (!changelog) {
            store = store.withLoggingDisabled();
        }

        var builder = new StreamsBuilder();
        var perSlot = builder.stream(Topics.SHIPPING_SCHEDULED,
                        Consumed.with(Serdes.String(), scheduled).withTimestampExtractor(new PayloadTimestampExtractor()))
                .groupBy((orderId, event) -> event.shipment().carrier().name(),
                        Grouped.with("by-carrier", Serdes.String(), scheduled))
                .windowedBy(TimeWindows.ofSizeAndGrace(SLOT, GRACE))
                .aggregate(PickupList::empty, (carrier, event, list) -> list.add(event.shipment().id()), store);

        KStream<Windowed<String>, PickupList> out = updates
                ? perSlot.toStream()
                : perSlot.suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded())).toStream();

        String closedBy = updates ? "update" : "window";
        out.map((slot, list) -> KeyValue.pair(slot.key() + "@" + slot.window().startTime(),
                        new PickupList(Carrier.valueOf(slot.key()), slot.window().startTime(), slot.window().endTime(),
                                list.shipmentIds(), list.count(), closedBy)))
                .to(Topics.SHIPPING_PICKUP_LISTS, Produced.with(Serdes.String(), lists));
        return builder.build();
    }
}
