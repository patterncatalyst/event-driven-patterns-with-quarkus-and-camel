# 14 Aggregate root and the event store

`order-service` keeps no order table. An order is the stream of its own events in an append-only `orders.events` table, and `OrderAggregate` rebuilds it from that stream, decides each command against it and returns the events that follow. Commands arrive from REST (place, cancel) and from Kafka (`PaymentProcessed` marks an order paid, `ShipmentScheduled` marks it shipped). The expected version of an append is the version that was loaded, so a command that loses a race with another writer is decided again, or reported to the caller who decided it. `OrderPlaced` and `OrderCancelled` leave for Kafka through the transactional outbox in the same transaction as the append; `OrderPaid` and `OrderShipped` stay in the stream. The same payments also run through a Camel route, as a comparison.

Quarkus, Hibernate ORM with Panache, Flyway, Quarkus REST, SmallRye Reactive Messaging with SmallRye Fault Tolerance (`@Retry`), the Debezium outbox extension, Camel Kafka for the comparison route, PostgreSQL, Kafka Connect, SmallRye Health and OpenTelemetry to the LGTM stack. Topic names come from `Topics` in `examples/domain-model`.

## Service

| Service | Port | Purpose |
|---|---|---|
| `order-service` | 8080 | `POST /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancellation`. Health on `/q/health/ready` |

| Setting | Default | Effect |
|---|---|---|
| `COMMAND_PROFILE` | `prod` | `camel-commands` turns the SmallRye payments channel off and starts the Camel route (group `order-payments-camel`) |
| `RETRY_MAX` | `3` | Times a command that lost a version race is decided again; `0` reports the conflict at once |
| `CAMEL_REDELIVERIES` | `3` | Redeliveries of a conflicting payment in the Camel route; `0` drops the command after one attempt |
| `RETRY_MODE` | `redecide` | Demo code. `reappend` appends a cancellation decided against an old version at the new head without deciding it again (the negative witness) |
| `DEMO_PAUSE_FOR` | none | Demo code. `<orderId>:<Command>` entries, comma separated; the command waits `DEMO_PAUSE_MS` between deciding and appending |
| `DEMO_PAUSE_MS` | `6000` (`8000` in `verify.sh`) | Length of that pause |

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # aggregate rules, races, If-Match, outbox, Kafka commands and the Camel route against Dev Services
./verify.sh        # compose stack
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`.

## What verify.sh does

1. Starts the stack, registers the `order-outbox` connector (slot and publication `edp_order_aggregate_outbox`) and checks that the schema holds the events, the outbox and the migration history, and no table of order state.
2. Places `O-1401`, reads its one row and its record on `edp.orders.placed`, and sends a second `POST` that is refused.
3. Produces the same `PaymentProcessed` twice in one call. The stream gets one `OrderPaid`, and no `OrderPaid` reaches a topic.
4. Race 1: `O-1402` is paid and a cancellation is paused after it decided at version 2. A shipment is appended as version 3 while the request is open. The cancellation is decided again and refused with 409, and nothing reaches `edp.orders.cancelled`.
5. Race 2: `O-1403` is placed and a cancellation is paused at version 1. A payment is appended as version 2. The retry succeeds at version 3 and one record reaches `edp.orders.cancelled`.
6. `O-1404`: a cancellation with a stale `If-Match` is answered 412 and runs no command; with the current tag it is accepted.
7. Race 3: a `PaymentProcessed` for `O-1405` is paused after it decided at version 1 and a REST cancellation wins. The payment is decided again and refused, and the record is acknowledged.
8. Restarts the service with `COMMAND_PROFILE=camel-commands`, resets the group `order-payments-camel` to the earliest offset while it is inactive and reads every payment again: no earlier stream changes. Race 3 runs again for `O-1406` through the route, which redelivers the payment once.
9. A trace in Tempo, logs in Loki and `order_command_conflicts_total` in Prometheus.

## Layout

| Path | Purpose |
|---|---|
| `order-service/.../OrderAggregate`, `Command`, `OrderEvent`, `CommandRefused` | The root: rehydrate, decide, apply; the commands and events as sealed records |
| `order-service/.../EventStore`, `StoredEvent`, `ConcurrencyConflict` | The two store operations over Panache; the unique constraint becomes the conflict |
| `order-service/.../OrderTransaction` | One command, one transaction: load, decide, append, outbox row |
| `order-service/.../OrderCommands` | The `@Retry` around the transactional bean |
| `order-service/.../OrderExports` | The two public events written to the outbox, with their topic |
| `order-service/.../OrderResource` | REST, `ETag`, `If-Match` and the 409 and 412 answers |
| `order-service/.../PaymentConsumer`, `ShipmentConsumer` | The SmallRye channels that turn records into commands |
| `order-service/.../PaymentCommandRoute` | The Camel route with redelivery on a conflict (comparison) |
| `order-service/.../DemoPause`, `DemoReappend` | Demo code for the experiments; nothing in a real service waits or re-appends |
| `order-service/src/main/resources/db/migration/V1__orders.sql` | The events table with its unique `(stream_id, version)`, the append-only triggers and the outbox table |
| `connect/order-outbox.json` | The outbox connector and the Event Router |
| `compose.yaml` | Topics and the service |

## Experiments

Each one changes a single thing and names the checks that fail. The run records are in `_plans/verification-log.md`.

| Change | What it shows | Checks that fail |
|---|---|---|
| `RETRY_MODE=reappend ./verify.sh` | Race 1 ends with `OrderCancelled` at version 4 after `OrderShipped`, and a cancelled record is published for it | 14, 15, 17 and 30 |
| `RETRY_MAX=0 ./verify.sh` | A conflict is reported instead of decided again, so race 1 has no refusal reason, race 2 is rejected and race 3 does not decide the payment again | 14, 19 to 21, 26 and 30 |
| Delete the `CONSTRAINT events_stream_version` line and the comma before it from `V1__orders.sql` | Nothing detects the race: no conflict is logged and the history of `O-1402` is no longer a legal lifecycle | 14 to 17, 19, 20, 26 and 29 to 31 |
| `CAMEL_REDELIVERIES=0 ./verify.sh` | The Camel route drops the conflicting payment after one attempt and commits its offset | 29 |
