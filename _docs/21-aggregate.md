---
title: "Aggregate"
order: 21
part: processing-and-coordinating-streams
permalink: /docs/21-aggregate/
description: "A carrier collects parcels in pickup slots, and the warehouse needs one list per carrier per slot. Kafka Streams closes a window of event time and emits one final list; the Camel Aggregator closes a batch by size or quiet time in arrival order."
duration: "60 minutes"
---

Each carrier collects parcels from the warehouse in pickup slots. For every slot the warehouse needs one list per carrier of the shipments scheduled in it, and the number to expect. `shipping-service` receives a `ShipmentScheduled` for every parcel booked, and the lists have to come out of that stream.

Two forms run side by side in the one service, on the same input, each writing its own topic: a Kafka Streams topology and a Camel route. A real service runs one of them. Having both in the same run shows where the lists differ.

{% include excalidraw.html file="21-window-vs-batch" alt="The same arrivals for FASTLINE grouped two ways; Kafka Streams gives the slot 09:00 list with F1 to F7 and F9, an open list for slot 09:15 holding F8, and drops L1; the Camel Aggregator gives four batches in arrival order closed by size or timeout, including L1 alone" caption="Figure 21.1: The same arrivals grouped by slot of event time and by size and quiet time at arrival" %}

## Pattern: Windowed aggregation

### The problem

A list per carrier per slot is not a lookup, because nothing says when a slot is complete. A consumer that appends each event to a list cannot tell the warehouse when to read it, and waiting for everything is no answer on a stream that never ends. Hohpe and Woolf describe the Aggregator as a stateful filter that stores related messages until it has a complete set and then publishes one message. A stream needs a completion rule, and the usual one is time.

Two questions follow. Which clock? Arrival time groups a shipment by when a consumer happened to see it, so a paused consumer, a replay or a republish through the outbox moves shipments into the wrong slot. The slot is a business fact: the time the shipment was scheduled, carried in the payload as `occurredAt`.

And what about records that arrive out of order? A shipment stamped 09:10 can arrive after one stamped 09:16. A list that closes the moment a later record shows up would miss it. A list that never closes would never be sent.

### The solution

**A windowed aggregation groups records by key into windows of event time, keeps each window open for a grace period so out-of-order records still land in it, emits one final result when stream time passes the window's end plus the grace, and drops a record that arrives after that.**

Stream time is the newest event time seen, and it advances only when a record arrives. The wall clock never closes a window, and a stream that goes quiet leaves its last window open until the next record. The grace period is a latency: the 09:00 slot ends at 09:15 but its list leaves when stream time passes 09:20.

{% include excalidraw.html file="21-tumbling-vs-hopping" alt="One shipment at 09:07 falls in one tumbling slot but in three hopping windows of the same size advancing by five minutes" caption="Figure 21.2: Pickup lists use tumbling windows, so a shipment is in exactly one list" %}

Windows of the same size that advance by less than their size overlap. A 15-minute window advancing by 5 puts every shipment in three lists, and the warehouse would collect it three times. A pickup slot is a tumbling window: it advances by its size.

### How Quarkus and Camel model it

`PickupTopology` produces a `Topology` bean in the plain DSL, and the Quarkus Kafka Streams extension runs it. The timestamp extractor reads event time from the payload, because the record timestamp is when the record was written, which after an outbox or a replay is not when the shipment was scheduled.

```java
builder.stream(Topics.SHIPPING_SCHEDULED,
                Consumed.with(Serdes.String(), scheduled).withTimestampExtractor(new PayloadTimestampExtractor()))
        .groupBy((orderId, event) -> event.shipment().carrier().name(),
                Grouped.with("by-carrier", Serdes.String(), scheduled))
        .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(15), Duration.ofMinutes(5)))
        .aggregate(PickupList::empty, (carrier, event, list) -> list.add(event.shipment().id()), store)
        .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
        .toStream()
        .map((slot, list) -> KeyValue.pair(slot.key() + "@" + slot.window().startTime(),
                new PickupList(Carrier.valueOf(slot.key()), slot.window().startTime(), slot.window().endTime(),
                        list.shipmentIds(), list.count(), "window")))
        .to(Topics.SHIPPING_PICKUP_LISTS, Produced.with(Serdes.String(), lists));
```

Re-keying by carrier makes Kafka Streams write the records to an internal repartition topic, named `by-carrier` here so the name is stable and readable. The window store is named `pickup-lists`, so its changelog topic is too. Named stores keep the internal topic names stable, and the state in them outlives a restart.

The topics are created with one partition, so one task owns the whole input and a single record with a later event time moves stream time for both carriers. `quarkus.kafka-streams.topics` lists the input, so readiness waits for it. Two pass-through settings make results prompt: `commit.interval.ms=1000` and `statestore.cache.max.bytes=0`. With the defaults, a 30-second commit interval and a 10 MiB record cache, the updates of an aggregate wait for the next commit.

Event time may lag the wall clock, but it may not lead the broker's clock by more than an hour. Kafka Streams forwards event time as the timestamp of every record it writes, the broker refuses a timestamp that far ahead, and the instance stops. A payload dated tomorrow is a fault. Event time may not lag by much more than a day either: the changelog topic of the window store keeps records for about a day after their timestamp, and a new instance that restores a slot older than that finds nothing to restore.

Camel takes the batch route in the next section; the two meet in a table after it.

## Final results, not updates

An aggregation's output is a table. Each record that lands in a window emits the updated list, which overwrites the one before. Read from the output topic, the FASTLINE 09:00 list would arrive with count 1, then 2, then 3, and a warehouse that acts on the first one picks up one parcel.

`suppress(untilWindowCloses(...))` holds the updates in a buffer and emits only the last value for each window, when the window closes. The warehouse sees one list per carrier per slot, marked `closedBy: window`. With `PICKUP_EMIT=updates` (demo code) the suppression is removed and every update leaves.

The buffer is unbounded and in memory, so it holds every open window until it closes; one list per carrier per slot is small, and its own changelog topic restores it after a restart. The cost is the latency already named: nothing leaves before the slot's end plus the grace.

## Late records

{% include excalidraw.html file="21-window-close-and-grace" alt="Seven FASTLINE records and a 09:16 record arrive, a 09:10 record arrives out of order inside the grace and joins the 09:00 slot, a tick at 09:21 passes the slot end plus grace, one list with count 8 leaves, and a 09:05 record arriving later is dropped and counted" caption="Figure 21.3: The window closes on stream time, and a record past the grace is dropped and counted" %}

Seven FASTLINE shipments stamped 09:01 to 09:07 arrive. A shipment stamped 09:16 opens the next slot and moves stream time to 09:16. One stamped 09:10 then arrives: it is behind stream time, but the 09:00 window stays open until 09:20, so it joins that list. A record stamped 09:21 moves stream time past 09:20, and the 09:00 list leaves with count 8.

A shipment stamped 09:05 arriving after that belongs to a closed window. Kafka Streams discards a record whose window ended more than the grace ago. It is not an error: the instance stays healthy and nothing is written. The only trace is the task's `dropped-records-total`, which the Quarkus Micrometer integration exports as `kafka_stream_task_dropped_records_total`. A warehouse never learns of the dropped shipment from the output, so an alert belongs on that counter.

A longer grace waits longer for stragglers and delays every list by the difference. A shorter one sends lists sooner and drops more of them. The right value is the largest disorder the source produces.

## Batches by size and time

The Camel form does not know about slots. The Aggregator groups by arrival and completes a group on a rule that needs no event time:

```java
from("kafka:" + Topics.SHIPPING_SCHEDULED + "?groupId=shipping-pickups-camel&autoOffsetReset=earliest")
    .unmarshal(incoming)
    .setHeader("carrier", simple("${body.shipment.carrier}"))
    .setBody(simple("${body.shipment.id}"))
    .aggregate(header("carrier"), PickupBatchRoute::collect)
        .completionSize(5)
        .completionTimeout(10000)
        .aggregationRepository(repository)
        .process(PickupBatchRoute::toPickupList)
        .marshal(outgoing)
        .to("kafka:" + Topics.SHIPPING_PICKUP_LISTS_CAMEL)
    .end();
```

A batch per carrier closes at 5 shipments or after 10 seconds without a new one, and the first rule to trigger wins. The same seven FASTLINE shipments give a batch of five closed by size and one of two closed by timeout; the three AIRCO shipments give one batch closed by timeout. `closedBy` carries `size` or `timeout`, read from `CamelAggregatedCompletedBy`.

A late record is no special case. It joins whatever batch is open when it arrives, so the shipment stamped 09:05 that Kafka Streams dropped leaves on a batch of its own. An out-of-order shipment sits in the batch with its neighbours in arrival order, with shipments from the next slot.

Open batches are rows in PostgreSQL. `PostgresAggregationRepository` stores each open batch in `shipping.pickups`, and a completed one in `shipping.pickups_completed`, through the Agroal datasource Quarkus configures. After a recreate the route restores the open batches and rearms their timeouts counted from the restart. The in-memory default would lose them, as chapter 20 showed. The Kafka Streams form keeps the same promise with changelog topics: three records, a recreate, two more records and a tick still give a list with count 5. With `PICKUP_CHANGELOG=off` (demo code) the window store is local only, and the list holds the two records that came after.

| | Kafka Streams | Camel Aggregator |
|---|---|---|
| Grouping rule | A slot of event time, per carrier | Size or quiet time at arrival, per carrier |
| A result leaves | When stream time passes slot end plus grace | At 5 shipments or after 10 seconds of quiet |
| A late record | Dropped and counted | Joins the open batch |
| Partial results | Suppressed: one final list | Never; a batch leaves when complete |
| State | Window store and suppress buffer, with changelog topics | Rows in `shipping.pickups` |
| 1,000 shipments, tick to last list | 1.3 s (1.25 to 1.33) | |
| 1,000 shipments, producer call to last batch | | 13.7 s (12.8 to 13.8), 201 batches |
| Matches the oracle count per slot | 8 lists, every count equal | |
| Batch scenario as expected, repeated runs | | 15 of 15 |

The two forms answer different questions. Kafka Streams answers what was scheduled in this slot. Camel answers how to send fewer, fuller messages. In the bench, the time to the last Camel batch includes the 10-second timeout of the final partial batch, so it is not a throughput figure.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/21-aggregate
mvn -B verify
./verify.sh
```

`verify.sh` sends the same shipments to both forms, closes a window with a later event time, sends a late record and recreates the service while a window and a batch are open. It prints `22/22 checks passed`. Watch the Kafka Streams list leave once and the Camel batches follow the arrival order.

Try one change and rerun:

- `PICKUP_EMIT=updates ./verify.sh` (demo code): several partial lists per carrier and slot leave.
- `PICKUP_CHANGELOG=off ./verify.sh` (demo code): the open window is lost on recreate and the list holds only two shipments.

## When not to use it

Windows need a clock the data carries. When the source has no reliable event time, or its disorder is larger than any grace anyone would accept, group by arrival.

A windowed result is final only for the records the grace admitted. A warehouse that must account for every shipment cannot rely on a list alone; it needs the dropped-records counter, or a grace as long as the slowest source.

Batches by size and time give a smaller, fuller stream and nothing more. They carry no slot and no lateness rule, and a list that must mean something about a period of time cannot come from them.

Neither form fits a count or a sum that a reader may poll at any moment. That is a read model (chapter 16), which changes on every record.

> **🤖 Explore with your agent**
> "In examples/21-aggregate, produce shipments for FASTLINE with event times 09:01 to 09:07, then 09:16 and 09:10, then 09:21, then a late 09:05, and compare the lists on edp.shipping.pickup-lists with the batches on edp.shipping.pickup-lists-camel. Run it again with PICKUP_EMIT=updates and state what the warehouse would see."
> Check: PickupTopology emits one list per carrier per slot with the 09:10 shipment inside and the 09:05 one on no list, PickupBatchRoute closes batches by size and timeout in arrival order, and PICKUP_EMIT=updates emits partial lists.

## References

- Gregor Hohpe and Bobby Woolf, [*Enterprise Integration Patterns*](https://www.enterpriseintegrationpatterns.com/patterns/messaging/Aggregator.html), Addison-Wesley, 2003: Aggregator
- Apache Kafka, [Kafka Streams core concepts](https://kafka.apache.org/42/streams/core-concepts/) (event time, stream time, grace and out-of-order records), [DSL API](https://kafka.apache.org/42/streams/developer-guide/dsl-api/) (windowing and suppression), [configuration](https://kafka.apache.org/42/streams/developer-guide/config-streams/) (`commit.interval.ms`, `statestore.cache.max.bytes`) and [monitoring](https://kafka.apache.org/42/operations/monitoring/) (`dropped-records-total`)
- Apache Kafka, [Kafka Streams Javadoc](https://kafka.apache.org/42/javadoc/org/apache/kafka/streams/kstream/TimeWindows.html): `TimeWindows.ofSizeAndGrace` and `Suppressed.untilWindowCloses`
- Quarkus, [Using Apache Kafka Streams](https://quarkus.io/guides/kafka-streams)
- Apache Camel, [Aggregate EIP](https://camel.apache.org/components/4.22.x/eips/aggregate-eip.html) and [SQL component](https://camel.apache.org/components/4.22.x/sql-component.html) (the aggregation repository)
- Apache Camel Quarkus, [SQL extension](https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/sql.html)
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 8: Stateful Event-Driven Microservices and chapter 9: Deterministic Stream Processing
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapter 9: Composing Data from Different Sources
- *Enterprise Integration Patterns with Camel*, chapter 13: Structural Transformation (Aggregator)
- Chapter 16: CQRS and projections, for a read model; chapter 19 for the GlobalKTable; chapter 20 for event time and the aggregation repository
