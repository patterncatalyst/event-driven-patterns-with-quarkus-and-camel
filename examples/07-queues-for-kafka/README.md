# 07 Queues for Kafka

`notification-service` sends an email for every order placed. Four replicas, `worker-1` to `worker-4`, take their jobs from `edp.orders.placed`, a topic with three partitions, in one of two ways: a share group (SmallRye Reactive Messaging, any worker takes any record and acknowledges it individually) or a consumer group (a Camel route, one owner per partition). `EmailGateway` stands in for the email provider, and one customer is slow. Both paths write every attempt to `notifications.delivery_log`, so the two can be compared on the same 30 jobs.

Quarkus, SmallRye Reactive Messaging (Kafka), Camel Kafka, Hibernate ORM with Panache, Flyway, PostgreSQL, SmallRye Health and OpenTelemetry to the LGTM stack. The shared records and topic names come from `examples/domain-model`.

## Services

| Service | Port | Purpose |
|---|---|---|
| `worker-1` | 8080 | A `notification-service` replica. Health on `/q/health/ready` |
| `worker-2` to `worker-4` | none | The other replicas of the same service and group. There are more workers than partitions |

`EmailGateway` behaviour by customer id: `C-SLOW*` takes `GATEWAY_SLOW_MS` (8000 ms), `C-RETRY*` fails once (transient) and then succeeds, `C-BOUNCE*` always fails (permanent). Every other customer takes 50 ms.

| Setting | Default | Effect |
|---|---|---|
| `DELIVERY_MODE` | `share` | `share` reads through the share group; `group` turns the SmallRye channel off and starts the Camel consumer-group route (it sets the Quarkus profile) |
| `GATEWAY_SLOW_MS` | 8000 | Time a `C-SLOW` customer takes |
| `SHARE_RECORD_LOCK_MS` | 30000 | `share.record.lock.duration.ms` on the share group |
| `SHARE_DELIVERY_COUNT_LIMIT` | 5 | `share.delivery.count.limit` on the share group |
| `SHARE_ACQUIRE_MODE` | `batch_optimized` | Consumer `share.acquire.mode` |
| `MAX_PARTITION_FETCH_BYTES` | 1 | Consumer `max.partition.fetch.bytes`: a fetch returns as little as the log allows |
| `SEED_LINGER_MS` | 0 | `verify.sh` only: 0 turns producer batching off (`batch.size=0`), so each job is its own batch; a larger value sets `linger.ms` and writes a partition's jobs as one batch |
| `MAX_POLL_RECORDS` | 1 | Consumer `max.poll.records` |
| `RETRY_MAX_WAIT` | 1 | Connector `retry-max-wait` (seconds): cap on the back-off while a refused poll is retried; the connector default is 30 |
| `POLL_TIMEOUT_MS` | 100 | Connector `poll-timeout`; the connector default is 1000 |

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # gateway, share path and group path against Kafka and PostgreSQL from Dev Services
./verify.sh        # compose stack, 30 jobs, share run, then the consumer-group control
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, and stops this one on exit unless `KEEP=1`.

## What verify.sh does

1. Starts the stack. `topics-init` creates the topic and sets `share.auto.offset.reset=earliest`, the lock duration and the delivery count limit on the share group, before any worker joins.
2. Waits for four members with a stable assignment, then publishes 30 jobs with the console producer, batching off, so every job is its own record batch. Job 1 is `C-SLOW-1`; job 5 is `C-RETRY-1`; job 6 is `C-BOUNCE-1`.
3. Share run: every job finishes, 29 are sent once each, the retry job has delivery counts 1 and 2, the bounce job is rejected once and not delivered again after the lock duration, at least three of the four workers deliver jobs, a partition is delivered by at least two different workers, at least half of the later jobs of the slow job's partition finish before the slow job, and the last of them finishes no more than 3 s after it (after asserting that the slow job is first in its partition with at least five later jobs).
4. Control run: the workers restart with `DELIVERY_MODE=group` and a new classic group joins, and the same 30 jobs are published again. The same outcomes hold, no later job of the slow partition finishes before the slow job, and each partition has one worker, and exactly three of the four workers deliver.

## Layout

| Path | Purpose |
|---|---|
| `notification-service/.../ShareDeliveryWorker` | Share group consumer: accept with `ack()`, release or reject with `nack()` and `ShareGroupAcknowledgement` |
| `notification-service/.../GroupDeliveryRoute` | Camel consumer-group route and its retry policy |
| `notification-service/.../DeliveryService` | One attempt: gateway call, then one log row |
| `notification-service/.../EmailGateway` | The email provider stand-in |
| `notification-service/.../DeliveryRow` | Panache entity for `notifications.delivery_log` |
| `compose.yaml` | Topic and group settings, four workers |
