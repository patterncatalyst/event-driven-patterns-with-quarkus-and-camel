---
title: "Tracing across async hops"
order: 25
permalink: /examples/25-tracing-across-async-hops/
description: "One order as one trace from POST /orders through the outbox, Debezium in Connect and a SmallRye consumer, with a span-link form beside it and a search by order id in Tempo."
---

[`examples/25-tracing-across-async-hops/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/25-tracing-across-async-hops) places an order in `order-service`, publishes it through the outbox and a Debezium connector, and reserves stock in `inventory-service`. It accompanies [Tracing across async hops]({{ '/docs/25-tracing-across-async-hops/' | relative_url }}).

It uses Quarkus, SmallRye Reactive Messaging, Hibernate ORM with Panache, Flyway, the Debezium outbox extension, OpenTelemetry, PostgreSQL, Kafka, Kafka Connect with the OpenTelemetry Java agent, and the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/25-tracing-across-async-hops
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="25-trace-waterfall" alt="A waterfall of six spans: POST /orders and outbox-write in order-service, db-log-write and debezium-read in connect, and edp.orders.placed receive and reserve stock in inventory-service, with a dashed bar below marking the hop through the log and the relay" caption="One order as one trace; the gap before the consumer span is the time outside the services" %}

## What to observe

1. **One trace.** A TraceQL search by order id returns one trace with spans of three services.
2. **The ancestry.** The reservation descends from the request through the outbox write and Connect's `db-log-write`.
3. **The link form.** With the consumer in link form the order's trace ends at Connect and a second trace links back.
4. **Without the agent.** Connect publishes the event with no `traceparent` and the consumer starts an unrelated trace.

## Infrastructure

Kafka, PostgreSQL, Kafka Connect with its tracing override, and the LGTM stack from `examples/_infra`, plus two services on ports 8080 and 8082. The compose project is `edp-25`.
