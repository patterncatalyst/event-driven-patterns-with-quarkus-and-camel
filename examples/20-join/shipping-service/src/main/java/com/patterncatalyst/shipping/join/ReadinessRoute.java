package com.patterncatalyst.shipping.join;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.spi.AggregationRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The Camel form: an Aggregator correlated on the record key. The group completes when both facts are present, or
 * when no new fact has arrived for the completion timeout. The timeout runs on the clock of this service, so the
 * window it gives is a window of arrival time.
 */
@ApplicationScoped
public class ReadinessRoute extends RouteBuilder {

    static final String RESERVED = "reserved";
    static final String PAID = "paid";

    @Inject
    AggregationRepository repository;

    @Inject
    ObjectMapper mapper;

    @Inject
    ReadinessMetrics metrics;

    @ConfigProperty(name = "readiness.timeout-ms")
    long timeoutMs;

    @Override
    public void configure() {
        // Both facts present, whatever order they came in. A count of two would also accept a duplicate reservation.
        Predicate bothKinds = exchange -> {
            Map<?, ?> facts = exchange.getIn().getBody(Map.class);
            return facts.containsKey(RESERVED) && facts.containsKey(PAID);
        };

        from("kafka:" + Topics.INVENTORY_RESERVED + "," + Topics.PAYMENTS_PROCESSED
                + "?groupId=shipping-readiness-camel&autoOffsetReset=earliest&sessionTimeoutMs=10000&heartbeatIntervalMs=3000")
                .routeId("shipping-readiness-camel")
                .choice()
                    .when(header(KafkaConstants.TOPIC).isEqualTo(Topics.INVENTORY_RESERVED))
                        .unmarshal(json(InventoryReserved.class))
                        .process(exchange -> fact(exchange, RESERVED, exchange.getIn().getBody(InventoryReserved.class).occurredAt()))
                    .otherwise()
                        .unmarshal(json(PaymentProcessed.class))
                        .process(exchange -> fact(exchange, PAID, exchange.getIn().getBody(PaymentProcessed.class).occurredAt()))
                .end()
                .aggregate(header("orderId"), new AddFact())
                    .completionPredicate(bothKinds)
                    .completionTimeout(timeoutMs)
                    .aggregationRepository(repository)
                    .process(this::conclude)
                    // A payment that arrives alone completes by timeout with nothing reserved, and says nothing.
                    .filter(header("emit"))
                        .setHeader(KafkaConstants.KEY, header("orderId"))
                        .to("kafka:" + Topics.SHIPPING_ORDER_READINESS_CAMEL)
                .end();
    }

    /** Jackson with the service's own mapper, which reads and writes Instant as ISO-8601 text. */
    private JacksonDataFormat json(Class<?> type) {
        return new JacksonDataFormat(mapper, type);
    }

    /** The group body is a map from the kind of fact to its event time; the order id is the record key. */
    private static void fact(Exchange exchange, String kind, Instant occurredAt) {
        exchange.getIn().setHeader("orderId", exchange.getIn().getHeader(KafkaConstants.KEY, String.class));
        exchange.getIn().setBody(new LinkedHashMap<>(Map.of(kind, occurredAt.toString())));
    }

    private void conclude(Exchange exchange) throws JsonProcessingException {
        Map<?, ?> facts = exchange.getIn().getBody(Map.class);
        String orderId = exchange.getIn().getHeader("orderId", String.class);
        boolean complete = "predicate".equals(exchange.getProperty(Exchange.AGGREGATED_COMPLETED_BY, String.class));
        boolean reserved = facts.containsKey(RESERVED);
        exchange.getIn().setHeader("emit", reserved);
        if (reserved) {
            Instant reservedAt = Instant.parse((String) facts.get(RESERVED));
            Readiness readiness = complete
                    ? Readiness.readyToShip(orderId, reservedAt, Instant.parse((String) facts.get(PAID)))
                    : Readiness.paymentOverdue(orderId, reservedAt);
            metrics.count("camel", readiness);
            exchange.getIn().setBody(mapper.writeValueAsString(readiness));
        }
    }

    /** Merges the new fact into the group. A second fact of the same kind replaces the first. */
    static final class AddFact implements AggregationStrategy {

        @Override
        @SuppressWarnings("unchecked")
        public Exchange aggregate(Exchange group, Exchange fact) {
            if (group == null) {
                return fact;
            }
            group.getIn().getBody(Map.class).putAll(fact.getIn().getBody(Map.class));
            return group;
        }
    }
}
