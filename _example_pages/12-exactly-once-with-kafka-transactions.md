---
title: "Exactly-once with Kafka transactions"
order: 12
permalink: /examples/12-exactly-once-with-kafka-transactions/
description: "A read-process-write loop whose output records and input offsets commit in one Kafka transaction, with a crash in the middle of a batch and a Camel transacted route as comparisons."
---

[`examples/12-exactly-once-with-kafka-transactions/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/12-exactly-once-with-kafka-transactions) reads `InventoryReserved` from `edp.inventory.reserved` and writes one `PaymentProcessed` to `edp.payments.processed` for each, inside one Kafka transaction. It accompanies [Exactly-once with Kafka transactions]({{ '/docs/12-exactly-once-with-kafka-transactions/' | relative_url }}).

It uses Quarkus, SmallRye Reactive Messaging (`KafkaTransactions`), Camel Kafka for the comparison route, SmallRye Health and OpenTelemetry to the LGTM stack. There is no database.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/12-exactly-once-with-kafka-transactions
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="12-read-process-write" alt="PaymentProcessor reads a batch from edp.inventory.reserved; the output records and the consumer offsets are written inside one Kafka transaction and a commit marker on both partitions makes them take effect" caption="Output records and input offsets in one transaction" %}

## What to observe

1. **A committed batch is five payments.** A read_committed reader sees one per order, and the group offset moved with them.
2. **A crash leaves written records nobody can read.** The open transaction holds seven payments that only a read_uncommitted reader sees.
3. **The restart fences the old transaction.** All ten orders appear once, within seconds, not after the timeout.
4. **Aborted copies stay in the log.** The default console consumer returns them.
5. **Offsets outside the transaction duplicate.** The Camel route commits each output alone and re-reads seven orders after the crash.

## Infrastructure

Kafka and the LGTM stack from `examples/_infra`, a one-shot container that creates both topics with one partition, and `payment-service`. The compose project is `edp-12`.
