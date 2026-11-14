# 05 Optimizing publishers

`order-service` publishes `OrderPlaced` records to `edp.orders.placed`, a topic with three partitions, through three Camel routes that differ only in producer settings: `UnbatchedPublishRoute` (`lingerMs=0`, `producerBatchSize=0`, the control that batches nothing), `DefaultPublishRoute` (Kafka's defaults) and `TunedPublishRoute` (`lingerMs=20`, `producerBatchSize=131072`, `compressionCodec=lz4`). A load driver places a burst of orders through one route, and Kafka's own producer metrics, bound to Micrometer and sent to Prometheus over OpenTelemetry, show how many records each request carried.

Quarkus, Camel Kafka, Micrometer and OpenTelemetry to the LGTM stack. The shared records and topic names come from `examples/domain-model`. There is no database.

## Service

| Service | Port | Purpose |
|---|---|---|
| `order-service` | 8080 | `POST /flash-sale?profile=unbatched\|default\|tuned&orders=20000&concurrency=200` places the orders and returns elapsed time, orders per second and the 50th and 99th percentile of send-to-acknowledgement time |

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # Kafka from Dev Services
./verify.sh        # compose stack, three bursts, the relations between them
./bench.sh         # repeated interleaved rounds, writes results/bench-<date>.csv
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000, Prometheus on http://localhost:19090
docker compose down -v
```

`verify.sh` and `bench.sh` refuse to start while another stack is running, and stop this one on exit (`verify.sh` unless `KEEP=1`). Run `bench.sh` with nothing else on the machine.

## What verify.sh does

For each route it publishes the same burst (`N=20000` orders, `CONCURRENCY=200` callers) and checks that the topic's end offsets grew by exactly N and that the producer's own counters counted N records in more than 0 requests. Then it checks relations, each with a control: records per request of the unbatched route is at most 3; the default route has at least twice, and the tuned route at least five times, the unbatched route's; the tuned route sends fewer bytes per record than the default route; the broker's log holds `lz4` batches next to uncompressed ones; one caller on the tuned route sends at most 1.5 records per request while 100 callers on the same route send more than 3; after a recreate with `acks=1` and idempotence off, new batches carry no producer id, where before every batch had one. Finally a Tempo trace and a Loki log line from `order-service`. No check depends on elapsed time.

## What bench.sh does

A warm-up burst, then `REPEATS=5` rounds with the three routes interleaved, then the tuned route with linger 0, 5, 20 and 100 milliseconds and with codecs none, gzip, lz4 and zstd. Every round goes to `results/bench-<date>.csv`; medians with minimum and maximum go to `results/bench-<date>-summary.csv`. The header records the burst size, callers, partitions, broker count, CPU class and load average. Both files are scrubbed with `scripts/host_ids.py scrub`.

## Tests and checks in detail

The Maven build runs six tests against Kafka from Dev Services with three partitions: every profile publishes every order exactly once, the tuned route sends more records per request than the unbatched route, one caller never fills a batch, each producer is metered under its own client id, an unknown profile is rejected, and latency is recorded per profile. `verify.sh` starts Kafka, the LGTM stack and `order-service`, bursts 20,000 orders through each route, and prints `24/24 checks passed`. Checks 1 and 2 confirm the partitions and the health of `order-service`. Checks 3 to 11 confirm that each route's burst reached the topic exactly once and that the producer's own counters agree. Checks 12 to 22 are the relations and their preconditions, and checks 23 and 24 find a trace in Tempo and a log line in Loki. `bench.sh` writes `results/bench-<date>.csv` and a summary of medians and ranges.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| Remove `&producerBatchSize=0` from `UnbatchedPublishRoute` | The control now sends about ten times as many records per request, and every other check still passes | 12 | - |
| `PUBLISHER_LINGER_MS=0`, `PUBLISHER_BATCH_SIZE=0` and `PUBLISHER_COMPRESSION=none` | The tuned route has no tuning: records per request, bytes per record, the `lz4` batches in the broker's log, and the 100 callers on the same route | 14, 15, 16 and 20 | - |
| Add `&lingerMs=0&producerBatchSize=0` to `DefaultPublishRoute` | The default route is no longer the default | 13 | - |
| Run the one-caller step with 100 callers instead of one | One caller no longer fails to fill a batch | 19 | - |
| Start with `REQUIRED_ACKS=1 ENABLE_IDEMPOTENCE=false` | Batches without a producer id appear before the recreate | 17 | - |
| Make the recreate keep `acks=all` and idempotence | No batch lacks a producer id | 22 | - |
| Start with `REQUIRED_ACKS=1` alone | `order-service` does not start: the route fails with `Must set acks to all in order to use the idempotent producer` | - | - |

## Layout

| Path | Purpose |
|---|---|
| `order-service/.../UnbatchedPublishRoute`, `DefaultPublishRoute`, `TunedPublishRoute` | The three publish routes, one `clientId` each |
| `order-service/.../FlashSaleResource` | The load driver: virtual threads, a semaphore for concurrency, a Micrometer timer `edp.publish.latency` by profile |
| `order-service/.../KafkaClientMetricsFactory` | A Camel `kafkaClientFactory` that binds Micrometer's Kafka client metrics to each producer, with a `client_seq` tag |
| `order-service/.../OrderPlaced` | The event payload, built from the domain `Order` |
| `compose.yaml` | Creates the topic (3 partitions), then `order-service` |
| `lib.sh` | Helpers shared by `verify.sh` and `bench.sh` |
