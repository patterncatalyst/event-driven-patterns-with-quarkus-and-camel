package com.patterncatalyst.shipping.projections;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/** One Jackson deserializer per topic; the channel configuration names the class. */
public final class Deserializers {

    private Deserializers() {
    }

    public static class Placed extends ObjectMapperDeserializer<Events.OrderPlaced> {
        public Placed() {
            super(Events.OrderPlaced.class);
        }
    }

    public static class Cancelled extends ObjectMapperDeserializer<Events.OrderCancelled> {
        public Cancelled() {
            super(Events.OrderCancelled.class);
        }
    }

    public static class Paid extends ObjectMapperDeserializer<Events.PaymentProcessed> {
        public Paid() {
            super(Events.PaymentProcessed.class);
        }
    }

    public static class Scheduled extends ObjectMapperDeserializer<Events.ShipmentScheduled> {
        public Scheduled() {
            super(Events.ShipmentScheduled.class);
        }
    }

    public static class InTransit extends ObjectMapperDeserializer<Events.ShipmentInTransit> {
        public InTransit() {
            super(Events.ShipmentInTransit.class);
        }
    }

    public static class Delivered extends ObjectMapperDeserializer<Events.ShipmentDelivered> {
        public Delivered() {
            super(Events.ShipmentDelivered.class);
        }
    }
}
