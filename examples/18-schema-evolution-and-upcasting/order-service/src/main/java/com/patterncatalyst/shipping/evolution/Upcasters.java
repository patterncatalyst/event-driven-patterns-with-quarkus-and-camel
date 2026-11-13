package com.patterncatalyst.shipping.evolution;

import java.util.Map;
import java.util.function.UnaryOperator;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * The upcaster chain: one step per schema_version and event type, each a transform from one stored shape to the
 * next. A stored payload is copied, upcast and mapped to the current record; the row is never written.
 */
@ApplicationScoped
public class Upcasters {

    /** The schema_version this release writes. */
    public static final short CURRENT = 2;

    /** Must equal the default of shippingPriority in order-placed.avsc, so both contracts say the same thing. */
    public static final String DEFAULT_PRIORITY = "STANDARD";

    private static final Map<String, Map<Integer, UnaryOperator<ObjectNode>>> CHAIN = Map.of(
            "OrderPlaced", Map.of(1, node -> node.put("shippingPriority", DEFAULT_PRIORITY)));

    private final MeterRegistry meters;
    private final boolean enabled;

    Upcasters(MeterRegistry meters, @ConfigProperty(name = "upcasters") String mode) {
        this.meters = meters;
        this.enabled = !"off".equals(mode); // demo code: UPCASTERS=off is the negative witness
    }

    public JsonNode toCurrent(String type, JsonNode stored, int schemaVersion) {
        if (schemaVersion < 1 || schemaVersion > CURRENT) {
            throw new IllegalStateException("unknown schema_version " + schemaVersion + " for " + type);
        }
        if (!enabled) {
            return stored;
        }
        ObjectNode node = ((ObjectNode) stored).deepCopy();
        for (int from = schemaVersion; from < CURRENT; from++) {
            UnaryOperator<ObjectNode> step = CHAIN.getOrDefault(type, Map.of()).get(from);
            if (step != null) {
                node = step.apply(node);
                meters.counter("order.upcasts", "from", String.valueOf(from)).increment();
            }
        }
        return node;
    }
}
