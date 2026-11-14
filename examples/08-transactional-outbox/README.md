# 08 Transactional outbox

`order-service` stores every order and its `OrderPlaced` event in one PostgreSQL transaction. Debezium Connect reads the outbox insert from the write-ahead log and the Outbox Event Router publishes it to `edp.orders.placed`, keyed by order id. Two comparison routes show what a dual write loses (commit first, then publish) and invents (publish first, then commit).

Quarkus, Hibernate ORM with Panache, Flyway, the Debezium Outbox extension (`debezium-quarkus-outbox`, pinned in the aggregator pom because the Quarkus BOM does not manage it), Camel Kafka for the comparison routes, PostgreSQL, Debezium Connect, SmallRye Health and OpenTelemetry to the LGTM stack. Shared records and topic names come from `examples/domain-model`.

## Service

| Service | Port | Purpose |
|---|---|---|
| `order-service` | 8080 | `POST /orders` (outbox), `POST /orders/dual-write?mode=commit-first\|publish-first` (comparison). Health on `/q/health/ready` |

| Setting | Default | Effect |
|---|---|---|
| `REMOVE_AFTER_INSERT` | `true` | `quarkus.debezium-outbox.remove-after-insert`: delete the outbox row in the transaction that inserts it |
| `CRASH_AFTER_COMMIT_FOR` | empty | Demo code: comma-separated order ids for which the JVM halts right after the commit |
| `CONNECTOR_JSON` | `connect/order-outbox.json` | `verify.sh` only: the connector configuration it registers |

The connector configuration is `connect/order-outbox.json`; `verify.sh` registers it with `PUT /connectors/order-outbox/config`.

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # outbox row, rollback, duplicate, crash and dual-write comparisons against Dev Services
./verify.sh        # compose stack with Connect
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, and stops this one on exit unless `KEEP=1`.

## What verify.sh does

1. Starts the stack. `topics-init` creates `edp.orders.placed` with one partition, so the order of events across orders is visible. Registers the connector and checks it, its slot and its publication (checks 1 to 4).
2. Places `O-1001`: one record, keyed, with `id` and `eventType` headers and a JSON payload; the outbox table is empty; no default `outbox.event.*` topic (5 to 9).
3. Places `O-1001` again: 409, still one record and one row (10, 11).
4. Comparison, publish first: the duplicate fails to commit and the topic holds two records (12 to 14).
5. Restarts the service with the crash switch for `O-2001` and `O-2002`. Through the outbox, the order and exactly one event survive; through the commit-first route, the order survives and its event is lost (15 to 19).
6. Stops Connect, places five orders, starts Connect: all five arrive in commit order (20 to 24).
7. A trace in Tempo and logs in Loki (25, 26).
8. Control: the commit-first route with no crash publishes exactly one event (27).

## Tests and checks in detail

The Maven build runs the order service against PostgreSQL and Kafka from Dev Services: the order row and the outbox row are written together, a rolled-back order leaves neither, a duplicate is refused without a second row, the commit-first route loses an event when the crash switch fires, and the publish-first route invents one when its commit fails. `verify.sh` starts Kafka, PostgreSQL, Connect, the LGTM stack and the service, registers the connector and prints `27/27 checks passed`.

The checks read the effects, not the status codes:

- Placing `O-1001` leaves exactly one record on `edp.orders.placed`, keyed `O-1001`, with an `id` header, `eventType` `OrderPlaced` and the order as a JSON object. The order row exists, the outbox table is empty, and no `outbox.event.*` topic was created.
- A second `O-1001` is refused with 409, and after a wait there is still one record and one row.
- With the crash switch set, `O-2001` through the outbox never gets a response and the container exits with 137, as after a SIGKILL. After a restart its order row exists and exactly one record for it arrives. The same crash after the commit of the commit-first route leaves the order row for `O-2002` and no record for it after a wait. With no crash switch the same commit-first route publishes exactly one event, so the loss comes from the crash and not from the route.
- Publish first, then commit: a second order with the same id fails at commit, and the topic holds two records for one order.
- Connect is stopped and five orders `O-4001` to `O-4005` are placed. All five are accepted, nothing reaches the topic while Connect is down, and after it starts again all five arrive in the order they committed. The topic has one partition so that this order is visible; with several, only the order per key holds.
- A trace and logs for `order-service` reach Tempo and Loki.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `REMOVE_AFTER_INSERT=false ./verify.sh` | The outbox rows stay in the table. The records on the topic are the same, because the connector reads the insert, not the table | 8 | - |
| Remove the `route.by.field` and `route.topic.replacement` lines from `connect/order-outbox.json` | The records go to `outbox.event.Order`, so everything that reads `edp.orders.placed` finds nothing | 6, 7, 9, 11, 17 and 24 | 21 of 27 pass |
| Stop Kafka and place an order | Through the outbox the request returns 201 in about 10 ms, because the service never talks to Kafka, and the event arrives within seconds of the broker coming back. Through the commit-first route the order is committed, the request fails with a 500 after 60 seconds (the producer's blocking timeout) and no event is ever published. Through the publish-first route the request also fails after 60 seconds, and there is no order row either | - | 201 in about 10 ms; 500 after 60 seconds |
| Place one order, wait a minute, then run `docker kill edp-connect` during a burst of 200 orders and start it again | Every order arrives. A consumer that must not process an event twice has to drop the duplicates. The wait matters: Connect stores offsets once a minute, and a connector that has never stored one takes a new snapshot after the restart (of an empty outbox table) and streams from the current end of the WAL, past changes the slot still holds. Whether that loses events depends on timing | - | In one run the topic held 337 records for 201 orders: 136 `id` headers appeared twice, because the killed connector had stored no offset since the first order and read the log again from there. In one run killed before the first offset the burst lost 3 of 200 events; in two others every change had been read before the kill and nothing was lost |

## Layout

| Path | Purpose |
|---|---|
| `order-service/.../OrderService` | `place` writes the order and fires the event in one transaction; `save` writes the order alone |
| `order-service/.../OrderPlacedEvent` | The `ExportedEvent`: aggregate, type, payload and the `topic` column |
| `order-service/.../OrderResource` | `POST /orders`, the comparison endpoint and the 409 mapping |
| `order-service/.../DualWriteCommitFirstRoute`, `DualWritePublishFirstRoute` | The comparison routes |
| `order-service/.../CrashSwitch` | Demo code: halts the JVM after the commit |
| `order-service/src/main/resources/db/migration/V1__orders.sql` | The orders table and the outbox table |
| `connect/order-outbox.json` | The Debezium connector with the Outbox Event Router |
| `compose.yaml` | Topic, Connect and the service |
