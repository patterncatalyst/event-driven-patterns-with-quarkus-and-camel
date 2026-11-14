---
title: "CQRS and projections"
order: 16
part: event-sourcing
permalink: /docs/16-cqrs-and-projections/
description: "A reader asks questions the write model was not built for. A projection applies the public events to a table shaped for those questions, moves only forward, and tells the reader how current it is."
duration: "55 minutes"
---

The `Order` aggregate in chapter 14 answers one question: may this command happen. It replays one stream to answer it. A customer asks something else: where are all my orders, and where is the one I paid for on Monday? The answer needs facts that live in three services, for many orders at once, and the aggregate holds none of them. `order-service` here is only the answering side, a read model fed by the topics the order, payment and shipping services already publish.

{% include excalidraw.html file="16-command-query-split" alt="Order, payment and shipping services publish to six public topics; the projector in the query side applies each event to the order_view table with a guarded upsert, and the two query endpoints read only that table; the lag window sits between the topics and the view" caption="Figure 16.1: Writers publish, the projector applies, readers query only the view" %}

## Pattern: CQRS

### The problem

The write model of chapter 14 is shaped for one job: load one stream, decide, append. A customer's order list does not fit it. The events table has no index by customer, its payloads are the owner's private format, and it holds none of the payment or shipping facts. Each way of answering from it, or around it, costs something.

Reading the owner's store couples every reader to a schema that belongs to someone else. Asking each owning service for each order at read time makes the list as slow and as available as the slowest of three services, once per order. Copying one owner's state, as chapter 3 does for `order_copy`, solves a narrower problem: one owner, one fact per order.

Fowler, who first heard the pattern from Greg Young, states the core idea as using a different model to update information than the model used to read it. Young adds why: the command side wants consistent, normalised data, and the query side can be eventually consistent and denormalised to avoid joins.

### The solution

**CQRS keeps a read model apart from the write model and feeds it from the write model's events, so each side can have the shape its questions need.**

The read model is a materialized view: disposable, because it can be rebuilt from the events, and never written by a request. Three decisions keep it correct. Every column is written by the one event that owns it, so a payment never touches the shipment. Every update only moves forward and is safe to apply twice, so the order of arrival and redelivery stop mattering. And every answer says how current it is, so a reader can judge a stale one.

### How Quarkus and Camel model it

`order_view` has one row per order. `OrderView` is a read-only Panache entity over it, and `OrderViewResource` answers from it without touching Kafka. Writing is the projector's job alone, with one native upsert per event, a single `INSERT ... ON CONFLICT DO UPDATE` statement. `OrderViewProjector` sets only the columns its event owns. The payment upsert:

```sql
INSERT INTO orders.order_view (order_id, payment_status, last_event_at)
VALUES (:id, :status, :at)
ON CONFLICT (order_id) DO UPDATE SET
    payment_status = EXCLUDED.payment_status,
    last_event_at = GREATEST(order_view.last_event_at, EXCLUDED.last_event_at), updated_at = now()
```

An event for an order the view has not seen inserts a partial row, and the later `OrderPlaced` fills in the customer. The status a reader sees is derived, not stored: `OrderView.status()` returns the furthest fact the row holds, as an `OrderStatus` from the shared domain model, with CANCELLED terminal.

Six SmallRye channels feed the projector, one per topic and one consumer group each:

```java
@Incoming("delivered")
@Blocking
public CompletionStage<Void> delivered(Message<Events.ShipmentDelivered> message) {
    return apply(message, projector::delivered);
}

private <T> CompletionStage<Void> apply(Message<T> message, BiConsumer<T, Instant> projection) {
    Instant produced = message.getMetadata(IncomingKafkaRecordMetadata.class).orElseThrow().getTimestamp();
    projection.accept(message.getPayload(), produced);
    lag.record(Duration.between(produced, Instant.now()));
    return message.ack();
}
```

The projector runs in its own transaction, and the record is acknowledged after it commits. A crash between the two replays the record, which the upsert tolerates. That is why the view needs no inbox table (chapter 11): a status column set by an upsert is idempotent by itself. A counter would not be, and the view keeps none; summaries are computed by query.

`mp.messaging.incoming.<channel>.pausable=true` makes each channel stoppable by name through the injected `ChannelRegistry`. Production code has no use for that. The demo endpoints do, and they are labelled as demo code.

Camel takes the other shape. `OrderViewRoute` is one consumer on all six topics, with `choice()` on the `KafkaConstants.TOPIC` header, `unmarshal().json(...)` and a call to the same projector bean:

```java
from("kafka:" + String.join(",", Topics.ORDERS_PLACED, Topics.ORDERS_CANCELLED, Topics.PAYMENTS_PROCESSED,
        Topics.SHIPPING_SCHEDULED, Topics.SHIPPING_IN_TRANSIT, Topics.SHIPPING_DELIVERED)
        + "?groupId=order-view-camel&autoOffsetReset=earliest&sessionTimeoutMs=10000")
    .choice()
        .when(header(KafkaConstants.TOPIC).isEqualTo(Topics.ORDERS_PLACED))
            .unmarshal().json(JsonLibrary.Jackson, Events.OrderPlaced.class)
            .bean(projector, "placed(${body}, ${exchangeProperty.at})")
        // four more when() branches, one per topic
        .otherwise()
            .unmarshal().json(JsonLibrary.Jackson, Events.ShipmentDelivered.class)
            .bean(projector, "delivered(${body}, ${exchangeProperty.at})")
```

| | Six channels | One Camel route |
|---|---|---|
| Consumers and groups | Six of each | One of each |
| Where lag is read | Per group, one partition each | One group, six partitions |
| Holding one topic back | Pause that channel | Stop the route, which reads all six topics |
| Projector | Shared | Shared |

The route is the smaller configuration and the channels are the finer control. Both write through the same guarded projector, so the view is identical.

## Read lag

The view is behind its sources by the time an event takes to travel: producer, topic, consumer, transaction. Usually that is short. When a consumer is paused or slow, it is as long as the stall, and a reader sees the old state.

{% include excalidraw.html file="16-read-lag-window" alt="ShipmentDelivered is produced while the delivered channel is paused; reads at one second and at four seconds return SHIPPED with the same asOf; after resume the view returns DELIVERED and the lag timer records the time from the record timestamp to the acknowledgement" caption="Figure 16.2: A paused topic leaves the view stale, and asOf shows it" %}

Two things make that staleness a property the reader can use. `asOf` on every answer is the record timestamp of the last event applied. A reader that sees SHIPPED with an `asOf` from yesterday knows something is behind; one that sees it from a second ago knows the order was shipped a second ago. And `order.view.lag` is a timer of the time from each record's timestamp to its acknowledgement, exported as a metric, so the lag can be graphed and alerted on.

What the view does not give is read-your-writes. A customer who has just placed an order may list their orders and not see it. The write side's response is the place to confirm the order; the list catches up. Fowler notes that separate models raise the likelihood of eventual consistency.

The same timer is the check on throughput. A projector that applies slower than events arrive falls behind by the difference, and the lag grows with the backlog until the traffic stops. A burst of 250 orders (1,250 records) was applied with a 95th percentile lag of 318 to 334 ms across three runs. With each event delayed by 50 ms, it was 12,881 ms.

## Order across topics

The six topics are read by six consumers. Kafka orders records within one partition (chapter 4), and these are six partitions, so nothing orders `ShipmentInTransit` before `ShipmentDelivered`. If the delivered channel is ahead of the in-transit channel, the view meets them in the wrong order.

{% include excalidraw.html file="16-cross-topic-order" alt="ShipmentScheduled and ShipmentDelivered are applied, then ShipmentInTransit arrives late; with the forward-only guard the row stays DELIVERED, and without it the last writer wins and the row regresses to IN_TRANSIT" caption="Figure 16.3: A late status must not move the view back" %}

A last-writer-wins upsert turns that into a wrong answer: an order delivered on Tuesday shows as in transit again. The shipment upsert refuses it in SQL:

```sql
ON CONFLICT (order_id) DO UPDATE SET shipment_status = EXCLUDED.shipment_status, carrier = EXCLUDED.carrier,
    tracking_number = EXCLUDED.tracking_number, last_event_at = GREATEST(order_view.last_event_at, EXCLUDED.last_event_at)
WHERE orders.shipment_rank(EXCLUDED.shipment_status) > orders.shipment_rank(order_view.shipment_status)
```

`shipment_rank` is an immutable SQL function that numbers the lifecycle. The `WHERE` makes the statement a no-op for an event that does not advance the row, which also makes a redelivered record harmless. It lives in the database statement, not in Java, so that the check and the write are one step and no other channel can write the row between them.

The guard fits this view because the facts are a lifecycle, where "further" is defined. For a column with no order, such as an address that can be changed twice, arrival order does not decide the answer and the event needs a version or a timestamp from its owner. Where the order of events matters, the owner keys them to one partition (chapter 4).

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/16-cqrs-and-projections
mvn -B verify
./verify.sh
```

`verify.sh` pauses one of the six topics, resets the consumer groups to the earliest offset and rebuilds the view through a Camel route. Watch a paused topic leave the order stale, with an `asOf` that says so, until the topic resumes. It prints `29/29 checks passed`.

Try one change and rerun:

- `PROJECTION_GUARD=off ./verify.sh` (demo code): a late `ShipmentInTransit` replaces DELIVERED and the reader sees SHIPPED.
- `PROJECTOR_DELAY_MS=50 ./verify.sh` (demo code): the burst outruns the projector and the lag grows with the backlog.

## When not to use it

Most services should not start here. Fowler warns that for most systems the pattern adds risky complexity, that it should apply to a bounded context and not a whole system, and that a reporting database fed from the same store is the lighter alternative when only the queries are demanding.

A read model is eventually consistent. If a reader must see its own write at once, or two readers must agree at the same instant, read from the write side. The Azure Architecture Center's CQRS pattern lists this cost, and adds that the read side can serve stale data and the application must be able to cope with a user acting on it.

A view that needs events the owner keeps private cannot be built from the public topics. Reading the owner's events means either tailing its store or publishing more events, and that is a decision for the owner. Chapter 9 covers tailing the log of a database.

A rebuild is a replay of the topics, so the topic's retention limits how far back one can go: `retention.ms` is the maximum time a log is kept before old segments are discarded. A view that must be rebuilt from the beginning needs retention that long, or a different source.

> **🤖 Explore with your agent**
> "In examples/16-cqrs-and-projections, pause the delivered channel, produce ShipmentDelivered for an order, and compare what OrderViewResource returns with what OrderViewProjector has applied. Then produce ShipmentInTransit after ShipmentDelivered with and without PROJECTION_GUARD=off, and state which column each of the six events in order_view is allowed to write."
> Check: while the channel is paused a read returns SHIPPED with an older asOf and the group lag is 1, and the late ShipmentInTransit leaves DELIVERED in place only with the guard.

## References

- Greg Young, [CQRS Documents](https://cqrs.wordpress.com/wp-content/uploads/2010/11/cqrs_documents.pdf), 2010: the sections Command and Query Responsibility Segregation, and CQRS and Event Sourcing
- Martin Fowler, [CQRS](https://martinfowler.com/bliki/CQRS.html), 2011
- Microsoft Azure Architecture Center, [CQRS pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/cqrs) and [Materialized View pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/materialized-view)
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapter 5: CQRS and chapter 6: Event Sourcing
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 7: Denormalization and Eventification and chapter 16: Eventual Consistency
- Quarkus, [Apache Kafka Reference Guide](https://quarkus.io/guides/kafka) and [Hibernate ORM with Panache](https://quarkus.io/guides/hibernate-orm-panache)
- SmallRye Reactive Messaging, [Pausable Channels](https://smallrye.io/smallrye-reactive-messaging/4.37.0/concepts/pausable-channels/)
- Apache Camel, [Kafka component](https://camel.apache.org/components/4.22.x/kafka-component.html) (several topics in one consumer) and [Choice](https://camel.apache.org/components/4.22.x/eips/choice-eip.html)
- PostgreSQL documentation, [INSERT](https://www.postgresql.org/docs/18/sql-insert.html) (`ON CONFLICT DO UPDATE ... WHERE`)
- Apache Kafka, [Managing consumer groups](https://kafka.apache.org/43/operations/basic-kafka-operations/) (describe a group and its lag) and [topic configuration](https://kafka.apache.org/43/configuration/topic-configs/) (`retention.ms`)
- Chapter 3: Event-carried state transfer, for `order_copy`; chapter 4 for order within one partition; chapter 11 for the inbox; chapter 14 for the write side
