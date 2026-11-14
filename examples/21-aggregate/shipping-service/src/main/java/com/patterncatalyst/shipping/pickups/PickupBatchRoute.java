package com.patterncatalyst.shipping.pickups;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.Topics;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The same shipments grouped the Camel way: a batch per carrier in arrival order, closed at {@code pickup.batch-size}
 * shipments or after {@code pickup.timeout} milliseconds without a new one. Open batches live in PostgreSQL.
 */
@ApplicationScoped
public class PickupBatchRoute extends RouteBuilder {

    // The mapper Quarkus configures, which knows java.time; Camel's own default mapper does not.
    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "pickup.batch-size")
    int batchSize;

    @ConfigProperty(name = "pickup.timeout")
    long timeoutMillis;

    @Override
    public void configure() {
        var incoming = new JacksonDataFormat(mapper, ShipmentScheduled.class);
        var outgoing = new JacksonDataFormat(mapper, PickupList.class);

        from("kafka:" + Topics.SHIPPING_SCHEDULED + "?groupId=shipping-pickups-camel&autoOffsetReset=earliest"
                + "&sessionTimeoutMs=10000&heartbeatIntervalMs=3000")
                .routeId("pickup-batches")
                .unmarshal(incoming)
                // A String: the repository drops header values that are not simple types.
                .setHeader("carrier", simple("${body.shipment.carrier}", String.class))
                .setBody(simple("${body.shipment.id}"))
                .aggregate(header("carrier"), PickupBatchRoute::collect)
                    .completionSize(batchSize)
                    .completionTimeout(timeoutMillis)
                    .aggregationRepository("pickupsRepository")
                    .process(PickupBatchRoute::toPickupList)
                    .marshal(outgoing)
                    .to("kafka:" + Topics.SHIPPING_PICKUP_LISTS_CAMEL)
                .end();
    }

    /** The batch body is the list of shipment ids so far; it is stored with the open batch, so it is serializable. */
    static Exchange collect(Exchange batch, Exchange next) {
        ArrayList<String> ids = batch == null ? new ArrayList<>() : batch.getIn().getBody(ArrayList.class);
        ids.add(next.getIn().getBody(String.class));
        Exchange result = batch == null ? next : batch;
        result.getIn().setBody(ids);
        return result;
    }

    static void toPickupList(Exchange exchange) {
        @SuppressWarnings("unchecked")
        List<String> ids = exchange.getIn().getBody(List.class);
        Carrier carrier = Carrier.valueOf(exchange.getIn().getHeader("carrier", String.class));
        String closedBy = exchange.getProperty(Exchange.AGGREGATED_COMPLETED_BY, String.class);
        exchange.getIn().setBody(new PickupList(carrier, null, null, ids, ids.size(), closedBy));
        // The key is the carrier. The header is set to the text: a key option on the endpoint URI would write the
        // expression itself as the key.
        exchange.getIn().setHeader(KafkaConstants.KEY, carrier.name());
    }
}
