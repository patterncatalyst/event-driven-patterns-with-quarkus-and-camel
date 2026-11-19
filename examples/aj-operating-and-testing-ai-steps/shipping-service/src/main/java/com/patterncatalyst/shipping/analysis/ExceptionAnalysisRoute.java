package com.patterncatalyst.shipping.analysis;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.kafka.KafkaConstants;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The one route: a reported note is memoized through {@link MemoizedAnalysisService}, demo code crashes once for
 * the exception id under test, and the analysis is recorded on its own topic. The memoized call and the demo crash
 * check run inside one Camel step on purpose: Camel's error handler redelivers the step that threw, not the whole
 * route, so a crash raised from a separate later step would retry only itself and never call {@link
 * MemoizedAnalysisService#analyseOnce} again. With the model unreachable the call fails, is retried, and then the
 * consumer seeks back: the note waits, and no analysis is invented. {@code analyst.enabled=false} (demo code, set
 * with {@code ANALYST_ENABLED}) leaves the route stopped so the rest of the service, including backfill and replay
 * over fixture data, runs without a model.
 */
@ApplicationScoped
public class ExceptionAnalysisRoute extends RouteBuilder {

    @Inject
    MemoizedAnalysisService analysis;

    @Inject
    DemoCrash demoCrash;

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
                .process(exchange -> {
                    var reported = exchange.getMessage().getBody(DeliveryExceptionReported.class);
                    var analysed = analysis.analyseOnce(reported);
                    demoCrash.afterAnalysis(analysed);
                    exchange.getMessage().setBody(analysed);
                })
                .setHeader(KafkaConstants.KEY, simple("${body.orderId}"))
                .marshal(new JacksonDataFormat(mapper, DeliveryExceptionAnalysed.class))
                .to("kafka:" + Topics.SHIPPING_EXCEPTION_ANALYSES);
    }
}
