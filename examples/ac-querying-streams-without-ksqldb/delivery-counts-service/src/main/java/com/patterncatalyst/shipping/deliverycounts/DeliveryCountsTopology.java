package com.patterncatalyst.shipping.deliverycounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.time.Duration;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.WindowStore;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Deliveries per carrier per tumbling window of one minute of event time, kept in a window store that the REST
 * resource queries. The grace period is the allowed lateness: how far behind the newest event time a record may
 * arrive and still be counted.
 */
@ApplicationScoped
public class DeliveryCountsTopology {

    public static final String STORE = "deliveries-by-carrier";
    static final Duration WINDOW = Duration.ofMinutes(1);
    // The store keeps a window for the retention only. Without an explicit value it keeps window size plus grace,
    // and a closed window disappears from the queries seconds after it closes.
    static final Duration RETENTION = Duration.ofHours(1);

    private static final Logger LOG = Logger.getLogger(DeliveryCountsTopology.class);

    @Inject
    ObjectMapper mapper;

    // Demo code: KS_GRACE_SECONDS=0 gives Kafka Streams a smaller lateness than the other two engines.
    @ConfigProperty(name = "counts.grace")
    Duration grace;

    @Produces
    public Topology topology() {
        LOG.infof("delivery counts: window=%s grace=%s", WINDOW, grace);
        return build(mapper, grace, RETENTION);
    }

    static Topology build(ObjectMapper mapper, Duration grace, Duration retention) {
        var delivered = new ObjectMapperSerde<>(ShipmentDelivered.class, mapper);
        var builder = new StreamsBuilder();
        builder.stream(Topics.SHIPPING_DELIVERED,
                        Consumed.with(Serdes.String(), delivered).withTimestampExtractor(new DeliveredTimestampExtractor()))
                .groupBy((orderId, event) -> event.carrier().name(), Grouped.with("by-carrier", Serdes.String(), delivered))
                .windowedBy(TimeWindows.ofSizeAndGrace(WINDOW, grace))
                .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as(STORE).withRetention(retention));
        return builder.build();
    }
}
