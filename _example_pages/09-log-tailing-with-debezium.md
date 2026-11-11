---
title: "Log tailing with Debezium"
order: 9
permalink: /examples/09-log-tailing-with-debezium/
description: "A legacy inventory table captured from the PostgreSQL log by Debezium on Kafka Connect and embedded in the service, published as InventoryItem documents."
---

[`examples/09-log-tailing-with-debezium/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/09-log-tailing-with-debezium) captures a legacy `inventory_item` table from the write-ahead log and publishes each row as an `InventoryItem` document on `edp.inventory.items`. It accompanies [Log tailing with Debezium]({{ '/docs/09-log-tailing-with-debezium/' | relative_url }}).

It uses Quarkus, Camel Kafka, the Camel Debezium PostgreSQL component, Kafka Connect with Debezium, PostgreSQL and OpenTelemetry to the LGTM stack. `verify.sh` needs `jq` on the host.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/09-log-tailing-with-debezium
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="09-wal-publication-slot" alt="Writes to the captured table, the uncaptured busy table and the heartbeat table land in the write-ahead log; the publication filters three tables for the connector, and the replication slot records how far it has confirmed" caption="The log holds every table's changes; the slot holds the reader's place" %}

## What to observe

1. **The snapshot.** Five `r` events and five documents, one per SKU, before anything changes.
2. **One change, one document.** An update arrives as a `u` event and as a document with the domain's names, keyed by SKU.
3. **`before` depends on the table.** Under the default replica identity a delete carries the key and not the old row; under `FULL` it carries the whole row.
4. **One tombstone.** Debezium follows a delete with its own tombstone. The route drops it, so the documents topic gets exactly one.
5. **Order.** Three updates of one row in one transaction arrive in commit order with increasing LSNs.
6. **A quiet slot.** A burst on a table nobody captures stays in the log with only a heartbeat interval, and is released when the heartbeat writes to a captured table.
7. **Incremental snapshot.** A row in the signal table re-emits the table's rows while changes keep streaming.
8. **Embedded.** The same documents from Debezium inside the service, with its own slot and an offset file.

## Infrastructure

Kafka, PostgreSQL, Kafka Connect with the published Debezium image and the LGTM stack from `examples/_infra`, a one-shot container that creates the two topics (the documents topic is compacted), and `inventory-service`. The compose project is `edp-09`.
