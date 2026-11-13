---
title: "Aggregate replay or CQRS projection"
order: 17
permalink: /examples/17-aggregate-replay-or-projection/
description: "Orders shipped per carrier per day answered two ways from one event store: a replay of every aggregate, and a projection with a checkpoint that waits for open transactions."
---

[`examples/17-aggregate-replay-or-projection/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/17-aggregate-replay-or-projection) keeps orders as event streams in PostgreSQL and answers one question over all of them, shipments per carrier per UTC day, by folding every `OrderAggregate` on request and from a table that `CarrierDailyProjector` keeps current. It accompanies [Aggregate replay or CQRS projection]({{ '/docs/17-aggregate-replay-or-projection/' | relative_url }}).

It uses Quarkus, Hibernate ORM with Panache, Flyway, Quarkus REST, Quarkus Scheduler, PostgreSQL and OpenTelemetry to the LGTM stack. There is no Kafka and no Camel.

```bash
cd examples/17-aggregate-replay-or-projection
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="17-replay-vs-projection" alt="Commands append events to orders.events; a replay reads every stream at the time of the question, folds each aggregate and counts the shipped ones; a projection reads the same events before the question through a projector with a checkpoint and keeps carrier_daily_shipments, which a query scans" caption="Two read paths over one store" %}

## What to observe

1. **Both paths give the same answer.** The replay, the projection and a SQL query over the raw events agree at 1,000, 10,000 and 100,000 orders.
2. **The replay grows with the store.** A projection read does not.
3. **A new question needs a rebuild.** Dropping the projection leaves it behind the replay until `lagEvents` reaches 0.
4. **A late commit is waited for.** While one transaction is held open, the projection counts neither it nor the commit after it, and counts both afterwards.
5. **A position checkpoint loses a row.** With `PROJECTION_WATERMARK=off` the projection never counts the held order.

## Infrastructure

PostgreSQL and the LGTM stack from `examples/_infra`, and `order-service`. There is no Kafka. The compose project is `edp-17`.
