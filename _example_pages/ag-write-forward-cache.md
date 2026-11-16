---
title: "Appendix G: Write-forward cache"
order: 42
permalink: /examples/ag-write-forward-cache/
description: "An order and its full-state event in one transaction, carried to Kafka by Debezium and applied to Redis by a Camel route behind a version guard, with the dual write and a guardless replay as comparisons."
---

[`examples/ag-write-forward-cache/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/ag-write-forward-cache) stores each order and its event in one PostgreSQL transaction, publishes the event through Debezium Connect, and applies it to Redis. `GET /orders/{id}` reads Redis only. It accompanies [Appendix G]({{ '/docs/ag-write-forward-cache/' | relative_url }}).

It uses Quarkus, Hibernate ORM with Panache, Flyway, the Debezium Outbox extension, the Quarkus Redis client, Camel Kafka, PostgreSQL, Debezium Connect, Redis and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/ag-write-forward-cache
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="ag-write-forward-path" alt="POST /orders writes the order row and the outbox row in one transaction; Debezium and the EventRouter publish the full state to edp.orders.placed and edp.orders.cancelled; a Camel route applies it to a Redis hash behind a version guard; GET /orders/{id} reads Redis only" caption="The request writes PostgreSQL, the events write Redis, the read endpoint reads Redis" %}

## What to observe

1. **Lag, not disagreement.** The cache trails each commit by a few hundred milliseconds and never holds an order PostgreSQL does not.
2. **No database fallback.** With Connect stopped, a committed order is a 404 from the cache.
3. **A cache loss is repaired by replay.** A restarted Redis is refilled by a new consumer group reading both topics.
4. **The guard matters.** A replay of older events after newer ones leaves cancelled orders cancelled.

## Infrastructure

Kafka, PostgreSQL, Connect, the LGTM stack and Redis from `examples/_infra`, a one-shot container that creates the two order topics with one partition, and `order-service` on port 8080. The compose project is `edp-ag`.
