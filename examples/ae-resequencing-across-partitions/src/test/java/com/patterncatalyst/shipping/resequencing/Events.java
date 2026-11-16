package com.patterncatalyst.shipping.resequencing;

import java.util.List;

import org.apache.camel.ProducerTemplate;

import io.quarkus.narayana.jta.QuarkusTransaction;

/** Test input: shipment events as JSON, sent straight to the intake route. */
final class Events {

    private Events() {
    }

    static String json(String orderId, int sequence, long globalSequence) {
        String type = switch (sequence) {
            case 1 -> "ShipmentScheduled";
            case 2 -> "ShipmentInTransit";
            default -> "ShipmentDelivered";
        };
        return """
                {"orderId":"%s","type":"%s","sequence":%d,"globalSequence":%d,"carrier":"FASTLINE"}"""
                .formatted(orderId, type, sequence, globalSequence);
    }

    static void send(ProducerTemplate template, String orderId, int sequence, long globalSequence) {
        template.sendBody("direct:intake", json(orderId, sequence, globalSequence));
    }

    /** The global sequences of the applied rows whose order id starts with the prefix, in order of application. */
    static List<Long> appliedGlobalSequences(String orderPrefix) {
        return QuarkusTransaction.requiringNew().call(() ->
                AppliedShipment.<AppliedShipment>list("orderId like ?1 order by id", orderPrefix + "%")
                        .stream().map(r -> r.globalSequence).toList());
    }

    /** "order:sequence" of the applied rows whose order id starts with the prefix, in order of application. */
    static List<String> appliedOrderSequences(String orderPrefix) {
        return QuarkusTransaction.requiringNew().call(() ->
                AppliedShipment.<AppliedShipment>list("orderId like ?1 order by id", orderPrefix + "%")
                        .stream().map(r -> r.orderId + ":" + r.sequence).toList());
    }
}
