package com.patterncatalyst.shipping.resequencing;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;

import com.patterncatalyst.shipping.Topics;

/** Applies a released event and publishes it to the order-keyed shipments stream, where one partition holds one order. */
public class ShipmentApplyRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:apply")
                .routeId("shipment-apply")
                .bean("shipmentRecorder", "record")
                .setHeader(KafkaConstants.KEY, header("orderId"))
                .marshal().json()
                .to("kafka:" + Topics.SHIPPING_SHIPMENTS + "?synchronous=true");
    }
}
