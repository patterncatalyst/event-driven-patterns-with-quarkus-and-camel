---
title: "Ordering and partitioning"
order: 4
part: getting-started
permalink: /docs/04-ordering-and-partitioning/
description: "Kafka orders records only within a partition. Key an order's events by the order id, and use a resequencer when the key cannot change."
duration: "40 minutes"
---

The shipping service tells the world three things about an order, in this sequence: ShipmentScheduled, ShipmentInTransit, ShipmentDelivered. The order service turns each one into an order status, SHIPPED, SHIPPED, DELIVERED, and the last write wins. If Delivered is applied before Scheduled, the order ends SHIPPED, and nothing logs an error.

{% include excalidraw.html file="04-keys-to-partitions" alt="Three key strategies: the order id puts all of an order's events in one partition, the event type splits an order across two partitions, and the carrier uses only two of three partitions" caption="Figure 4.1: The key picks the partition" %}

## Pattern: Partitioned ordering

### The problem

A consumer group splits a topic's partitions among its members, so two replicas of the order service each read part of the stream. They read at different speeds, and one of them can stall for a garbage collection pause, a slow database call or a deployment. Events of one order that sit in different partitions are applied in whatever order the replicas get to them.

### The solution

**Give every event of an entity the same record key, so the entity lives in one partition, which is the only place Kafka guarantees order.**

Kafka's documentation states the guarantee as order per partition: any consumer of a topic partition reads that partition's events in the order they were written, and events with the same key go to the same partition. It states no order across partitions. The producer's default partitioner hashes the key bytes with murmur2 and takes the result modulo the partition count, so the same key always lands in the same place.

### How Quarkus and Camel model it

`shipping-service` plans the three events of an order with two sequence numbers: `sequence` counts within the order (1, 2, 3) and `streamSequence` counts across everything the service publishes. A Camel route splits the plan, sets the Kafka key header from a strategy, marshals each event to JSON and sends it with the Kafka producer, waiting for the broker's answer. The response lists the partition and offset of every record.

`PartitionKeyStrategy` has three values, chosen by `SHIPPING_PARTITION_KEY`:

- `ORDER_ID`, the default, uses the order id. All three events of `O-1001` go to partition 2 in increasing offsets, and different orders spread over all three partitions.
- `EVENT_TYPE` uses the event name. ShipmentScheduled goes to partition 2, and ShipmentInTransit and ShipmentDelivered go to partition 0, for every order. One order now spans two partitions.
- `CARRIER` uses FASTLINE or AIRCO. Each order stays in one partition, but two keys can occupy at most two of three partitions, so one partition idles and the others carry the load.

Two `order-service` replicas read the topic in one group. Each applies an event to `orders.order_status`, mapping Scheduled and InTransit to SHIPPED and Delivered to DELIVERED, and appends a row to `orders.order_status_history`. The history's identity column records the order of application, so it shows exactly what each replica did and when. Each replica's hostname is its Kafka client id, which makes the group listing readable: `kafka-consumer-groups.sh --describe` shows which replica owns which partition.

{% include excalidraw.html file="04-stalled-partition" alt="Event-type key: the replica that owns partition 2 is paused, so Delivered is applied before Scheduled" caption="Figure 4.2: One stalled partition reorders a correctly published order" %}

The stall is staged, not timed. The script finds the replica that owns the ShipmentScheduled partition from the group listing and pauses its container. It publishes the order, waits until the other replica has applied everything it owns, and only then resumes the first one. The pause stays far below the session timeout, so the group does not rebalance, and the listing before and after is identical.

With the order id as the key, one replica owns all three events and the other owns none. Nothing is applied while the owner is paused. After it resumes, the history reads ShipmentScheduled, ShipmentInTransit, ShipmentDelivered and the status is DELIVERED.

With the event type as the key, the unpaused replica applies ShipmentInTransit and ShipmentDelivered. When the paused replica resumes it applies ShipmentScheduled last, and the status regresses from DELIVERED to SHIPPED. The script asserts first that the Scheduled and Delivered partitions have different owners, and stops with an error if they do not, because without that the run proves nothing.

The carrier key shows the cost of a poor key without reordering anything. Order is kept per order and parallelism is capped by the number of distinct keys.

## Pattern: Resequencer

### The problem

Sometimes the key cannot be the entity id. Several producers publish about one order, or one order's events travel on several topics, and no single key puts them together. Order is gone before any consumer sees the events.

### The solution

**A Resequencer is a stateful filter that holds out-of-sequence messages and releases them in order.**

Hohpe and Woolf's Resequencer needs a sequence number in each message and a place where all messages of a sequence arrive. Keying by entity id is the first answer, because it needs no buffer. A resequencer is for the cases where the key cannot change, and it pays in latency and memory.

### How Quarkus and Camel model it

Camel's Resequence EIP has a batch mode and a stream mode. Stream mode releases a message as soon as the next expected sequence number arrives, needs integer sequence numbers starting at 1, and buffers up to a capacity of 1000 messages by default. When a number does not arrive, the resequencer waits for its timeout, disregards the expected number and moves on.

Two properties of that design shaped the example.

**One sequence per resequencer.** Camel's stream resequencer orders a single run of consecutive integers, so a per-order number (1, 2, 3 for every order) cannot drive it for many orders at once. The example resequences on `streamSequence`, the producer's own count. The cost is real: a lost event holds back every order behind it until the timeout. The example's counter lives in memory, so the producer and the resequencer restart together; a production producer needs a persistent counter. A per-order sequence would avoid both, but the EIP takes one expression and keeps one sequence.

**Every event in one place.** Each replica sees only its partitions. With `RESEQUENCE=true` a replica forwards each event to a topic with one partition, the funnel, and one consumer in the group reads it and runs the resequencer.

{% raw %}
```java
from("{{order.resequence.from}}")
        .unmarshal().json(ShipmentEvent.class)
        .resequence(simple("${body.streamSequence}"))
            .stream().timeout("{{order.resequence.timeout}}").capacity(1000)
        .to("direct:apply-shipment");
```
{% endraw %}

{% include excalidraw.html file="04-resequencer" alt="Both replicas forward events to a one-partition funnel; the resequencer holds events 2 and 3 until event 1 arrives and then applies 1, 2, 3" caption="Figure 4.3: Funnel to one partition, then restore the sequence" %}

The same stall that leaves an order SHIPPED now ends DELIVERED. The unpaused replica forwards ShipmentInTransit and ShipmentDelivered, the resequencer holds them because number 1 is missing, and nothing is applied. When the paused replica resumes and forwards ShipmentScheduled, the resequencer releases 1, 2 and 3 in order.

The timeout sets the price of a gap. If an event is lost, later events wait the full timeout and then flow, with the lost number skipped. The timeout must exceed the longest stall you want to absorb, and the buffer must hold what arrives meanwhile.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/04-ordering-and-partitioning
mvn -B verify
./verify.sh
```

The Maven build runs eight tests across both services against Kafka and PostgreSQL from Dev Services, with three partitions. They compute the expected partition of every key with Kafka's own murmur2 and compare it with the placement the broker reports, for all three strategies. `verify.sh` starts Kafka, PostgreSQL, the LGTM stack, `shipping-service` and two `order-service` replicas, runs the stalled-replica order under four configurations and prints `25/25 checks passed`.

Change one thing and watch a guard fail:

- Export `SHIPPING_PARTITION_KEY=event-type` before `./verify.sh`. The first run now spreads the order across partitions: checks 3, 5, 6, 7 and 9 fail, because events are applied while the paused replica is down, the history leaves lifecycle order and the status ends SHIPPED.
- Compare the third and fourth runs. They differ only in `RESEQUENCE=true`: the third ends SHIPPED with Delivered applied first, the fourth ends DELIVERED in lifecycle order.
- Give the resequencer a missing number and a short timeout in the tests: the later event is held until the timeout, then released, and the gap is skipped.

## When not to use it

Keying by entity id does not order events across entities, and it leaves the partition count as the limit on parallelism. A resequencer adds latency equal to the longest gap it waits out, memory for everything it holds, and a single point where the whole sequence must pass, which limits throughput to one consumer. If the events can share a key, use the key. If order only matters for the final state, a version or sequence number checked at the write, which rejects an older event, is cheaper than reordering.

> **🤖 Explore with your agent**
> "In examples/04-ordering-and-partitioning, publish one order with SHIPPING_PARTITION_KEY set to order-id and then to event-type, pause the order replica that owns the ShipmentScheduled partition each time, and compare the OrderStatus and the order_status_history rows. State which key keeps ShipmentDelivered after ShipmentScheduled, and which sequence number the Resequencer needs to put them back."
> Check: order-id ends DELIVERED in lifecycle order; event-type ends SHIPPED with ShipmentDelivered applied before ShipmentScheduled.

## References

- Gregor Hohpe and Bobby Woolf, *Enterprise Integration Patterns*, Addison-Wesley, 2003: [Resequencer](https://www.enterpriseintegrationpatterns.com/patterns/messaging/Resequencer.html)
- Apache Kafka documentation, Introduction, "Main Concepts and Terminology": [kafka.apache.org/documentation](https://kafka.apache.org/documentation/)
- Apache Camel, [Resequence EIP](https://camel.apache.org/components/latest/eips/resequence-eip.html)
