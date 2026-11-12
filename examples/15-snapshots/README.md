# 15 Snapshots

`inventory-service` keeps the stock of a SKU as a stream of `StockReceived` and `StockReserved` events in PostgreSQL. A load folds the stream into an `InventoryItemAggregate`. A snapshot stores that state at one version, so a load reads the snapshot and replays only the events after it. A command writes a snapshot in its own transaction every N events. A Camel route that takes snapshots on a timer runs as a comparison.

Quarkus, Hibernate ORM with Panache (a projection for the replay), Flyway, Quarkus REST, SmallRye Fault Tolerance (`@Retry`), Camel Scheduler and SQL for the comparison route, PostgreSQL, SmallRye Health, Micrometer and OpenTelemetry to the LGTM stack. There is no Kafka: the pattern needs a database and nothing else. The ledger events are internal and reach no topic.

## Service

| Service | Port | Purpose |
|---|---|---|
| `inventory-service` | 8080 | `GET /items/{sku}`, `POST /items/{sku}/reservations`, `POST /items/{sku}/receipts`. Health on `/q/health/ready` |

`/demo/bench/{sku}` and `/demo/stale-take/{sku}`, and the query parameter `ignoreSnapshot=true` on `GET /items/{sku}`, are demo code for `verify.sh`. A real service would not expose them.

| Setting | Default | Effect |
|---|---|---|
| `SNAPSHOT_EVERY` | `100` | A command writes a snapshot once this many events follow the last one; `0` writes none |
| `SNAPSHOT_SCHEMA` | `1` | The schema version of the snapshot record: stamped on new snapshots, and a stored snapshot with another value is ignored |
| `SNAPSHOT_GUARD` | `true` | `false` removes the version guard from the upsert (demo) |
| `APP_PROFILE` | `prod` | `camel-snapshotter` stops the commands writing snapshots and starts the Camel route |
| `SNAPSHOTTER_DELAY_MS` | `1000` | Period of the Camel route |
| `INVENTORY_FETCH` | `projection` | `entities` replays through managed entities instead of a projection (demo) |
| `EXTRA_JAVA_OPTS` | empty | Appended to the JVM options, for example `-Xmx96m` |

## Run

```bash
mvn -B verify      # equivalence, policy, schema, guard, contention and the Camel route against Dev Services
./verify.sh        # compose stack
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`.

## What verify.sh does

1. Starts PostgreSQL, LGTM and the service, then loads `seed.sql`: three streams of 1,000, 10,000 and 100,000 events, no snapshots. The quantity of the longest is computed in SQL from the payloads and compared with what the service answers.
2. Reserves on `SKU-1503`: the first command writes a snapshot, a load then replays 0 events, the next N-1 commands leave the snapshot alone and a load replays N-1, the Nth moves it.
3. Compares every stream with and without its snapshot, and checks that a stream nobody commanded has none.
4. Times 21 full and 21 snapshot loads inside the service on each stream, after 5 warm-up loads, and requires the snapshot load on 100,000 events to be at least 20 times faster. The medians, the cost per event, the size of the state and the `EXPLAIN` row counts are printed as NOTE lines.
5. Deletes every snapshot (the state is the same, only the cost changes), outdates one with `schema_version = 0` (ignored and logged), and refuses a reservation larger than the stock (409, no event, snapshot untouched).
6. Sends 200 reservations from 8 parallel clients to `SKU-1502` and checks the quantity and the version sequence, then writes a stale snapshot through the demo endpoint and checks that the guard refuses it.
7. Restarts the service with `camel-snapshotter`, runs a burst of 10N reservations while sampling the snapshot every 0.2 seconds: only the route writes, the version never goes down and the snapshot catches up after the burst.
8. A trace in Tempo, logs in Loki and the rehydrate timer and ignored-snapshot counter in Prometheus.

## Layout

| Path | Purpose |
|---|---|
| `inventory-service/.../InventoryItemAggregate` | The root: `receive`, `reserve`, `apply`, `snapshot()` and `from(snapshot, version, tail)` |
| `inventory-service/.../StockLedger` | `load` (snapshot, then the tail through a Panache projection) and `append` (the unique version decides a race) |
| `inventory-service/.../SnapshotStore` | The native upsert with the version guard |
| `inventory-service/.../ReservationTransaction`, `ReservationCommands` | The transactional command with the inline policy, and the `@Retry` around it |
| `inventory-service/.../SnapshotterRoute`, `Snapshots` | The Camel route and the transactional `take` it calls |
| `inventory-service/.../DemoResource` | Demo code: the in-service benchmark and the stale snapshot write |
| `inventory-service/src/main/resources/db/migration/V1__inventory.sql` | The append-only `events` table and the `snapshots` table |
| `seed.sql` | The three bulk-loaded streams |
| `compose.yaml` | The service, PostgreSQL and LGTM |
