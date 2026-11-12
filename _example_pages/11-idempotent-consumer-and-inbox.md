---
title: "Idempotent consumer and inbox"
order: 11
permalink: /examples/11-idempotent-consumer-and-inbox/
description: "Stock reserved once per event id: an inbox row in the effect's transaction, with a duplicate record, a crash before the acknowledgement and Camel's Idempotent Consumer as comparisons."
---

[`examples/11-idempotent-consumer-and-inbox/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/11-idempotent-consumer-and-inbox) reserves stock for each `OrderPlaced` event from `edp.orders.placed`, once per event id, with the id recorded in the same PostgreSQL transaction as the decrement. It accompanies [Idempotent consumer and inbox]({{ '/docs/11-idempotent-consumer-and-inbox/' | relative_url }}).

It uses Quarkus, SmallRye Reactive Messaging, Hibernate ORM with Panache, Flyway, Camel Kafka with the JPA and JTA extensions for the Camel form, PostgreSQL and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/11-idempotent-consumer-and-inbox
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="11-inbox-same-transaction" alt="First delivery: insert into the inbox returns one row, the stock is decremented and both commit before the acknowledgement. Second delivery: the insert returns zero rows, nothing is decremented and the record is acknowledged" caption="One transaction holds the id and the effect" %}

## What to observe

1. **A duplicate record is skipped.** The same id twice changes the stock once and leaves one inbox row.
2. **A crash does not repeat the effect.** The service halts after the commit and before the acknowledgement; the redelivered record meets its inbox row.
3. **Without the inbox the stock is wrong.** The same four records take off 12 instead of 9.
4. **Camel's repository can do the same.** Inside a transacted route its key row commits with the decrement.
5. **A key stored apart leaves a gap.** Without the transaction a failed effect keeps its key, and the redelivery is skipped.

## Infrastructure

Kafka, PostgreSQL and the LGTM stack from `examples/_infra`, a one-shot container that creates the order topic with one partition, and `inventory-service`. The compose project is `edp-11`.
