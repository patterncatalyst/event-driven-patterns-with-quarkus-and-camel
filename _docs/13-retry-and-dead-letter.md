---
title: "Retry and dead letter"
order: 13
part: getting-events-out-reliably
permalink: /docs/13-retry-and-dead-letter/
description: "A consumer meets three kinds of bad record: one that never parses, one that is never valid and one that fails only while a row is locked. Retry the third, dead-letter the first two, and keep their original bytes."
duration: "45 minutes"
---

`inventory-service` reads `OrderPlaced` from `edp.orders.placed` and takes the ordered quantity out of stock. Six records arrive on its one partition. `O-7002` is cut off in the middle of its JSON. `O-7006` is not JSON at all. `O-7003` asks for a quantity of 0. `O-7004` is a good order for a SKU whose row another transaction holds for the next twelve seconds. `O-7001` and `O-7005` are ordinary. Each needs a different answer, and the partition is read in order, so whatever the consumer does with one record decides when the next is read.

{% include excalidraw.html file="13-failure-classes" alt="Three failure classes: an unparseable or invalid record is tried once and goes to the dead letter topic; a record that fails on a locked row is retried and then reserved; a record still failing after the last retry goes to the dead letter topic" caption="Figure 13.1: Retry what can succeed later, dead-letter what never will" %}

## Pattern: Dead Letter Channel

### The problem

A consumer that cannot process a record has three sound options and one unsound one. It can stop, which keeps the record and every record behind it waiting. It can try again, which helps only if the next attempt can succeed. It can move the record aside and carry on. The unsound option is to log the failure and commit the offset anyway, which is what a Camel route's default error handler does: the record is gone and the log line is the only trace.

Hohpe and Woolf name two of the cases. A message that makes no sense to the receiver belongs on an Invalid Message Channel, and a message the system cannot deliver belongs on a Dead Letter Channel. On Kafka both are a topic, and the useful distinction is a different one: whether trying again can change the outcome. A truncated JSON document never parses. A quantity of 0 never becomes valid. A locked row is free again in a moment. Retrying the first two wastes time while the partition waits, and dead-lettering the third throws away an order that would have succeeded.

{% include excalidraw.html file="13-poison-pill-defaults" alt="With nothing configured, the SmallRye channel stops at a bad record and reports readiness DOWN, and the Camel route logs the record, commits its offset and carries on, so the record is kept nowhere" caption="Figure 13.2: Unconfigured, a bad record either stops the consumer or disappears" %}

### The solution

**A Dead Letter Channel sends a record the receiver cannot process to a separate topic with its original content, after retrying only the failures that can still succeed.**

Three decisions make it work. Classify the failure: the consumer says which exceptions are final and which are worth another attempt. Bound the retries, so a failure that never clears ends up on the dead letter topic instead of holding the partition forever. Keep the original record, key and bytes, so that whoever repairs it starts from what was sent and not from what the consumer made of it.

### How Quarkus and Camel model it

The SmallRye path declares the retry on the method and the dead letter on the channel. The channel reads the value as bytes and the method parses it:

```java
@Incoming("orders-placed")
@Blocking
@Retry(maxRetries = 8, delay = 1000, retryOn = Exception.class,
        abortOn = { IllegalArgumentException.class, JsonProcessingException.class })
public void on(byte[] raw) throws Exception {
    reservations.reserve(json.readValue(raw, OrderPlaced.class));
}
```

`abortOn` is the classification: an unparseable record and a validation failure end the retry at once, and any other exception, including the lock timeout, is retried after one second. The retry count is set in configuration under the method's key, so an environment variable can change it without a rebuild:

```properties
retry.max=${RETRY_MAX:8}
com.patterncatalyst.shipping.deadletter.OrderPlacedConsumer/on/Retry/maxRetries=${retry.max}

mp.messaging.incoming.orders-placed.value.deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer
mp.messaging.incoming.orders-placed.failure-strategy=${FAILURE_STRATEGY:dead-letter-queue}
mp.messaging.incoming.orders-placed.dead-letter-queue.topic=edp.orders.placed-dlq
mp.messaging.incoming.orders-placed.dead-letter-queue.key.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.incoming.orders-placed.dead-letter-queue.value.serializer=org.apache.kafka.common.serialization.ByteArraySerializer
```

Reading bytes is the point. A channel with a typed deserializer hands the method an `OrderPlaced`, and when validation fails afterwards the dead letter carries either nothing or the object serialized again, not the record that arrived. With bytes, the value written to `edp.orders.placed-dlq` is the value read, and the key is kept. SmallRye adds headers that say what happened: the exception class, the reason, and the source topic, partition and offset. The offset header is the position of the record in `edp.orders.placed`, which is where to look for it after the dead letter has been repaired. When the failure strategy is `dead-letter-queue`, the consumer's offset is committed once the record is on the dead letter topic, so the consumer moves on.

`@Blocking` puts the method on a worker thread, which a blocking database call needs. The method calls `ReservationService`, a separate `@Transactional` bean. The retry wraps the method and each attempt calls the bean again, so every attempt is a new transaction: a failed attempt has rolled back and left nothing behind.

```java
@Transactional
public void reserve(OrderPlaced order) {
    if (order.quantity() <= 0) {
        throw new IllegalArgumentException("quantity must be positive: " + order.quantity());
    }
    InventoryItemEntity item = InventoryItemEntity.<InventoryItemEntity>find("sku", order.itemSku())
            .withLock(LockModeType.PESSIMISTIC_WRITE)
            .withHint("jakarta.persistence.lock.timeout", 2000)
            .firstResult();
    item.quantityOnHand -= order.quantity();
}
```

The lock hint matters. Without it the attempt waits until the holder releases the row, about ten seconds of the twelve here, and succeeds on its first and only attempt: the retry policy never runs and the record behind it waits that long. With it, PostgreSQL gives up after two seconds, Hibernate raises a `PessimisticLockException`, and the retry sees an ordinary failure.

A retry in place has a price that the figure shows. The partition has one reader, and while it retries `O-7004` it reads nothing else. `O-7005` is a valid order for another SKU, and it waits for the whole retry.

{% include excalidraw.html file="13-retry-blocks-partition" alt="While O-7004 is retried against a locked row, O-7005 behind it on the same partition is not read; after the lock is released O-7004 is reserved and O-7005 follows" caption="Figure 13.3: A retry in place holds back the records behind it" %}

SmallRye has a retry that does not block, the `delayed-retry-topic` failure strategy, which republishes the record to delay topics. The documentation marks it experimental.

A retried effect must also survive being applied twice. Here the failed attempt rolled back, so a retry decrements the stock once. A record that is delivered again after a commit is applied again, as the last experiment shows. Chapter 11 puts a key in the same transaction to stop that.

The Camel route says the same things in the route's own terms. `DeadLetterRoute` is the comparison, started with the `camel` profile:

```java
errorHandler(deadLetterChannel("kafka:" + Topics.ORDERS_PLACED_DLQ
        + "?valueSerializer=" + ByteArraySerializer.class.getName())
        .useOriginalBody().maximumRedeliveries(retryMax).redeliveryDelay(1000));
onException(JsonProcessingException.class, IllegalArgumentException.class)
        .maximumRedeliveries(0).useOriginalBody();

from("kafka:" + Topics.ORDERS_PLACED + "?groupId=inventory-camel&autoOffsetReset=earliest"
        + "&valueDeserializer=" + ByteArrayDeserializer.class.getName())
        .unmarshal().json(JsonLibrary.Jackson, OrderPlaced.class)
        .bean(ReservationService.class, "reserve");
```

The error handler redelivers every failure up to the limit. The `onException` clause overrides it for the two exceptions that cannot succeed, with no redelivery. `useOriginalBody` restores the body that entered the route before the dead letter is written. It has to be on the clause as well as on the error handler: without it the dead letter channel is handed the unmarshalled `OrderPlaced`, cannot serialize it as bytes, and the record is lost. Enriching dead letters and the rest of the builder are covered in Enterprise Integration Patterns with Camel, chapter 5.

What Camel does not do is describe the failure. Its dead letter channel writes the original value and no reason, source topic, partition or offset. The two designs differ in other ways too:

| | SmallRye Reactive Messaging | Camel route |
|---|---|---|
| Retry is declared | `@Retry` on the method, count overridable by config | `maximumRedeliveries` on the error handler |
| Not worth retrying | `abortOn` exception list | `onException` clause with no redelivery |
| Dead letter content | Key and original bytes; headers for exception, reason, source topic, partition and offset | Original value bytes only |
| Parse failure | Parsed in the method, so it is classified like any exception | Parsed in the route, so it goes through the same handler |
| Nothing configured | `fail`: the channel stops and readiness goes DOWN | The default error handler logs, skips the record after one attempt and commits |
| Offset committed | After the record is written to the dead letter topic | On the consumer's auto-commit timer, after the exchange; with the default error handler also for a record it gave up on |
| Blocked while retrying | The partition | The partition |
| Retry without blocking | `delayed-retry-topic`, experimental, not used here | Not used here |
| 20 s hold with `max.poll.interval.ms` at 10 s | The record is not delivered again | The group rebalances and the batch is delivered again |

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/13-retry-and-dead-letter
mvn -B verify
./verify.sh
```

The Maven build runs the service against PostgreSQL and Kafka from Dev Services: 7 tests cover a truncated record that reaches the dead letter topic with its bytes, its source topic and its source offset, an invalid record attempted once, a locked row retried until it is free, `fail` stopping the channel, and the Camel route dead-lettering with the original body. `verify.sh` starts Kafka, PostgreSQL, the LGTM stack and the service, and prints `31/31 checks passed`.

The checks read the effects:

- With `SKU-14` locked for twelve seconds and the six records produced in one batch, `O-7004` is attempted more than once while the lock is held, and `SKU-14` and `SKU-15` both still hold 100: `O-7005` waits. After the lock is released `SKU-14` and `SKU-15` hold 99, and `SKU-11` holds 99 for `O-7001`.
- The dead letter topic holds `O-7002` and `O-7006` with exactly the bytes that were sent. `O-7002` names `edp.orders.placed` and the offset it had there. `O-7003` carries its original bytes, source topic and offset, names the quantity rule in its reason and was attempted once. Three records are on the topic, none a valid order.
- The consumer group has no lag, so the offsets of the dead-lettered records were committed.
- The service restarts with the `camel` profile, `SKU-14` is locked again and the same six records are produced. The route redelivers `O-7004` while the lock is held, the stock ends as before, and the dead letter topic gains the same three records.
- A trace and logs for `inventory-service` reach Tempo and Loki, and Prometheus holds the records consumed by its Kafka consumer.

Change one thing and watch a guard fail:

- Run `FAILURE_STRATEGY=fail ./verify.sh`. The channel stops at `O-7002`, whose offset is never committed (the group stays at offset 1 with 5 records of lag), `SKU-15` stays at 100 and no dead letter is written. Readiness answers 503. Checks 4, 6, 7, 9 to 14, 16 and 24 to 28 fail; the lock preconditions pass.
- Run `RETRY_MAX=0 ./verify.sh`. In both phases `O-7004` is attempted once, dead-lettered, and `SKU-14` stays at 100, while `O-7005` is reserved at once. Checks 4 to 6, 14, 15, 19 to 21 and 26 fail. The lock preconditions pass, so the retry limit is the cause.
- Run `RETRY_MAX=2 ./verify.sh`. The lock outlasts three attempts, so `O-7004` is dead-lettered after the last one: a failure that was transient but did not clear within the limit. The end matches `RETRY_MAX=0`, after 3 attempts instead of one, in both phases; checks 6, 14, 15, 21 and 26 fail.
- Run `CAMEL_DEAD_LETTER_CHANNEL=false ./verify.sh`. The SmallRye phase is unchanged; in the Camel phase the default error handler gives up on `O-7002`, `O-7003` and `O-7006` after one attempt and commits their offsets, and the lock timeout on `O-7004` is dropped the same way: `SKU-14` stays at 100 while `O-7005` is reserved. The group ends with no lag, because the offsets of the records it gave up on were committed. Nothing is dead-lettered in that phase, and checks 19 to 21 and 24 to 26 fail.
- Add `CAMEL_BREAK_ON_FIRST_ERROR=true` to that run. In the Camel phase the failed record is polled again, and the valid record before it is applied again on every pass: `O-7001` was applied 100 times and `SKU-11` fell from 100 to 0 in under a minute. `SKU-14` and `SKU-15` stay at 100, the group commits no offset (lag 6) and checks 19 and 21 to 26 fail.
- Run `HOLD=20 MAX_POLL_INTERVAL_MS=10000 ./verify.sh`. The SmallRye consumer attempts `O-7004` seven times during the 20 second hold, is not rebalanced and `SKU-14` ends at 99. In the Camel phase the consumer misses its poll deadline, the group rebalances and the batch is delivered again: `SKU-11`, `SKU-14` and `SKU-15` all end at 98, each reserved twice. Checks 21 to 26 fail.

## When not to use it

A dead letter topic needs an owner. Someone has to watch its depth, read the reasons and decide whether a record is repaired and republished, discarded or a sign of a bug in the producer. Without one, records collect on the topic and nobody acts on them.

Skipping a record is a decision about ordering. The consumer carries on with the records behind it, so they are processed without the one that failed. When a later record depends on an earlier one, as in a ledger, stopping the channel with `fail` and fixing the cause is the correct behaviour, and the readiness probe is how it gets noticed.

Retrying in place suits failures that clear in seconds. A dependency that is down for minutes holds the partition for as long, and a long retry runs into the consumer's poll interval, as the Camel route in the last experiment shows. A retry is only safe when the effect can be applied twice, which is the work of chapter 11.

> **🤖 Explore with your agent**
> "In examples/13-retry-and-dead-letter, lock SKU-14 for twelve seconds, produce a truncated record, an order with quantity 0 and a valid order for SKU-14 to edp.orders.placed, and compare what OrderPlacedConsumer and ReservationService do with each against DeadLetterRoute under the camel profile. State which exceptions OrderPlacedConsumer retries and which it dead-letters, and why."
> Check: the truncated and the zero-quantity records are on edp.orders.placed-dlq with their original bytes after one attempt each, and the valid order is reserved after at least one retry.

## References

- Gregor Hohpe and Bobby Woolf, [Dead Letter Channel](https://www.enterpriseintegrationpatterns.com/patterns/messaging/DeadLetterChannel.html) and [Invalid Message Channel](https://www.enterpriseintegrationpatterns.com/patterns/messaging/InvalidMessageChannel.html), *Enterprise Integration Patterns*, Addison-Wesley, 2003
- SmallRye Reactive Messaging, [Receiving Kafka Records](https://smallrye.io/smallrye-reactive-messaging/4.37.0/kafka/receiving-kafka-records/): failure strategies, dead letter queue, deserialization failures and the experimental delayed retry topic
- Quarkus, [Apache Kafka Reference Guide](https://quarkus.io/guides/kafka), sections on commit strategies and handling of failures, and [SmallRye Fault Tolerance](https://quarkus.io/guides/smallrye-fault-tolerance), `@Retry`, `retryOn` and `abortOn`
- Quarkus, [Hibernate ORM with Panache](https://quarkus.io/guides/hibernate-orm-panache), locking
- Apache Camel, [Dead Letter Channel](https://camel.apache.org/components/4.22.x/eips/dead-letter-channel.html), [Error Handler](https://camel.apache.org/manual/error-handler.html), [Exception Clause](https://camel.apache.org/manual/exception-clause.html) and the [Kafka component](https://camel.apache.org/components/4.22.x/kafka-component.html) (`breakOnFirstError`, `pollOnError`)
- Apache Kafka documentation, [Consumer configs](https://kafka.apache.org/43/configuration/consumer-configs/) (`max.poll.interval.ms`)
- PostgreSQL documentation, [Client Connection Defaults](https://www.postgresql.org/docs/18/runtime-config-client.html) (`lock_timeout`) and [SELECT](https://www.postgresql.org/docs/18/sql-select.html), the locking clause
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 18: Handling Bad Data in Event Streams
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapter 2: Anatomy of Message-Driven Systems
- Enterprise Integration Patterns with Camel, chapter 5: [Channel Reliability](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/05-channel-reliability/) (the dead letter channel builder and enrichment), chapter 15: [Producer and Transactional Patterns](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/15-producer-patterns/) (duplicates from redelivery)
