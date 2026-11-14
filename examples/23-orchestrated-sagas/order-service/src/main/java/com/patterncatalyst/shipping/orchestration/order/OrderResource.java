package com.patterncatalyst.shipping.orchestration.order;

import org.apache.camel.ProducerTemplate;
import org.jboss.resteasy.reactive.RestResponse;

import com.patterncatalyst.shipping.Order;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/** One endpoint starts an order's saga under the Camel orchestrator, one under the workflow, one reports the order. */
@Path("/orders")
public class OrderResource {

    public record Status(String id, String status, String orchestrator) {
    }

    @Inject
    OrderService orders;

    @Inject
    OrderSagaWorkflow workflow;

    @Inject
    ProducerTemplate camel;

    @POST
    @Path("camel")
    public RestResponse<Void> startCamel(Order order) {
        orders.place(order, "camel-lra");
        camel.sendBodyAndHeader("direct:camel-saga", null, "orderId", order.id());
        return RestResponse.accepted();
    }

    @POST
    @Path("flow")
    public RestResponse<Void> startFlow(Order order) {
        orders.place(order, "flow");
        workflow.instance(order.id()).start();
        return RestResponse.accepted();
    }

    @GET
    @Path("{id}")
    public RestResponse<Status> get(@PathParam("id") String id) {
        OrderEntity order = orders.find(id);
        return order == null ? RestResponse.notFound() : RestResponse.ok(new Status(order.id, order.status, order.orchestrator));
    }
}
