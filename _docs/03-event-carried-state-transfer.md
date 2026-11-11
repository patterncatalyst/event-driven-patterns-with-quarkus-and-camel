---
title: "Event-carried state transfer and claim check"
order: 3
part: getting-started
permalink: /docs/03-event-carried-state-transfer/
description: "Put the whole order in the event so consumers keep a local copy and answer without the source, then keep the large part out of the event with a claim check."
duration: "40 minutes"
---

A customer asks the notification service about order O-1001. The service needs the customer, the item and the status. If it has to ask the order service, it fails whenever the order service is down, and every question costs a request. If the event already told it everything, it can answer alone.

{% include excalidraw.html file="03-notification-vs-state-transfer" alt="Event notification carries only the order id and the consumer fetches the current state; event-carried state transfer carries the whole Order and the consumer reads its local copy" caption="Figure 3.1: What the consumer has to do after the event" %}

## Pattern: Event-Carried State Transfer

### The problem

An event that carries only an order id makes every consumer call back for the data. That keeps one owner of the order and always returns current state, but it couples availability: a consumer cannot answer while the source is down. It also puts load on the source that grows with the number of consumers and the rate of events.

### The solution

**Event-carried state transfer puts the state a consumer needs into the event, so the consumer keeps its own copy and never calls the source.**

Fowler's "What do you mean by 'Event-Driven'?" gives the pattern its name and sets it beside event notification, event sourcing and CQRS. Lawrence's chapter "Composing Data from Different Sources" is the related reading on combining data that several services own. The copy is a read model. It is owned by the consumer, shaped for the consumer's questions and rebuilt from events alone.

### How Quarkus and Camel model it

order-service publishes `OrderPlaced` on `edp.orders.placed` and `OrderCancelled` on `edp.orders.cancelled`. Each carries the whole `Order` from the shared domain model and its `OrderStatus`, keyed by order id:

```json
{"orderId":"O-1001","order":{"id":"O-1001","customerId":"CU-77","itemSku":"SKU-42","quantity":3,"amount":59.90},"status":"PLACED"}
```

The event goes out after the transaction has committed. A `ProducerTemplate` sends it into a `direct:` route that owns the topic:

```java
from(DIRECT)
        .routeId("publish-order-placed")
        .setHeader(KafkaConstants.KEY, simple("${body.orderId}"))
        .marshal().json()
        .to("kafka:" + Topics.ORDERS_PLACED);
```

notification-service has one consumer route per topic. Both are the same route over a different topic, so they share a base class. An event that cannot be applied is logged and skipped, and the consumer moves on:

```java
onException(IllegalArgumentException.class, JsonProcessingException.class)
        .handled(true)
        .log(LoggingLevel.WARN, "skipped record on " + topic + ": ${exception.message}");

from("kafka:" + topic + "?groupId=notification-order-copy")
        .unmarshal().json(OrderStateEvent.class)
        .bean(OrderCopies.class, "apply");
```

`OrderStateEvent` lists only the fields this consumer reads and ignores the rest. `OrderCopies.apply` is a Panache upsert into `notifications.order_copy`, keyed by order id. It does two things beyond copying fields. An event with no order state throws `IllegalArgumentException`, which is how the thin event is refused. And an event that would move the status backwards is ignored, using the same `OrderStatus.canMoveTo` rule the lifecycle uses, so a late `OrderPlaced` cannot undo a cancellation that arrived first. The two topics have no order between them, and a copy that applied events in arrival order would regress.

`GET /order-copies/{id}` reads the table through Quarkus REST. The copy needs no HTTP client, no retry and no circuit breaker, because it makes no call.

### What the copy costs

The copy is as fresh as the last event applied. A reader that needs the current status the instant an order changes cannot use it. The producer's event is also a contract: renaming a field breaks every consumer that copied it. And the copy is more data to store and keep correct, in every service that keeps one. Event notification costs one request per use and returns current state. State transfer costs storage and staleness and removes the request.

## Pattern: Claim Check

### The problem

An event that carries state grows with the state. An order that can hold a gift message, a delivery note or an attached document gives the event a variable, unbounded size. Every consumer receives every byte, including the consumers that never read the attachment. Kafka bounds the size: the producer's `max.request.size` defaults to 1048576 bytes and the broker's `message.max.bytes` to 1048588. A payload that grows past the smaller one is rejected by the producer. Short of that, every consumer receives every byte of an attachment it never reads.

### The solution

**A claim check stores the large part of a message in a persistent store and passes a reference to it, so only the consumers that need the content fetch it.**

Hohpe and Woolf describe the pattern as storing message data in a persistent store and passing a claim check to subsequent components. Camel has a Claim Check EIP of its own, but it keeps the content in an internal memory store of its own Camel context. A second service has no access to that store, so it cannot carry content between two services, and this example does not use it. Between services the store has to be shared: here, PostgreSQL.

{% include excalidraw.html file="03-claim-check" alt="order-service stores the attachment in orders.claim_check and publishes a small event with the claim id and size; notification-service fetches the content from GET /claims/{id} only when asked" caption="Figure 3.2: Store, publish the claim, fetch on demand" %}

### How Quarkus and Camel model it

A request to `POST /orders` may carry an `attachment` next to the order fields. order-service stores it in `orders.claim_check` (`id`, `order_id`, `content`, `created_at`) in the same transaction as the order, and the event carries a `Claim` in place of the content:

```json
{"orderId":"O-1002","order":{...},"status":"PLACED","claim":{"id":"2f0c9a1e-...","size":102400}}
```

The event stays under 1 KB whether the attachment is one line or 100 KB. Any holder of the claim id can read the content from `GET /claims/{id}` on order-service. notification-service records the claim with the copy and fetches the content only for `GET /order-copies/{id}/attachment`, through a Quarkus REST client. The order fields never need that call.

The producer's request limit is set to 64 KB in the example, far below Kafka's default, so that a 100 KB attachment shows the limit:

```properties
camel.component.kafka.max-request-size=65536
```

With `ORDER_EVENTS_ATTACHMENT=inline` the same attachment goes inside the event instead. The producer refuses it with `RecordTooLargeException`, `POST /orders` returns 502 and nothing reaches the topic. The order itself has already committed, because the event is published after the commit. That window between the commit and the publish is a cost of publishing after commit, and it is the same for every event, large or small.

### What the claim check costs

The content now lives in a second place with its own lifecycle. Someone has to decide when a claim expires, and the example keeps every claim. A consumer that needs the content depends on the source again: with order-service stopped, the copy answers but the attachment returns 503. Notification, state transfer and claim check sit on one scale:

| | Notification only | Full state | Claim check |
|---|---|---|---|
| Event size | An id | Grows with the state | Small and fixed, plus a claim |
| Coupling | Consumer calls the source for everything | Consumer depends on the event's fields | Fields in the event, large part by claim |
| Source down | Consumer cannot answer | Consumer answers | Consumer answers, but not the large part |
| Freshness | Current at fetch | As old as the last event | Fields as old as the last event, content as stored |
| Producer limit | Never reached | Reached when state grows | Not reached by the attachment |
| Choose it when | One owner must answer with current data | Consumers read most of the state and it is small | Few consumers need a large, optional part |

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/03-event-carried-state-transfer
mvn -B verify
./verify.sh
```

The Maven build runs the order-service tests and the notification-service tests, which need no order-service at all. `verify.sh` starts Kafka, PostgreSQL, LGTM and both services with Docker Compose and prints `30/30 checks passed`:

- The copy of a placed order has every field, the copy of a cancelled order shows CANCELLED, and the event for the order with the 100 KB attachment is under 1 KB and holds the claim size.
- Prometheus has no `GET /orders/{id}` series for order-service and Tempo has no HTTP client span from notification-service, while both can see other series and spans.
- With order-service stopped, the copy still answers 200 with the customer, item and amount, and the attachment returns 503. With it back, the attachment is fetched, byte for byte, and the first HTTP client span appears.
- With `ORDER_EVENTS_ATTACHMENT=inline`, the 100 KB attachment is refused with 502 and the topic does not grow.

Change one thing and watch a guard fail:

- Run `ORDER_EVENTS_CARRY_STATE=false ./verify.sh`. The event holds only the order id, notification-service logs it as skipped and every copy answers 404: checks 5 to 10, 16 to 19 and 21 to 26 fail, 14 of 30 pass.
- Run `ORDER_EVENTS_ATTACHMENT=inline ./verify.sh`. The 100 KB attachment goes inside the event, the producer refuses it, and the order with the attachment is never placed on the topic: checks 2, 7, 9, 10, 19 and 21 to 23 fail.
- Set `camel.component.kafka.max-request-size` to 1048576 and run `ORDER_EVENTS_ATTACHMENT=inline ./verify.sh`. The inline event is accepted, so the topic carries 100 KB for one order and the negative witness disappears: checks 9, 10, 19 and 21 to 27 fail.
- Remove the status guard from `OrderCopies.apply`. A late `OrderPlaced` turns a cancelled copy back into PLACED, and the unit test for it fails.

## When not to use it

Do not copy state that must be current. A balance, a stock level or a payment status that decides something right now belongs with its owner, and event notification with a fetch keeps one answer. Do not copy state that more than a few consumers will have to keep correct, because every copy is a place to be wrong. Do not carry state whose shape you cannot hold stable: the event is a contract, and every field you add is one more thing consumers depend on.

Do not use a claim check for small payloads. A reference costs a second store, a second call and a lifecycle for the stored content, and a claim that outlives its order is data nobody owns. If every consumer reads the attachment, put it in the event and raise the limits, or store it once and let them all fetch it. And a claim check does not make the publish atomic with the commit: the order can commit and the event can still fail.

> **🤖 Explore with your agent**
> "In examples/03-event-carried-state-transfer, place one Order with no attachment and one with a 100 KB attachment, then read both events from edp.orders.placed. For each, say whether a consumer can answer GET /order-copies/{id} with order-service stopped, and apply the rule: carry state the consumer reads on every use, keep a large optional part behind a claim, and fetch current data from the owner when staleness is not acceptable."
> Check: both copies answer with order-service stopped, the 100 KB event is under 1 KB and holds a claim, and only the attachment request fails.

## References

- Martin Fowler, 2017: [What do you mean by "Event-Driven"?](https://martinfowler.com/articles/201701-event-driven.html)
- Gregor Hohpe and Bobby Woolf, *Enterprise Integration Patterns*, Addison-Wesley, 2003: [Claim Check](https://www.enterpriseintegrationpatterns.com/patterns/messaging/StoreInLibrary.html)
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026: Chapter 9, Composing Data from Different Sources
- Apache Camel: [Claim Check EIP](https://camel.apache.org/components/latest/eips/claimCheck-eip.html)
- Apache Kafka: [Broker configuration, `message.max.bytes`](https://kafka.apache.org/43/configuration/broker-configs/) and [producer configuration, `max.request.size`](https://kafka.apache.org/43/configuration/producer-configs/)
