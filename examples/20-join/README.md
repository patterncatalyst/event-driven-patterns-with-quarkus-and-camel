# 20 Join

`shipping-service` may book a carrier only when the stock is reserved and the payment is taken. The two facts arrive on `edp.inventory.reserved` and `edp.payments.processed`, keyed by order id, in either order. The service joins them two ways on the same input: a Kafka Streams windowed left join by event time, and a Camel aggregator by arrival time. Each form has its own consumer group and its own output topic, so one input feeds both and every difference is visible. A real service would run one of them. Output records are `ReadyToShip(orderId, reservedAt, paidAt)` or `PaymentOverdue(orderId, reservedAt)`.

Quarkus, Kafka Streams, Camel Kafka and Camel SQL with the PostgreSQL aggregation repository, Flyway, PostgreSQL, Kafka, SmallRye Health and OpenTelemetry to the LGTM stack. Topic names come from `Topics` in `examples/domain-model`.

## Service

| Service | Port | Purpose |
|---|---|---|
| `shipping-service` | 8080 | The two joins. Health on `/q/health/ready` and `/q/health/live` |

| Setting | Default | Effect |
|---|---|---|
| `AGGREGATION_REPOSITORY` | `postgres` | Demo code. `memory` keeps the Camel aggregator's open groups in the JVM, so a recreate loses them |
| `READINESS_TIMEOUT_MS` | `10000` | Demo code. How long a Camel group waits for its other fact, counted on the service's clock |

The Streams window is 30 minutes with 5 minutes of grace. `kafka-streams.commit.interval.ms=1000` and `kafka-streams.statestore.cache.max.bytes=0` make a result leave within about a second. The session timeout of both consumers is 10 s and the heartbeat 3 s. A recreate of the service is live again after about 13 s (three recreates, 13.45 to 13.47 s).

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # the join in event time, the aggregator on Dev Services, co-partitioning, the in-memory repository
./verify.sh        # compose stack
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`. It ends with `24/24 checks passed`.

## What verify.sh does

1. Starts the stack and checks that Kafka Streams is RUNNING, that both groups own the partition of each input topic, that the four topics have one partition each and that the aggregation tables are empty.
2. Happy path: `O-2001` (reservation, then payment) and `O-2002` (payment first) give `ReadyToShip` on both outputs.
3. Bench: 200 orders, all reservations in one producer call and then all payments, event times inside the window. Both outputs hold 200 `ReadyToShip` and no `PaymentOverdue`.
4. `O-2003`: event times two minutes apart. The reservation is produced first and the payment only after the Camel output holds `PaymentOverdue`, so the records are further apart than the timeout. The Streams output holds `ReadyToShip`. The late payment on the Camel path leaves no open group, and once its timeout has passed the Camel output holds exactly one record for the order, the `PaymentOverdue` of the reservation.
5. `O-2004`: event times 90 minutes apart, produced back to back. The Camel output holds `ReadyToShip`; the Streams output holds `PaymentOverdue`, because the payment's own event time moves stream time past the window, and never `ReadyToShip`.
6. `O-2005`, reserved and never paid: Camel emits `PaymentOverdue` after the timeout and not before. Streams stays silent for 15 s because no record moves stream time, and emits `PaymentOverdue` after a later record does.
7. Recreate: the service is recreated with a 60 s timeout, `O-2006` is reserved (an open row in `shipping.readiness`, and the join store's changelog grows), the service is recreated again with an empty state directory, and the payment arrives. Both outputs hold `ReadyToShip`, and the log shows the join store and the Camel timeouts restored.
8. Prometheus holds `order_readiness_total` for the Camel path, a trace is in Tempo and logs are in Loki.

Event times are computed from the clock: the label 09:00 of the scenario is a 15-minute boundary 20 hours before now, and the last label (14:01) is 15 hours before now. Kafka Streams forwards event time as the timestamp of every record it writes, so a date in the future would stop the instance (the broker refuses a timestamp more than an hour ahead of its clock). The times are computed relative to now, so a run at any hour of the day uses the same offsets. `BASE_AGE_HOURS=44 ./verify.sh` (demo setting) makes the event times 44 hours old; all 24 checks pass in that run.

## Layout

| Path | Purpose |
|---|---|
| `shipping-service/.../InventoryReserved`, `PaymentProcessed`, `Readiness`, `HasEventTime` | The input events, the output record and the interface that carries the event time |
| `shipping-service/.../PayloadTimestampExtractor` | Event time from `occurredAt`, falling back to the record timestamp |
| `shipping-service/.../ReadinessTopology` | The Kafka Streams left join |
| `shipping-service/.../ReadinessRoute` | The Camel aggregator: key correlation, completion predicate and timeout |
| `shipping-service/.../ReadinessRepository` | The PostgreSQL aggregation repository; the in-memory one is demo code |
| `shipping-service/.../ReadinessMetrics` | The `order.readiness` counter by path and outcome |
| `shipping-service/src/main/resources/db/migration/V1__readiness.sql` | The two tables the repository uses |
| `compose.yaml` | Topics and the service |

## Tests and checks in detail

The Maven build runs 18 tests. They cover the join in event time with the topology test driver (a match in either order, a payment 31 minutes later, an unmatched reservation leaving only after the window and the grace, the wall clock not closing a window, an out-of-order payment inside the grace, a late payment after `PaymentOverdue`, and the two crafted pairs), the timestamp extractor, the Camel aggregator on Dev Services Kafka and PostgreSQL (both facts in either order, the timeout, a duplicate reservation, the open row, a lone payment), the count-of-two aggregator that completes on a duplicate reservation, the in-memory repository leaving no row, and the mismatched partition counts, where Streams matches some orders and Camel all of them. `verify.sh` prints `24/24 checks passed`. The timing witnesses (the `O-2003` and `O-2005` Camel checks, and the late payment that emits nothing) were green in 15 of 15 consecutive runs from fresh containers. That loop ran before the mapping of the scenario's event times to the clock changed, which touched event times only; `h20-run1`, `h20-run2` and `f8-20-run`, on the final script, also passed 24/24. The mismatched-partition test first reads the partition counts from the broker (3 for reservations, 2 for payments) and logs the Streams tasks: two tasks pair a reservations partition with the payments partition of the same number and one holds a reservations partition alone. The instance runs and logs no co-partitioning error. It matched 7 of 30 orders on the Streams output and 30 of 30 on the Camel output, in each of three runs.

Notes the script prints: the time to the last `ReadyToShip` of the bench on each output, the peak number of open groups in `shipping.readiness`, the records added to each output, and when the Camel `PaymentOverdue` for `O-2005` was seen. Medians of 15 runs: 2.58 s on the Streams output and 3.10 s on the Camel output for the 200 bench orders (polled every 0.5 s; over those 15 runs and 5 more of the final script the range is 2.52 to 2.62 s and 3.04 to 3.13 s), the Camel `PaymentOverdue` for `O-2005` seen 10.05 to 11.07 s after the reservation was produced in all 20 runs (polled every second), 200 records added to each output, and a peak of 25 to 50 open groups in `shipping.readiness` (sampled every 0.5 s, so it depends on timing).

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `AGGREGATION_REPOSITORY=memory ./verify.sh` (demo code) | The group open at the recreate is lost on the Camel path, and the Camel consumer had already committed the reservation. The Streams side restores its store from the changelog | Checks 18, 20 and 21 (the open row, `ReadyToShip` after the recreate, the restore log line); 21 of 24 pass | The Camel side loses `O-2006`; the Streams changelog check (19) still passes |
| `READINESS_TIMEOUT_MS=30000 ./verify.sh` (demo code) | The payment for `O-2003` arrives inside the longer timeout, so the Camel path no longer reports it overdue | Checks 9, 11 and 15 (`O-2003` overdue, the late payment silent, `O-2005` overdue within 15 s); 21 of 24 pass | `O-2003` is no longer overdue within 20 s, and `O-2005` is not reported within 15 s |
