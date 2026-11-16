---
title: "Saga"
order: 22
part: processing-and-coordinating-streams
permalink: /docs/22-saga/
description: "An order touches three databases and no transaction spans them. A saga is a chain of local transactions, each publishing the event that starts the next, and a failure is undone by new events. Order, stock and payment are choreographed with no coordinator, and the saga handles a declined payment, a service that does not answer and a late reply."
duration: "65 minutes"
---

One order touches three databases: the order in `order-service`, the stock in `inventory-service` and the payment in `payment-service`. No transaction spans them, and a Kafka transaction stops at the cluster. If the payment is declined after the stock is reserved, the reservation has to be undone by someone, later, by design.

The three services below carry an order from placed to paid by reacting to each other's events, with no coordinator. Each publishes through the transactional outbox, so a step that commits always announces itself.

{% include excalidraw.html file="22-saga-choreography" alt="order-service, inventory-service and payment-service in a row; orders.placed and inventory.reserved lead forward, payments.processed returns to order-service, and payments.failed, inventory.insufficient and orders.cancelled lead back as the compensation path" caption="Figure 22.1: Each service reacts to the previous event and publishes its own; the dashed arrows are the way back" %}

## Pattern: Saga

### The problem

A business transaction that spans services cannot commit or roll back as one. The outbox gives each service an atomic step: its row and its event commit together. It does not give the order a way to go back. When the payment is declined, the stock is already reserved and committed in another database, and nothing rolls it back.

There is a second cost. While the saga runs, other readers see its intermediate states: stock that is reserved for an order that will be cancelled, an order that is placed and not yet paid. A saga has atomicity by agreement and no isolation.

### The solution

**A saga is a sequence of local transactions, each publishing the event that triggers the next, in which a failure is undone by compensating transactions that are new events, not rollbacks.**

Garcia-Molina and Salem introduced the saga in 1987. Richardson names the two ways to coordinate one: choreography, where each participant reacts to events, and orchestration, where a central unit tells each participant what to do. Hohpe and Woolf's Process Manager is the orchestrating form, a central unit that keeps the state of the sequence and picks the next step. Here nobody does: the flow exists only as the events the services publish.

Steps differ in what can be done about them afterwards. The three kinds, compensatable, pivot and retriable, come from Chris Richardson's *Microservices Patterns* (chapter 4) and the Azure Architecture Center's Saga pattern, which also names the semantic lock and the commutative update used below:

| Step | Action | Compensation | Kind |
|---|---|---|---|
| Reserve stock | `inventory-service` holds the quantity | Release the reservation, idempotent and commutative | Compensatable |
| Take payment | `payment-service` takes the money | A refund, which is a business decision and not an undo | Pivot |
| Schedule shipment | Book the carrier | None; it is retried until it succeeds | Retriable |

The pivot is the step after which the saga goes forward or ends. Everything before it needs a compensation. Everything after it needs a retry, because there is no way back. The saga here ends at the payment, and the shipment is the retriable step that follows.

### How Quarkus and Camel model it

Each service owns its tables and writes its event through `debezium-quarkus-outbox` in the same transaction as the state change. One Debezium connector reads the three outbox tables and routes each row to the topic named in its `topic` column. The six topics are created with one partition each, because a topic the connector creates gets the broker default of three, and the replies of one order have to arrive in the order they were written.

`order-service` holds the saga. Each reply is a method on a bean that moves one row of `order_saga`:

```java
@Transactional
public boolean onPaymentProcessed(UUID eventId, PaymentProcessed processed) {
    if (!firstTime(eventId)) {
        return false;
    }
    String orderId = processed.payment().orderId();
    boolean won = moved(SagaEntity.update("state = ?1, version = version + 1 where orderId = ?2 and state = ?3",
            SagaState.COMPLETED, orderId, SagaState.AWAITING_PAYMENT), orderId, "payment processed");
    if (won) {
        OrderEntity.update("status = ?1 where id = ?2", OrderStatus.PAID, orderId);
        meters.counter("saga.outcomes", "outcome", "completed").increment();
    }
    return won;
}
```

`firstTime` is an inbox: the event id is inserted in the same transaction, and a second delivery changes nothing. The replies arrive through SmallRye Reactive Messaging, one `@Incoming` method per topic.

`payment-service` is the Camel participant. A route reads the reservations and hands each to a bean that takes the payment and writes `PaymentProcessed` or `PaymentFailed` to its outbox:

```java
from("kafka:" + Topics.INVENTORY_RESERVED + "?groupId=payment-reserved&autoOffsetReset=earliest&breakOnFirstError=true")
        .routeId("take-payment")
        .autoStartup(false)
        .unmarshal().json(JsonLibrary.Jackson, InventoryReserved.class)
        .bean("paymentService", "take");
```

`breakOnFirstError` makes a failed payment be read again instead of skipped. The route starts on request, after the cancellations have been read.

## Compensation is a new event

A compensation does not restore the earlier state. It records a new fact that makes the earlier one harmless. A declined payment publishes `PaymentFailed`, and `order-service` cancels the order and publishes `OrderCancelled`. Inventory releases on either event.

Inventory sees both, in no guaranteed order, and may see either twice or before the reservation exists. The release has to survive all of it. The reservation row is the state:

```sql
INSERT INTO inventory.reservation (order_id, quantity, state) VALUES (:id, 0, 'RELEASED')
ON CONFLICT (order_id) DO NOTHING;                         -- nothing reserved yet: leave a release behind

UPDATE inventory.reservation SET state = 'RELEASED'
WHERE order_id = :id AND state = 'RESERVED' RETURNING sku, quantity;   -- reserved: release it once
```

The first statement makes a release that arrives early leave a `RELEASED` row, and the reservation that follows finds the row and is refused. The second changes a `RESERVED` row once and returns what to put back. Applied twice, the release releases once. The inbox of inventory drops a repeated event id before the release runs, but the release would change nothing a second time either, so the stock looks the same with or without the inbox. Applied before the reservation, it still ends with the stock untouched. A compensation that is idempotent and commutative cannot be broken by the order in which Kafka delivers the events or by a redelivery, and the Camel Saga documentation gives the same advice for the same reason.

Not every failure needs one. When the stock is not there, `InventoryInsufficient` ends the saga at once, and no release exists for a reservation that was never made. The compensation for each step is part of its design: a step that can be undone publishes how.

## Timeouts and late replies

Three cases recur in every saga: a declined payment, a service that does not answer, and an answer that arrives after the saga gave up. The first is a message. The second is silence, and silence has no event to react to.

So every wait has a deadline, and one service owns it. `order-service` writes the deadline into `order_saga` when a saga enters a waiting state. A scheduled sweeper selects the sagas past their deadline with `FOR UPDATE SKIP LOCKED`, sets them `CANCELLED` and writes `OrderCancelled` to the outbox in the same transaction:

```java
@Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
void sweep() {
    saga.expire();
}
```

The sweeper runs every second, so a saga is cancelled between 0.6 and 1.7 seconds after its deadline. The timeout itself is a business decision. A short one cancels orders that were about to be paid. A long one keeps stock reserved for a customer whose payment never comes.

{% include excalidraw.html file="22-timeout-and-late-reply" alt="With payment-service stopped, an order is reserved and waits with a deadline, the sweeper cancels it at the deadline, inventory releases the stock, and when payment-service returns it reads the cancellation and refuses the reservation; a dashed branch shows the payment taken when the check is off" caption="Figure 22.2: The saga gives up at its deadline, and the participant that returns must not act on the old reservation" %}

The hazard is the third case. `payment-service` was down for the whole timeout. The saga cancelled, inventory released the stock, and now the participant restarts to find an `InventoryReserved` waiting. Taking the payment would charge a customer for an order that no longer exists and goods that are back on the shelf.

A saga has no isolation, and the countermeasure is a semantic lock. `payment-service` reads `OrderCancelled` and keeps the order ids in `cancelled_order`. `PaymentService` checks the table before it takes any money and refuses a payment for a cancelled order. The check works only if the lock is set before the payment is attempted, and two consumers on two topics make no promise about order. After a restart, a gate route waits until the cancellation consumer has no lag, which is a comparison of its committed offset with the end of the topic, and only then starts the payment route. A participant that was away learns what it missed before it acts.

With `PAYMENT_CANCEL_GUARD=off` (demo code) the check is removed, and the payment for the cancelled order is taken: money taken, no goods. With every service up the two sagas behave the same; the difference shows only when a participant returns late.

The limit of the lock is in what it knows. It protects against a payment that starts after the cancellation is recorded. A payment already in flight when the order is cancelled completes, and its refund is a business decision about money, which is why the step is a pivot.

## Where the saga state lives

Choreography has no coordinator, and the flow still has to be answerable: where is this order, and until when does it wait? One service holds that and nothing else does. `order_saga` is a row per order with its state, its deadline and a version:

{% include excalidraw.html file="22-saga-state-machine" alt="AWAITING_STOCK moves to AWAITING_PAYMENT on InventoryReserved, AWAITING_PAYMENT moves to COMPLETED on PaymentProcessed, and both waiting states move to CANCELLED on a failure or the deadline; every edge is an update guarded by the current state" caption="Figure 22.3: The saga state is one row, and every transition is a conditional update on it" %}

Every transition is `UPDATE order_saga SET state = new WHERE state = expected`. A payment reply and the sweeper can arrive in the same instant for the same saga, and exactly one of them updates a row. The other updates none and changes nothing, so a late `PaymentProcessed` for a cancelled order is logged and ignored. The winner writes its event in the same transaction, and `SKIP LOCKED` leaves a saga that a reply is deciding to that reply.

The state lives in PostgreSQL, so a restart of `order-service` loses nothing. A saga that waited through the restart is still waiting when the service is back, and is cancelled at its original deadline by a sweeper that knows nothing but the table.

| | Choreography | A coordinator that holds the sequence |
|---|---|---|
| Coupling | Each service knows the events it reads and publishes, not who reads them | The coordinator knows every participant and what to ask of each |
| Seeing the flow | Assembled from three services and their topics | In one place, as the sequence the coordinator runs |
| Adding a step | A new service subscribes; the others do not change | The coordinator changes |
| A step that goes silent | Needs a deadline owned by one service, here `order-service` | The coordinator times out the step it waits for |
| Failure points | None central; the topics and each service's own state | The coordinator holds the state of every saga in flight |
| Compensation | Each participant publishes or reacts to the events that undo its step | Ordered by the coordinator |

Choreography fits a short chain of few services, owned by one team, where each step has one obvious successor. It stops fitting when the question "what happens to this order" needs a map of every subscription, and when a fourth and fifth participant start reacting to each other's events.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/22-saga
mvn -B verify
./verify.sh
```

`verify.sh` runs a happy order, a burst, a decline, a `payment-service` that stays down while a saga times out, and a recreate of `order-service` with a saga in flight. Watch the stock return after the decline and the late reservation get refused; it prints `30/30 checks passed`.

Try one change and rerun:

- `PAYMENT_CANCEL_GUARD=off ./verify.sh` (demo code): the payment for a cancelled order is taken.
- `SAGA_PAYMENT_TIMEOUT=60s ./verify.sh` (demo code): the sweeper cancels the saga after 60 seconds, later than the script waits for it.

## When not to use it

A saga is for work that cannot share a transaction. When one service and one database can do the steps, a local transaction is simpler and isolated, and the problem the saga solves comes only from splitting the order across services.

Compensations are code to write and test for every step. A step with no real undo, such as an email that was sent, can only be followed by another message, and a design that needs many of them has put the pivot too early.

The lack of isolation is the price. Readers see a reserved stock count that a cancellation will correct, and a design that cannot tolerate that needs a different boundary, not a better saga.

Choreography stops scaling at the point where nobody can say what the whole flow is. A flow with branches, parallel steps or many participants needs its sequence written down in one place.

> **🤖 Explore with your agent**
> "In examples/22-saga, stop payment-service, place an order, wait for order_saga to reach CANCELLED, start payment-service again and show what PaymentService does with the waiting InventoryReserved. Run it again with PAYMENT_CANCEL_GUARD=off and state what the customer would be charged for."
> Check: order_saga ends CANCELLED with reason TIMED_OUT, inventory releases the reservation once, PaymentService refuses the payment because the order is in cancelled_order, and with the guard off a payment row exists for the cancelled order.

## References

- Hector Garcia-Molina and Kenneth Salem, [*Sagas*](https://doi.org/10.1145/38713.38742), Proceedings of the 1987 ACM SIGMOD International Conference on Management of Data
- Microsoft, [Saga design pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/saga), Azure Architecture Center (compensable, pivot and retryable transactions; semantic lock and commutative updates)
- Chris Richardson, [Pattern: Saga](https://microservices.io/patterns/data/saga.html), microservices.io, and *Microservices Patterns*, Manning, 2018
- Gregor Hohpe and Bobby Woolf, [*Enterprise Integration Patterns*](https://www.enterpriseintegrationpatterns.com/patterns/messaging/ProcessManager.html), Addison-Wesley, 2003: Process Manager
- Apache Camel, [Saga EIP](https://camel.apache.org/components/4.22.x/eips/saga-eip.html) (compensations that are idempotent and commutative)
- Debezium, [Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html) and the [Quarkus outbox extension](https://debezium.io/documentation/reference/stable/integrations/outbox.html)
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapter 7: Long-Running Processes
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 10: Building Workflows with Microservices
- *Enterprise Integration Patterns with Camel*, chapter 10: Composed Routing
- *Cloud-Native Design Patterns*, [Appendix D: Sagas](https://patterncatalyst.github.io/cloud-native-design-patterns/docs/17-appendix-d-sagas/)
- Chapter 8: the transactional outbox; chapter 11: the inbox; chapter 12: transactions stop at the cluster; chapter 13: retry
