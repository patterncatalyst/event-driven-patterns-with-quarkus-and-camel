---
title: "Aggregate root and the event store"
order: 14
part: event-sourcing
permalink: /docs/14-aggregate-root-and-event-store/
description: "An order is the stream of its own events. One object owns every rule about it, appends at the version it loaded, decides again when it loses a race, and publishes only the events that are public."
duration: "55 minutes"
---

Chapter 1 appends order events to a table and lets a unique version settle a race between two writers. Its rules live in a service bean that checks a status change rather than in an object rebuilt from the stream, the losing writer gets 409 and nothing decides again, and the events never reach Kafka. `order-service` keeps no order table at all: an order is its stream. `O-1402` is paid, and then a cancellation and a shipment arrive in the same moment, both having read PAID.

{% include excalidraw.html file="14-aggregate-command-cycle" alt="A command is loaded against the stream, the aggregate is rebuilt, the command is decided and the events are appended at the loaded version plus one in one transaction with an outbox row for OrderPlaced and OrderCancelled; Debezium carries those two to edp.orders.placed and edp.orders.cancelled" caption="Figure 14.1: One command, one transaction, and two public events out" %}

## Pattern: Aggregate

### The problem

Three callers change an order. A REST request cancels it, a `PaymentProcessed` record marks it paid and a `ShipmentScheduled` record ships it. If each reads the status, checks the move and writes the result, the rules live in three places and drift apart. Two callers that act on the same status at the same moment also both pass their own check: a cancellation that read PAID and a shipment that read PAID are each correct against what they saw, and together they leave CANCELLED after SHIPPED.

Fowler describes an aggregate as a cluster of domain objects treated as one unit and reached through a single root, which keeps the whole consistent, and says a transaction should not cross its boundary. Vernon gives aggregates a chapter of their own, "Aggregates". With event sourcing the cluster is a stream: the state of an order is the fold of its events, and the only way to change it is to append.

### The solution

**An aggregate is one object, reached through its root, that rebuilds its state from its own event stream, decides each command against that state and appends the resulting events at the version it loaded.**

Three decisions make it work. The aggregate is rebuilt for every command, so no stored copy of its state can be stale. It decides before it records: a command returns the events it causes or is refused, and a refusal writes nothing. And the append names the version it was decided against, so the store can reject it when the stream has moved on.

### How Quarkus and Camel model it

`OrderAggregate` is a plain class with no framework type in it. `rehydrate` folds the stored events into a status, an amount and a payment id. `decide` checks a command against that state and returns the events to append, or throws `CommandRefused` with the reason:

```java
case Command.MarkPaid pay -> {
    if (pay.paymentId().equals(paymentId)) {
        return events; // the payment is already recorded: a redelivery changes nothing
    }
    if (paymentId != null) {
        throw new CommandRefused("already paid by " + paymentId);
    }
    moveTo(OrderStatus.PAID);
    if (pay.amount().compareTo(amount) != 0) {
        throw new CommandRefused("amount " + pay.amount() + " differs from order amount " + amount);
    }
    events.add(new OrderEvent.OrderPaid(pay.paymentId(), pay.amount()));
}
```

The lifecycle comes from `OrderStatus.canMoveTo`, the rule chapter 1 uses. A payment whose id is already recorded returns no events, so a redelivered `PaymentProcessed` is harmless without an inbox table: the natural idempotence of chapter 11, kept inside the aggregate.

`EventStore` has the two operations an event store needs. `load` reads a stream in version order, and `append` writes events at `expectedVersion + 1` onward through Panache. The unique constraint on `(stream_id, version)` is the concurrency check. Hibernate reports a violation as a `ConstraintViolationException` inside a `PersistenceException`, and the store turns the one named `events_stream_version` into a `ConcurrencyConflict`:

```java
try {
    persistAndFlush(row);
} catch (PersistenceException e) {
    if (violatesVersionConstraint(e)) {
        throw new ConcurrencyConflict(streamId, expectedVersion, e);
    }
    throw e;
}
```

Young's CQRS Documents describe the same two operations with an optimistic check against an expected version. He keeps the current version in an aggregates table; the constraint needs no second table.

`OrderTransaction` runs one command in one transaction:

```java
@Transactional
public Result handle(Command command, OptionalInt clientVersion) {
    OrderAggregate order = load(command.orderId());
    int loaded = order.version();
    // ... a client version that differs from the loaded one is a conflict
    List<OrderEvent> events = order.decide(command); // CommandRefused when the rules say no
    // ... a hook that holds the command here
    int expected = clientVersion.orElse(loaded);
    store.append(STREAM_TYPE, command.orderId(), expected, events); // ConcurrencyConflict on a stale version
    events.forEach(event -> export(command.orderId(), event));
    return new Result(expected + events.size(), events);
}
```

The expected version is the one the aggregate was loaded at. A caller that decided from a version it read can pass that version instead.

## Conflicts: retry or report

When two commands load the same version, one append wins and the other fails with `ConcurrencyConflict`. The loser's transaction rolls back and writes nothing. What happens next depends on who made the decision.

{% include excalidraw.html file="14-conflict-redecide" alt="A cancellation decided at version 2 loses to a shipment appended as version 3; decided again from version 3 it is refused with 409; appended blind at version 4 it lands after the shipment" caption="Figure 14.2: A retry reloads and decides again; a blind re-append skips the decision" %}

A cancellation sent with no precondition means "cancel this order if the rules allow it". Its intent does not depend on the state the server read, so the server can load the stream again and decide again. If a shipment won, the second decision is a refusal; if a payment won, the cancellation is accepted at the next version. `OrderCommands` declares that with SmallRye Fault Tolerance:

```java
@Retry(retryOn = ConcurrencyConflict.class, maxRetries = 3, delay = 50, jitter = 25)
public OrderTransaction.Result handle(Command command) {
    return transaction.handle(command);
}
```

The retry wraps a call to another bean, so every attempt is a new transaction that loads the stream again. A `CommandRefused` is not in `retryOn` and is not retried. The count comes from configuration under the method's key, so `RETRY_MAX` changes it without a rebuild.

A client that read version 2 and cancels because of what it saw has not asked for a cancellation at whatever version exists later. It sends the `ETag` it holds as `If-Match`, and RFC 9110 defines the answer when the precondition fails: 412 Precondition Failed. `OrderResource` compares the tag with the current version through `Request.evaluatePreconditions`, and passes the client's version to the append so that a race behind a matching tag is reported too:

```java
int current = transaction.load(id).version();
Response.ResponseBuilder stale = request.evaluatePreconditions(tag(current));
if (stale != null) {
    return stale.build();
}
var result = transaction.handle(command, OptionalInt.of(current));
```

{% include excalidraw.html file="14-retry-or-report" alt="Without If-Match the cancellation goes through OrderCommands, which retries in a new transaction and answers 200 or 409; with If-Match a stale tag runs no command and is answered 412, and the caller reads the order again and decides" caption="Figure 14.3: Retry what does not depend on what the caller saw, report what does" %}

| Policy | When it is right | What goes wrong if it is misapplied |
|---|---|---|
| Reload and decide again (`@Retry`, Camel redelivery) | The command's intent does not depend on the state the caller saw | Nothing: a refusal is a valid outcome |
| Report to the caller (`If-Match`, 412) | The caller decided from a state it read | A retry would override a decision the caller never made |
| Append the decided events at the new head | Never | The history contains CANCELLED after SHIPPED |

A refused command is a business outcome. Over REST it is a 409 with the reason. On a Kafka channel it is logged and acknowledged, since failing the channel would stop every order behind it.

`PaymentConsumer` and `ShipmentConsumer` are `@Incoming` methods with `@Blocking` that build the command and call `OrderCommands`. `PaymentCommandRoute` is the comparison, started with the `camel-commands` profile in place of the payments channel:

```java
// in PaymentCommandRoute.configure()
onException(ConcurrencyConflict.class)
        .maximumRedeliveries(redeliveries).redeliveryDelay(50)
        .logRetryAttempted(true).retryAttemptedLogLevel(LoggingLevel.INFO);
onException(CommandRefused.class)
        .handled(true)
        .log(LoggingLevel.INFO, "camel acknowledged a refused command: ${exception.message}");

from("kafka:" + Topics.PAYMENTS_PROCESSED + "?groupId=order-payments-camel&autoOffsetReset=earliest&sessionTimeoutMs=10000")
        .routeId("payment-commands")
        // ... autoStartup is read from configuration
        .unmarshal().json(JsonLibrary.Jackson, PaymentProcessed.class)
        .bean("orderTransaction", "markPaid");
```

The route decides nothing. It is an adapter from a topic to the same transactional bean, with the same aggregate and store behind it. Camel's Aggregator EIP shares the word and nothing else: it combines messages and has no relation to a domain aggregate.

| | SmallRye Reactive Messaging | Camel route |
|---|---|---|
| Retry is declared | `@Retry` on `OrderCommands.handle`, count overridable by config | `maximumRedeliveries` in an `onException` clause |
| What is run again | The transactional bean, which loads the stream | The same bean, which loads the stream |
| A refused command | Caught, logged and acknowledged | `handled(true)`, logged and acknowledged |
| Retries used up | The exception propagates and the channel fails | The command is dropped and the offset is committed |

## Publishing from an event-sourced aggregate

The events in the store are the service's own format. A Debezium connector that tailed `events` directly, the store used as a queue in Young's terms, would publish `OrderPaid` and `OrderShipped` too and make the schema of the table the contract with every consumer. The service publishes only `OrderPlaced` and `OrderCancelled`, the two other services act on.

`OrderTransaction.export` fires an `ExportedEvent` for those two inside the same transaction as the append. The Debezium outbox extension from chapter 8 writes the row, and the connector and the Event Router carry it to `edp.orders.placed` or `edp.orders.cancelled`, keyed by order id. A command that fails after the append rolls back the event row and the outbox row together, so a record exists on a topic only for a stream that holds the event.

```java
private void export(String orderId, OrderEvent event) {
    switch (event) {
        case OrderEvent.OrderPlaced placed -> exports.fire(OrderExports.placed(orderId, placed, json));
        case OrderEvent.OrderCancelled cancelled -> exports.fire(OrderExports.cancelled(orderId, cancelled, json));
        case OrderEvent.OrderPaid paid -> { }
        case OrderEvent.OrderShipped shipped -> { }
    }
}
```

An event-sourced service still publishes through an outbox. The event row is the state change, and the outbox row for a public event commits beside it.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/14-aggregate-root-and-event-store
mvn -B verify
./verify.sh
```

The Maven build runs the service against PostgreSQL and Kafka from Dev Services: 27 tests cover every aggregate rule without a database, a stale append that adds no row, both outcomes of the race, a stale `If-Match`, a failure after the append that leaves neither row, Kafka commands, the Camel route with and without redelivery, and the blind re-append as the negative witness. `verify.sh` starts Kafka, PostgreSQL, Connect, the LGTM stack and the service, and prints `33/33 checks passed`.

The checks read the effects:

- The schema holds no table of order state. `O-1401` is one row and one record on `edp.orders.placed`. The same `PaymentProcessed` produced twice gives one `OrderPaid`, and no `OrderPaid` is on any topic.
- Race 1: a cancellation for `O-1402` is held after it decided at version 2, and a shipment is appended as version 3 while the request is open. The cancellation answers 409 `SHIPPED to CANCELLED` after one logged conflict, the stream stays at three events and nothing is on `edp.orders.cancelled`. Every history in the table obeys the lifecycle.
- Race 2: a cancellation for `O-1403` is held at version 1 and a payment is appended as version 2. The cancellation returns 200 with `ETag` `"3"` and one record reaches `edp.orders.cancelled`.
- A cancellation of the paid `O-1404` with `If-Match` `"1"` returns 412 and runs no command. With `"2"` it returns 200.
- Race 3: a payment for `O-1405` read from Kafka is held at version 1 while a REST cancellation wins. The payment is decided again, refused with `CANCELLED to PAID` after one conflict, and acknowledged.
- The service restarts with `camel-commands`, the group `order-payments-camel` is reset to the earliest offset, and every payment is read again with no change to any earlier stream. Race 3 repeats for `O-1406`: the route redelivers the payment once and acknowledges the refusal.
- A trace, logs and `order_command_conflicts_total` for `order-service` reach Tempo, Loki and Prometheus.

Change one thing and watch a guard fail:

- Run `RETRY_MODE=reappend ./verify.sh`. The cancellation that lost the race is appended again at the new head without being decided: `O-1402` ends with `OrderCancelled` as version 4 after `OrderShipped`, and a cancelled record is published for an order that shipped. Checks 14, 15, 17 and 30 fail.
- Run `RETRY_MAX=0 ./verify.sh`. A conflict is reported instead of decided again, so race 1 has no refusal reason, race 2 is rejected and race 3 does not decide the payment again. Checks 14, 19 to 21, 26 and 30 fail.
- Delete the `CONSTRAINT events_stream_version` line and the comma before it from the first migration and run `./verify.sh`. Nothing detects the race: no conflict is logged and the history of `O-1402` is no longer a legal lifecycle. Checks 14 to 17, 19, 20, 26 and 29 to 31 fail.
- Run `CAMEL_REDELIVERIES=0 ./verify.sh`. The route drops the conflicting payment after one attempt and commits its offset. `O-1406` ends in the same stream, since the cancellation had already won, so only the redelivery and the refusal are missing: check 29 fails.

## When not to use it

An aggregate is a consistency boundary for one stream. A rule that spans two orders, such as a limit on a customer's open orders, is enforced by neither stream.

A stream answers one question, the state of one order by id. Any other, such as the orders of one customer, needs a second structure derived from the events and kept current by something that reads them.

Loading replays the whole stream on every command. For four events that is negligible; a stream of thousands needs a cheaper starting point, as chapter 1 notes.

A server-side retry fits only a command whose intent survives a changed state. And events are permanent: once other services read `OrderPlaced`, a wrong fact is corrected by appending another event, never by editing a row.

Kafka is not an event store: the Azure Architecture Center's Event Sourcing pattern asks of a store that it reject an append when the stream has changed since it was read.

> **🤖 Explore with your agent**
> "In examples/14-aggregate-root-and-event-store, pause a Cancel of a paid order between decide and append, append OrderShipped as version 3, and compare what OrderAggregate, OrderTransaction and OrderCommands do with the cancellation against the same cancellation with an If-Match header. State when a conflict is decided again and when it is reported to the caller."
> Check: without If-Match the cancellation is decided again from version 3 and answered 409 with SHIPPED to CANCELLED, and with If-Match "2" it is answered 412 with no event added.

## References

- Martin Fowler, [DDD Aggregate](https://martinfowler.com/bliki/DDD_Aggregate.html), 2013, and [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html), 2005
- Vaughn Vernon, *Implementing Domain-Driven Design*, Addison-Wesley, 2013, chapter 8: Domain Events, chapter 10: Aggregates and Appendix A: Aggregates and Event Sourcing
- Greg Young, [CQRS Documents](https://cqrs.wordpress.com/wp-content/uploads/2010/11/cqrs_documents.pdf), 2010: building an event storage, event storage as a queue
- Microsoft Azure Architecture Center, [Event Sourcing pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing)
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapter 4: Tactical Building Blocks and chapter 6: Event Sourcing
- Martin Kleppmann and Chris Riccomini, *Designing Data-Intensive Applications*, 2nd ed., O'Reilly, 2026
- RFC 9110, [HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html): the `If-Match` header field and the 412 Precondition Failed status code
- Quarkus, [Hibernate ORM with Panache](https://quarkus.io/guides/hibernate-orm-panache), [SmallRye Fault Tolerance](https://quarkus.io/guides/smallrye-fault-tolerance), [REST](https://quarkus.io/guides/rest) and [Apache Kafka Reference Guide](https://quarkus.io/guides/kafka)
- Debezium, [Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html) and [Outbox Quarkus Extension](https://debezium.io/documentation/reference/stable/integrations/outbox.html)
- Apache Camel, [Exception Clause](https://camel.apache.org/manual/exception-clause.html) and [Kafka component](https://camel.apache.org/components/4.22.x/kafka-component.html)
- PostgreSQL documentation, [Constraints](https://www.postgresql.org/docs/18/ddl-constraints.html) (unique constraints)
