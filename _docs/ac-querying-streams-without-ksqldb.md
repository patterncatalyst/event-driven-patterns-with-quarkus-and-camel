---
title: "Appendix C: Querying streams without ksqlDB"
order: 38
marker: "C"
label: "Appendix C"
part: appendices
permalink: /docs/ac-querying-streams-without-ksqldb/
description: "Operations wants deliveries per carrier per minute of event time. Three open source engines answer it from one topic: an interactive query on a Kafka Streams window store, a Flink SQL job that writes closed windows to a topic, and RisingWave materialized views read with psql."
duration: "60 minutes"
---

`shipping-service` publishes a `ShipmentDelivered` to `edp.shipping.delivered` for every parcel handed over. Operations wants the number of deliveries per carrier per minute, and wants it right when a record turns up a few seconds after its minute ended. A consumer that increments a counter counts by arrival time, so a paused or replayed consumer puts a delivery in the wrong minute. The count has to follow `deliveredAt`, the time in the payload.

Streaming SQL engines exist for this question, and ksqlDB is the best known for Kafka. It is under the Confluent Community License, which forbids offering a service that competes with Confluent's own products, so it is not open source and not used here. Materialize is under the Business Source License, and its own licence text says that licence is not an Open Source license. Three engines that are open source answer the same question from the same topic.

{% include excalidraw.html file="ac-three-ways" alt="One input topic edp.shipping.delivered feeds three engines: Kafka Streams in the service with a window store asked through GET /counts, a Flink SQL job whose closed windows land on an upsert-kafka topic, and RisingWave views asked with psql" caption="Figure C.1: One topic, three engines, three places for the state and three ways to ask" %}

## One question

The input is one JSON record per delivery on a single-partition topic:

```json
{"orderId":"O-3801","carrier":"FASTLINE","deliveredAt":"2026-11-16T15:23:05Z"}
```

The answer is one row per carrier per window: carrier, window start, deliveries. A window is a tumbling minute of event time, so every delivery is in exactly one. Each engine takes event time from `deliveredAt`, and each closes a window by a lateness bound; how the bound advances differs between them.

Every engine gets the same lateness bound, 10 seconds. A window closes when the newest event time seen passes the window's end plus 10 seconds, and a record for a closed window is dropped. In Kafka Streams the bound is the grace period, in Flink and RisingWave it is the delay in the watermark expression.

## Kafka Streams: ask the application

The topology lives in the service. It counts per carrier per window into a named window store, and a REST resource asks that store.

```java
builder.stream(Topics.SHIPPING_DELIVERED,
                Consumed.with(Serdes.String(), delivered).withTimestampExtractor(new DeliveredTimestampExtractor()))
        .groupBy((orderId, event) -> event.carrier().name(), Grouped.with("by-carrier", Serdes.String(), delivered))
        .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(1), grace))
        .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as("deliveries-by-carrier")
                .withRetention(Duration.ofHours(1)));
```

```java
var store = streams.store(StoreQueryParameters.fromNameAndType("deliveries-by-carrier",
        QueryableStoreTypes.<String, Long>windowStore()));
try (var windows = store.fetch(carrier, from, to)) { ... }
```

The timestamp extractor reads `deliveredAt`; the record's own timestamp is when it was written. The retention matters for queries. A window store keeps a window for its retention only, and the default is the window size plus the grace period. A window that closed a minute ago is then gone from the store, and a query for the day answers with the newest two windows. Setting the retention to an hour keeps closed windows queryable.

This is what Kafka's documentation calls an interactive query: an instance queries the part of the state it manages locally. The Quarkus guide exposes it as a REST endpoint, which returns the latest aggregate without subscribing to any topic. The answer covers open windows too, so it is an updating result.

The cost is that the state belongs to the application. With several instances each holds part of the state, and a client has to be sent to the instance that holds the carrier it asks about. The question is also code: a new question is a new endpoint and a deployment.

## Flink SQL: a job that writes answers

Flink runs the query as a job, and the answer leaves as records. The statements are submitted to the JobManager with `sql-client.sh -f`:

```sql
CREATE TABLE delivered (
  orderId STRING, carrier STRING, deliveredAt TIMESTAMP_LTZ(3),
  WATERMARK FOR deliveredAt AS deliveredAt - INTERVAL '10' SECOND
) WITH ('connector' = 'kafka', 'topic' = 'edp.shipping.delivered', 'format' = 'json', ...);

CREATE TABLE counts (
  carrier STRING, window_start TIMESTAMP_LTZ(3), window_end TIMESTAMP_LTZ(3), deliveries BIGINT,
  PRIMARY KEY (carrier, window_start) NOT ENFORCED
) WITH ('connector' = 'upsert-kafka', 'topic' = 'edp.shipping.delivery-counts-flink', ...);

INSERT INTO counts
SELECT carrier, window_start, window_end, COUNT(*)
FROM TABLE(TUMBLE(TABLE delivered, DESCRIPTOR(deliveredAt), INTERVAL '1' MINUTE))
GROUP BY carrier, window_start, window_end;
```

A window leaves once, when the watermark passes its end, so the output topic holds closed windows only. The `upsert-kafka` connector writes a record keyed by the primary key, which is carrier and window start. A reader that wants the current answer takes the last record per key.

Flink is pinned at 2.2.1, below the newest minor, because the Kafka SQL connector has no build for Flink 2.3. The connector jar, `flink-sql-connector-kafka` 5.0.0-2.2, is downloaded by an init service, checked against its sha512 and copied into Flink's `lib` directory. The watermark of a Kafka source is the minimum over its partitions, and an idle partition holds it back; with more than one partition the `table.exec.source.idle-timeout` option keeps windows closing.

The answer is a topic, which any consumer can read and no one has to query. The price is a second system to operate, with its own memory, and no way to ask a question the job does not already compute.

## RisingWave: a database that keeps the answer current

RisingWave reads the topic through a source and maintains the answer as a materialized view. It speaks the PostgreSQL wire protocol, so the PostgreSQL container's `psql` is the client.

```sql
CREATE SOURCE delivered ("orderId" VARCHAR, carrier VARCHAR, "deliveredAt" TIMESTAMPTZ,
  WATERMARK FOR "deliveredAt" AS "deliveredAt" - INTERVAL '10 seconds')
WITH (connector = 'kafka', topic = 'edp.shipping.delivered', properties.bootstrap.server = 'kafka:9094',
      scan.startup.mode = 'earliest') FORMAT PLAIN ENCODE JSON;

CREATE MATERIALIZED VIEW counts_rw AS
SELECT carrier, window_start, count(*) AS deliveries
FROM TUMBLE(delivered, "deliveredAt", INTERVAL '1 minute') GROUP BY carrier, window_start;
```

```bash
docker exec -e PGPASSWORD= edp-postgres psql -h risingwave -p 4566 -U root -d dev \
  -c "select * from counts_rw order by window_start, carrier"
```

By default RisingWave emits on update: a window shows partial counts while it is open. `EMIT ON WINDOW CLOSE` holds a window back until the watermark passes its end, as the Flink job does, and RisingWave's documentation recommends it when the downstream is append-only, like Kafka. The second view, `counts_rw_final`, uses the clause to show the same final-result form on the same input.

The Community Edition is Apache-2.0. Premium features need a licence key, and none is used: a plain Kafka source, `tumble()` and the emit clause are not on the premium list. Run as `single_node`, RisingWave needs a small configuration file that caps the compactor's memory, or it stops at startup under a 4 GiB limit.

The answer is a table. Any SQL question over the same windows is one more query, with no deployment. The state is in the database, and a restart does not rebuild it from the topic by running a consumer you wrote.

## Late records

Take the scenario fourteen records long. Three windows, a record for window 1 that arrives after a record from window 2, and a fourth window that opens last, so that windows 1 to 3 close.

{% include excalidraw.html file="ac-window-and-late-record" alt="Records for window 1 and 2 arrive, a 55 second record arrives 7 seconds behind the newest and is counted in window 1, a 195 second record opens window 4 and closes windows 1 to 3, a later 58 second record for window 1 is dropped by all three engines" caption="Figure C.2: A record inside the lateness is counted; a record for a closed window is dropped" %}

The record stamped 55 seconds arrives after one stamped 62. It is 7 seconds behind the newest event time, inside the 10-second bound, and all three engines count it in window 1. A record stamped 58 seconds, produced after window 4 opened, belongs to a closed window. Kafka Streams skips it and logs a warning. Flink and RisingWave find it behind the watermark and ignore it. The closed windows are the same list in all three, before and after the late record.

The three engines agree only when an out-of-order record is inside the lateness of every one of them. Kafka Streams advances stream time with every record. The Flink and RisingWave watermarks advance per batch or interval, so a record that Kafka Streams has already dropped can still be inside the watermark of the other two. Choosing the same number is the first step. The bound is also a latency: a window leaves after its end plus 10 seconds of event time, in every engine.

Give one engine a smaller bound and it disagrees. With a grace of 0 in Kafka Streams, the 55-second record is late there and window 1 counts 3 where the others count 4. The service reads that switch from `KS_GRACE_SECONDS`, which is demo code.

None of the three tells the reader about a dropped record in its answer. Kafka Streams exposes a dropped-records metric; an answer that must account for every delivery needs a lateness as long as the slowest source, or a second path for late records.

## Which one

| | Kafka Streams | Flink SQL | RisingWave |
|---|---|---|---|
| State lives in | The service, in a window store | The Flink job | The database |
| A question is asked by | A REST call into the service | Reading a topic | SQL over the PostgreSQL protocol |
| New question | New endpoint and deployment | New job | New query |
| Open windows | Visible, updating | Not emitted | Visible; `EMIT ON WINDOW CLOSE` hides them |
| Late record | Dropped | Dropped | Dropped |
| Runs as | Inside the service's JVM | Two JVMs, JobManager and TaskManager | One process |
| Licence | Apache-2.0 (Apache Kafka) | Apache-2.0 | Apache-2.0 (Community Edition) |

Kafka Streams fits when one service owns the question and already runs Kafka Streams: the count is an endpoint of that service and adds no system. Flink fits when the answer is itself an event stream for other consumers, or when the processing needs more than SQL. RisingWave fits when people ask questions of the windows that nobody has written yet.

Apache Pinot, which describes itself as a distributed OLAP database, and Apache Druid, which describes itself as an analytics database for streaming and batch data, are databases for analytical queries over event data. That is a different job from a windowed count, and neither is run here.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/ac-querying-streams-without-ksqldb
mvn -B verify
./verify.sh
```

`verify.sh` starts Kafka, Flink and RisingWave with the service, submits both SQL scripts, sends the fourteen records and the late one, and compares the three answers. Watch the closed windows come out equal everywhere and the late record change none of them. It prints `18/18 checks passed`.

Try one change and rerun:

- `KS_GRACE_SECONDS=0 ./verify.sh` (demo code): Kafka Streams counts window 1 as 3 while the other two count 4.
- `KEEP=1 ./verify.sh`, then open the Flink UI on port 18081 to see the running job.

## When not to use it

A count that one service needs for itself, over a topic it already consumes, is a Kafka Streams state store. Adding Flink or RisingWave for it adds a system to run.

A value that changes per key and is read by key is a lookup. A table fed by the topic or a compacted topic read into a global table is simpler than a window.

Windows need event time the data carries. When the source has no reliable time, or its disorder exceeds any lateness anyone accepts, count by arrival and say so.

State grows with the number of open windows and keys. A count per carrier per minute is small; a count per customer per second is a storage problem that needs a retention decision first.

> **🤖 Explore with your agent**
> "In examples/ac-querying-streams-without-ksqldb, produce the fourteen ShipmentDelivered records from counts.py and read the closed windows from Kafka Streams, from the Flink topic and from RisingWave. Rerun with KS_GRACE_SECONDS=0 and say which window differs, in which engine, and why."
> Check: DeliveryCountsTopology uses the grace from the environment, the Flink and RisingWave watermarks use LATENESS_SECONDS, and with KS_GRACE_SECONDS=0 only the Kafka Streams count for FASTLINE window 1 changes, from 4 to 3.

## References

- Apache Kafka, [Kafka Streams interactive queries](https://kafka.apache.org/43/streams/developer-guide/interactive-queries/) (local and remote state)
- Quarkus, [Using Apache Kafka Streams](https://quarkus.io/version/3.40/guides/kafka-streams) (interactive queries through a REST endpoint)
- Apache Flink, [Windowing table-valued functions](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/dev/table/sql/queries/window-tvf/), [Kafka SQL connector](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/connectors/table/kafka/) (watermarks per partition, idle partitions) and [Upsert Kafka SQL connector](https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/connectors/table/upsert-kafka/)
- RisingWave, [Kafka source](https://docs.risingwave.com/ingestion/sources/kafka), [time windows](https://docs.risingwave.com/processing/sql/time-windows), [emit on window close](https://docs.risingwave.com/processing/emit-on-window-close) and [premium features](https://docs.risingwave.com/get-started/premium-features)
- Apache Pinot, [home page](https://pinot.apache.org/); Apache Druid, [home page](https://druid.apache.org/)
- Confluent, [Confluent Community License](https://www.confluent.io/confluent-community-license/) and the ksqlDB [LICENSE](https://github.com/confluentinc/ksql/blob/master/LICENSE)
- Materialize, [LICENSE](https://raw.githubusercontent.com/MaterializeInc/materialize/main/LICENSE) (Business Source License)
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 14: Streaming SQL
- *Cloud-Native Design Patterns*, [chapter 6: Stream Processing](https://patterncatalyst.github.io/cloud-native-design-patterns/docs/06-stream-processing/)
- [Aggregate]({{ '/docs/21-aggregate/' | relative_url }}): event time, stream time and grace
- [CQRS and projections]({{ '/docs/16-cqrs-and-projections/' | relative_url }}): a read model
- [Enrich]({{ '/docs/19-enrich/' | relative_url }}): the global table
