package com.patterncatalyst.shipping.orchestration.order;

import static io.quarkiverse.flow.dsl.FlowDSL.caseDefault;
import static io.quarkiverse.flow.dsl.FlowDSL.caseOf;
import static io.quarkiverse.flow.dsl.FlowDSL.cases;
import static io.quarkiverse.flow.dsl.FlowDSL.listen;
import static io.quarkiverse.flow.dsl.FlowDSL.switchCase;
import static io.quarkiverse.flow.dsl.FlowDSL.tasks;
import static io.quarkiverse.flow.dsl.FlowDSL.consumed;
import static io.quarkiverse.flow.dsl.FlowDSL.to;
import static io.quarkiverse.flow.dsl.FlowDSL.tryCatch;
import static io.quarkiverse.flow.dsl.FlowDSL.withInstanceId;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.quarkiverse.flow.dsl.FuncListenSpec;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The orchestrator written as a Quarkus Flow workflow. The sequence is the list of tasks, the state of an instance
 * is in PostgreSQL, and the compensation is tasks: a declined payment releases the stock and cancels the order, a
 * payment that does not answer within the listen timeout refunds, releases and cancels.
 */
@ApplicationScoped
public class OrderSagaWorkflow extends Flow {

    static final String TIMEOUT = "https://serverlessworkflow.io/spec/1.0.0/errors/timeout";

    @Inject
    FlowCommands commands;

    @Inject
    OrderService orders;

    @Inject
    SagaOutcomes outcomes;

    @Inject
    ObjectMapper json;

    @ConfigProperty(name = "saga.timeout")
    String timeout;

    /** Demo code: "off" leaves the refund and the release out of the compensation, so only the order is cancelled. */
    @ConfigProperty(name = "flow.compensation")
    String compensation;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("order-saga")
                .tasks(
                        withInstanceId("reserve", (String instance, String orderId) -> {
                            orders.bindInstance(orderId, instance);
                            commands.reserve(instance, orders.reserveCommand(orderId));
                            return orderId;
                        }, String.class),
                        listen("awaitReserved", replyTo("InventoryReserved", "InventoryInsufficient")),
                        switchCase("afterReserved", cases(
                                caseOf((JsonNode reply) -> "INSUFFICIENT".equals(outcome(reply)), JsonNode.class).then("cancel"),
                                caseDefault("takePayment"))),
                        withInstanceId("takePayment", (String instance, JsonNode reply) -> {
                            String orderId = orders.findByInstance(instance).id;
                            commands.take(instance, orders.takeCommand(orderId));
                            return reply;
                        }, JsonNode.class),
                        tryCatch("payment", t -> t
                                .tryCatch(tasks(listen("awaitPayment", replyTo("PaymentProcessed", "PaymentFailed")).timeout(timeout)))
                                .catchType(TIMEOUT, tasks(
                                        withInstanceId("refund", (String instance, JsonNode error) -> {
                                            if (compensating()) {
                                                commands.refund(instance, new RefundPayment(orders.findByInstance(instance).id));
                                            }
                                            return json.createObjectNode().put("outcome", "TIMEOUT");
                                        }, JsonNode.class)))),
                        switchCase("afterPayment", cases(
                                caseOf((JsonNode reply) -> "PAID".equals(outcome(reply)), JsonNode.class).then("markPaid"),
                                caseDefault("release"))),
                        withInstanceId("markPaid", (String instance, JsonNode reply) -> {
                            outcomes.completed("flow", orders.findByInstance(instance).id);
                            return reply;
                        }, JsonNode.class).then(FlowDirectiveEnum.END),
                        withInstanceId("release", (String instance, JsonNode reply) -> {
                            if (compensating()) {
                                commands.release(instance, new ReleaseStock(orders.findByInstance(instance).id));
                            }
                            return reply;
                        }, JsonNode.class),
                        withInstanceId("cancel", (String instance, JsonNode reply) -> {
                            outcomes.compensated("flow", orders.findByInstance(instance).id);
                            return reply;
                        }, JsonNode.class))
                .build();
    }

    /**
     * Waits for one of these events, but only one whose {@code flowinstanceid} attribute is this instance's id. A
     * listen on the event type alone wakes every waiting instance, so one order's reply would complete another's.
     */
    private static FuncListenSpec replyTo(String... types) {
        return to().any(java.util.Arrays.stream(types)
                .map(type -> consumed(type).extensionByInstanceId("flowinstanceid"))
                .toArray(io.quarkiverse.flow.dsl.FuncEventFilterSpec[]::new));
    }

    private boolean compensating() {
        return !"off".equals(compensation);
    }

    /** The outcome field of the payload a listen task returns: an array of the events it received. */
    static String outcome(JsonNode node) {
        JsonNode event = node.isArray() ? node.get(0) : node;
        return event == null || event.get("outcome") == null ? "" : event.get("outcome").asText();
    }
}
