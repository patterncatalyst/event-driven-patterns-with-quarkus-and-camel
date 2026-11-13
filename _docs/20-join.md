---
title: "Join"
order: 20
part: processing-and-coordinating-streams
permalink: /docs/20-join/
description: "shipping-service books a carrier only when stock is reserved and payment is taken, two facts on two topics that arrive in either order. A windowed join pairs them by event time; a Camel aggregator pairs them by arrival, and the two disagree."
duration: "55 minutes"
---

`shipping-service` may book a carrier only when the stock is reserved and the payment is taken. The two facts come from two services on two topics, keyed by the same order id, and either one can arrive first. Waiting for both is a join, and a join over streams carries a question that a join over tables does not: both sides keep arriving, so how long does a fact wait for its partner, and by whose clock?

{% include excalidraw.html file="20-join-window" alt="A reservation at time T opens a 30 minute window with 5 minutes of grace; a payment at T plus 2 minutes inside the window gives ReadyToShip, a payment at T plus 90 minutes does not match, and the unmatched reservation leaves as PaymentOverdue only when a later record moves stream time past T plus 35 minutes" caption="Figure 20.1: A reservation opens a window in event time, and only later records close it" %}

## Pattern: Stream-stream join

### The problem

The two facts live in two services, and services share no tables (chapter 3). A query cannot ask whether both have happened. Asking each service per order (chapter 2) turns a question about events into polling. A local copy of each (chapter 3) answers what the current state is, not whether the two facts arrived within the same business moment. What is needed is the correlation itself: two events with the same key, within a bounded time of each other, and a decision about the one that never finds its partner.

Hohpe and Woolf call the shape an Aggregator, a stateful filter that stores related messages until a complete set has arrived and then publishes one message built from them. It has nothing to do with the aggregate of chapter 14, which is a consistency boundary. Both of the forms below are Aggregators in Hohpe and Woolf's sense. They differ in how they decide that a set is complete.

### The solution

**A stream-stream join pairs two events that share a key and fall within a time window of each other, and keeps each side in state until the window closes.**

A left join keeps one more outcome: the event from the left side that found no partner is emitted on its own once the window has closed. That turns an absence into an event, which is how an order that is never paid becomes visible.

### How Quarkus and Camel model it

Kafka Streams has the join as a DSL operator. `ReadinessTopology` is a `@Produces Topology` method, and Quarkus starts and stops the engine around it:

```java
reserved.leftJoin(paid, Readiness::of,
                JoinWindows.ofTimeDifferenceAndGrace(Duration.ofMinutes(30), Duration.ofMinutes(5)),
                StreamJoined.with(Serdes.String(), reservedSerde, paidSerde).withStoreName("readiness"))
        .to(Topics.SHIPPING_ORDER_READINESS, Produced.with(Serdes.String(), readinessSerde));
```

A payment within 30 minutes of the reservation, in either direction, matches and gives `ReadyToShip`. The five minutes of grace admit a record that is late in event time. A reservation that finds no payment leaves as `PaymentOverdue`, with a null payment passed to the join function. Both source streams read their timestamp from the payload with a `PayloadTimestampExtractor`, so the window is measured on when the facts happened.

Both topics must have the same number of partitions, and the same keys must land in the same partition number (chapter 4). Streams does not check this for a stream-stream join. With three partitions on reservations and two on payments the instance starts, reports healthy and matches only the orders whose two records happen to share a partition number: 14 of 30 in each of three runs, while the Camel route, which reads both topics in one consumer, matched all 30.

Camel has no join. It has the Aggregator, correlated here on the record key:

```java
from("kafka:" + Topics.INVENTORY_RESERVED + "," + Topics.PAYMENTS_PROCESSED + "?groupId=shipping-readiness-camel...")
    // unmarshal by topic, then set the order id and a map of kind to event time as the body
    .aggregate(header("orderId"), new AddFact())
        .completionPredicate(bothKinds)
        .completionTimeout(timeoutMs)
        .aggregationRepository(repository)
        .process(this::conclude)
        .filter(header("emit"))
            .setHeader(KafkaConstants.KEY, header("orderId"))
            .to("kafka:" + Topics.SHIPPING_ORDER_READINESS_CAMEL)
```

The group completes when it holds both kinds of fact, whichever came first, or when no fact has arrived for the timeout. The exchange property `CamelAggregatedCompletedBy` says which, and `conclude` turns `predicate` into `ReadyToShip` and `timeout` into `PaymentOverdue`. The completion predicate looks for the two kinds, not for a count of two. A duplicate `InventoryReserved` (chapter 11) is also two messages, and a count would complete the group with no payment in it.

The two forms in one place:

| | Kafka Streams left join | Camel Aggregator |
|---|---|---|
| Time base | Event time from the payload | Arrival at the service |
| An unmatched reservation | Emitted after window plus grace, once a later record moves stream time | Emitted when the timeout expires, which needs only the clock |
| Late or replayed records | Placed by event time; accepted within the grace | Placed by arrival; a replay looks like a burst |
| State | Local store plus a changelog topic | A PostgreSQL row per open group |
| Partitioning | Both topics need the same partition count | None; one consumer reads both topics |
| Time to match 200 orders | About 2.6 s | About 3.1 s |

A real service runs one of the two. The example runs both on the same input, each with its own consumer group and its own output topic, so every difference is visible in one run.

## Event time and arrival time

Kafka Streams orders by the timestamp its extractor returns. A record's own timestamp is the time it reached Kafka. A record that went through an outbox (chapter 8) or a replay carries the time it was published, not the time its fact happened, so the extractor reads `occurredAt` from the payload and falls back to the record timestamp when there is none.

Camel never reads `occurredAt`. The completion timeout is the time a group has been inactive, counted on the clock of the service. **The Camel form approximates the window by arrival time, not event time.** Where records arrive close to when they happened, the two clocks agree and the aggregator is the smaller piece of code. Where they do not, the forms disagree, and two crafted pairs show it in opposite directions.

{% include excalidraw.html file="20-event-vs-arrival-time" alt="O-2003 has event times two minutes apart and arrives 20 seconds apart, so Kafka Streams matches it and the Camel aggregator, with a 10 second timeout, emits PaymentOverdue first; O-2004 has event times 90 minutes apart and arrives back to back, so Kafka Streams emits PaymentOverdue and the Camel aggregator matches it" caption="Figure 20.2: Two clocks, two answers, in opposite directions" %}

`O-2003` is reserved at 09:10 and paid at 09:12 in event time, and the two records are produced more than the 10 second timeout apart. Streams matches it. The Camel group times out first and emits `PaymentOverdue`; the payment then arrives alone and is ignored. `O-2004` is reserved at 09:20 and paid at 10:50, a replayed payment, and the two records are produced back to back. Streams sees 90 minutes and emits `PaymentOverdue`. Camel sees both facts together and emits `ReadyToShip`.

Neither is wrong about its own clock. The question is which clock the business rule means. "Payment within 30 minutes of the reservation" is a statement about when things happened, and only the event-time form can keep it when records are late, replayed or backlogged. "Payment within 10 seconds of the reservation reaching this service" is a statement about the service, and the timeout is the direct form of it. A timeout chosen as a guess at the spread of arrival times is a third thing, and it moves the answer: a longer timeout makes `O-2003` match on the Camel path as well.

Event times have to be in the past. The broker refuses a record whose timestamp is more than an hour ahead, and Kafka Streams forwards the event time as the timestamp of its output, so a payload dated in the future puts the instance in ERROR.

The window does not retract. A payment that arrives after `PaymentOverdue` has been emitted can still match while the reservation is retained in the store, and the output then holds `ReadyToShip` for an order that was already reported overdue. The join does not count that record as dropped. A consumer of the output topic has to accept two answers for one order, and chapter 11's idempotent consumer is the tool for deciding which wins.

## When one side never arrives

An inner join would leave an order that is never paid with no trace. The left join emits `PaymentOverdue` after the window and the grace have passed, but only when stream time passes them, and stream time advances only with records. An idle stream never closes its windows, so a reservation with no further traffic behind it waits in silence until some record, from any order, arrives with a later event time. A wall clock does not move it.

Camel is the other way round. The timeout is per correlation key and needs only the clock, so an unpaid reservation is reported about one timeout after it arrived: between 10.05 and 11.07 seconds in 15 runs with a 10 second timeout, the checker interval of one second included. What the timeout cannot see is event time, which is why `O-2004` slips through.

A payment that arrives first and alone forms a group with no reservation. By timeout it would be reported as overdue with nothing reserved, so the route filters it: a group without a reservation completes silently and leaves no row behind.

## State that survives a restart

Both forms hold open state, and it has to be somewhere a recreated container can find.

{% include excalidraw.html file="20-state-where" alt="Kafka Streams keeps the join store as local files and writes every put to a changelog topic, restoring the store from it after a recreate; the Camel aggregator saves each open group as a row in shipping.readiness and reads it back after a recreate" caption="Figure 20.3: Local files and a changelog on one side, a table on the other" %}

The join store is local files, and every put is also written to a changelog topic, so a recreated instance rebuilds the store by reading it. The Camel repository is `PostgresAggregationRepository` over the Agroal datasource, with a `DataSourceTransactionManager` from `camel-quarkus-sql`: each open group is a row of `shipping.readiness`, written in the same JDBC transaction as the aggregation step. After a recreate both forms finish a pair whose reservation arrived before it.

```java
new PostgresAggregationRepository(new DataSourceTransactionManager(dataSource), "shipping.readiness", dataSource)
```

The in-memory repository is the contrast, and it is demo code: `AGGREGATION_REPOSITORY=memory` keeps groups in the JVM. The Camel consumer has already committed the reservation's offset, so after a recreate the group is gone and nothing will replay it.

Two details of the restart are worth knowing. A restored group's timeout starts again from the restart, so its `PaymentOverdue` arrives one timeout after the service is back, not one timeout after the reservation. And a recreated Streams instance reports RUNNING on the liveness check about 13 seconds after the recreate. The Quarkus readiness check covers the input topics and does not wait for RUNNING. The liveness check reports the Streams state, and a deployment that routes traffic by what the stream needs waits for that one.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/20-join
mvn -B verify
./verify.sh
```

`verify.sh` produces the two crafted pairs, an unpaid reservation and a payment that follows a recreate of the service, and reads both output topics side by side. It prints `24/24 checks passed`. Watch `O-2003` and `O-2004` get opposite answers from the two forms.

Try one change and rerun:

- `AGGREGATION_REPOSITORY=memory ./verify.sh` (demo code): the reservation open at the recreate is lost on the Camel path and never becomes `ReadyToShip` there.
- `READINESS_TIMEOUT_MS=30000 ./verify.sh` (demo code): `O-2003` is no longer reported overdue on the Camel path, and `O-2005` is reported later than 15 seconds after it arrived.

## When not to use it

A window is for facts that happen close together. If the second fact can come a week later, a window either grows into a large store or drops the pair. A fact that changes slowly and is looked up (the carrier reference of chapter 19) is a table, and the lookup is a table join without a window.

If the two facts already sit in one database, a SQL join answers the question with no state to keep. If the correlation is short, live and low in volume, and arrival time is close enough to event time, the Camel aggregator is the lighter choice: no local store, no changelog, no partition rule. A Streams join also keeps both sides for longer than the window itself, so a long window on a busy topic is a large store.

> **🤖 Explore with your agent**
> "In examples/20-join, ReadinessTopology and ReadinessRoute join InventoryReserved and PaymentProcessed by order id. Produce the pair O-2003 (event times two minutes apart, records produced further apart than the route's completion timeout) and O-2004 (event times 90 minutes apart, produced back to back). From PayloadTimestampExtractor and completionTimeout, state which clock each output used and why the two disagree, then recreate the service with a reservation open and say where each form found it again."
> Check: O-2003 is ReadyToShip on the Streams output and PaymentOverdue on the Camel output, O-2004 is the opposite, and the reservation open at the recreate is paired afterwards on both outputs.

## References

- Gregor Hohpe and Bobby Woolf, [Aggregator](https://www.enterpriseintegrationpatterns.com/patterns/messaging/Aggregator.html), *Enterprise Integration Patterns*, Addison-Wesley, 2003
- Apache Kafka, [Kafka Streams DSL](https://kafka.apache.org/42/streams/developer-guide/dsl-api/) (joins, windows, co-partitioning) and [core concepts](https://kafka.apache.org/42/streams/core-concepts/) (event time, stream time, grace)
- Apache Kafka, [Kafka Streams configuration](https://kafka.apache.org/42/streams/developer-guide/config-streams/) and [testing](https://kafka.apache.org/42/streams/developer-guide/testing/)
- Quarkus, [Using Apache Kafka Streams](https://quarkus.io/guides/kafka-streams)
- Apache Camel, [Aggregate EIP](https://camel.apache.org/components/4.22.x/eips/aggregate-eip.html), [SQL component](https://camel.apache.org/components/4.22.x/sql-component.html) (the JDBC aggregation repository) and [Kafka component](https://camel.apache.org/components/4.22.x/kafka-component.html)
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 8: Stateful Event-Driven Microservices and chapter 9: Deterministic Stream Processing
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapter 9: Composing Data from Different Sources
- Enterprise Integration Patterns with Camel, chapter 13: Structural Transformation, for the Aggregator
- Chapter 2: Event notification, for fetching back; chapter 3 for the local copy; chapter 4 for keys and partitions; chapter 8 for the outbox; chapter 11 for the idempotent consumer; chapter 14 for the aggregate; chapter 19 for the table join
