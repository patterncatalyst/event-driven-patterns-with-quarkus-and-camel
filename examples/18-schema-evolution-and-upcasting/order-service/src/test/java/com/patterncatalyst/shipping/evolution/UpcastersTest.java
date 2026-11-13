package com.patterncatalyst.shipping.evolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.evolution.avro.OrderPlaced;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** The chain on its own, without a database. */
class UpcastersTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final Upcasters upcasters = new Upcasters(meters, "on");

    private static JsonNode placed(String extra) throws Exception {
        return JSON.readTree("{\"order\":{\"id\":\"O-1\",\"customerId\":\"C-1\",\"itemSku\":\"SKU-1\",\"quantity\":1,"
                + "\"amount\":25.00}" + extra + "}");
    }

    @Test
    void versionOneGainsTheDefaultAndTheStoredNodeIsLeftAlone() throws Exception {
        JsonNode stored = placed("");
        JsonNode current = upcasters.toCurrent("OrderPlaced", stored, 1);
        assertEquals("STANDARD", current.get("shippingPriority").asText());
        assertEquals(placed(""), stored);
        assertEquals(1.0, meters.get("order.upcasts").tag("from", "1").counter().count());
    }

    @Test
    void versionTwoPassesThroughUnchanged() throws Exception {
        JsonNode stored = placed(",\"shippingPriority\":\"EXPRESS\"");
        assertEquals(stored, upcasters.toCurrent("OrderPlaced", stored, 2));
        assertEquals(0, meters.getMeters().size());
    }

    @Test
    void anEventWithoutAStepIsReturnedAsItIs() throws Exception {
        JsonNode stored = JSON.readTree("{\"reason\":\"customer\"}");
        assertEquals(stored, upcasters.toCurrent("OrderCancelled", stored, 1));
    }

    @Test
    void anUnknownVersionFailsLoudly() throws Exception {
        assertThrows(IllegalStateException.class, () -> upcasters.toCurrent("OrderPlaced", placed(""), 3));
        assertThrows(IllegalStateException.class, () -> upcasters.toCurrent("OrderPlaced", placed(""), 0));
    }

    @Test
    void theUpcastDefaultIsTheDefaultOfTheAvroSchema() {
        Object avroDefault = OrderPlaced.getClassSchema().getField("shippingPriority").defaultVal();
        assertEquals(Upcasters.DEFAULT_PRIORITY, avroDefault);
    }

    @Test
    void theCurrentRecordRefusesAMissingPriority() throws Exception {
        var order = JSON.convertValue(placed("").get("order"), com.patterncatalyst.shipping.Order.class);
        assertThrows(NullPointerException.class, () -> new OrderEvent.OrderPlaced(order, null));
    }

    @Test
    void withTheChainOffTheStoredNodeIsReturnedUntouched() throws Exception {
        JsonNode stored = placed("");
        assertSame(stored, new Upcasters(meters, "off").toCurrent("OrderPlaced", stored, 1));
    }
}
