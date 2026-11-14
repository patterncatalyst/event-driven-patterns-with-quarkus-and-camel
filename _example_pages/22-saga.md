---
title: "Saga"
order: 22
permalink: /examples/22-saga/
description: "An order choreographed over Kafka by three services with no coordinator: the saga state in order-service, a compensation that is idempotent and commutative, a sweeper for silent participants, and a semantic lock that refuses a late payment."
---

[`examples/22-saga/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/22-saga) carries an order from placed to paid through `order-service`, `inventory-service` and `payment-service`. Each publishes through the outbox, and one Debezium connector reads the three outbox tables. It accompanies [Saga]({{ '/docs/22-saga/' | relative_url }}).

It uses Quarkus, SmallRye Reactive Messaging, Camel Kafka (in `payment-service`), Hibernate ORM with Panache, Flyway, the Debezium outbox extension, Quarkus Scheduler, SmallRye Health, PostgreSQL, Kafka, Kafka Connect and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/22-saga
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="22-saga-choreography" alt="order-service, inventory-service and payment-service in a row; orders.placed and inventory.reserved lead forward, payments.processed returns to order-service, and payments.failed, inventory.insufficient and orders.cancelled lead back as the compensation path" caption="Each service reacts to the previous event and publishes its own; the dashed arrows are the way back" %}

## What to observe

1. **Compensation once.** A declined payment ends in a cancelled order and a released reservation, and a repeated PaymentFailed is skipped by the inbox (the release is idempotent, so the stock is the same either way).
2. **A deadline owned by one service.** With payment-service stopped, the sweeper cancels the saga after its deadline and inventory releases the stock.
3. **A late reservation refused.** When payment-service returns, it waits until it has read the cancellations and takes no money for the cancelled order.
4. **State in PostgreSQL.** A saga in flight is still waiting after a recreate of order-service and is cancelled at its original deadline.

## Infrastructure

Kafka, PostgreSQL, Kafka Connect and the LGTM stack from `examples/_infra`, plus the three services on ports 8080, 8082 and 8084. The compose project is `edp-22`.
