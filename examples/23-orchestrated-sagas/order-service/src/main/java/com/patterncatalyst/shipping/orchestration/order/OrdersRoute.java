package com.patterncatalyst.shipping.orchestration.order;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import com.patterncatalyst.shipping.Order;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The HTTP surface: one endpoint starts an order's saga under the Camel orchestrator, one under the workflow, and
 * one reports where an order stands. HTTP is a Camel route because the LRA extension brings the classic JAX-RS
 * client, which Quarkus REST cannot sit beside.
 */
@ApplicationScoped
public class OrdersRoute extends RouteBuilder {

    public record Status(String id, String status, String orchestrator) {
    }

    @Inject
    OrderService orders;

    @Inject
    OrderSagaWorkflow workflow;

    @Override
    public void configure() {
        from("platform-http:/orders/camel?httpMethodRestrict=POST").routeId("start-camel-saga")
                .unmarshal().json(JsonLibrary.Jackson, Order.class)
                .process(e -> {
                    Order order = e.getMessage().getBody(Order.class);
                    orders.place(order, "camel-lra");
                    e.getMessage().setHeader("orderId", order.id());
                })
                .to("direct:camel-saga")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(202));

        from("platform-http:/orders/flow?httpMethodRestrict=POST").routeId("start-flow-saga")
                .unmarshal().json(JsonLibrary.Jackson, Order.class)
                .process(e -> {
                    Order order = e.getMessage().getBody(Order.class);
                    orders.place(order, "flow");
                    workflow.instance(order.id()).start();
                })
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(202));

        from("platform-http:/orders/{id}?httpMethodRestrict=GET").routeId("get-order")
                .process(e -> {
                    OrderEntity order = orders.find(e.getMessage().getHeader("id", String.class));
                    if (order == null) {
                        e.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                        e.getMessage().setBody("");
                    } else {
                        e.getMessage().setBody(new Status(order.id, order.status, order.orchestrator));
                    }
                })
                .marshal().json(JsonLibrary.Jackson);
    }
}
