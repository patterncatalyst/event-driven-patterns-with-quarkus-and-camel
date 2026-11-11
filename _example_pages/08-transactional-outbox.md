---
title: "Transactional outbox"
order: 8
permalink: /examples/08-transactional-outbox/
description: "An order and its event in one transaction, carried to Kafka by Debezium, with the dual write and a crash after the commit as comparisons."
---

[`examples/08-transactional-outbox/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/08-transactional-outbox) stores each order and its `OrderPlaced` event in one PostgreSQL transaction and publishes the event to `edp.orders.placed` through Debezium Connect. It accompanies [Transactional outbox]({{ '/docs/08-transactional-outbox/' | relative_url }}).

It uses Quarkus, Hibernate ORM with Panache, Flyway, the Debezium Outbox extension, Camel Kafka for the comparison routes, PostgreSQL, Debezium Connect and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/08-transactional-outbox
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="08-outbox-wal-router" alt="OrderService writes the order and the outbox row in one transaction; Debezium reads the outbox insert from the write-ahead log and the router publishes it to edp.orders.placed" caption="The order and its event commit together; the log carries the event out" %}

## What to observe

1. **One order, one record.** The record is keyed by the order id and carries `id` and `eventType` headers.
2. **A refused order is silent.** A duplicate id gets a 409, and no record and no row follow.
3. **A crash after the commit loses nothing.** The order placed through the outbox has its event after the restart.
4. **The dual write loses it.** The commit-first route leaves the order without an event, and publish-first announces an order twice.
5. **Connect can be down.** Orders are accepted, and their events arrive in commit order once Connect is back.

## Infrastructure

Kafka, PostgreSQL, Connect and the LGTM stack from `examples/_infra`, a one-shot container that creates the order topic with one partition, and `order-service`. The compose project is `edp-08`.
