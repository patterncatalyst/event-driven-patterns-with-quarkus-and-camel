# 09 Log tailing with Debezium

`inventory-service` publishes the stock of a legacy inventory table as `InventoryItem` documents. The table, `inventory.inventory_item`, belongs to another team: it is not changed, and nothing in it publishes events. Debezium reads the PostgreSQL write-ahead log through a replication slot and a publication and turns every committed insert, update and delete into a change event. A Camel route maps each event to a document on `edp.inventory.items`, keyed by SKU, and publishes a delete as a tombstone.

Quarkus, Camel Kafka, the Camel Debezium PostgreSQL component, Kafka Connect with the Debezium connector, PostgreSQL, SmallRye Health and OpenTelemetry to the LGTM stack. There is no database access in the service except the embedded engine's own connection. The shared `InventoryItem` record and the topic names come from `examples/domain-model`.

## Services

| Service | Port | Purpose |
|---|---|---|
| `inventory-service` | 8080 | Camel routes from change events to documents. Health on `/q/health/ready` |
| `connect` | 8083 | Kafka Connect with the Debezium PostgreSQL connector (the published image). REST API for connectors |

| Setting | Default | Effect |
|---|---|---|
| `CDC_MODE` | `connect` | `connect` reads the envelopes Kafka Connect writes; `embedded` stops that route and starts Debezium inside the service (it sets the Quarkus profile) |

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # mapping, tombstones, ordering and a rejected row against Kafka from Dev Services
./verify.sh        # compose stack, connector, then the experiments below; needs jq on the host
KEEP=1 ./verify.sh # leave the stack up; Connect is on http://localhost:8083, Grafana on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, and stops this one on exit unless `KEEP=1`. The embedded engine keeps its offsets in `/tmp/cdc-offsets.dat` inside the container, so recreating the container takes a new snapshot.

## What verify.sh does

1. Creates and seeds the legacy table with five SKUs, plus a busy `legacy_audit` table nobody captures, the signal table and the heartbeat table. Registers two connectors with `PUT /connectors/<name>/config` from `connect/`: `inventory-cdc`, the real one, and `interval-only`, a witness that has a heartbeat interval but no heartbeat query.
2. Checks the slot and the publication, the snapshot (five `r` events, five documents), an update, and deletes under both replica identities: what `before` holds, and that each delete reaches the documents topic as exactly one tombstone.
3. Writes three updates of one row in one transaction and checks they arrive in commit order with increasing LSNs.
4. Heartbeat: a burst of about 55 MB into `legacy_audit`. Both slots retain it. With `heartbeat.action.query` the slot releases it after an offset flush; with the interval alone it never does.
5. Incremental snapshot: one insert into the signal table re-emits each row as an `r` event while an update made at the same time still arrives, and the latest document per SKU matches the table.
6. Embedded: the service is recreated with `CDC_MODE=embedded`. The engine takes its own slot, publishes the same `sku:qty` set as the Connect path, streams an update, and publishes every row again after its offset file is deleted.
7. Checks that every topic under `edp.` is named `edp.<domain>.<event>`. Debezium names a table's topic `<prefix>.<schema>.<table>`, so the two helper tables would land on `edp.inventory.dbz_signal` and `edp.inventory.dbz_heartbeat`; each has a RegexRouter to a name without underscores. `interval-only` has its own `topic.prefix` because two connectors with one prefix in one Connect worker collide on the same JMX metrics name, and the second waits almost two minutes before its slot starts.

## Tests and checks in detail

The Maven build runs the route against Kafka from Dev Services: mapping, tombstones, the order of changes to one row, the rows the embedded engine hands over, and a row the domain rejects. `verify.sh` starts Kafka, PostgreSQL, Kafka Connect and the LGTM stack, creates and seeds the legacy table, registers the connectors with `PUT /connectors/<name>/config` and prints `35/35 checks passed`. It needs `jq` on the host.

- Checks 1 to 6: the connector and its task run, the slot is active, the publication holds the legacy table, the signal table and the heartbeat table and not the busy one, and the snapshot published five `r` events and five documents that match the table.
- Checks 7 to 16: an update becomes a `u` event and a document with the domain's names. Under the default replica identity a delete's `before` carries the key and not the old quantity; after `ALTER TABLE ... REPLICA IDENTITY FULL` it carries the whole row. Debezium's own tombstone is on the change topic, the document topic gets exactly one for each delete, and the service logs no failure for the record it drops.
- Checks 17 and 18: three updates of one row in one transaction arrive as documents in commit order, with increasing `source.lsn`.
- Checks 19 to 24: a burst of about 55 MB goes into a table nobody captures. Both slots retain it. With the heartbeat query the slot's retained WAL falls to a few hundred bytes within one offset flush; with the interval alone, on a second connector, it is still 55 MB when the first has released it.
- Checks 25 to 29: one insert into the signal table re-emits each untouched row as an `r` event while an update made at once still arrives, and the latest document per SKU matches the table. Tempo has a trace for the service.
- Checks 30 to 34: the service is recreated with `CDC_MODE=embedded`. The engine holds its own slot, publishes the same `sku:qty` set as the Connect path, streams an update, and publishes every row again after its offset file is deleted.
- Check 35: every topic under `edp.` is named `edp.<domain>.<event>`, the helper-table and heartbeat topics included.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| Remove `.filter(body().isNotNull())` from `InventoryItemDocumentRoute` | Debezium's tombstone has no body to unmarshal, so each delete logs a failed change, and the documents are unchanged | 13 | - |
| Delete the `ALTER TABLE ... REPLICA IDENTITY FULL` line in `verify.sh` | The table stays on the default, and the delete's `before` carries the key and not the old quantity | 14 and 15 | - |
| Remove `heartbeat.action.query` from `connect/inventory-cdc.json` | The real connector now behaves like the witness, and the burst stays in the log | 22 | 55 MB after the wait |
| Update a row to a negative quantity | The domain's `InventoryItem` rejects it, the route logs the failed change and skips it, the SKU's document keeps its earlier value, and the next change on the topic is processed | none | - |
| Remove the line that deletes the offset file in `verify.sh` | The engine resumes from its stored offset and publishes nothing again | 34 | - |

## Layout

| Path | Purpose |
|---|---|
| `src/.../InventoryItemDocumentRoute` | Reads `edp.inventory.item-changes`, drops the null-value tombstone, maps the envelope |
| `src/.../EmbeddedCdcRoute` | The same capture with `debezium-postgres:`, started by `CDC_MODE=embedded` |
| `src/.../EmbeddedRowRoute` | Maps a row from the embedded engine to the same document, or a tombstone |
| `src/.../DocumentPublishRoute` | Publishes a document keyed by SKU, or a tombstone |
| `src/.../ItemDocuments` | Maps a legacy row to `InventoryItem` for both paths |
| `src/.../ChangeEnvelope`, `LegacyItemRow` | The part of the Debezium envelope the service reads |
| `connect/inventory-cdc.json` | The connector: slot, filtered publication, signal and heartbeat tables, one RegexRouter each to `edp.inventory.item-changes`, `edp.inventory.signals` and `edp.inventory.heartbeats` |
| `connect/interval-only.json` | The heartbeat witness: a slot on a quiet table with `heartbeat.interval.ms` and nothing else |
| `compose.yaml` | Topics, `inventory-service`, and Kafka, PostgreSQL, Connect and LGTM from `_infra` |
