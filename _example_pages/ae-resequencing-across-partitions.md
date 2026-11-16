---
title: "Resequencing across partitions"
order: 40
permalink: /examples/ae-resequencing-across-partitions/
description: "One order-service that reads three per-event topics, restores order with the Camel stream or batch Resequencer, applies each event to PostgreSQL and publishes it keyed by order id."
---

[`examples/ae-resequencing-across-partitions/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/ae-resequencing-across-partitions) resequences shipment events that arrive from several producers on three topics. `RESEQUENCER` selects the stream or the batch form. It accompanies [Appendix E]({{ '/docs/ae-resequencing-across-partitions/' | relative_url }}).

It uses Quarkus, Camel Kafka, the Camel Resequencer, Hibernate ORM with Panache, Flyway, SmallRye Health, PostgreSQL, Kafka and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/ae-resequencing-across-partitions
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="ae-stream-vs-batch" alt="Top: arrivals 6, 9, 3, 5, 1, 2 enter the stream resequencer, which holds them and applies 1, 2, 3, 4 in global order. Bottom: a batch of six is sorted by order and sequence, and event 1 of the first order sits in the second batch, so it is applied after event 2" caption="The stream form orders everything it sees; the batch form orders only inside a batch" %}

## What to observe

1. **Global order.** Twelve events of four orders arrive scrambled and are applied as 1 to 12; each order's events leave on `edp.shipping.shipments` as 1, 2, 3 in one partition.
2. **Gaps.** A missing event is skipped at the timeout and a late one is applied last, or refused with `rejectOld`.
3. **The per-order number.** Driving the stream form with it applies only some of the events and logs nothing.
4. **Batches.** Each batch is sorted, and an order split over two batches is still applied out of order.
5. **Restart.** Records held at a restart are not applied afterwards, because their offsets were committed.

## Infrastructure

Kafka, PostgreSQL and the LGTM stack from `examples/_infra`, plus `order-service`. The compose project is `edp-ae`.
