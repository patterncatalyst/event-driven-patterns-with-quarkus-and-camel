---
title: "Ordering and partitioning"
order: 4
permalink: /examples/04-ordering-and-partitioning/
description: "One order's shipment events keyed three ways, a paused consumer replica, and a per-order buffer that restores the lifecycle."
---

[`examples/04-ordering-and-partitioning/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/04-ordering-and-partitioning) publishes the shipment events of an order to a topic with three partitions and applies them in two `order-service` replicas that share a consumer group. It accompanies [Ordering and partitioning]({{ '/docs/04-ordering-and-partitioning/' | relative_url }}).

It uses Quarkus, Camel Kafka, Hibernate ORM with Panache, Flyway, PostgreSQL and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/04-ordering-and-partitioning
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="04-stalled-partition" alt="Event-type key: the replica that owns partition 2 is paused, so Delivered is applied before Scheduled" caption="A stalled partition reorders an order" %}

## What to observe

1. **Order id keeps the order.** With one replica paused, the other applies nothing of the order, and after the pause the history is Scheduled, InTransit, Delivered and the status is DELIVERED.
2. **Event type loses it.** The replica that stays up applies InTransit and Delivered while the other is paused. After it resumes, Scheduled arrives last and the status ends SHIPPED.
3. **Carrier keeps each order whole.** Six orders use at most two of three partitions.
4. **The buffer restores it.** With `RESEQUENCE=true` the replica that stays up holds the two early events in `pending_shipment_event` and applies none. After the pause the same run ends DELIVERED in lifecycle order.
5. **A missing event holds its order.** The buffer holds that order's later events until the missing one arrives. Other orders are not held.
6. **No rebalance.** The consumer group assignment is the same before and after the pause.

## Infrastructure

Kafka, PostgreSQL and the LGTM stack from `examples/_infra`, a one-shot container that creates the topics, `shipping-service` and two `order-service` replicas. The compose project is `edp-04`.
