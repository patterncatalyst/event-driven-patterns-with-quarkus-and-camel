package com.patterncatalyst.shipping.decisions;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.kafka.KafkaConstants;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Stage one as a route: a reported note is analysed by the model and the analysis is recorded on its own topic. With
 * the model unreachable the call fails, is retried, and then the consumer seeks back: the record waits, and no
 * analysis is invented. {@code analyst.enabled=false} (demo code, set with {@code ANALYST_ENABLED}) leaves the route
 * stopped so the rest of the service runs without a model.
 */
@ApplicationScoped
public class ExceptionAnalysisRoute extends RouteBuilder {

    @Inject
    AnalysisService analysis;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "analyst.redelivery-delay-ms", defaultValue = "5000")
    long redeliveryDelayMs;

    @Override
    public void configure() {
        errorHandler(defaultErrorHandler().maximumRedeliveries(3).redeliveryDelay(redeliveryDelayMs));

        from("kafka:" + Topics.SHIPPING_DELIVERY_EXCEPTIONS
                + "?groupId=shipping-exception-analysis&autoOffsetReset=earliest&breakOnFirstError=true"
                + "&sessionTimeoutMs=10000&heartbeatIntervalMs=3000")
                .routeId("exception-analysis")
                .autoStartup("{{analyst.enabled}}")
                .unmarshal(new JacksonDataFormat(mapper, DeliveryExceptionReported.class))
                .bean(analysis, "analyse")
                .setHeader(KafkaConstants.KEY, simple("${body.orderId}"))
                .marshal(new JacksonDataFormat(mapper, DeliveryExceptionAnalysed.class))
                .to("kafka:" + Topics.SHIPPING_EXCEPTION_ANALYSES);
    }
}
