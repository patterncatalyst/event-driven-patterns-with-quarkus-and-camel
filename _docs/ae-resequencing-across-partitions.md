---
title: "Appendix E: Resequencing across partitions in depth"
order: 40
marker: "E"
label: "Appendix E"
part: appendices
permalink: /docs/ae-resequencing-across-partitions/
description: "A per-order buffer in PostgreSQL works because an order's events share a key and a database row. When several producers and topics carry one order, only a sequence that spans all of them can restore order, and the Camel Resequencer holds its records in memory."
duration: "50 minutes"
---

An order's three shipment events arrive from two producers on three topics, each keyed to scatter across partitions. A per-order buffer in PostgreSQL, which keeps held events as rows and applies them in the transaction that releases them, puts events back in order, but it needs every event of an order to reach one table with its own number. Here the events of many orders interleave on several topics, the only ordering fact is a number that a single sequencer stamped on every event, and the service must apply them in that order and publish them, keyed by order id, to `edp.shipping.shipments`.

{% include excalidraw.html file="ae-stream-vs-batch" alt="Top: arrivals 6, 9, 3, 5, 1, 2 enter the stream resequencer, which holds them and applies 1, 2, 3, 4 in global order. Bottom: a batch of six is sorted by order and sequence, and event 1 of the first order sits in the second batch, so it is applied after event 2" caption="Figure E.1: The stream form orders everything it sees; the batch form orders only inside a batch" %}

## Pattern: Resequencer across partitions

### The problem

Kafka orders records inside one partition. An order whose events are written by different producers, or to different topics, spans partitions, and a consumer reads partitions at different speeds, so the service sees an event before the one that caused it. Re-keying cannot help, because the producers and the topics are not the service's to change.

The Camel Resequencer is the tool for this, in two forms that look alike and answer different questions.

### The solution

**A resequencer across partitions holds each arriving event in memory until an ordering rule says it may be released, and releases events in that order, so the order it restores is exactly as wide as the rule: one global sequence orders everything, a per-order number orders only within a batch.**

Hohpe and Woolf define the Resequencer as a stateful filter that collects and re-orders messages so they can be published in a specified order. Camel implements it as a batch resequencer and a stream resequencer (Apache Camel, Resequence EIP). The batch form collects up to a size or a time, sorts what it has and sends it on. The stream form sends an event as soon as the one before it has been sent, detecting gaps between integer sequence numbers.

### How Quarkus and Camel model it

One `order-service` reads the three per-event topics in one consumer group. A route parses each event and sets the headers the resequencer reads: the global sequence, and a sort key made of the order id and the per-order sequence. `RESEQUENCER` selects the form when the routes are built, and one route per form ends in the same apply route.

```java
from("direct:stream")
        .resequence(header("seq")).stream().capacity(capacity).timeout(timeout)
        .to("direct:apply");

from("direct:batch")
        .resequence(header("sortKey")).batch().size(batchSize).timeout(batchTimeout)
        .to("direct:apply");
```

The apply route writes one row per event to PostgreSQL through a Panache entity, where the identity column records the order of application, and sends the event to `edp.shipping.shipments` with the order id as the record key. The shipments topic is then the ordered stream a per-key consumer can assume: one order, one partition, events 1, 2, 3.

## A global sequence or a per-order one

The stream form can only release an event when it knows the event before it. That works when the numbers form one run without gaps across everything the resequencer sees: 1, 2, 3 and so on over all orders. A single sequencer that stamps every event before it is published gives that run. The first event has no predecessor to confirm it, so even correctly ordered input leaves after the timeout.

The per-order number does not give the run. Order O-1 has events 1, 2, 3 and so does O-2, so the stream form sees the same value from different orders. Events with equal numbers collide. Camel's resequencer does not support duplicate messages, so an event whose number it already holds is dropped as a duplicate, with nothing logged. Only the three distinct numbers survive: 3 of 12 events are applied and 9 are lost in each run measured. `SEQUENCE_BY=order` (demo code) drives the stream form with the per-order number to show it.

The batch form sorts by order id and sequence, which is the per-order key it can use. Its limit is the batch. A batch of six that holds an order's event 2 and not its event 1 releases event 2, and event 1 arrives in the next batch and is applied after it. Sorting within a batch makes disorder rarer. It does not remove it, and a batch that closes on time instead of size makes the boundary depend on the clock.

{% include excalidraw.html file="ae-gap-timeout" alt="Events 13, 14 and 15 are applied at once, 17 and 18 wait for the missing 16 until the timeout and are then applied, and a late 16 is either applied last or refused with an exception depending on rejectOld" caption="Figure E.2: A gap holds later events until the timeout, and the missing event is never reported" %}

## Gaps, timeouts and lost events

Events 13, 14, 15, 17 and 18 arrive and 16 does not. The stream form applies 13, 14 and 15 at once, because each is the successor of the one before. It holds 17 and 18 until the timeout, then releases them and moves on. Camel documents the rule: after a timeout the resequencer stops waiting for the number it expected and goes on to the next one. Nothing is logged for the gap, and if 16 was lost, no message says so. The gap is skipped, and the order that needed 16 carries on without it.

If 16 turns up afterwards, `rejectOld` decides. Off, the default, the late event is applied when it arrives, after 17 and 18, so the apply order is 13, 14, 15, 17, 18, 16. On, the resequencer throws, and the route's error handler decides what to do with an event that arrived too late to be placed. Which is right depends on what the consumer can do with an event out of order: a last-write-wins status regresses, so refusing it is safer, while an append-only history can take it and record it.

The timeout is the latency of every gap. A short one gives up on slow events and applies them out of order. A long one holds correct events behind a lost one. It has to be longer than the slowest producer's delay and shorter than the delay a user can see.

## Restarts and memory bounds

The stream form's capacity bounds how many events it holds, 1000 by default. When the limit is reached the consumer thread blocks inside the resequencer until the timeout releases something or the gap fills. It does not release early and it does not raise, so the consumer stops polling and the group's lag grows. Backpressure is a useful property for a buffer, and it needs a timeout short enough that the stopped consumer does not pass `max.poll.interval.ms`, after which Kafka treats it as failed and rebalances the group.

The held events live only in the process. The Camel Kafka consumer commits offsets on a timer through the Kafka client's auto-commit, whether or not the resequencer has released the events it read. Records held when the service stops have therefore already been acknowledged, and a restarted consumer resumes after them: they are neither released nor read again. A normal shutdown does not flush them.

{% include excalidraw.html file="ae-restart-loss" alt="Events 502 to 506 are consumed and their offset committed, the resequencer holds them while 501 is missing, a restart empties its memory, and afterwards only 501 is applied; the per-order buffer in PostgreSQL keeps held rows across the restart" caption="Figure E.3: Held records are lost when their offsets were committed before they were released" %}

The per-order buffer is the restart-safe form for one order: its held events are rows, written in the transaction that also decides what to apply, so a restart reads them back. That safety is the reason to prefer the database buffer whenever the key can carry the order. The in-memory resequencer fits when the sequence is global, the volume is high, and a lost record is repaired from the topic or the source.

## Several producers and topics

Several producers can write the same order's events only if they agree on one sequence. A sequencer in front of them, one process that assigns the global number before the event is published, is the usual design. It is a single point of ordering and so a single limit on throughput, and it must survive its own restart without reusing a number or leaving a hole. A hole is a permanent gap that costs a timeout on every pass.

Several topics add nothing the sequence does not already cover: the consumer reads all three in one group and the resequencer sees one stream of numbered events. The partition layout then affects only the arrival order, which is what the resequencer exists to repair.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/ae-resequencing-across-partitions
mvn -B verify
./verify.sh
```

`verify.sh` scatters events of four orders over three topics, applies them through the stream form, then recreates the service for the per-order number, `rejectOld`, the batch form, a full buffer and a restart while holding. Watch the apply order and the shipments topic. It prints `23/23 checks passed`.

Try one change and rerun:

- `RESEQUENCER=none ./verify.sh` (demo code): events are applied as they arrive and the orders come out of sequence.
- `SEQUENCE_BY=order RESEQUENCER_TIMEOUT_MS=500 ./verify.sh` (demo code): the stream form runs on the per-order number and most events never leave.

## When not to use it

Keying by the entity id needs no resequencer at all, and a database buffer holds out-of-order events when the key cannot change. Use the Camel Resequencer when order must be restored across keys, the sequence is global and gapless, and losing a held event at a restart is a loss someone can repair.

A global sequencer is a bottleneck and a single point of failure. If events cannot be stamped by one process, resolve ordering where the state lives, with a version checked at the write, and accept that an older event is refused.

> **🤖 Explore with your agent**
> "In examples/ae-resequencing-across-partitions, send twelve ShipmentEvent records for four orders to edp.shipping.scheduled, edp.shipping.in-transit and edp.shipping.delivered with scattered keys, run the stream form with SEQUENCE_BY=global and then SEQUENCE_BY=order, and compare the rows of resequencing.shipment_applied. State when the stream form can be used and what happens to a missing event."
> Check: ShipmentApplyRoute writes the twelve events in global order for the global sequence, the per-order number leaves most of them unapplied with nothing logged, and a gap is skipped at the timeout.

## References

- Gregor Hohpe and Bobby Woolf, [*Enterprise Integration Patterns*](https://www.enterpriseintegrationpatterns.com/patterns/messaging/Resequencer.html), Addison-Wesley, 2003: Resequencer
- Apache Camel, [Resequence EIP](https://camel.apache.org/components/4.22.x/eips/resequence-eip.html) (batch and stream forms, capacity, timeout and `rejectOld`) and [Kafka component](https://camel.apache.org/components/4.22.x/kafka-component.html)
- Apache Kafka, [documentation](https://kafka.apache.org/documentation/): ordering within a partition and consumer offset commits
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapter 2: Anatomy of Message-Driven Systems
- [Ordering and partitioning]({{ '/docs/04-ordering-and-partitioning/' | relative_url }}): the per-order buffer in PostgreSQL
