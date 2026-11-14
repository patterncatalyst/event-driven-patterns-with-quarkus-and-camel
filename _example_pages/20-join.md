---
title: "Join"
order: 20
permalink: /examples/20-join/
description: "Two events joined by order id two ways on the same input: a Kafka Streams windowed left join in event time and a Camel aggregator in arrival time, with two crafted pairs where they disagree, an unpaid reservation, and state that survives a recreate."
---

[`examples/20-join/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/20-join) joins `InventoryReserved` and `PaymentProcessed` in `shipping-service` and emits `ReadyToShip` or `PaymentOverdue` on one topic per form. It accompanies [Join]({{ '/docs/20-join/' | relative_url }}).

It uses Quarkus, Kafka Streams, Camel Kafka and Camel SQL with the PostgreSQL aggregation repository, Flyway, PostgreSQL, Kafka, SmallRye Health and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/20-join
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="20-event-vs-arrival-time" alt="O-2003 has event times two minutes apart and arrives more than the 10 second timeout apart, so Kafka Streams matches it and the Camel aggregator, with a 10 second timeout, emits PaymentOverdue first; O-2004 has event times 90 minutes apart and arrives back to back, so Kafka Streams emits PaymentOverdue and the Camel aggregator matches it" caption="Two clocks, two answers, in opposite directions" %}

## What to observe

1. **The forms agree when the clocks agree.** Facts close together in event time and in arrival give `ReadyToShip` on both outputs, in either order.
2. **They disagree in opposite directions when the clocks differ.** `O-2003` matches on the Streams output and is overdue on the Camel output; `O-2004` is the other way round.
3. **An unpaid reservation is reported by each form in its own way.** Camel reports it after its timeout; Streams reports it only after a later record moves stream time.
4. **Open state survives a recreate.** A reservation made before the service was recreated is paired with its payment afterwards on both outputs.

## Infrastructure

Kafka, PostgreSQL and the LGTM stack from `examples/_infra`, a one-shot container that creates the two input and two output topics with one partition each, and `shipping-service`. The compose project is `edp-20`.
