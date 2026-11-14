---
title: "Orchestrated sagas: Camel Saga and Quarkus Flow"
order: 23
part: processing-and-coordinating-streams
permalink: /docs/23-orchestrated-sagas/
description: "An orchestrator holds the sequence of an order saga in one place. The Camel Saga EIP hands the saga to an LRA coordinator that calls each participant's registered compensation; a Quarkus Flow workflow keeps the sequence and its state itself and compensates with tasks."
duration: "75 minutes"
---

In the choreographed saga nobody holds the whole sequence. Each service knows its own next step, so changing the order of the steps, adding one, or finding out why an order is stuck touches three codebases. An orchestrator holds the sequence in one place and tells each participant what to do.

`order-service` hosts two orchestrators for the same flow, order then stock then payment, over the same `inventory-service` and `payment-service`. One is the Camel Saga EIP with an LRA coordinator. The other is a Quarkus Flow workflow. A real service runs one of them. Having both in the same run shows where they differ: who holds the state, who triggers the compensation, and what happens to a participant that answers late.

## Pattern: Orchestrated saga

### The problem

A flow that spans services cannot be one transaction. A Kafka transaction covers Kafka only (chapter 12), and each service owns its database. The flow is a sequence of local transactions, and when a later step fails the earlier ones are undone by compensating actions: a new step that reverses the effect, never a rollback (Richardson). Chapter 22 coordinated that with events and no coordinator.

That works while the flow is short and one team owns it. Three things push toward a central owner. The sequence changes, and every change is a coordinated release. Someone has to answer where order O-2314 is, and the answer is spread over three tables. And a time limit is a property of the whole saga, not of any one service. Hohpe and Woolf call the central unit a Process Manager: it keeps the state of the sequence and chooses the next step from the intermediate results.

The orchestrator has to answer two questions. What does it do when a step fails? And what does it do when a participant never answers?

### The solution

**An orchestrated saga keeps the sequence of local transactions, and the compensation of each, in one orchestrator that sends a command to each participant, waits for its reply, and on a failure or an expired time limit tells the participants that already acted to undo their work.**

Participants only execute commands. Commands go to `edp.inventory.commands` and `edp.payments.commands`, keyed by order id on one partition, so a refund sent after a payment is read after it. Replies go to the catalog topics, `edp.inventory.reserved`, `edp.inventory.insufficient`, `edp.payments.processed` and `edp.payments.failed`, with the outcome in the payload. Every saga gets an explicit time limit, because Camel's Saga documentation states there is no default timeout on Sagas. The coordinator cancels the saga when it passes.

### How Quarkus and Camel model it

The participants are the same for both orchestrators, and each Camel route does one job: reading the commands, running a command, the compensation, the reply. ReserveStock and TakePayment run inside `saga().propagation(SUPPORTS)` and register their undo:

```java
from("direct:reserve-stock").routeId("reserve-stock")
        .unmarshal().json(JsonLibrary.Jackson, ReserveStock.class)
        .saga().propagation(SagaPropagation.SUPPORTS)
            .compensation("direct:release-compensation")
            .option("orderId", simple("${header.orderId}"))
        .process(e -> reservations.reserve(e.getMessage().getBody(ReserveStock.class))
                .ifPresentOrElse(e.getMessage()::setBody, () -> e.getMessage().setBody(null)))
        .filter(body().isNotNull())
        .to("direct:reply");
```

With a saga header on the command, the participant joins that saga and the coordinator will call `direct:release-compensation` if the saga is cancelled. Without one it runs plain, which is how the workflow uses the same route. Release and refund are idempotent, and a release that arrives before its reserve leaves a mark so the late reserve takes nothing: the two commute.

Replies of both forms share four topics, so each orchestrator skips the other's. The Camel route skips records without the `Long-Running-Action` header. Every reply is a CloudEvent, and a reply to a workflow command names the instance in `flowinstanceid`. The workflow's wait takes only events that name its instance, so it passes over the Camel form's replies quietly.

## Camel Saga with an LRA coordinator

The start route opens the saga at the coordinator and sends ReserveStock. The saga stays open until the replies route ends it.

```java
from("direct:camel-saga").routeId("camel-saga-start")
        .saga().propagation(SagaPropagation.REQUIRES_NEW).completionMode(SagaCompletionMode.MANUAL)
            .timeout(Duration.parse(timeout))
            .compensation("direct:camel-cancel-order")
            .completion("direct:camel-complete-order")
            .option("orderId", simple("${header.orderId}"))
        .process(e -> e.getMessage().setBody(orders.reserveCommand(e.getMessage().getHeader("orderId", String.class))))
        .setHeader("type", constant("ReserveStock"))
        .marshal().json(JsonLibrary.Jackson)
        .to("kafka:" + Topics.INVENTORY_COMMANDS);
```

`MANUAL` matters. With the default `AUTO` the saga completes when the route that started it ends, which is right after the command is sent and before any participant has answered. A later PaymentFailed would then find nothing to compensate.

The saga id is the `Long-Running-Action` header. Camel writes it onto the Kafka record, the participant reads it as bytes and joins, and the replies carry it back. The replies route moves the saga with its own endpoints:

```java
from("kafka:" + String.join(",", Topics.INVENTORY_RESERVED, Topics.INVENTORY_INSUFFICIENT,
        Topics.PAYMENTS_PROCESSED, Topics.PAYMENTS_FAILED) + "?groupId=order-service-camel")
        .filter(header("Long-Running-Action").isNotNull())
        .choice()
            .when(header(KafkaConstants.TOPIC).isEqualTo(Topics.INVENTORY_RESERVED)).to("direct:camel-take-payment")
            .when(header(KafkaConstants.TOPIC).isEqualTo(Topics.PAYMENTS_PROCESSED)).to("saga:complete")
            .otherwise().to("saga:compensate")
        .end();
```

The step that sends TakePayment joins with `MANDATORY`, so it fails if no saga is in progress. When the saga completes or is cancelled, the coordinator calls the registered routes over HTTP: the participants' compensations, and the orchestrator's own `camel-cancel-order` or `camel-complete-order`. The calls are signed with a shared callback secret, and a call with a bad signature is refused with HTTP 403. Each callback is a conditional update, so a repeated call changes the order once.

{% include excalidraw.html file="23-camel-saga-lra" alt="order-service sends commands over Kafka with the saga header, inventory-service and payment-service join the saga at the LRA coordinator, replies come back over Kafka, and the coordinator calls the callback routes over HTTP" caption="Figure 23.1: The coordinator owns the saga and participants register their own undo" %}

The saga is the coordinator's state. The orchestrator holds nothing but the routes, and the participants hold nothing but the header.

## The same saga as a Quarkus Flow workflow

A workflow is the sequence as a list of tasks. State and compensation are explicit.

```java
FlowWorkflowBuilder.workflow("order-saga").tasks(
        withInstanceId("reserve", (String instance, String orderId) -> {
            orders.bindInstance(orderId, instance);
            commands.reserve(instance, orders.reserveCommand(orderId));
            return orderId;
        }, String.class),
        tryCatch("reservation", t -> t
                .tryCatch(tasks(listen("awaitReserved", replyTo("InventoryReserved", "InventoryInsufficient")).timeout(timeout)))
                .catchType(TIMEOUT, tasks(withInstanceId("releaseUnanswered", ...)))),
        switchCase("afterReserved", cases(
                caseOf((JsonNode reply) -> "INSUFFICIENT".equals(outcome(reply)) || "TIMEOUT".equals(outcome(reply)), JsonNode.class).then("cancel"),
                caseDefault("takePayment"))),
        withInstanceId("takePayment", ...),
        tryCatch("payment", t -> t
                .tryCatch(tasks(listen("awaitPayment", replyTo("PaymentProcessed", "PaymentFailed")).timeout(timeout)))
                .catchType(TIMEOUT, tasks(withInstanceId("refund", ...)))),
        switchCase("afterPayment", cases(
                caseOf((JsonNode reply) -> "PAID".equals(outcome(reply)), JsonNode.class).then("markPaid"),
                caseDefault("release"))),
        withInstanceId("markPaid", ...).then(FlowDirectiveEnum.END),
        withInstanceId("release", ...),
        withInstanceId("cancel", ...))
```

A command is a record sent through a SmallRye emitter with the workflow instance id in a header. The participant copies it onto its reply as the CloudEvent attribute `flowinstanceid`. A `listen` on the event type alone wakes every waiting instance, so one order's reply completes another order's wait. `replyTo` adds the filter that makes the reply wake the instance it names:

```java
private static FuncListenSpec replyTo(String... types) {
    return to().any(Arrays.stream(types)
            .map(type -> consumed(type).extensionByInstanceId("flowinstanceid"))
            .toArray(FuncEventFilterSpec[]::new));
}
```

The output of a `listen` is the event data without its type, so the workflow branches on the `outcome` field the participants put in the payload.

The compensation is the tasks after a failure. A declined payment releases the stock and cancels the order. Every `listen` has a time limit, and passing it raises the standard timeout error, which a `tryCatch` catches. The payment wait refunds, releases and cancels; the reservation wait releases, in case the reply was lost, and cancels. The Serverless Workflow DSL has no compensation construct, only these tasks.

{% include excalidraw.html file="23-flow-workflow" alt="The workflow tasks reserve, awaitReserved, afterReserved, takePayment, awaitPayment and afterPayment in a row, with paid going to markPaid and a decline or a timeout going through refund where needed, release and cancel, and the instance tables in PostgreSQL below" caption="Figure 23.2: The sequence is the task list and the compensation is tasks you write" %}

The state is in PostgreSQL. A waiting instance has one row in the instance table and one per executed task, written as each task completes or pauses, and a completed instance is removed. That is what the workflow adds that Camel's saga does not need: tables and their migration, in the same database as the orders.

The engine can also lose a reply. It hands an event only to an instance that is already waiting, so a reply that arrives between the task that sends the command and the `listen` task is dropped. Unthrottled, one order of the first three stalled in 9 of 29 runs; with the container held to 0.3 CPU, 18 and 25 orders of 100 did. The reservation limit does not end such an order: it stayed PLACED minutes past the 20 s limit, because the instance never reaches a wait that has a timer.

It does not make the wait durable. When order-service was recreated with an instance waiting for payment and payment-service then took the payment, the row stayed and the order was still PLACED 30 s later, with the restore's periodic rescan set to 5 s as well as without it. Restore is on by default. Nothing may recreate order-service while workflows wait, or something else must find and cancel the stuck orders.

## When the payment service does not answer

With payment-service stopped, an order is placed under each orchestrator. Each has a time limit of 20 s.

The coordinator cancels the Camel saga when the limit passes, never before it. It calls every compensation registered by then: inventory-service releases the stock and order-service cancels the order. payment-service never joined, so it has nothing to compensate. When it starts it reads TakePayment, tries to join, and the coordinator refuses because the saga is cancelled. The payment is not taken. That refusal holds only for a participant that never joined. A payment already running when the limit passes is compensated first and then completes, so the money is taken for a cancelled order. The participant needs a guard of its own, like the semantic lock of chapter 22.

The workflow's listen task gives up when its limit passes, and then the tail runs: RefundPayment, ReleaseStock, cancel. The commands queue behind TakePayment on the same partition. When payment-service starts it takes the payment and then refunds it, in that order. The money moved and came back, and the payment row ends REFUNDED. That is the price of compensating by command rather than by refusing to join.

Both end with the order CANCELLED, the stock released and one OrderCancelled published. The order row and an outbox row change in one transaction and Debezium publishes the row (chapter 8), so a crash cannot leave a cancelled order without its event. The paths differ in what the late participant did, not in the terminal state.

## Three ways compared

{% include excalidraw.html file="23-three-ways" alt="Three columns, choreography, Camel Saga with an LRA coordinator and Quarkus Flow, each with where the sequence lives, where the state lives, what triggers the compensation and what owns the time limit" caption="Figure 23.3: Three places to look when an order is stuck" %}

| | Choreography (chapter 22) | Camel Saga with LRA | Quarkus Flow |
|---|---|---|---|
| Who knows the sequence | Each service its next step | The routes in order-service | The task list of one workflow |
| Where the saga state lives | Order, inventory and payment tables | The coordinator, and the header on each record | The workflow instance in PostgreSQL |
| What triggers compensation | Consumers of failure events | Coordinator callbacks to joined participants | Tasks after a failed or timed-out listen |
| Who owns the time limit | A sweeper in order-service | The saga timeout | The listen timeout |
| A late participant | A cancelled order is refused (semantic lock) | The join is refused only if it never joined; a payment already running completes | Taken, then refunded |
| Added infrastructure | Outbox tables and Debezium Connect | A coordinator container | Workflow tables |
| Coordinator requests per order | None | 6 | None |
| 100 orders in one burst, last one PAID | Not measured | 3.8 s | 2.0 s |

Choreograph a short flow owned by one team. Orchestrate when the sequence changes often or has to be audited. With LRA the participants register their own undo and the coordinator calls it, which suits participants owned by other teams. With a workflow the undo is a step you write, which keeps everything in one file and one database.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/23-orchestrated-sagas
mvn -B verify
./verify.sh
```

`verify.sh` runs both orchestrators on the same orders over three services and a coordinator, stops payment-service, and recreates order-service with sagas in flight. Watch both forms reach the same states and part at the recreate; it ends with `33/33 checks passed`.

Try one change and rerun:

- `PAYMENT_JOIN=off ./verify.sh` (demo code): the payment service stays out of the saga, so the late payment for the cancelled order is not refused.
- `FLOW_COMPENSATION=off ./verify.sh` (demo code): the workflow cancels the order and leaves the payment and the stock alone.

## When not to use it

A short flow owned by one team does not need a coordinator or a workflow engine. Choreography has fewer moving parts to run.

The coordinator is state you have to keep. Restarting it with one saga in flight lost that saga in 6 of 7 restarts, and the order stayed PLACED with its stock reserved past the time limit; the seventh compensated it.

A workflow engine is as durable as its restore. If the flow waits for days, find out what a waiting instance does after a restart before the first one is in production.

Neither form gives isolation. While the saga runs the order is PLACED and the stock is reserved, and a reader sees both. A countermeasure, such as marking the order pending, belongs in the data, not in the orchestrator.

A step that cannot be undone has no compensation. Shipping is the usual one: it comes after the pivot step, and the saga should end before it.

> **🤖 Explore with your agent**
> "In examples/23-orchestrated-sagas, stop payment-service, place one order with POST /orders/camel and one with POST /orders/flow, and follow each until it is CANCELLED: read CamelOrderSagaRoute and the Long-Running-Action header on edp.payments.commands for the first, OrderSagaWorkflow and the flowinstanceid header for the second. Then start payment-service and compare what each form did with the late TakePayment."
> Check: the Camel order is cancelled by the coordinator and no payment row exists, the Flow order is cancelled by the listen timeout with RefundPayment and ReleaseStock on the topics and a REFUNDED payment row, and both orders end CANCELLED with one OrderCancelled each.

## References

- Hector Garcia-Molina and Kenneth Salem, [Sagas](https://doi.org/10.1145/38713.38742), Proceedings of the 1987 ACM SIGMOD International Conference on Management of Data, pages 249 to 259
- Gregor Hohpe and Bobby Woolf, [*Enterprise Integration Patterns*](https://www.enterpriseintegrationpatterns.com/patterns/messaging/ProcessManager.html), Addison-Wesley, 2003: Process Manager
- Chris Richardson, [Pattern: Saga](https://microservices.io/patterns/data/saga.html) and *Microservices Patterns*, Manning, 2018, chapter 4
- Apache Camel, [Saga EIP](https://camel.apache.org/components/4.22.x/eips/saga-eip.html) and [LRA component](https://camel.apache.org/components/4.22.x/others/lra.html); Apache Camel Quarkus, [LRA extension](https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/lra.html)
- Eclipse MicroProfile, [Long Running Actions 2.0](https://download.eclipse.org/microprofile/microprofile-lra-2.0/microprofile-lra-spec-2.0.html) and the Narayana [LRA coordinator image](https://quay.io/repository/jbosstm/lra-coordinator)
- Quarkiverse, [Quarkus Flow](https://docs.quarkiverse.io/quarkus-flow/dev/index.html): [messaging](https://docs.quarkiverse.io/quarkus-flow/dev/messaging.html), [persistence](https://docs.quarkiverse.io/quarkus-flow/dev/persistence.html) and the [DSL cheatsheet](https://docs.quarkiverse.io/quarkus-flow/dev/dsl-cheatsheet.html)
- CNCF Serverless Workflow, [DSL reference](https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md) (timeouts, `try` and `listen`)
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapter 7: Long-Running Processes
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 10: Building Workflows with Microservices
- *Enterprise Integration Patterns with Camel*, [chapter 10: Composed Routing](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/10-composed-routing/) and [Appendix G: Quarkus Flow](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/25-appendix-quarkus-flow/)
- *Cloud-Native Design Patterns*, [Appendix D: Saga State and Compensation](https://patterncatalyst.github.io/cloud-native-design-patterns/docs/17-appendix-d-sagas/)
- Chapter 12: Exactly-once with Kafka transactions, for the limit of a Kafka transaction; chapter 22 for the choreographed form
