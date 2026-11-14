# 17 Aggregate replay or CQRS projection

`order-service` keeps orders as streams of `OrderPlaced`, `OrderPaid`, `OrderShipped`, `OrderDelivered` and `OrderCancelled` events in PostgreSQL, the event store of chapter 14. One question spans every order: how many orders each carrier shipped each UTC day. `ReplayReport` answers it by folding every `OrderAggregate` on request. `CarrierDailyProjector` answers it from `carrier_daily_shipments`, a table it builds from the whole history and keeps current, with a checkpoint that only advances past transactions that can no longer commit.

Quarkus, Hibernate ORM with Panache (a projection for the replay), Flyway, Quarkus REST, Quarkus Scheduler, PostgreSQL, SmallRye Health, Micrometer and OpenTelemetry to the LGTM stack. There is no Kafka and no Camel: the decision needs a database and nothing else.

## Service

| Service | Port | Purpose |
|---|---|---|
| `order-service` | 8080 | `POST /orders/{id}`, `/orders/{id}/payment`, `/orders/{id}/shipment?carrier=`, `/orders/{id}/cancellation`; `GET /reports/shipments-per-carrier?source=replay\|projection`. Health on `/q/health/ready` |

`/demo/bench` and `/demo/projection/rebuild` are demo code for `verify.sh`. A real service would not expose them.

| Setting | Default | Effect |
|---|---|---|
| `PROJECTION_POLL` | `1s` | Period of the projector's tick |
| `PROJECTION_BATCH` | `1000` | Events per batch |
| `PROJECTION_WATERMARK` | `on` | `off` reads by position alone and skips a late commit (demo) |
| `DEMO_HOLD_FOR` | empty | `<orderId>:<Command>` entries whose transaction stays open after the append (demo) |
| `DEMO_HOLD_MS` | `10000` | How long the held transaction stays open (demo) |

## Run

```bash
mvn -B verify      # replay, projection, rollback, concurrency, commit-order gap against Dev Services
./verify.sh        # compose stack
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`.

## What verify.sh does

1. Starts PostgreSQL, LGTM and the service and checks that the store starts empty, with both append-only triggers.
2. Loads `seed.sql` three times, cumulatively, to 1,000, 10,000 and 100,000 orders (3.6 events per order). At each size it compares the replay with a SQL oracle, waits for the projection to reach `lagEvents=0`, compares the projection with the replay, and prints the bench: replay and projection read medians (3 warm-up and 11 timed, alternating), the rebuild time, and the sizes of the events and the projection.
3. At 100,000 orders requires a projection read at least 250 times faster than the replay, and drops the projection to show it catch up from fewer shipments than the replay to equal.
4. Ships an order on the large store and requires it in the next replay at once and in the projection within 2 seconds, then times 50 commands.
5. Holds the transaction of `ShipOrder O-1701` open after its append, commits `O-1702`, and requires the projection to count neither until the hold ends, and both after it.
6. Restarts the service and requires the projection to resume from its checkpoint with nothing counted twice.
7. A trace in Tempo, logs in Loki and `projection_lag_events` in Prometheus.

## Tests and checks in detail

The Maven build runs 16 tests against PostgreSQL from Dev Services: a replay against a history worked out by hand, UTC days, a projection equal to the replay after catch-up and in batches of three, a failed batch that rolls back its rows and its checkpoint, four overlapping ticks that count each event once, a rebuild, the timer, the commands and their refusals, a replay of 100,000 orders in a 256 MB heap, and the commit-order gap on two connections, with the watermark and with it off. `verify.sh` starts PostgreSQL, the LGTM stack and the service, loads the three sizes, and prints `22/22 checks passed`.

The checks read the effects:

- At each size the replay equals the SQL oracle, the projection reaches `lagEvents=0` within 10 seconds and equals the replay. NOTE lines print the medians, the rebuild time and the sizes.
- At 100,000 orders a projection read is at least 250 times faster than a replay (1,277 times by the medians of three runs). A rebuild starts with work left and a count below the replay, then catches up and equals it.
- A shipment is in the next replay at once and in the projection within 2 seconds. The median over 50 commands is 994 ms.
- `ShipOrder` for `O-1701` is held after its append with its transaction open. `O-1702` ships and commits, and the projection counts neither: `lagEvents` is at least 1, the total has not moved, and a replay already counts `O-1702`. When the hold ends the projection equals the replay with both counted.
- After a restart the projection resumes from its checkpoint and counts nothing twice. It stays keyed by carrier and day, so it holds at most 60 rows at every size.
- Prometheus holds `projection_lag_events`, and Tempo and Loki hold a trace and logs of `order-service`.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `PROJECTION_WATERMARK=off ./verify.sh` (demo code) | The batch reads by position alone. The projection moves past `O-1701`, counts `O-1702` and never counts `O-1701`: it counted `O-1702` while `O-1701` was still open, and it stays one order short after the hold and after a restart. No other check fails | 16, 17 and 18 | - |
| `PROJECTION_POLL=5s ./verify.sh` | A command reaches the projection within a tick, now 5 seconds. The check sends its command right after a tick, so it fails every time and passes every time at the default | 14 (no other) | freshness median 4,994 ms |
| `POST /demo/projection/rebuild` (demo code) with the stack up | Empties the projection and rewinds the checkpoint. The projection report then shows a count below the replay and a `lagEvents` that falls to 0 | - | - |

## Layout

| Path | Purpose |
|---|---|
| `order-service/.../OrderAggregate`, `OrderEvent`, `Command`, `OrderCommands` | The aggregate of chapter 14 with smaller payloads and an `OrderDelivered` event, and its transactional command |
| `order-service/.../EventStore` | `load`, `append` and `replayStream`, a Panache projection that folds one stream at a time |
| `order-service/.../ReplayReport` | The replay: every stream, one aggregate at a time |
| `order-service/.../CarrierDailyProjector`, `ProjectionBatch` | The timer and the transactional batch with the locked checkpoint |
| `order-service/.../ProjectionWatermark` | The read below the oldest running transaction, and the `off` switch (demo) |
| `order-service/.../CarrierDailyStore`, `CarrierDailyShipments`, `ProjectionCheckpoint` | The read model and its checkpoint |
| `order-service/.../DemoHold`, `DemoResource` | Demo code: the held transaction, the bench and the rebuild |
| `order-service/src/main/resources/db/migration/V1__orders.sql` | The append-only `events` table with `tx_id`, the projection and the checkpoint |
| `seed.sql` | The bulk-loaded orders `:lo` to `:hi` |
| `compose.yaml` | The service, PostgreSQL and LGTM |
