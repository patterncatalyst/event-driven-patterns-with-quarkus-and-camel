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

## Tests and checks in detail

The Maven build runs the service against PostgreSQL and Kafka from Dev Services: 27 tests cover every aggregate rule without a database, a stale append that adds no row, both outcomes of the race, a stale `If-Match`, a failure after the append that leaves neither row, Kafka commands, the Camel route with and without redelivery, and the blind re-append as the negative witness. `verify.sh` starts Kafka, PostgreSQL, Connect, the LGTM stack and the service, and prints `33/33 checks passed`.

The checks read the effects:

- The schema holds no table of order state. `O-1401` is one row and one record on `edp.orders.placed`. The same `PaymentProcessed` produced twice gives one `OrderPaid`, and no `OrderPaid` is on any `edp.orders` topic.
- Race 1: a cancellation for `O-1402` is held after it decided at version 2, and a shipment is appended as version 3 while the request is open. The cancellation answers 409 `SHIPPED to CANCELLED` after one logged conflict, the stream stays at three events and nothing is on `edp.orders.cancelled`. Every history in the table obeys the lifecycle.
- Race 2: a cancellation for `O-1403` is held at version 1 and a payment is appended as version 2. The cancellation returns 200 with `ETag` `"3"` and one record reaches `edp.orders.cancelled`.
- A cancellation of the paid `O-1404` with `If-Match` `"1"` returns 412 and runs no command. With `"2"` it returns 200.
- Race 3: a payment for `O-1405` read from Kafka is held at version 1 while a REST cancellation wins. The payment is decided again, refused with `CANCELLED to PAID` after one conflict, and acknowledged.
- The service restarts with `camel-commands`, the group `order-payments-camel` is reset to the earliest offset, and every payment is read again with no change to any earlier stream. Race 3 repeats for `O-1406`: the route redelivers the payment once and acknowledges the refusal.
- A trace, logs and `order_command_conflicts_total` for `order-service` reach Tempo, Loki and Prometheus.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `RETRY_MODE=reappend ./verify.sh` | Race 1 ends with `OrderCancelled` as version 4 after `OrderShipped`, and a cancelled record is published for an order that shipped, because the cancellation that lost the race is appended again at the new head without being decided | 14, 15, 17 and 30 | - |
| `RETRY_MAX=0 ./verify.sh` | A conflict is reported instead of decided again, so race 1 has no refusal reason, race 2 is rejected and race 3 does not decide the payment again | 14, 19 to 21, 26 and 30 | - |
| Delete the `CONSTRAINT events_stream_version` line and the comma before it from `V1__orders.sql` | Nothing detects the race: no conflict is logged and the history of `O-1402` is no longer a legal lifecycle | 14 to 17, 19, 20, 26 and 29 to 31 | - |
| `CAMEL_REDELIVERIES=0 ./verify.sh` | The route drops the conflicting payment after one attempt and commits its offset. `O-1406` ends in the same stream, since the cancellation had already won, so only the redelivery and the refusal are missing | 29 | - |

