---
title: "Idempotent consumer and inbox"
order: 11
part: getting-events-out-reliably
permalink: /docs/11-idempotent-consumer-and-inbox/
description: "Kafka delivers an event at least once, and a decrement is not safe to repeat. The inbox records the event id in the same transaction as the effect, so a duplicate record and a redelivery after a crash change nothing."
duration: "45 minutes"
---

`inventory-service` reads `OrderPlaced` from `edp.orders.placed` and reserves stock: `SKU-1` has 100 on hand and each event takes its quantity off. The outbox of the order service publishes at least once, so a restarted connector can put the same event on the topic twice. And the consumer can process a record, commit the stock and die before it tells Kafka, so the record arrives again. Each repeat takes another three off the shelf.

{% include excalidraw.html file="11-crash-before-ack" alt="A record is committed to the database and the JVM halts before the acknowledgement; after the restart the record is delivered again and the inbox insert returns zero rows, so it is skipped" caption="Figure 11.1: A crash between the commit and the acknowledgement becomes a duplicate" %}

## Pattern: Idempotent Consumer

### The problem

Kafka commits a consumer's position separately from the consumer's own writes. Acknowledge first and a crash loses the event; process first and a crash repeats it. Delivery is therefore at least once, and a duplicate record is a normal input whether it came from a relay that read the log again or from a redelivery.

Subtracting a quantity is not idempotent: the second application gives a different answer. Remembering ids in memory fails at the restart that causes the duplicate. Remembering them in another store has the dual-write problem of the outbox on the receiving side: the id and the stock are two writes with no shared transaction, so a crash between them either loses the reservation or applies it twice.

### The solution

**An Idempotent Consumer records the id of every event it has applied in the same transaction as the effect, and skips an event whose id is already recorded.**

Hohpe and Woolf describe the receiver that can safely receive the same message more than once; Chris Richardson names the form used here: the consumer stores the ids of processed messages in its database, in the transaction that handles the message, and a failed insert of the id means a duplicate. The table is an inbox. The id comes from the event itself, not from the transport, so it survives a relay that republishes: the outbox in chapter 8 puts the row id in the `id` header of the record.

{% include excalidraw.html file="11-inbox-same-transaction" alt="First delivery: insert into the inbox returns one row, the stock is decremented and both commit before the acknowledgement. Second delivery: the insert returns zero rows, nothing is decremented and the record is acknowledged" caption="Figure 11.2: One transaction holds the id and the effect" %}

### How Quarkus and Camel model it

Flyway owns two tables: the stock and the inbox.

```sql
CREATE TABLE inventory.inventory_item (
    sku              TEXT PRIMARY KEY,
    quantity_on_hand INTEGER NOT NULL CHECK (quantity_on_hand >= 0)
);

CREATE TABLE inventory.inbox (
    event_id    UUID PRIMARY KEY,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

`ReservationService` is a single transactional method. The insert uses PostgreSQL's `ON CONFLICT DO NOTHING`, so the number of rows it reports is the answer to "first time?":

```java
@Transactional
public boolean reserveOnce(UUID eventId, OrderPlaced event) {
    int inserted = em.createNativeQuery(
            "INSERT INTO inventory.inbox (event_id) VALUES (:id) ON CONFLICT (event_id) DO NOTHING")
            .setParameter("id", eventId)
            .executeUpdate();
    if (inserted == 0) {
        return false;
    }
    reserve(event);
    return true;
}
```

`reserve` is one Panache update, `quantityOnHand = quantityOnHand - ?1 where sku = ?2`, so the decrement happens in the database and not in a value read earlier. Everything in the method is one transaction. If `reserve` throws, because the SKU is unknown or the stock would go below zero, the exception rolls the transaction back and the inbox row goes with it. The id is recorded only for an effect that happened. A plain `INSERT` that waits for a unique violation would do the same job by failing the whole transaction, which turns every duplicate into an error; `DO NOTHING` makes it an ordinary return value. When two transactions insert the same id at once, the second waits for the first to finish and then inserts nothing.

`OrderPlacedInbox` is the SmallRye Reactive Messaging consumer. It takes the id from the record's header and the payload from the value:

```java
@Incoming("orders-placed")
@Blocking
public void on(ConsumerRecord<String, OrderPlaced> record) {
    UUID eventId = UUID.fromString(new String(record.headers().lastHeader("id").value(), StandardCharsets.UTF_8));
    if (!reservations.reserveOnce(eventId, record.value())) {
        duplicates.increment();
        LOG.infof("duplicate event %s skipped", eventId);
    }
}
```

SmallRye acknowledges the record when the method returns, and with the default commit strategy the offset follows shortly after. The transaction therefore commits before the acknowledgement, which is the order that makes a crash between them harmless: the stock and the id are durable, the offset is not, and the next delivery meets the inbox row. `inventory.inbox.duplicates` is a Micrometer counter and reaches the LGTM stack over OpenTelemetry with the rest of the service's metrics.

The channel's settings sit in `application.properties`: the topic, the group `inventory-inbox`, `auto.offset.reset=earliest` and the two deserializers. After a crash the replacement consumer has to wait until the broker expires the dead member's session, which is 45 seconds by default. The channel sets `session.timeout.ms=10000`, so a run does not spend most of its time waiting.

#### The Camel form

Camel's Idempotent Consumer EIP does the same job with a repository of keys. The route reads the `id` header, asks the repository, and skips the exchange when the key is known:

```java
from("kafka:" + Topics.ORDERS_PLACED + "?groupId=inventory-camel&autoOffsetReset=earliest&sessionTimeoutMs=10000")
        .routeId("idempotent-reservation")
        .transacted()
        .unmarshal().json(JsonLibrary.Jackson, OrderPlaced.class)
        .idempotentConsumer(header("id"), repository).removeOnFailure(false)
            .bean(ReservationService.class, "reserve")
        .end();
```

The repository is `JpaMessageIdRepository`, built from the CDI `EntityManagerFactory` and the CDI `TransactionStrategy` that the Camel Quarkus JPA extension provides. Inside a `.transacted()` route (the JTA extension) it joins the route's transaction, so the key row and the decrement are again one commit, and a rollback removes both. That is why `removeOnFailure(false)` is enough: nothing needs to be removed by hand. The two-argument constructor, which creates its own transaction strategy, fails when the application starts, so the strategy is injected. The key table is a Flyway migration like the others, and Hibernate only validates it.

Two other repositories do not fit. `JdbcMessageIdRepository` fails on every message under the JTA transaction of a transacted route, with a commit error. `KafkaIdempotentRepository` keeps keys in a Kafka topic, so it can never share a transaction with a database and has the gap described next. The Enterprise Integration Patterns with Camel book walks through the JDBC repository.

#### Where the key lives

Take `.transacted()` off the route and the repository commits the key by itself, before the reservation runs. If the reservation then fails, the key is stored and the effect is not. The record is redelivered, the repository says it has seen the id, and the reservation never happens. The same gap exists for any key store that cannot commit with the effect.

{% include excalidraw.html file="11-where-the-key-lives" alt="Stores that commit with the effect: the inbox table, the JPA repository in a transacted route, natural idempotence. Stores that commit apart from it: the JPA repository without a transaction, the JDBC repository under JTA, the Kafka repository and a cache in another store" caption="Figure 11.3: A key stored apart from the effect leaves a gap" %}

| Approach | Where the key is | Commits with the effect | Use it when |
|---|---|---|---|
| Inbox table | A table in the service's database | Yes, one transaction | The effect is a database write, and the code is a handler method |
| Camel `JpaMessageIdRepository`, transacted route | A table in the same database | Yes, joins the JTA transaction | The flow is already a Camel route and the effect is a database write |
| Camel `JpaMessageIdRepository`, route not transacted | The same table | No, the key commits first | Never for a database effect |
| Camel `JdbcMessageIdRepository` under JTA | A table | Not usable: fails on every message | Outside JTA only |
| Camel `KafkaIdempotentRepository` | A Kafka topic | No, never | The effect is outside any database transaction and a rare duplicate is tolerable |
| Natural idempotence | None | Not needed | The effect is an upsert or a state set, keyed by the order id |

Natural idempotence is the cheapest form when the effect allows it. "Set the reservation for order `O-1101` to 3" can be applied twice with the same result, and needs no table. A decrement cannot be written that way without a record of what was already subtracted, which is what the inbox is.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/11-idempotent-consumer-and-inbox
mvn -B verify
./verify.sh
```

`verify.sh` produces the same event id twice, crashes the service after its effect commits, then reads the same records with the inbox off and through the Camel route. It prints `29/29 checks passed`. Watch the stock drop once per event id.

Try one change and rerun:

- `INBOX_ENABLED=false ./verify.sh`: the duplicate and the redelivery are both applied, the inbox stays empty and nothing is skipped.
- `CAMEL_TRANSACTED=false ./verify.sh`: after the failed effect the repository keeps the key, so the redelivery is skipped and the reservation is lost.
- Delete `session.timeout.ms` from the channel: every check still passes, but the group takes longer to catch up after the crash.

## When not to use it

An inbox costs a table, an insert per event and a cleanup job. The table gets one row per event and keeps growing until something deletes rows.

The inbox protects effects that can join a database transaction. If the effect is a call to another system, such as an email or a payment provider, the id cannot commit with it, and the inbox does not cover that case. If the effect is an upsert, natural idempotence needs no table. A consumer that only reads from Kafka and writes to Kafka belongs to Kafka's own transactions, which cover offsets and output together. And the pattern removes duplicates of one event id; two different events that mean the same thing, such as a user clicking twice, need a business key and a different rule.

> **🤖 Explore with your agent**
> "In examples/11-idempotent-consumer-and-inbox, set CRASH_AFTER_COMMIT_FOR=O-1103, send an OrderPlaced for O-1103 through OrderPlacedInbox and restart the service. From ReservationService.reserveOnce and the InboxEntry table, explain why the redelivery does not decrement again, then say what IdempotentRepositoryRoute loses if its route is not transacted."
> Check: SKU-1 drops by 4 exactly once and the log shows the redelivery skipped; without a transacted route a failed effect leaves the key and no reservation.

## References

- Gregor Hohpe and Bobby Woolf, [Idempotent Receiver](https://www.enterpriseintegrationpatterns.com/patterns/messaging/IdempotentReceiver.html), Enterprise Integration Patterns
- Chris Richardson, [Pattern: Idempotent Consumer](https://microservices.io/patterns/communication-style/idempotent-consumer.html), microservices.io
- Apache Camel documentation, [Idempotent Consumer EIP](https://camel.apache.org/components/4.22.x/eips/idempotentConsumer-eip.html)
- Apache Camel documentation, [JPA component](https://camel.apache.org/components/4.22.x/jpa-component.html)
- Apache Camel Quarkus documentation, [JPA extension](https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/jpa.html)
- Apache Camel Quarkus documentation, [JTA extension](https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/jta.html)
- Apache Camel documentation, [Transactional Client EIP](https://camel.apache.org/components/4.22.x/eips/transactional-client.html)
- Apache Camel documentation, [Kafka component](https://camel.apache.org/components/4.22.x/kafka-component.html) (`KafkaIdempotentRepository`)
- Quarkus guide, [Apache Kafka Reference Guide](https://quarkus.io/guides/kafka) (blocking `@Transactional` consumers, acknowledgement and commit strategies)
- Quarkus guide, [Using transactions](https://quarkus.io/guides/transaction)
- PostgreSQL documentation, [INSERT](https://www.postgresql.org/docs/18/sql-insert.html) (`ON CONFLICT`)
- Apache Kafka documentation, [Message delivery semantics](https://kafka.apache.org/43/design/design/)
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapter 2: Anatomy of Message-Driven Systems
- Enterprise Integration Patterns with Camel, chapter 15: [Producer and Transactional Patterns](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/15-producer-patterns/) (the Idempotent Receiver with a JDBC repository)
- Cloud-Native Design Patterns, chapter 5: [Event-Driven](https://patterncatalyst.github.io/cloud-native-design-patterns/docs/05-event-driven/) (exactly-once as at-least-once plus deduplication)
