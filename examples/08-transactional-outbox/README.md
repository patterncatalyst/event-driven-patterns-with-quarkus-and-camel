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
