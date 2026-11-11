---
title: "Optimizing publishers"
order: 5
part: events-between-services
permalink: /docs/05-optimizing-publishers/
description: "How the Kafka producer batches, compresses and acknowledges records, which settings change throughput, and how to measure a publisher from Camel."
duration: "45 minutes"
---

## The problem

A flash sale places twenty thousand orders in a few seconds. Each order becomes an `OrderPlaced` record on `edp.orders.placed`, a topic with three partitions on one broker. The Camel route that publishes them works, and nobody has looked at it since it passed its first test. The question is how many requests those twenty thousand records cost the broker, how many bytes each one puts on the wire, and what a caller waits for before it is told the order was published.

The producer has four settings that change the answer: how long it waits to fill a batch, how large a batch may grow, whether it compresses, and how many brokers must acknowledge a write. A fifth fact matters more than any of them: the kind of caller that sends the records.

{% include excalidraw.html file="05-batch-fill" alt="Records from many callers go into one open batch per partition; a request carries every batch that is ready" caption="Figure 5.1: One open batch per partition, one request for every ready batch" %}

## How the producer sends

`send` does not write to the network. The producer appends the record to an open batch for the record's partition and returns. A background sender thread takes batches that are ready and sends them to the partition leaders. One request to a broker carries the ready batches for every partition that broker leads.

A batch is ready when it is full or when it has waited long enough. Full means `batch.size` bytes, 16384 by default. Waiting long enough means `linger.ms`, which is 5 milliseconds by default since Kafka 4.0. Before that the default was 0, and at 0 Kafka effectively sends one message per batch (KIP-1030). The two settings are the two limits on a batch: the first one reached sends it.

A producer that is busy fills batches while earlier requests are still waiting for their acknowledgement. A producer that sends one record at a time has nothing to fill them with.

## Batching: linger.ms and batch.size

Raising `linger.ms` buys fuller batches with latency: a record may wait that long before it leaves. Raising `batch.size` lets a batch grow larger before it is sent. Fewer, larger requests cost the broker less per record, and the cost shows up as records per request and as throughput under load.

The setting only helps when sends overlap. A caller that sends one record and waits for the acknowledgement before sending the next never has two records in the producer at once, so a batch never holds more than one record, and each record waits out the full linger time. In a Camel route, that caller is a thread that blocks in `to("kafka:...")` or a route that sets `synchronous=true`. Concurrency makes the producer efficient, not the linger value.

A fixed number of callers also turns `linger.ms` into a ceiling on throughput. With 200 callers in flight, each waiting for its acknowledgement, a burst cannot move faster than 200 records per linger interval once the batches are full or the linger has expired. The measured 50th percentile latency follows the linger setting, and throughput falls as it rises, as the results below show. A longer linger pays only when callers do not wait for each acknowledgement, or when there are many more of them.

{% include excalidraw.html file="05-sequential-vs-concurrent" alt="One caller waits for each acknowledgement and sends one record per request; many callers share batches that fill before linger.ms ends" caption="Figure 5.2: linger.ms only helps when sends overlap" %}

## Compression

The producer compresses a whole batch, so a fuller batch compresses better, and the broker stores the batch as it received it. Compression trades CPU in the producer for bytes on the network and on disk, and it can add latency that does not suit a low-latency publisher (Strimzi, Optimizing Kafka producers). The codecs are `gzip`, `snappy`, `lz4` and `zstd`. The default is none.

## acks and idempotence

`acks=all` waits for every in-sync replica, and it is the default, as is `enable.idempotence=true`. With idempotence the broker gives the producer an id, numbers each batch, and discards a retried batch it has already written, so a retry after a lost acknowledgement does not duplicate records. Idempotence requires `acks=all`, retries above zero and at most five requests in flight per connection.

What happens when a setting conflicts depends on who sets what. The Kafka client, given `acks=1` and nothing about idempotence, switches idempotence off without a message, and its documentation says so. The same client with `enable.idempotence=true` set explicitly and a conflicting setting throws a `ConfigException`. The Camel Kafka component always passes `enable.idempotence` to the client, with the value `true` unless it is changed. Setting only `requestRequiredAcks=1` on a Camel route therefore fails at startup with `Must set acks to all in order to use the idempotent producer`. Running a Camel publisher with `acks=1` takes both settings, `requestRequiredAcks=1` and `enableIdempotence=false`, and the broker's log then shows batches without a producer id.

On a cluster of one broker, `acks=all` and `acks=1` both wait for the same single replica, so the setting changes nothing about durability there. It matters on a cluster with replicas, and no result from one broker says anything about its cost.

## How Quarkus and Camel model it

`order-service` has three routes that publish the same `OrderPlaced` record to `edp.orders.placed`, each with its own `clientId`:

- `UnbatchedPublishRoute` sets `lingerMs=0` and `producerBatchSize=0`. It is the control: every record is its own batch.
- `DefaultPublishRoute` sets no tuning options, so it runs with Kafka's defaults.
- `TunedPublishRoute` sets `lingerMs`, `producerBatchSize` and `compressionCodec` from `application.properties`: 20 milliseconds, 128 KiB and `lz4`.

The Camel Kafka component takes these as endpoint options, so the route reads like the settings it applies:

{% raw %}
```java
.to("kafka:" + Topics.ORDERS_PLACED + "?clientId=publisher-tuned"
        + "&lingerMs={{publisher.tuned.linger-ms}}"
        + "&producerBatchSize={{publisher.tuned.batch-size}}"
        + "&compressionCodec={{publisher.tuned.compression}}");
```
{% endraw %}

`FlashSaleResource` drives them. `POST /flash-sale?profile=tuned&orders=20000&concurrency=200` places the orders from virtual threads, with a semaphore that bounds how many are in flight, and returns the elapsed time, orders per second and the 50th and 99th percentile of the time from `send` to acknowledgement. The same latencies go to a Micrometer timer, `edp.publish.latency`, tagged by profile.

Camel creates its own Kafka clients, and neither Camel nor the Quarkus Micrometer binder observes them. `KafkaClientMetricsFactory` extends Camel's `DefaultKafkaClientFactory`, binds Micrometer's `KafkaClientMetrics` to every producer Camel creates, and unbinds it when the producer closes. The `camel.component.kafka.kafka-client-factory` property names the bean. Each client gets a `client_seq` tag, because two clients with the same `client.id` would otherwise register the same meters twice and lose one set. Quarkus sends the meters to the LGTM stack over OpenTelemetry, and Prometheus holds them as `kafka_producer_record_send_total`, `kafka_producer_request_total`, `kafka_producer_batch_size_avg`, `kafka_producer_records_per_request_avg` and `kafka_producer_outgoing_byte_total`, labelled with `client_id`.

Records per request is the first number to look at: `kafka_producer_record_send_total` divided by `kafka_producer_request_total`. Bytes per record is `kafka_producer_outgoing_byte_total` divided by the same record count. Neither depends on how fast the machine is.

## Measuring a publisher

The comparisons that hold on any machine are structural. The unbatched route cannot send more than one record in a batch, and with three partitions on one broker that bounds its records per request at three. The tuned route sends several times that. A single caller on the tuned route sends about one record per request. These are relations between two routes measured in the same run, and `verify.sh` asserts them with a control for each: the unbatched route is the control for batching, the tuned route at 100 callers is the control for the single caller, and an `lz4` batch next to an uncompressed one in the broker's log is the control for compression.

Throughput and latency belong to the machine and to the moment, so `bench.sh` measures them separately. It runs a warm-up burst, then five rounds with the three routes interleaved, and writes every round to a CSV file under `results/`, after removing host identifiers. A median with its minimum and maximum is the number to quote, together with the settings: 20,000 orders per burst, 200 callers in flight, three partitions, one broker with replication factor 1, and a 16-thread laptop CPU, with nothing else running but idle tooling containers.

| Route (200 callers) | Orders per second, median (range) | p50 latency, median | Records per request, median | Bytes per record, median |
|---|---|---|---|---|
| Unbatched | 23,500 (10,200 to 32,900) | 7.8 ms | 3.0 | 352 |
| Default | 115,000 (50,500 to 140,700) | 1.0 ms | 172 | 265 |
| Tuned (20 ms, 128 KiB, lz4) | 9,300 (9,200 to 9,400) | 21 ms | 200 | 101 |

Relative to the unbatched route, the default route moves about 4.9 times the orders per second, with about 58 times the records per request. The tuned route sends the most records per request and the fewest bytes per record, and it is the slowest of the three: about 0.4 times the unbatched throughput, because every record waits for the linger. The unbatched and default medians come from rounds that differ widely, as the ranges show, and the first round of each was the slowest. The tuned route's rounds agree closely, because its time is set by the linger.

The same route with other lingers, 200 callers, `lz4`:

| `linger.ms` | Orders per second, median | p50 latency | Records per request |
|---|---|---|---|
| 0 | 56,800 | 2.5 ms | 38 |
| 5 | 24,900 | 6.4 ms | 194 |
| 20 | 9,100 | 20.9 ms | 198 |
| 100 | 1,900 | 102 ms | 200 |

Batches are already near full at 5 ms, so a longer linger adds latency and no batching. With the linger at 20 ms, the codec changes the bytes and little else:

| Codec | Bytes per record, median | Orders per second, median |
|---|---|---|
| none | 265 | 9,100 |
| lz4 | 101 | 9,100 |
| gzip | 61 | 8,900 |
| zstd | 52 | 8,900 |

Compared with no compression, `lz4` sends about 0.38 times the bytes per record, `gzip` about 0.23 times and `zstd` about 0.19 times, and the throughput differences are inside the run-to-run ranges.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/05-optimizing-publishers
mvn -B verify
./verify.sh
./bench.sh
```

The Maven build runs six tests against Kafka from Dev Services with three partitions: every profile publishes every order exactly once, the tuned route sends more records per request than the unbatched route, one caller never fills a batch, each producer is metered under its own client id, an unknown profile is rejected, and latency is recorded per profile. `verify.sh` starts Kafka, the LGTM stack and `order-service`, bursts 20,000 orders through each route, and prints `24/24 checks passed`. Checks 1 to 11 confirm that each route's burst reached the topic exactly once and that the producer's own counter agrees. Checks 12 to 22 are the relations, and checks 23 and 24 find a trace in Tempo and a log line in Loki. `bench.sh` writes `results/bench-<date>.csv` and a summary of medians and ranges.

Change one thing and watch a guard fail:

- Remove `&producerBatchSize=0` from `UnbatchedPublishRoute`. Check 12 fails: the control now sends about 29 records per request instead of 3, and every other check still passes.
- Set `PUBLISHER_LINGER_MS=0`, `PUBLISHER_BATCH_SIZE=0` and `PUBLISHER_COMPRESSION=none`, so the tuned route has no tuning. Checks 14, 15, 16 and 20 fail: records per request, bytes per record, the `lz4` batches in the broker's log, and the 100 callers on the same route.
- Add `&lingerMs=0&producerBatchSize=0` to `DefaultPublishRoute`. Check 13 fails.
- Run the one-caller step with 100 callers instead of one. Check 19 fails.
- Start with `REQUIRED_ACKS=1 ENABLE_IDEMPOTENCE=false`. Check 17 fails, because batches without a producer id appear before the recreate.
- Make the recreate keep `acks=all` and idempotence. Check 22 fails, because no batch lacks a producer id.
- Start with `REQUIRED_ACKS=1` alone. `order-service` does not start: the route fails with `Must set acks to all in order to use the idempotent producer`.

## When not to tune

A publisher that sends a few records a second is already inside Kafka's defaults, and a 20 millisecond linger adds latency to every record in exchange for batches that never fill. In the measurements above the default settings were the fastest and the tuned route the slowest, because 200 callers each waited for an acknowledgement and the linger set the pace. Tune when measured records per request is close to one while the sends are concurrent, or when bytes on the wire or on disk are the cost, and then raise the linger only as far as the latency budget allows. Compression costs CPU on the producer and makes little difference for records that are already small and varied. A caller that waits for each acknowledgement before it sends the next needs more callers, or a different route design, before any producer setting helps.

> **🤖 Explore with your agent**
> "In examples/05-optimizing-publishers, run `POST /flash-sale` for the unbatched, default and tuned profiles with the same number of orders and with concurrency 1 and then 200, and compare records per request from `kafka_producer_record_send_total` and `kafka_producer_request_total` for each `client_id`. State which setting changes records per request, and what the single caller shows about `lingerMs`."
> Check: the tuned profile has far more records per request than the unbatched profile at concurrency 200, and about one record per request at concurrency 1.

## References

- Apache Kafka: [producer configuration](https://kafka.apache.org/43/configuration/producer-configs/), for `linger.ms`, `batch.size`, `acks`, `enable.idempotence` and `compression.type`
- KIP-1030: [Change constraints and default values for various configurations](https://cwiki.apache.org/confluence/display/KAFKA/KIP-1030%3A+Change+constraints+and+default+values+for+various+configurations)
- Paul Mellor, Strimzi blog, 2020: [Optimizing Kafka producers](https://strimzi.io/blog/2020/10/15/producer-tuning/)
- Strimzi blog, 2023: [Developing Kafka client applications: A simple producer](https://strimzi.io/blog/2023/10/03/kafka-producer-client-essentials/)
- Adam Bellemare, *Building Event-Driven Microservices*, Second Edition, O'Reilly, 2025: Chapter 11, Basic Producer and Consumer Microservices
- Apache Camel: [Kafka component](https://camel.apache.org/components/latest/kafka-component.html)
