---
title: "Transactional outbox"
order: 8
part: getting-events-out-reliably
permalink: /docs/08-transactional-outbox/
description: "An order and the event that announces it are two writes to two systems. The outbox turns them into one transaction in the database and lets the transaction log carry the event to Kafka."
duration: "45 minutes"
---

`order-service` stores an order and publishes `OrderPlaced` on `edp.orders.placed`. The code does the obvious thing: save the row, send the record. It works until the process dies between the two lines. Then the order exists and no event ever says so, and the inventory service never reserves the stock. Reverse the two lines and the failure reverses too: a record announces an order whose commit failed.

{% include excalidraw.html file="08-dual-write-gap" alt="Commit first then publish: a crash between the two leaves an order without an event. Publish first then commit: a failed commit leaves an event without an order" caption="Figure 8.1: The two orders of a dual write and how each one fails" %}

## Pattern: Transactional Outbox

### The problem

PostgreSQL and Kafka have no shared transaction. A service that changes its own state and announces the change writes to both, and any failure between the two writes leaves them disagreeing. Retrying does not help when the process itself is gone, and publishing before the commit cannot be taken back. Kafka transactions are scoped to a single Kafka cluster and cannot enlist a database, so the atomic unit has to be something the database alone can commit.

### The solution

**A Transactional Outbox stores the event as a row in the same database transaction as the state change, and a separate relay publishes the row to the broker.**

Chris Richardson names the parts: the sender writes the business change and the message in one transaction, the message sits in an outbox table, and a message relay sends it. The event exists if and only if the order does. The relay may publish a message more than once, so consumers must tolerate duplicates.

Two relays are common. A polling publisher queries the outbox table on a schedule and marks or deletes what it sent; the Enterprise Integration Patterns with Camel book builds that one. A log tailer reads the outbox inserts from the database's transaction log instead. It adds no query load, needs no `published` flag and sees rows in commit order, and it is the relay here: Debezium reads the PostgreSQL write-ahead log through a logical replication slot, and its Outbox Event Router turns each inserted row into a Kafka record.

{% include excalidraw.html file="08-outbox-wal-router" alt="OrderService writes the order and the outbox row in one transaction; Debezium reads the outbox insert from the write-ahead log and the router publishes it to edp.orders.placed" caption="Figure 8.2: The order and its event commit together; the log carries the event out" %}

### How Quarkus and Camel model it

The write side is the Debezium Outbox extension for Quarkus. An event class implements `ExportedEvent`, and firing it as a CDI event inside a transaction inserts one row into the outbox table:

```java
@Transactional
public void place(Order order) {
    save(order);
    events.fire(OrderPlacedEvent.of(OrderPlaced.of(order), json));
}
```

`OrderPlacedEvent` supplies the aggregate id (the order id, which becomes the Kafka key), the aggregate type, the event type, a timestamp and the payload as a JSON tree. One additional field, `topic`, carries the name of the destination topic from `Topics.ORDERS_PLACED` into its own column:

```java
@Override
public Map<String, Object> getAdditionalFieldValues() {
    return Map.of("topic", Topics.ORDERS_PLACED);
}
```

```properties
quarkus.debezium-outbox.additional-fields=topic:string
quarkus.debezium-outbox.remove-after-insert=true
```

Flyway owns the table. It has the extension's default name and columns plus `topic`, and Hibernate creates nothing, so a mapping that drifts from the migration fails validation at boot. `remove-after-insert` deletes the row in the same transaction that inserts it. The insert is in the log, which is all the connector needs, and the table does not grow.

The read side is a connector configuration, not code. It captures only the outbox table and applies the router:

```json
"table.include.list": "orders.outboxevent",
"slot.name": "edp_order_outbox",
"transforms": "outbox",
"transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
"transforms.outbox.route.by.field": "topic",
"transforms.outbox.route.topic.replacement": "${routedByValue}",
"transforms.outbox.table.expand.json.payload": "true",
"transforms.outbox.table.fields.additional.placement": "type:header:eventType"
```

Without `route.by.field`, the router sends every row to `outbox.event.<aggregatetype>`. Pointing it at the `topic` column and replacing the whole topic name with its value puts the record on `edp.orders.placed`. The payload column holds JSON text, and `table.expand.json.payload` publishes it as a JSON object instead of an escaped string. The record key is the aggregate id. The row's `id` becomes a record header, which a consumer can use to drop duplicates, and the event type becomes the `eventType` header. The router drops the delete that `remove-after-insert` produces; it never reaches Kafka.

The connector is registered with a `PUT` to Connect's REST API once the service has created the table. The publication that Debezium creates for the slot is filtered to that one table.

Camel has a small part. The comparison code that shows what the outbox prevents is two routes, one per `RouteBuilder`. `DualWriteCommitFirstRoute` calls the `@Transactional` `save` method and then sends the record with the Camel Kafka component:

```java
from(ENDPOINT)
        .routeId("dual-write-commit-first")
        .bean("orderService", "save")
        .process(e -> crash.afterCommit(e.getMessage().getBody(Order.class).id()))
        .setBody(e -> OrderPlaced.of(e.getMessage().getBody(Order.class)))
        .setHeader(KafkaConstants.KEY, simple("${body.orderId}"))
        .marshal().json()
        .toF("kafka:%s", Topics.ORDERS_PLACED);
```

`DualWritePublishFirstRoute` sends first and saves second. `CrashSwitch` is demo code and not part of the pattern: for the order ids named in an environment variable it halts the JVM right after the commit, which is what a power cut or an out-of-memory kill looks like to the code. Both comparison routes and the outbox path run the same crash.

A duplicate order id fails at commit with a unique violation. That surfaces as a `RollbackException` caused by Hibernate's `ConstraintViolationException`, and an exception mapper turns it into a 409. The whole transaction rolls back, so the refused request leaves no order row and no outbox row, and no event.

What the guarantee covers is the hand-off from the database to the log. Everything after it is Connect's: the connector stores its position in Kafka and in the slot, and after a restart it may deliver the last records again. The outbox gives at-least-once delivery, which is why the row id travels as a header.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/08-transactional-outbox
mvn -B verify
./verify.sh
```

The Maven build runs the order service against PostgreSQL and Kafka from Dev Services: the order row and the outbox row are written together, a rolled-back order leaves neither, a duplicate is refused without a second row, the commit-first route loses an event when the crash switch fires, and the publish-first route invents one when its commit fails. `verify.sh` starts Kafka, PostgreSQL, Connect, the LGTM stack and the service, registers the connector and prints `27/27 checks passed`.

The checks read the effects, not the status codes:

- Placing `O-1001` leaves exactly one record on `edp.orders.placed`, keyed `O-1001`, with an `id` header, `eventType` `OrderPlaced` and the order as a JSON object. The order row exists, the outbox table is empty, and no `outbox.event.*` topic was created.
- A second `O-1001` is refused with 409, and after a wait there is still one record and one row.
- With the crash switch set, `O-2001` through the outbox never gets a response and the container exits with 137, as after a SIGKILL. After a restart its order row exists and exactly one record for it arrives. The same crash after the commit of the commit-first route leaves the order row for `O-2002` and no record for it after a wait. With no crash switch the same commit-first route publishes exactly one event, so the loss comes from the crash and not from the route.
- Publish first, then commit: a second order with the same id fails at commit, and the topic holds two records for one order.
- Connect is stopped and five orders `O-4001` to `O-4005` are placed. All five are accepted, nothing reaches the topic while Connect is down, and after it starts again all five arrive in the order they committed. The topic has one partition so that this order is visible; with several, only the order per key holds.
- A trace and logs for `order-service` reach Tempo and Loki.

Change one thing and watch a guard fail:

- Run `REMOVE_AFTER_INSERT=false ./verify.sh`. Check 8 fails: the outbox rows stay in the table. The records on the topic are the same, because the connector reads the insert, not the table.
- Remove the `route.by.field` and `route.topic.replacement` lines from `connect/order-outbox.json`. Checks 6, 7, 9, 11, 17 and 24 fail, 21 of 27 pass: the records go to `outbox.event.Order`, so everything that reads `edp.orders.placed` finds nothing.
- Stop Kafka and place an order. Through the outbox the request returns 201 in about 10 ms, because the service never talks to Kafka, and the event arrives within seconds of the broker coming back. Through the commit-first route the order is committed, the request fails with a 500 after 60 seconds (the producer's blocking timeout) and no event is ever published. Through the publish-first route the request also fails after 60 seconds, and there is no order row either.
- Place one order, wait a minute, then run `docker kill edp-connect` during a burst of 200 orders and start it again. Every order arrives. In one run the topic held 337 records for 201 orders: 136 `id` headers appeared twice, because the killed connector had stored no offset since the first order and read the log again from there. A consumer that must not process an event twice has to drop them. The wait matters. Connect stores offsets once a minute, and a connector that has never stored one takes a new snapshot after the restart (of an empty outbox table) and streams from the current end of the WAL, past changes the slot still holds. Whether that loses events depends on timing: in one run killed before the first offset the burst lost 3 of 200 events; in two others every change had been read before the kill and nothing was lost.

## When not to use it

An outbox costs a table, a connector and a replication slot to operate. A slot that nobody reads keeps the write-ahead log on disk, so a stopped connector is an alarm and not a harmless state. The event reaches Kafka after the commit, not with it, so a reader of the topic sees it a moment after a reader of the database does.

If a lost or phantom event is acceptable, such as a metric or a cache hint, publishing from the request is simpler. If the service has no database transaction to join, for example because it only forwards messages, there is nothing to make atomic. When the events are the state itself, event sourcing removes the second write instead of making it safe. When the data is already in tables the service cannot change, Transaction Log Tailing reads those tables directly, with no outbox rows.

> **🤖 Explore with your agent**
> "In examples/08-transactional-outbox, set CRASH_AFTER_COMMIT_FOR for two order ids, place one through OrderService.place and one through DualWriteCommitFirstRoute, restart the service, and compare which of them has an event on edp.orders.placed. State the rule that makes the outbox side safe."
> Check: the outbox order has exactly one event after the restart; the commit-first order has an order row and no event.

## References

- Chris Richardson, [Pattern: Transactional outbox](https://microservices.io/patterns/data/transactional-outbox.html), microservices.io
- Strimzi blog, [Exactly-once semantics with Kafka transactions](https://strimzi.io/blog/2023/05/03/kafka-transactions/)
- Apache Kafka documentation, [Producer configs](https://kafka.apache.org/43/configuration/producer-configs/) (`max.block.ms`)
- Chris Richardson, [Pattern: Transaction log tailing](https://microservices.io/patterns/data/transaction-log-tailing.html), microservices.io
- Debezium documentation, [Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html)
- Debezium documentation, [Outbox Quarkus Extension](https://debezium.io/documentation/reference/stable/integrations/outbox.html)
- Debezium documentation, [Debezium connector for PostgreSQL](https://debezium.io/documentation/reference/stable/connectors/postgresql.html)
- PostgreSQL documentation, [Logical Decoding Concepts](https://www.postgresql.org/docs/18/logicaldecoding-explanation.html)
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 6: Integrating Event-Driven Architectures with Existing Systems
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapter 2: Anatomy of Message-Driven Systems
- Enterprise Integration Patterns with Camel, chapter 15: [Producer and Transactional Patterns](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/15-producer-patterns/) (the polling outbox)
- Cloud-Native Design Patterns, chapter 4: [Data](https://patterncatalyst.github.io/cloud-native-design-patterns/docs/04-data/)
