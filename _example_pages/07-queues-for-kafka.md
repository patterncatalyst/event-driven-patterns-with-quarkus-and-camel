---
title: "Queues for Kafka"
order: 7
permalink: /examples/07-queues-for-kafka/
description: "Thirty notification jobs, one slow email, four workers: delivered through a share group and through a consumer group."
---

[`examples/07-queues-for-kafka/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/07-queues-for-kafka) delivers 30 notification jobs from `edp.orders.placed` with four `notification-service` replicas, once through a share group and once through a consumer group. It accompanies [Queues for Kafka]({{ '/docs/07-queues-for-kafka/' | relative_url }}).

It uses Quarkus, SmallRye Reactive Messaging, Camel Kafka, Hibernate ORM with Panache, Flyway, PostgreSQL and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/07-queues-for-kafka
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="07-head-of-line" alt="In a consumer group worker-1 owns the partition and the later jobs wait behind the slow one; in a share group worker-1 holds the slow job and worker-2 and worker-3 take the later jobs from the same partition" caption="Who waits behind one slow job" %}

## What to observe

1. **A consumer group waits.** The slow job is first in its partition, and every later job of that partition finishes after it.
2. **A share group does not.** Another worker takes the later jobs while the first holds the slow one, so at least one later job finishes first.
3. **Release redelivers.** The retry job is delivered twice, with delivery counts 1 and 2.
4. **Reject ends it.** The bounce job is delivered once and is not delivered again after the lock duration.
5. **One job per worker.** No worker has two attempts in progress at once.
6. **More workers than partitions.** The share group lists four members and at least three deliver jobs. In the consumer group one of the four workers owns no partition and delivers nothing.

## Infrastructure

Kafka, PostgreSQL and the LGTM stack from `examples/_infra`, a one-shot container that creates the topic and the share group's settings, and four `notification-service` replicas. The compose project is `edp-07`.
