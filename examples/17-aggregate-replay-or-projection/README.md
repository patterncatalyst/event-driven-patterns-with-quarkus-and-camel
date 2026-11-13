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
