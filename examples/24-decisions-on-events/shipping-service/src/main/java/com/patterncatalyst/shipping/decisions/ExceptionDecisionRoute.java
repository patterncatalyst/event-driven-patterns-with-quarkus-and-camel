package com.patterncatalyst.shipping.decisions;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.kafka.KafkaConstants;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Stage two as a route: every recorded analysis is decided by the rule unit and the decision is recorded. */
@ApplicationScoped
public class ExceptionDecisionRoute extends RouteBuilder {

    @Inject
    DecisionService decisions;

    @Inject
    ObjectMapper mapper;

    @Override
    public void configure() {
        from("kafka:" + Topics.SHIPPING_EXCEPTION_ANALYSES
                + "?groupId=shipping-exception-decision&autoOffsetReset=earliest&breakOnFirstError=true"
                + "&sessionTimeoutMs=10000&heartbeatIntervalMs=3000")
                .routeId("exception-decision")
                .unmarshal(new JacksonDataFormat(mapper, DeliveryExceptionAnalysed.class))
                .bean(decisions, "decideAndCount")
                .setHeader(KafkaConstants.KEY, simple("${body.orderId}"))
                .marshal(new JacksonDataFormat(mapper, DeliveryExceptionDecided.class))
                .to("kafka:" + Topics.SHIPPING_EXCEPTION_DECISIONS);
    }
}
