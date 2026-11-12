---
title: "Retry and dead letter"
order: 13
permalink: /examples/13-retry-and-dead-letter/
description: "A consumer that retries a locked row, dead-letters records that can never succeed with their original bytes, and the same inputs through a Camel route."
---

[`examples/13-retry-and-dead-letter/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/13-retry-and-dead-letter) reads `OrderPlaced` records from `edp.orders.placed`, reserves stock, retries a failure that can clear and sends the records that cannot succeed to `edp.orders.placed-dlq`. It accompanies [Retry and dead letter]({{ '/docs/13-retry-and-dead-letter/' | relative_url }}).

It uses Quarkus, Hibernate ORM with Panache, Flyway, SmallRye Reactive Messaging with SmallRye Fault Tolerance, Camel Kafka for the comparison route, PostgreSQL and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/13-retry-and-dead-letter
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="13-failure-classes" alt="Three failure classes: an unparseable or invalid record is tried once and goes to the dead letter topic; a record that fails on a locked row is retried and then reserved; a record still failing after the last retry goes to the dead letter topic" caption="Retry what can succeed later, dead-letter what never will" %}

## What to observe

1. **A truncated record is kept.** It is on `edp.orders.placed-dlq` with the bytes that were sent, and its headers name the source topic and offset.
2. **An invalid record is tried once.** A quantity of 0 is dead-lettered without a retry, and the reason says why.
3. **A locked row is retried.** The order is attempted again until the lock is released, then reserved.
4. **The record behind it waits.** A valid order for another SKU is not read while the first is retried.
5. **The Camel route does the same.** It dead-letters the same records with their original bytes, and no headers describe the failure.

## Infrastructure

Kafka, PostgreSQL and the LGTM stack from `examples/_infra`, a one-shot container that creates the order topic and its dead letter topic with one partition each, and `inventory-service`. The compose project is `edp-13`.
