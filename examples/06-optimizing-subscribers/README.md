# 06 Optimizing subscribers

`notification-service` reads three catalog topics (`edp.orders.placed`, `edp.payments.processed`, `edp.shipping.scheduled`, three partitions each, nine in all) in one consumer group and stores one `notifications.notification` row per record. Every consumer setting is a Camel Kafka component property that an environment variable overrides, so a run changes one setting and nothing else. A seeder writes a fixed backlog with the stock Kafka performance tool, and each run drains it in a fresh consumer group.

Quarkus, Camel Kafka, Hibernate ORM with Panache, Flyway, PostgreSQL, Micrometer and OpenTelemetry to the LGTM stack. The topic names come from `examples/domain-model`.

## Services

| Service | Port | Purpose |
|---|---|---|
| `notification-service` | 8080 | Replica 1. Health on `/q/health/ready`; the Kafka client id is `notification-1` |
| `notification-2` | none | Replica 2 of the same group, in compose profile `scale` |
| `seeder` | none | Writes `SEED_RECORDS` records to each topic in `SEED_TOPICS`, profile `seed` |

## Settings

| Variable | Camel option or meaning | Default |
|---|---|---|
| `NOTIFY_GROUP` | consumer group of the run | `notify-default` |
| `NOTIFY_WORK_MS` | fixed cost per record, a stand-in for rendering | 0 |
| `MAX_POLL_RECORDS` | `maxPollRecords` | 500 |
| `FETCH_MIN_BYTES` | `fetchMinBytes` | 1 |
| `FETCH_WAIT_MAX_MS` | `fetchWaitMaxMs` | 500 |
| `MAX_POLL_INTERVAL_MS` | `maxPollIntervalMs` | 300000 |
| `CONSUMERS_COUNT` | `consumersCount` | 1 |
| `GROUP_PROTOCOL` | `groupProtocol`, `classic` or `consumer` | `classic` |
| `PARTITION_ASSIGNOR` | `partitionAssignor`, classic protocol only | `RangeAssignor` |

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # writer, concurrency and client-metrics tests, Kafka and PostgreSQL from Dev Services
./verify.sh        # compose stack, one run per setting; FIXED_MAX_POLL_RECORDS, CLASSIC_ASSIGNOR, TRICKLE_MIN_BYTES and SECOND_PROTOCOL break one guard each
./bench.sh         # five interleaved repeats per profile, writes results/bench-<date>.csv
```

By hand:

```bash
docker compose up -d --wait kafka postgres lgtm
docker compose run --rm seeder                      # 200 records per topic
NOTIFY_GROUP=run-1 CONSUMERS_COUNT=3 docker compose up -d --no-deps notification-service
docker exec edp-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group run-1
docker compose --profile '*' down -v
```

`verify.sh` and `bench.sh` refuse to start while another stack is running and remove this one on exit (`KEEP=1` keeps the stack for `verify.sh`).

## What verify.sh does

1. Checks that the seeder wrote 200 records into each topic and that the first group does not exist yet, so nothing has been consumed.
2. Drains with the defaults: one row per record, lag 0, one attempt per row, all nine partitions, one consumer thread.
3. `consumersCount=3`: three members, none idle, three consumer threads. `consumersCount=12`: with the default `RangeAssignor` only three members own partitions (three each) and nine are idle; with `RoundRobinAssignor` nine own one each and exactly three are idle.
4. Poll interval: with 50 ms of work per record and an interval of 5 seconds, `max.poll.records=500` delivers records again (attempts above 1) and logs the client leaving the group; `max.poll.records=20` does neither and stores every record.
5. Fetch sizing: while 6000 records arrive at 300 per second, `fetch.min.bytes=65536` sends at most a third of the fetch requests the defaults send, with preconditions on both runs (36 checks in all).
6. Rebalance: replica 2 joins two-thread replica 1. Under the classic protocol (group type Classic) replica 1 gives up all nine partitions. Under the consumer protocol (group type Consumer) it gives up at least one and fewer than nine.
7. Tempo, Prometheus and Loki hold telemetry from the service.

## Tests and checks in detail

`verify.sh` seeds 200 records into each of the three topics with the stock Kafka performance tool, drains them under different settings, one consumer group per run, and prints `36/36 checks passed`. It asserts relations, never durations, and every relation has a control run: eviction is checked against a run that is not evicted, the fetch ratio against a run with the default settings, the idle members of the default assignor against the round-robin assignor, and the revoked partitions of the new protocol against the classic protocol's nine. `bench.sh` repeats the profiles five times, interleaved, and writes the timings to `results/`.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `FIXED_MAX_POLL_RECORDS=500` | The run that should not be evicted is now evicted like the failing one: it never stores all 600 records within four minutes, records are delivered again and the client leaves the group | 21, 22 and 23 | - |
| `CLASSIC_ASSIGNOR=org.apache.kafka.clients.consumer.CooperativeStickyAssignor` | Replica 1 gives up 4 partitions under the classic protocol instead of 9 | 29 | 4 partitions instead of 9 |
| `TRICKLE_MIN_BYTES=1` | The run that was meant to send fewer fetch requests sends as many as the default | 26 | about 2,400 requests in both runs against 60 |
| `SECOND_PROTOCOL=classic` | The group type is Classic instead of Consumer, and replica 1 gives up all nine partitions | 30 and 33 | - |

## Layout

| Path | Purpose |
|---|---|
| `notification-service/.../NotificationConsumeRoute` | The consume route over the three topics |
| `notification-service/.../NotificationTemplate` | The per-record cost (`NOTIFY_WORK_MS`) |
| `notification-service/.../NotificationWriter` | The upsert with the attempts counter, and the event age timer |
| `notification-service/.../KafkaClientMetricsFactory` | Binds Micrometer's client metrics for each consumer thread |
| `notification-service/src/main/resources/db/migration` | `notifications.notification` |
| `seed/*.jsonl` | Payloads for the seeder, one file per topic |
| `compose.yaml` | Topics, seeder, replica 1 and replica 2 |
| `results/` | Scrubbed bench output |
