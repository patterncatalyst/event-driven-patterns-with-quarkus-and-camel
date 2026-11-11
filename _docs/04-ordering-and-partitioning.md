---
title: "Ordering and partitioning"
order: 4
part: events-between-services
permalink: /docs/04-ordering-and-partitioning/
description: "Kafka orders records only within a partition. Key an order's events by the order id, and hold events in a per-order buffer when the key cannot change."
duration: "40 minutes"
---

The shipping service tells the world three things about an order, in this sequence: ShipmentScheduled, ShipmentInTransit, ShipmentDelivered. The order service turns each one into an order status, SHIPPED, SHIPPED, DELIVERED, and the last write wins. If Delivered is applied before Scheduled, the order ends SHIPPED, and no error is raised.

{% include excalidraw.html file="04-keys-to-partitions" alt="Three key strategies: the order id puts all of an order's events in one partition, the event type splits an order across two partitions, and the carrier uses only two of three partitions" caption="Figure 4.1: The key picks the partition" %}

## Pattern: Partitioned ordering

### The problem

A consumer group splits a topic's partitions among its members, so two replicas of the order service each read part of the stream. They read at different speeds, and one of them can stop for a while while the other carries on. Events of one order that sit in different partitions are applied in whatever order the replicas get to them.

### The solution

**Give every event of an entity the same record key, so the entity lives in one partition, which is the only place Kafka guarantees order.**

Kafka's documentation states the guarantee as order per partition: any consumer of a topic partition reads that partition's events in the order they were written, and events with the same key go to the same partition. It states no order across partitions. The producer's default partitioner hashes the key bytes with murmur2 and takes the result modulo the partition count, so the same key always lands in the same place.

### How Quarkus and Camel model it

`shipping-service` numbers the three events of an order 1, 2 and 3 in `sequence`. A Camel route splits the plan, sets the Kafka key header from a strategy, marshals each event to JSON and sends it with the Kafka producer, waiting for the broker's answer. The response lists the partition and offset of every record.

`PartitionKeyStrategy` has three values, chosen by `SHIPPING_PARTITION_KEY`:

- `ORDER_ID`, the default, uses the order id. All three events of `O-1001` go to partition 2 in increasing offsets, and different orders spread across the partitions.
- `EVENT_TYPE` uses the event name. ShipmentScheduled goes to partition 2, and ShipmentInTransit and ShipmentDelivered go to partition 0, for every order. One order now spans two partitions.
- `CARRIER` uses FASTLINE or AIRCO. Each order stays in one partition, but two keys can occupy at most two of three partitions, so one partition idles and the others carry the load.

Two `order-service` replicas read the topic in one group. Each applies an event to `orders.order_status`, mapping Scheduled and InTransit to SHIPPED and Delivered to DELIVERED, and appends a row to `orders.order_status_history`. The history's identity column records the order of application, so it shows which replica applied each event, in the order they were applied. Each replica's hostname is its Kafka client id, which makes the group listing readable: `kafka-consumer-groups.sh --describe` shows which replica owns which partition.

{% include excalidraw.html file="04-stalled-partition" alt="Event-type key: the replica that owns partition 2 is paused, so Delivered is applied before Scheduled" caption="Figure 4.2: One stalled partition reorders a correctly published order" %}

One replica is paused: the one that owns the ShipmentScheduled partition. The other replica applies everything it owns, and only then does the first one resume. The pause lasts only until the other replica has caught up, and the group listing before and after is identical.

With the order id as the key, one replica owns all three events and the other owns none. Nothing is applied while the owner is paused. After it resumes, the history reads ShipmentScheduled, ShipmentInTransit, ShipmentDelivered and the status is DELIVERED.

With the event type as the key, the unpaused replica applies ShipmentInTransit and ShipmentDelivered. When the paused replica resumes it applies ShipmentScheduled last, and the status regresses from DELIVERED to SHIPPED.

The carrier key keeps each order's events in one partition, so nothing is reordered, but it has only two values: at most two of the three partitions receive records, and the replica that owns the third has nothing to do.

## Pattern: Resequencer

### The problem

Sometimes the key cannot be the entity id. Several producers publish about one order, or one order's events travel on several topics, and no single key puts them together. No single partition then holds all of the order's events, so Kafka cannot keep them in sequence.

### The solution

**A Resequencer is a stateful filter that holds out-of-sequence messages and releases them in order.**

Hohpe and Woolf's Resequencer needs a sequence number in each message and a place where the held messages wait. Keying by entity id is the first answer, because it needs no buffer. A resequencer is for the cases where the key cannot change, and it pays with held rows and with events that wait until every earlier one has arrived.

### How Quarkus and Camel model it

Every shipment event already carries `sequence`, its number within the order. Both `order-service` replicas share the database, so the database is the place where an order's events wait. With `RESEQUENCE=true` the consume route hands each event to `ShipmentBuffer` instead of applying it:

1. Create the order's row in `orders.order_status` if it is missing, with status PAID and `next_sequence` 1, using an insert that does nothing when the row exists. Then lock the row with `PESSIMISTIC_WRITE`. `next_sequence` is the number to apply next.
2. If the event's sequence is below `next_sequence`, or the same event is already held, ignore it.
3. Otherwise store it in `orders.pending_shipment_event`, which has a unique constraint on order id and sequence.
4. While the buffer holds the event numbered `next_sequence`, apply it, delete it and increment `next_sequence`.

All four steps run in one `@Transactional` method, so an event is either held or applied, never both.

```java
@Transactional
public void accept(String payload) throws JsonProcessingException {
    ShipmentEvent event = json.readValue(payload, ShipmentEvent.class);
    OrderStatusRow order = applier.lockOrCreate(event.orderId());
    // ... ignore an event that was already applied or is already held, else persist it
    PendingShipmentEvent next;
    while ((next = PendingShipmentEvent.<PendingShipmentEvent>find("orderId = ?1 and sequence = ?2",
            order.orderId, order.nextSequence).firstResult()) != null) {
        applier.record(order, json.readValue(next.payload, ShipmentEvent.class));
        next.delete();
        order.nextSequence++;
    }
}
```

Both replicas can receive events of the same order, because the key puts them in different partitions. Each creates the order's row first with an insert that does nothing on a conflict, so two replicas handling the order's first event cannot both insert. The row lock then serialises them: the second waits until the first commits and sees its changes, so no event is applied twice and none is applied out of turn.

{% include excalidraw.html file="04-resequencer" alt="Replica B receives events 2 and 3 and stores them in the pending table; replica A receives event 1, and under the order's row lock events 1, 2 and 3 are applied in order and deleted from the table" caption="Figure 4.3: Hold in the database, apply from the next expected sequence" %}

The same stall that leaves an order SHIPPED now ends DELIVERED. The unpaused replica receives ShipmentInTransit and ShipmentDelivered, finds that number 1 is missing, and stores both. Nothing is applied and the table holds two rows for the order. When the paused replica resumes and receives ShipmentScheduled, one transaction applies 1, 2 and 3 in order and empties the table.

The buffer handles the other orderings the same way. Events arriving as 3, 2, 1 are applied as 1, 2, 3. With 1 and 3 delivered, event 3 waits until 2 arrives. A duplicate is ignored. A gap in one order does not hold another order's events, because the lock and the counter belong to one order. Two replicas that receive an order's first two events at the same moment end with 1 and 2 applied and nothing held.

A missing event holds that order's later events indefinitely: nothing here gives up on a gap, so a lost event needs a timeout or a repair decision. Only that order waits. The buffer is a database table, so every event that arrives early costs an insert and a delete. The lock makes events of the same order wait for each other; events of other orders do not wait for it.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/04-ordering-and-partitioning
mvn -B verify
./verify.sh
```

The Maven build runs twelve tests across both services against Kafka and PostgreSQL from Dev Services, with three partitions. Four of them compute the expected partition of every key with Kafka's own murmur2 and compare it with the placement the broker reports, for all three strategies; the others cover arrival order and the buffer, including two replicas that handle the same order at the same moment, repeated 20 times. `verify.sh` starts Kafka, PostgreSQL, the LGTM stack, `shipping-service` and two `order-service` replicas, runs the stalled-replica order under four configurations and prints `28/28 checks passed`. When the Scheduled and Delivered partitions of an event-type run belong to the same replica, the pause cannot change the order in which events are applied, so the script stops with an error instead of reporting a pass. It also records how long each replica was paused and checks that every pause stayed under the 45 second session timeout.

Change one thing and watch a guard fail:

- Export `SHIPPING_PARTITION_KEY=event-type` before `./verify.sh`. The first run now spreads the order across partitions: check 3 fails because the three events no longer share the key's partition, and check 5 fails because the unpaused replica applies events while the other is paused. Checks 6, 7 and 9 may fail as well, depending on which replica owns the partition of the order id.
- Compare the third and fourth runs. They differ only in `RESEQUENCE=true`: the third ends SHIPPED with Delivered applied first, the fourth ends DELIVERED in lifecycle order.
- Export `RESEQUENCE=true` before `./verify.sh`. The buffer is now on in the event-type run as well, so the negative witness stops failing: checks 14, 15 and 16 fail because the order no longer ends SHIPPED.

## When not to use it

Keying by entity id does not order events across entities, and it leaves the partition count as the limit on parallelism. A buffer holds an event until every earlier event of its order arrives, and it costs a database row for everything it holds and a row lock per event. If the events can share a key, use the key. If order only matters for the final state, a version or sequence number checked at the write can reject an older event with no buffer at all.

> **🤖 Explore with your agent**
> "In examples/04-ordering-and-partitioning, publish one order through PartitionKeyStrategy ORDER_ID and then EVENT_TYPE (SHIPPING_PARTITION_KEY), pause the order replica that owns the ShipmentScheduled partition each time, and compare the OrderStatus and the order_status_history rows. State which key keeps ShipmentDelivered after ShipmentScheduled, and how ShipmentBuffer puts them back using the sequence number."
> Check: order-id ends DELIVERED in lifecycle order; event-type ends SHIPPED with ShipmentDelivered applied before ShipmentScheduled.

## References

- Gregor Hohpe and Bobby Woolf, *Enterprise Integration Patterns*, Addison-Wesley, 2003: [Resequencer](https://www.enterpriseintegrationpatterns.com/patterns/messaging/Resequencer.html)
- Apache Kafka documentation, Introduction, "Main Concepts and Terminology": [kafka.apache.org/documentation](https://kafka.apache.org/documentation/)
