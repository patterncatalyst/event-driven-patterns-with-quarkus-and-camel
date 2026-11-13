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

## Tests and checks in detail

The Maven build runs the service against PostgreSQL and Kafka from Dev Services: 7 tests cover a truncated record that reaches the dead letter topic with its bytes, its source topic and its source offset, an invalid record attempted once, a locked row retried until it is free, `fail` stopping the channel, and the Camel route dead-lettering with the original body. `verify.sh` starts Kafka, PostgreSQL, the LGTM stack and the service, and prints `31/31 checks passed`.

The checks read the effects:

- With `SKU-14` locked for twelve seconds and the six records produced in one batch, `O-7004` is attempted more than once while the lock is held, and `SKU-14` and `SKU-15` both still hold 100: `O-7005` waits. After the lock is released `SKU-14` and `SKU-15` hold 99, and `SKU-11` holds 99 for `O-7001`.
- The dead letter topic holds `O-7002` and `O-7006` with exactly the bytes that were sent. `O-7002` names `edp.orders.placed` and the offset it had there. `O-7003` carries its original bytes, source topic and offset, names the quantity rule in its reason and was attempted once. Three records are on the topic, none a valid order.
- The consumer group has no lag, so the offsets of the dead-lettered records were committed.
- The service restarts with the `camel` profile, `SKU-14` is locked again and the same six records are produced. The route redelivers `O-7004` while the lock is held, the stock ends as before, and the dead letter topic gains the same three records.
- A trace and logs for `inventory-service` reach Tempo and Loki, and Prometheus holds the records consumed by its Kafka consumer.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `FAILURE_STRATEGY=fail ./verify.sh` | The channel stops at `O-7002`, whose offset is never committed, `SKU-15` stays at 100 and no dead letter is written. Readiness answers 503. The lock preconditions pass | 4, 6, 7, 9 to 14, 16 and 24 to 28 | group stays at offset 1 with 5 records of lag |
| `RETRY_MAX=0 ./verify.sh` | In both phases `O-7004` is attempted once, dead-lettered, and `SKU-14` stays at 100, while `O-7005` is reserved at once. The lock preconditions pass, so the retry limit is the cause | 4 to 6, 14, 15, 19 to 21 and 26 | - |
| `RETRY_MAX=2 ./verify.sh` | The lock outlasts three attempts, so `O-7004` is dead-lettered after the last one: a failure that was transient but did not clear within the limit. The end matches `RETRY_MAX=0`, in both phases | 6, 14, 15, 21 and 26 | 3 attempts instead of one |
| `CAMEL_DEAD_LETTER_CHANNEL=false ./verify.sh` | The SmallRye phase is unchanged; in the Camel phase the default error handler gives up on `O-7002`, `O-7003` and `O-7006` after one attempt and commits their offsets, and the lock timeout on `O-7004` is dropped the same way: `SKU-14` stays at 100 while `O-7005` is reserved. The group ends with no lag, because the offsets of the records it gave up on were committed. Nothing is dead-lettered in that phase | 19 to 21 and 24 to 26 | - |
| Add `CAMEL_BREAK_ON_FIRST_ERROR=true` to that run | In the Camel phase the failed record is polled again, and the valid record before it is applied again on every pass. `SKU-14` and `SKU-15` stay at 100 and the group commits no offset | 19 and 21 to 26 | `O-7001` applied 100 times and `SKU-11` fell from 100 to 0 in under a minute; lag 6 |
| `HOLD=20 MAX_POLL_INTERVAL_MS=10000 ./verify.sh` | The SmallRye consumer is not rebalanced and `SKU-14` ends at 99. In the Camel phase the consumer misses its poll deadline, the group rebalances and the batch is delivered again: `SKU-11`, `SKU-14` and `SKU-15` all end at 98, each reserved twice | 21 to 26 | SmallRye attempts `O-7004` seven times during the 20 second hold |

## Layout

| Path | Purpose |
|---|---|
| `inventory-service/.../OrderPlacedConsumer` | The SmallRye consumer: bytes in, `@Retry` with the abort list |
| `inventory-service/.../ReservationService` | The `@Transactional` reservation, with the row lock and its 2 second timeout |
| `inventory-service/.../DeadLetterRoute` | The Camel route with `deadLetterChannel` and `onException` |
| `inventory-service/.../InventoryItemEntity`, `OrderPlaced` | The Panache entity and the event payload |
| `inventory-service/src/main/resources/db/migration/V1__inventory.sql` | The stock table and five seeded SKUs |
| `compose.yaml` | Topics and the service |
