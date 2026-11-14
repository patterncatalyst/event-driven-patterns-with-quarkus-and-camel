---
title: "Aggregate"
order: 21
permalink: /examples/21-aggregate/
description: "One shipping-service that builds pickup lists from ShipmentScheduled twice, with a Kafka Streams tumbling window that emits one final list per carrier per slot and a Camel Aggregator that closes batches by size or quiet time."
---

[`examples/21-aggregate/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/21-aggregate) groups the shipments scheduled for each carrier into pickup lists. `shipping-service` runs a Kafka Streams topology and a Camel route on the same `edp.shipping.scheduled` input, each writing its own topic. It accompanies [Aggregate]({{ '/docs/21-aggregate/' | relative_url }}).

It uses Quarkus, Quarkus Kafka Streams, Camel Kafka and the Camel SQL aggregation repository, Flyway, SmallRye Health, PostgreSQL, Kafka and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/21-aggregate
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="21-window-vs-batch" alt="The same arrivals for FASTLINE grouped two ways; Kafka Streams gives the slot 09:00 list with F1 to F7 and F9, an open list for slot 09:15 holding F8, and drops L1; the Camel Aggregator gives four batches in arrival order closed by size or timeout, including L1 alone" caption="The same arrivals grouped by slot of event time and by size and quiet time at arrival" %}

## What to observe

1. **One final list per slot.** After a later event time closes the 09:00 window, Kafka Streams has written exactly one list for each carrier, with the shipment that arrived out of order inside it.
2. **A late record has no list.** The 09:05 shipment that arrives after the window closed is on no Kafka Streams list, the dropped-records counter reaches Prometheus, and the Camel route puts it in a batch of its own.
3. **Two grouping rules.** The same shipments give a batch of five closed by size and a batch of two closed by timeout on the Camel side.
4. **State survives a recreate.** An open window and an open batch are both restored, and a list or batch of five closes after two more shipments.

## Infrastructure

Kafka, PostgreSQL and the LGTM stack from `examples/_infra`, plus `shipping-service`. The compose project is `edp-21`.
