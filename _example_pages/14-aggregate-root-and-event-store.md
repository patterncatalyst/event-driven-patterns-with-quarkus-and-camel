---
title: "Aggregate root and the event store"
order: 14
permalink: /examples/14-aggregate-root-and-event-store/
description: "An order kept only as its own event stream: one aggregate that decides every command, appends at the version it loaded, decides again when it loses a race, and publishes OrderPlaced and OrderCancelled through the outbox."
---

[`examples/14-aggregate-root-and-event-store/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/14-aggregate-root-and-event-store) keeps orders as streams in an append-only `orders.events` table, with no order state table. Commands come from REST and from `PaymentProcessed` and `ShipmentScheduled` records on Kafka. It accompanies [Aggregate root and the event store]({{ '/docs/14-aggregate-root-and-event-store/' | relative_url }}).

It uses Quarkus, Hibernate ORM with Panache, Flyway, Quarkus REST, SmallRye Reactive Messaging with SmallRye Fault Tolerance, the Debezium outbox extension and Kafka Connect, Camel Kafka for the comparison route, PostgreSQL and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/14-aggregate-root-and-event-store
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="14-conflict-redecide" alt="A cancellation decided at version 2 loses to a shipment appended as version 3; decided again from version 3 it is refused with 409; appended blind at version 4 it lands after the shipment" caption="A retry reloads and decides again; a blind re-append skips the decision" %}

## What to observe

1. **The stream is the state.** `GET /orders/{id}` replays the events; the schema holds no table of order state.
2. **A payment delivered twice adds one event.** The aggregate ignores a payment id it has recorded.
3. **A cancellation that loses to a shipment is refused.** It is decided again from version 3 and answered 409.
4. **A cancellation that loses to a payment is accepted.** The retry succeeds at the next version.
5. **A stale `If-Match` is answered 412.** No command runs and nothing is retried.
6. **Only two events reach Kafka.** `OrderPlaced` and `OrderCancelled` leave through the outbox in the same transaction as the append.
7. **The Camel route and the channel agree on a conflict.** Both run the transactional bean again, which reloads and decides again, and both acknowledge a refusal. They differ once retries are used up: the channel fails, the route drops the command and commits the offset.

## Infrastructure

Kafka, PostgreSQL, Kafka Connect and the LGTM stack from `examples/_infra`, a one-shot container that creates the four topics with one partition each, and `order-service`. The outbox connector `order-outbox` is registered by `verify.sh`. The compose project is `edp-14`.
