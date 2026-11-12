---
title: "Snapshots"
order: 15
permalink: /examples/15-snapshots/
description: "A stock ledger of 100,000 events loaded from a snapshot plus the events after it, written in the command's transaction, and the same snapshots taken by a Camel route on a timer."
---

[`examples/15-snapshots/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/15-snapshots) keeps the stock of three SKUs as streams of `StockReceived` and `StockReserved` events in PostgreSQL, loads an `InventoryItemAggregate` from its latest snapshot plus the later events, and writes a snapshot every N events. It accompanies [Snapshots]({{ '/docs/15-snapshots/' | relative_url }}).

It uses Quarkus, Hibernate ORM with Panache, Flyway, Quarkus REST, SmallRye Fault Tolerance, Camel Scheduler and SQL for the comparison route, PostgreSQL and OpenTelemetry to the LGTM stack. There is no Kafka.

```bash
cd examples/15-snapshots
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="15-snapshot-plus-tail" alt="A stream of events ordered by version; a snapshot holds the fold of versions 1 to s, so a load reads one snapshot row and the events after s, where a load without it reads every event from version 1" caption="A load reads the snapshot and the events after it" %}

## What to observe

1. **A bulk-loaded stream is read in full.** `SKU-1503` has 100,000 events and no snapshot, and the first load replays all of them.
2. **A command writes the snapshot.** After a reservation the load replays 0 events, and it replays at most N-1 until the next snapshot.
3. **The snapshot is a cache.** Deleting it, or changing its schema version, changes the cost of a load and never the state.
4. **A late snapshot is refused.** The upsert will not replace a snapshot with an older one.
5. **The Camel route lags a burst.** With the `camel-snapshotter` profile the tail passes N during a burst and is brought back by the next tick.

## Infrastructure

PostgreSQL and the LGTM stack from `examples/_infra`, and `inventory-service`. There is no Kafka. The compose project is `edp-15`.
