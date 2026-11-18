package com.patterncatalyst.shipping.batch.runner;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteConfigurationBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;

import jakarta.enterprise.context.ApplicationScoped;

/** One answer for every refusal: the status of the BatchException and a JSON body, or 400 for a body that is not JSON. */
@ApplicationScoped
public class RestErrorsRoute extends RouteConfigurationBuilder {

    @Override
    public void configuration() {
        routeConfiguration("rest-errors")
                .onException(BatchException.class).handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, simple("${exception.status}"))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setBody(simple("{\"error\":\"${exception.message}\"}"))
                .end()
                .onException(JsonProcessingException.class).handled(true)
                .log(LoggingLevel.WARN, "refused a body that is not a run request: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setBody(constant("{\"error\":\"the body is not a valid run request\"}"))
                .end();
    }
}
