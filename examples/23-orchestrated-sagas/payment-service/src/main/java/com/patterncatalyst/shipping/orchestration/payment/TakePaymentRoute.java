package com.patterncatalyst.shipping.orchestration.payment;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * TakePayment joins the saga the command carries and registers a refund as its compensation; without the header it
 * runs plain, which is how the workflow form uses it. A TakePayment whose saga the coordinator has already
 * cancelled cannot join, so nothing is taken.
 */
@ApplicationScoped
public class TakePaymentRoute extends RouteBuilder {

    @Inject
    PaymentService payments;

    /** Demo code: "off" runs TakePayment outside any saga, so the coordinator never calls the refund. */
    @ConfigProperty(name = "payment.join")
    String join;

    /** The coordinator's refusal reaches the route wrapped, so the whole cause chain is read. */
    static boolean isRefusedJoin(Exchange exchange) {
        // Camel moves the exception to a property before it asks an onWhen predicate.
        Throwable caught = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        for (Throwable e = caught != null ? caught : exchange.getException(); e != null; e = e.getCause()) {
            if (e.getMessage() != null && e.getMessage().contains("Cannot join LRA")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void configure() {
        // The coordinator answers a join of a cancelled or unknown saga with HTTP 404. The record is not retried: the
        // saga it belonged to is over. Any other failure is not handled here.
        onException(RuntimeCamelException.class)
                .onWhen(TakePaymentRoute::isRefusedJoin)
                .handled(true)
                .log(LoggingLevel.WARN, "TakePayment for ${header.orderId} refused: ${exception.message}");

        RouteDefinition start = from("direct:take-payment").routeId("take-payment")
                .unmarshal().json(JsonLibrary.Jackson, TakePayment.class)
                .process(e -> e.getMessage().setHeader("orderId", e.getMessage().getBody(TakePayment.class).orderId()));
        if ("off".equals(join)) {
            // No saga step at all: the payment is taken outside the saga, and the header stays on the exchange so
            // the reply still carries it.
            start.process(e -> e.getMessage().setBody(payments.take(e.getMessage().getBody(TakePayment.class))))
                    .to("direct:reply");
        } else {
            start.saga().propagation(SagaPropagation.SUPPORTS)
                    .compensation("direct:refund-compensation")
                    .option("orderId", simple("${header.orderId}"))
                    .process(e -> e.getMessage().setBody(payments.take(e.getMessage().getBody(TakePayment.class))))
                    .to("direct:reply");
        }
    }
}
