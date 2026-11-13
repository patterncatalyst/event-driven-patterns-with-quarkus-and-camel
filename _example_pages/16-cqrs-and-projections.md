---
title: "CQRS and projections"
order: 16
permalink: /examples/16-cqrs-and-projections/
description: "An order-status read model built from six public topics: one guarded upsert per event, a view that says how current it is, a paused topic that leaves it visibly stale, and the same view built by a Camel route."
---

[`examples/16-cqrs-and-projections/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/16-cqrs-and-projections) keeps one table, `orders.order_view`, built from the events of the order, payment and shipping services, and answers `GET /orders/{id}` and `GET /customers/{customerId}/orders` from it. It has no write model. It accompanies [CQRS and projections]({{ '/docs/16-cqrs-and-projections/' | relative_url }}).

It uses Quarkus, Hibernate ORM with Panache, Flyway, Quarkus REST, SmallRye Reactive Messaging with pausable channels, Camel Kafka for the comparison route, PostgreSQL, Kafka and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/16-cqrs-and-projections
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="16-read-lag-window" alt="ShipmentDelivered is produced while the delivered channel is paused; reads at one second and at four seconds return SHIPPED with the same asOf; after resume the view returns DELIVERED and the lag timer records the time from the record timestamp to the acknowledgement" caption="A paused topic leaves the view stale, and asOf shows it" %}

## What to observe

1. **Each event moves one column.** A payment sets the payment status and never the shipment; the status a reader sees is the furthest fact the row holds.
2. **A paused topic leaves the view stale, and the answer says so.** The group has lag 1, reads return SHIPPED with an older `asOf`, and after resume the order is DELIVERED.
3. **A late status does not move the view back.** `ShipmentInTransit` applied after `ShipmentDelivered` leaves DELIVERED.
4. **An event can arrive before its order.** The row exists without a customer until `OrderPlaced` is applied.
5. **Replay is harmless.** Resetting all six groups to the earliest offset leaves the view unchanged.
6. **One route builds the same view.** A single Camel consumer over the six topics produces the same hash, through the same projector.

## Infrastructure

Kafka, PostgreSQL and the LGTM stack from `examples/_infra`, a one-shot container that creates the six topics with one partition each, and `order-service`. There is no Connect and no registry. The compose project is `edp-16`.
