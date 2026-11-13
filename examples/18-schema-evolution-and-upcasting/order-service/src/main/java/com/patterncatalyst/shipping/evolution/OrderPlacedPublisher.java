package com.patterncatalyst.shipping.evolution;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.Topics;
import com.patterncatalyst.shipping.evolution.avro.OrderPlaced;

import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.debezium.outbox.quarkus.ExportedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

/**
 * Serializes OrderPlaced with the Apicurio Avro serializer inside the command's transaction and writes the bytes to
 * the outbox. The serializer registers the schema, so a registry that refuses it throws here and the append and the
 * outbox row roll back together. The schema id is the first bytes of the payload, not a header: the connector then
 * moves the bytes unchanged.
 */
@ApplicationScoped
public class OrderPlacedPublisher {

    private static final Logger LOG = Logger.getLogger(OrderPlacedPublisher.class);

    @ConfigProperty(name = "evolution.registry-url")
    String registryUrl;

    @Inject
    ReleaseSchema release;

    @Inject
    Event<ExportedEvent<?, ?>> outbox;

    private AvroKafkaSerializer<Object> serializer;

    @PostConstruct
    void init() {
        serializer = new AvroKafkaSerializer<>();
        Map<String, Object> config = new HashMap<>();
        config.put("apicurio.registry.url", registryUrl);
        config.put("apicurio.registry.auto-register", "true");
        config.put("apicurio.registry.headers.enabled", "false");
        serializer.configure(config, false);
    }

    public byte[] serialize(Order order, String priority) {
        Object record = release.isCurrent()
                ? OrderPlaced.newBuilder().setOrderId(order.id()).setCustomerId(order.customerId())
                        .setItemSku(order.itemSku()).setQuantity(order.quantity())
                        .setAmount(order.amount().toPlainString()).setShippingPriority(priority).build()
                : release.wireRecord(order, priority);
        try {
            // The topic name picks the artifact: edp.orders.placed-value.
            return serializer.serialize(Topics.ORDERS_PLACED, record);
        } catch (RuntimeException e) {
            String detail = registryDetail(e);
            LOG.warnf("registry refused OrderPlaced for %s: %s", order.id(), detail);
            throw new SchemaRefused(detail, e);
        }
    }

    public void publish(Order order, String priority) {
        outbox.fire(new OrderExports(order.id(), serialize(order, priority), Instant.now()));
    }

    /** The registry's problem details sit at the bottom of the cause chain, and its exception message is null. */
    private static String registryDetail(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RuleViolationProblemDetails violation) {
                return violation.getDetail();
            }
            if (cause instanceof ProblemDetails problem) {
                return problem.getDetail();
            }
        }
        return String.valueOf(failure.getMessage());
    }
}
