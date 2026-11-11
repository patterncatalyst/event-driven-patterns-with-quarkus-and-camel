---
title: "Managing connector offsets"
order: 10
permalink: /examples/10-managing-connector-offsets/
description: "One Debezium connector on a legacy inventory table: stopped, altered forward and backward, reset, and recovered from a dropped and a lost replication slot."
---

[`examples/10-managing-connector-offsets/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/10-managing-connector-offsets) runs the `inventory-cdc` connector against a legacy `inventory_item` table and records every change event `inventory-service` receives. It accompanies [Managing connector offsets]({{ '/docs/10-managing-connector-offsets/' | relative_url }}).

It uses Quarkus, Camel Kafka, Hibernate ORM with Panache, Flyway, PostgreSQL, Debezium Connect and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/10-managing-connector-offsets
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="10-offset-lifecycle" alt="A running connector is stopped with PUT stop; while stopped its offsets can be altered with PATCH or reset with DELETE, and PUT resume starts it again; PATCH or DELETE while running is refused with HTTP 400" caption="Offsets change only while the connector is stopped" %}

## What to observe

1. **Offsets are refused while running.** PATCH and DELETE return 400 until the connector is stopped.
2. **A stopped connector keeps WAL.** The slot goes inactive, retained bytes grow with writes to tables the connector does not capture, and resume delivers every change.
3. **A forward alter skips.** Ten updates made while stopped never arrive; the next one does.
4. **A backward alter does nothing.** The slot's position wins and nothing is replayed.
5. **A reset re-snapshots.** Exactly five `r` rows with current values, no history.
6. **A dropped slot fails the task; a lost slot does not.** The first shows a trace; the second leaves the connector running with no task.
7. **Recovery is the same for both.** Stop, drop the slot, delete the offsets, resume.

## Infrastructure

Kafka, PostgreSQL, Debezium Connect and the LGTM stack from `examples/_infra`, a one-shot container that creates the topic, and `inventory-service`. The compose project is `edp-10`.
