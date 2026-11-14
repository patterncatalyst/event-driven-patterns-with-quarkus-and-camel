---
title: "Correlated logs and traces"
order: 27
permalink: /examples/27-correlated-logs-and-traces/
description: "An order saga over three services with the order id on every span and log line, the trace id on every log line, and the sweeper's trace for a timed-out order linked to the trace that placed it, followed through Tempo and Loki."
---

[`examples/27-correlated-logs-and-traces/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/27-correlated-logs-and-traces) carries an order from placed to paid through `order-service`, `inventory-service` and `payment-service`, a trimmed copy of the saga of chapter 22. Each service publishes through the outbox, one Debezium connector relays the three outbox tables, and Connect is traced. It accompanies [Correlated logs and traces]({{ '/docs/27-correlated-logs-and-traces/' | relative_url }}).

It uses Quarkus, SmallRye Reactive Messaging, Camel Kafka (in `payment-service`), Hibernate ORM with Panache, Flyway, the Debezium outbox extension, Quarkus Scheduler, SmallRye Health, PostgreSQL, Kafka, Kafka Connect and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/27-correlated-logs-and-traces
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="27-one-order-many-signals" alt="order-service, Connect, inventory-service and payment-service in a row inside one trace, each service marked with order.id and orderId, with arrows to Tempo and to Loki" caption="One trace per order, with the order id on its spans and on every log line" %}

## What to observe

1. **One order, one trace.** A search for `{ span.order.id = "O-2701" }` returns one trace with spans of order-service, connect, inventory-service and payment-service.
2. **A trace id on every line.** Every Loki line with `orderId` O-2701 carries that trace id, and the trace id leads back to the lines of three services.
3. **A timeout is a second trace.** With payment-service stopped, the order that the sweeper cancels is two traces by order id, and the second trace's root links to the first.
4. **Two switches as witnesses.** `SWEEPER_LINK=off` removes the link and nothing else; `ORDER_ID_IN_LOGS=off` empties the queries by order id while the trace id still works.

## Infrastructure

Kafka, PostgreSQL, Kafka Connect with the tracing override and the LGTM stack from `examples/_infra`, plus the three services on ports 8080, 8082 and 8084. The compose project is `edp-27`.
