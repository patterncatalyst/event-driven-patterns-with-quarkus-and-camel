package com.patterncatalyst.shipping.deliverycounts;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.jboss.resteasy.reactive.RestQuery;

/** Interactive query: ask the window store of this instance for the windows that start in a range (ISO instants). */
@Path("/counts")
@Produces(MediaType.APPLICATION_JSON)
public class DeliveryCountsResource {

    @Inject
    KafkaStreams streams;

    @GET
    public List<WindowCount> counts(@RestQuery String carrier, @RestQuery String from, @RestQuery String to) {
        var store = streams.store(StoreQueryParameters.fromNameAndType(DeliveryCountsTopology.STORE,
                QueryableStoreTypes.<String, Long>windowStore()));
        var answers = new ArrayList<WindowCount>();
        try (var windows = store.fetch(carrier, Instant.parse(from), Instant.parse(to))) {
            while (windows.hasNext()) {
                var window = windows.next();
                answers.add(new WindowCount(carrier, Instant.ofEpochMilli(window.key), window.value));
            }
        }
        return answers;
    }
}
