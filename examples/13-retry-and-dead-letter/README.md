# 13 Retry and dead letter

`inventory-service` reads `OrderPlaced` records from `edp.orders.placed` and takes the ordered quantity out of stock. A transient failure (a locked row) is retried in place; a record that can never succeed (unparseable, or a quantity of 0) goes to `edp.orders.placed-dlq` with its original bytes. The same inputs also run through a Camel route with a dead letter channel, as a comparison.

Quarkus, Hibernate ORM with Panache, Flyway, SmallRye Reactive Messaging with SmallRye Fault Tolerance (`@Retry`), Camel Kafka for the comparison route, PostgreSQL, SmallRye Health and OpenTelemetry to the LGTM stack. Topic names come from `Topics` in `examples/domain-model`.

## Service

| Service | Port | Purpose |
|---|---|---|
| `inventory-service` | 8080 | Consumer only. Health on `/q/health/ready` |

| Setting | Default | Effect |
|---|---|---|
| `CONSUMER_PROFILE` | `prod` | `camel` turns the SmallRye channel off and starts the Camel route |
| `FAILURE_STRATEGY` | `dead-letter-queue` | SmallRye `failure-strategy` of the channel: `dead-letter-queue`, `fail` or `ignore` |
| `RETRY_MAX` | `8` | Retries of a transient failure, SmallRye and Camel; `0` dead-letters at the first failure |
| `MAX_POLL_INTERVAL_MS` | `300000` | `max.poll.interval.ms` of the SmallRye channel and `maxPollIntervalMs` of the route |
| `CAMEL_DEAD_LETTER_CHANNEL` | `true` | `false` leaves Camel's default error handler on the route |
| `CAMEL_BREAK_ON_FIRST_ERROR` | `false` | `true` sets `breakOnFirstError` on the Kafka endpoint |
| `HOLD` | `12` | `verify.sh` only: seconds the background session holds the row lock |

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # dead letters, retry, fail strategy and the Camel route against Dev Services
./verify.sh        # compose stack
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`.

## What verify.sh does

1. Starts the stack. `topics-init` creates both topics with one partition, so the record behind a retried one has to wait.
2. Holds a row lock on `SKU-14` in a background session for 12 seconds and produces six records in one call: `O-7001` (valid), `O-7002` (truncated JSON), `O-7006` (not JSON), `O-7003` (quantity 0), `O-7004` (valid, on the locked SKU) and `O-7005` (valid). While the lock is held, `O-7004` is retried and `O-7005` has not been reserved. After it is released both are reserved.
3. Reads the dead letter topic: the original bytes of `O-7002` and `O-7006`, the source topic and offset of `O-7002`, the reason of `O-7003`, exactly 3 records, and no lag on the group.
4. Resets the stock, restarts the service with `CONSUMER_PROFILE=camel`, locks `SKU-14` again and produces the same six records. The route redelivers `O-7004`, and the dead letter topic gains the same three records, 6 in all.
5. Reads the `O-7003` dead letter again from the copy taken before the Camel phase: its original bytes, its source topic and its offset.
6. A trace in Tempo, logs in Loki and the records consumed by the Kafka consumer in Prometheus.

## Layout

| Path | Purpose |
|---|---|
| `inventory-service/.../OrderPlacedConsumer` | The SmallRye consumer: bytes in, `@Retry` with the abort list |
| `inventory-service/.../ReservationService` | The `@Transactional` reservation, with the row lock and its 2 second timeout |
| `inventory-service/.../DeadLetterRoute` | The Camel route with `deadLetterChannel` and `onException` |
| `inventory-service/.../InventoryItemEntity`, `OrderPlaced` | The Panache entity and the event payload |
| `inventory-service/src/main/resources/db/migration/V1__inventory.sql` | The stock table and five seeded SKUs |
| `compose.yaml` | Topics and the service |
