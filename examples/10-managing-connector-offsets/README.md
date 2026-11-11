# 10 Managing connector offsets

A Debezium PostgreSQL connector, `inventory-cdc`, tails the legacy `inventory.inventory_item` table and publishes its changes to `edp.inventory.item-changes`. `inventory-service` reads that topic with a Camel route (`ChangeLogRoute`) and writes every change event to `inventory.item_change` (Panache entity `ItemChange`: op, sku, qty, lsn, received time). `verify.sh` then stops, alters, resets and breaks the connector through the Kafka Connect REST API and the PostgreSQL slot, and reads the effect from `item_change` and `pg_replication_slots`.

Quarkus, Camel Kafka, Hibernate ORM with Panache, Flyway, PostgreSQL, SmallRye Health and OpenTelemetry to the LGTM stack. The topic name comes from `examples/domain-model`.

## Services

| Service | Port | Purpose |
|---|---|---|
| `inventory-service` | 8080 | The change log. Health on `/q/health/ready` |
| `connect` | 8083 | Debezium Connect, from `examples/_infra`. REST API for connectors and offsets |
| `topics-init` | none | Creates `edp.inventory.item-changes` with one partition, then exits |

| Setting | Default | Effect |
|---|---|---|
| `OFFSET_FLUSH_INTERVAL_MS` | 1000 | Connect's `offset.flush.interval.ms`. Connect's own default is 60000, which makes a GET on the offsets lag the connector by up to a minute |

The connector configuration is in `connect/`: `inventory-cdc.json` (`snapshot.mode=initial`) and `inventory-cdc-no-data.json` (`snapshot.mode=no_data`). `verify.sh` creates the legacy tables, because they belong to the legacy system: `inventory.inventory_item` with five SKUs, `inventory.legacy_audit` (a heavy write load the connector does not capture) and the signal table `inventory.dbz_signal`.

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # change-log route against Kafka and PostgreSQL from Dev Services
./verify.sh        # compose stack, connector, every offset operation
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, and stops this one on exit unless `KEEP=1`. It takes several minutes: the lost-slot phase waits a fixed 60 seconds to show that no task ever appears.

## What verify.sh does

1. Starts the stack, creates the legacy tables, registers `inventory-cdc`. The snapshot delivers five `r` rows; GET offsets returns the `{"server":"edp"}` partition and an `lsn` that equals the slot's `confirmed_flush_lsn`; the offsets topic holds the same key.
2. A PATCH or DELETE on the running connector is refused. After `PUT /stop` the slot is inactive, 20,000 uncaptured rows and 100 updates keep more than 15 MB of WAL, and no row reaches `item_change`. After `PUT /resume` all 100 updates arrive and the retained WAL falls below 1 MB.
3. Forward alter: ten updates made while stopped are skipped when the offset is set to the current WAL position, and the next update arrives. Backward alter: the offset is set to the snapshot position and the two new changes plus one repeat of the last change before the stop arrive.
4. Reset: `DELETE` on the offsets, then exactly five `r` rows with the current values and no `u` row.
5. Dropped slot with offsets kept: the task is `FAILED` with the "no longer available on the server" trace, and a restart with `onlyFailed` leaves it failed. Stop, delete offsets and resume recover it.
6. Lost slot (`max_slot_wal_keep_size` of 1 MB, a WAL switch and a checkpoint): `wal_status` is `lost`, the connector stays `RUNNING` with no task for at least 60 seconds, delivers nothing, and a restart changes nothing. Drop the slot, delete the offsets and resume to recover.
7. An incremental snapshot signal re-reads the five rows while the connector keeps running. Tempo has a trace for `inventory-service`.
8. Every topic under `edp.` is named `edp.<domain>.<event>`; the signal table's topic is routed to `edp.inventory.signals` (check 40).

## Layout

| Path | Purpose |
|---|---|
| `inventory-service/.../ChangeLogRoute` | Camel route: topic, JSON envelope, recorder |
| `inventory-service/.../ChangeEnvelope` | The parts of the Debezium envelope that are kept |
| `inventory-service/.../ItemChangeRecorder` | Writes one `ItemChange` per envelope |
| `inventory-service/.../ItemChange` | Panache entity for `inventory.item_change` |
| `connect/` | Connector configurations registered with `PUT /connectors/<name>/config` |
| `compose.yaml` | Connect flush interval, topic, `inventory-service` |
