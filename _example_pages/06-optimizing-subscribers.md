---
title: "Optimizing subscribers"
order: 6
permalink: /examples/06-optimizing-subscribers/
description: "A notification service draining a seeded backlog under different Kafka consumer settings, with client metrics, lag and rebalances observed."
---

[`examples/06-optimizing-subscribers/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/06-optimizing-subscribers) drains a seeded backlog of notification events through one Camel consumer route and changes one Kafka consumer setting per run. It accompanies [Optimizing subscribers]({{ '/docs/06-optimizing-subscribers/' | relative_url }}).

It uses Quarkus, Camel Kafka, Hibernate ORM with Panache, Flyway, PostgreSQL, Micrometer and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/06-optimizing-subscribers
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="06-concurrency-cap" alt="Nine partitions each assigned to one of the first nine of twelve consumers; the last three consumers are idle" caption="Twelve consumers on nine partitions" %}

## What to observe

1. **One row per record.** A fresh consumer group drains the backlog, and every row has one attempt.
2. **Threads stop helping at the partition count.** With an even assignor, twelve consumer threads leave exactly three without a partition. The default assignor leaves nine idle.
3. **A long poll gets the member removed.** With 500 records per poll at 50 ms each and a 5 second interval, records are delivered again and the log shows the client leaving the group. With 20 records per poll nothing is.
4. **Fetch settings matter for a trickle.** A larger `fetch.min.bytes` sends a fraction of the fetch requests.
5. **A joining replica.** The classic protocol revokes all nine partitions from the first replica, and the consumer protocol only those that move.

## Infrastructure

Kafka, PostgreSQL and the LGTM stack from `examples/_infra`, a one-shot container that creates the topics, a seeder under the `seed` profile, `notification-service` and a second replica under the `scale` profile. The compose project is `edp-06`.
