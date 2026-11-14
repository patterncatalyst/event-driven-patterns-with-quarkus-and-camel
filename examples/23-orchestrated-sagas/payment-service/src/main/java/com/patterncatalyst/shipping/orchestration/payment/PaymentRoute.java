package com.patterncatalyst.shipping.orchestration.payment;

import java.util.UUID;

import org.apache.camel.LoggingLevel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Commands in, saga participation, replies out. TakePayment joins the saga the command carries and registers a
 * refund as its compensation; without the header it runs plain, which is how the workflow form uses it. A
 * TakePayment whose saga the coordinator has already cancelled cannot join, so nothing is taken.
 */
@ApplicationScoped
public class PaymentRoute extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentRoute.class);

    @Inject
    PaymentService payments;

    /** Demo code: "off" runs TakePayment outside any saga, so the coordinator never calls the refund. */
    @ConfigProperty(name = "payment.join")
    String join;

    @Override
    public void configure() {
        // The coordinator answers a join of a cancelled or unknown saga with 404. The record is not retried: the
        // saga it belonged to is over.
        onException(RuntimeCamelException.class)
                .handled(true)
                .log(LoggingLevel.WARN, "TakePayment for ${header.orderId} refused: ${exception.message}");

        from("kafka:" + Topics.PAYMENTS_COMMANDS + "?groupId=payment-service").routeId("payment-commands")
                .choice()
                    .when(header("type").isEqualTo("TakePayment")).to("direct:take-payment")
                    .when(header("type").isEqualTo("RefundPayment")).to("direct:refund-payment")
                    .otherwise().log("ignored command ${header.type}")
                .end();

        from("direct:take-payment").routeId("take-payment")
                .unmarshal().json(JsonLibrary.Jackson, TakePayment.class)
                .process(e -> e.getMessage().setHeader("orderId", e.getMessage().getBody(TakePayment.class).orderId()))
                .saga().propagation("off".equals(join) ? SagaPropagation.NOT_SUPPORTED : SagaPropagation.SUPPORTS)
                    .compensation("direct:refund-compensation")
                    .option("orderId", simple("${header.orderId}"))
                .process(e -> e.getMessage().setBody(payments.take(e.getMessage().getBody(TakePayment.class))))
                .to("direct:reply");

        from("direct:refund-payment").routeId("refund-payment")
                .unmarshal().json(JsonLibrary.Jackson, RefundPayment.class)
                .process(e -> refund(e.getMessage().getBody(RefundPayment.class).orderId()));

        // The coordinator calls this over HTTP with the option set at the join.
        from("direct:refund-compensation").routeId("refund-compensation")
                .log("compensation: refund payment for ${header.orderId}")
                .process(e -> refund(e.getMessage().getHeader("orderId", String.class)));

        from("direct:reply").routeId("payment-reply")
                .process(e -> {
                    Reply reply = e.getMessage().getBody(Reply.class);
                    e.getMessage().setHeader("type", reply.eventType());
                    e.getMessage().setHeader(KafkaConstants.OVERRIDE_TOPIC, reply.topic());
                    String instance = e.getMessage().getHeader("flowinstanceid", String.class);
                    if (instance != null) {
                        e.getMessage().setHeader("ce_specversion", "1.0");
                        e.getMessage().setHeader("ce_id", UUID.randomUUID().toString());
                        e.getMessage().setHeader("ce_source", "/payment-service");
                        e.getMessage().setHeader("ce_type", reply.eventType());
                        e.getMessage().setHeader("ce_flowinstanceid", instance);
                        e.getMessage().setHeader("content-type", "application/json");
                    }
                })
                .setHeader(KafkaConstants.KEY, header("orderId"))
                .marshal().json(JsonLibrary.Jackson)
                // The topic of the endpoint is overridden per record by the OVERRIDE_TOPIC header.
                .to("kafka:" + Topics.PAYMENTS_PROCESSED);
    }

    private void refund(String orderId) {
        if (!payments.refund(orderId)) {
            LOG.info("refund for {} refused: no completed payment", orderId);
        }
    }
}
