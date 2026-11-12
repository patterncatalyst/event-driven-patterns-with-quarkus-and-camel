---
title: "Exactly-once with Kafka transactions"
order: 12
part: getting-events-out-reliably
permalink: /docs/12-exactly-once-with-kafka-transactions/
description: "A service that reads a record, processes it and writes a result can crash between the write and the offset commit. A Kafka transaction holds the output records and the input offsets together, and a read_committed reader never sees half of it."
duration: "50 minutes"
---

`payment-service` reads `InventoryReserved` from `edp.inventory.reserved` and writes one `PaymentProcessed` to `edp.payments.processed` for each. Two things have to happen for every input: the output record is written, and the consumer's position moves past the input. They live in different places, and the process can die between them. Commit the position first and the payment is never written. Write the payment first and the restarted service reads the same input again and writes a second payment for the same order.

{% include excalidraw.html file="12-read-process-write" alt="PaymentProcessor reads a batch from edp.inventory.reserved; the output records and the consumer offsets are written inside one Kafka transaction and a commit marker on both partitions makes them take effect" caption="Figure 12.1: Output records and input offsets in one transaction" %}

## Pattern: Transactional Client

### The problem

Retrying does not fix this. The producer's idempotence setting removes duplicates caused by its own retries, but a restarted process is a new producer with no memory of what the old one wrote, and it starts reading from the last committed offset. Delivery is at-least-once, and the second payment is the price. The usual repair is a deduplication step written by the consumer, which moves the problem into code that has to be right under every crash.

### The solution

**A Transactional Client groups its reads and writes into one transaction, so that they all take effect or none of them do.**

Hohpe and Woolf describe it as a client that can specify transaction boundaries with the messaging system. On Kafka the boundary covers a read-process-write loop. A producer with a `transactional.id` writes the output records and sends the input offsets to the same transaction, the position is stored as a record in an internal topic, and the commit applies both. The reader of the output sets `isolation.level=read_committed`. Kafka's own definition of the goal is that each message is processed once and only once.

Three rules make it work:

- **Fencing.** One `transactional.id` belongs to one producer instance. A new producer that initializes with the same id gets a higher epoch, the old one is fenced off, and the old one's open transaction is aborted. That is what makes a restart safe without waiting for a timer.
- **The last stable offset.** A `read_committed` consumer receives records in offset order and stops at the first record of an open transaction, so one open transaction holds back everything behind it.
- **Aborted records stay in the log.** An abort does not remove what was written. A `read_committed` reader skips it, and a `read_uncommitted` reader, the default, returns it.

{% include excalidraw.html file="12-aborted-batch-visibility" alt="The output log while a transaction is open: read_committed stops at the last stable offset and read_uncommitted sees the open records; after the restart the aborted copies stay in the log and only read_uncommitted returns them" caption="Figure 12.2: What each isolation level reads while a transaction is open and after it is aborted" %}

### How Quarkus and Camel model it

The write side is `KafkaTransactions`, a SmallRye Reactive Messaging emitter that wraps a transactional producer. `PaymentProcessor` consumes a whole batch per poll and writes its outputs inside one transaction:

```java
@Inject
@Channel("payments-processed")
KafkaTransactions<PaymentProcessed> transactions;

@Incoming("inventory-reserved")
public Uni<Void> process(Message<ConsumerRecords<String, InventoryReserved>> batch) {
    return transactions.withTransactionAndAck(batch, tx -> {
        for (ConsumerRecord<String, InventoryReserved> record : batch.getPayload()) {
            tx.send(KafkaRecord.of(record.key(), PaymentProcessed.of(record.value())));
        }
        return Uni.createFrom().voidItem();
    });
}
```

`withTransactionAndAck` begins the transaction, runs the function, adds the batch's consumer offsets to the transaction, commits, and acknowledges the batch. If the function fails or the transaction is marked for abort, the transaction aborts and the consumer's position goes back to the last committed offset. The channel configuration matters as much as the code:

```properties
mp.messaging.incoming.inventory-reserved.batch=true
mp.messaging.incoming.inventory-reserved.commit-strategy=ignore
mp.messaging.incoming.inventory-reserved.failure-strategy=ignore
mp.messaging.incoming.inventory-reserved.isolation.level=read_committed
mp.messaging.outgoing.payments-processed.transactional.id=payment-service-tx
mp.messaging.outgoing.payments-processed.acks=all
```

`commit-strategy=ignore` is required: the consumer must commit nothing itself, because the transaction does. `failure-strategy=ignore` makes a failed batch reset and read again instead of stopping the channel. Batch mode is the recommended form: the Quarkus guide warns of a significant performance cost when each record is its own transaction. The `transactional.id` is set explicitly so the broker's record of it can be inspected by name. Each running instance needs its own id, because two producers with the same id fence each other, and the SmallRye emitter allows one transaction at a time per producer.

The Camel Kafka component has a transactional producer too. A producer endpoint with `transacted=true`, or an explicit `transactionalId`, opens a transaction and commits it when the exchange finishes routing, or aborts it when the exchange failed:

```java
from("kafka:" + Topics.INVENTORY_RESERVED + "?groupId=payment-camel&autoOffsetReset=earliest")
        .unmarshal().json(JsonLibrary.Jackson, InventoryReserved.class)
        // map each InventoryReserved to a PaymentProcessed
        .marshal().json(JsonLibrary.Jackson)
        .to("kafka:" + Topics.PAYMENTS_PROCESSED + "?transacted=true&transactionalId=camel-payment-tx");
```

Every output is atomic. The consumer is an ordinary one, though: it commits its offsets on a timer, outside any transaction, and the component source never sends offsets to a transaction. Without an explicit `transactionalId`, the id is made from the endpoint and route ids, which is a name nobody chose. `CamelTransactedPaymentRoute` runs under the `camel-transacted` profile as the comparison. Same input, same mapping, same crash, one difference: the offsets.

{% include excalidraw.html file="12-camel-transacted-vs-eos" alt="The same crash with and without the offsets in the transaction: SmallRye aborts the whole batch and the restart produces ten outputs once each; the Camel route has committed seven outputs and not the offset, so the restart produces seven of them twice" caption="Figure 12.3: The same crash, with and without the offsets in the transaction" %}

`CrashSwitch` is demo code and not part of the pattern. For the order id named in an environment variable it waits half a second, so the records already sent reach the broker, and then halts the JVM. That is what a power cut looks like to a transaction that is still open.

What the guarantee covers is Kafka to Kafka. Transactions are only supported inside a single Kafka cluster, and a database cannot enlist in one. Kafka's design notes say why the position has to be stored in the same place as the output: many destinations do not support a two-phase commit. Chaining a Hibernate ORM transaction with a Kafka transaction in Quarkus commits the database transaction first and the Kafka transaction after it, which orders two commits and does not make them atomic. For a database on either side, the outbox on the way out (chapter 8) and the inbox on the way in (chapter 11) are the tools; the transaction covers the hop between topics.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/12-exactly-once-with-kafka-transactions
mvn -B verify
./verify.sh
```

The Maven build runs the service against a Kafka broker from Dev Services: a batch of five inputs gives exactly five payments to a `read_committed` reader, an aborted transaction leaves its record in the log and out of the `read_committed` view, and the input offset is committed together with the output. `verify.sh` starts Kafka, the LGTM stack and the service, and prints `TBD checks passed`.

The checks read the effects, not the exit codes:

- Five orders `O-5001` to `O-5005` produce exactly five payments for a `read_committed` reader, one per order, keyed by the order id, with payment id `P-O-5001` and so on, status `COMPLETED` and the amount of the input. The group `payment-service` has committed offset 5, and `kafka-transactions.sh describe` shows `payment-service-tx` as `CompleteCommit`.
- With the crash switch set for `O-5108`, ten orders `O-5101` to `O-5110` are produced in one burst. The container exits with 137, and a `read_uncommitted` reader sees TBD of the first seven payments: the open transaction wrote them. A `read_committed` reader sees none of the ten, and the group is still at offset 5.
- After a restart without the switch, a `read_committed` reader sees all ten orders, each exactly once, within 30 seconds, well below the transaction timeout of 60 seconds. The restarted producer fenced the old one and aborted its transaction, so nothing waited for the timer. A `read_uncommitted` reader sees the ten plus the TBD aborted copies, and the group is at offset 15.
- The comparison runs the Camel route with the crash at `O-6008`. After the crash a `read_committed` reader already sees payments for some of `O-6001` to `O-6007`, and the group has not committed past `O-6008`. After the restart every order has a payment and at least one of the first seven has two committed.
- A trace and logs for `payment-service` reach Tempo and Loki.

Change one thing and watch a guard fail:

- Run `PAYMENT_MODE=camel-transacted ./verify.sh`. The main phase runs on the Camel route. Checks 6 and 7 still pass, proving the crash and the partial write, and check 10 fails: the restart re-reads from the last committed offset and `read_committed` returns TBD records for the ten orders.
- Run the stack with `TX_TIMEOUT_MS=10000` and the crash switch set, produce the burst, and leave the service down. `kafka-transactions.sh describe` shows the transaction `Ongoing`, and the coordinator aborts it within about half a minute, TBD seconds in the recorded run. A `read_committed` reader sees nothing of the batch before or after, and a `read_uncommitted` reader still sees the seven written records.
- Read the output with the console consumer's default isolation level after the restart. The duplicates of the second bullet above appear: aborted records are in the log, and the default reader returns them.

## When not to use it

A transaction costs throughput; the Strimzi write-up says the effect can be significant. It costs latency for readers too: a `read_committed` consumer cannot pass an open transaction, so a producer that stalls inside one stalls every reader behind it until the coordinator's timeout aborts it. A transaction that never completes is a hanging transaction, and `kafka-transactions.sh` has `find-hanging` and `abort` for them.

A record that always fails the batch also stalls the loop. The transaction aborts, the position goes back to the last committed offset, and the same batch is read again, every time. A good record behind the bad one is never processed. Something has to keep the bad record out of the batch before it reaches the transaction, because the transaction cannot decide that.

If the outputs are keyed state that a later write simply overwrites, at-least-once delivery already yields the right result, and a transaction adds cost for nothing. If the output is a database row or an HTTP call, the transaction does not reach it. If the loop is a stream topology, Kafka Streams wraps this same consume-transform-produce cycle behind one setting, and writing it by hand is only worth it when the processing is not a topology.

> **🤖 Explore with your agent**
> "In examples/12-exactly-once-with-kafka-transactions, set CRASH_MID_BATCH_AT to O-5108, produce O-5101 to O-5110 in one burst, then restart payment-service without the switch. Compare what a read_committed and a read_uncommitted consumer return from edp.payments.processed for PaymentProcessor, and run the same crash through CamelTransactedPaymentRoute. State the rule that makes the first one exactly-once."
> Check: the read_committed reader returns each of the ten orders once; the Camel route returns at least one order twice; the rule is that the consumed offsets commit in the producer's transaction and every reader uses read_committed.

## References

- Federico Valeri, Strimzi blog, [Exactly-once semantics with Kafka transactions](https://strimzi.io/blog/2023/05/03/kafka-transactions/)
- Apache Kafka documentation, [Design: message delivery semantics and using transactions](https://kafka.apache.org/43/design/design/)
- Apache Kafka documentation, [Producer configs](https://kafka.apache.org/43/configuration/producer-configs/) (`transactional.id`, `transaction.timeout.ms`) and [Consumer configs](https://kafka.apache.org/43/configuration/consumer-configs/) (`isolation.level`)
- [KIP-98: Exactly Once Delivery and Transactional Messaging](https://cwiki.apache.org/confluence/display/KAFKA/KIP-98+-+Exactly+Once+Delivery+and+Transactional+Messaging)
- [KIP-447: Producer scalability for exactly once semantics](https://cwiki.apache.org/confluence/display/KAFKA/KIP-447%3A+Producer+scalability+for+exactly+once+semantics)
- [KIP-664: Provide tooling to detect and abort hanging transactions](https://cwiki.apache.org/confluence/display/KAFKA/KIP-664%3A+Provide+tooling+to+detect+and+abort+hanging+transactions)
- SmallRye Reactive Messaging, [Kafka transactions and exactly-once processing](https://smallrye.io/smallrye-reactive-messaging/4.37.0/kafka/transactions/)
- Quarkus, [Apache Kafka guide](https://quarkus.io/guides/kafka) (transactions, exactly-once processing, chaining with Hibernate ORM)
- Apache Camel, [Kafka component](https://camel.apache.org/components/4.22.x/kafka-component.html) (Kafka Transaction) and the producer's [transaction synchronization](https://github.com/apache/camel/blob/camel-4.22.2/components/camel-kafka/src/main/java/org/apache/camel/component/kafka/KafkaTransactionSynchronization.java)
- Gregor Hohpe and Bobby Woolf, [Transactional Client](https://www.enterpriseintegrationpatterns.com/patterns/messaging/TransactionalClient.html), Enterprise Integration Patterns
- Enterprise Integration Patterns with Camel, chapter 15: [Producer and Transactional Patterns](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/15-producer-patterns/) (Transactional Client with JMS and JDBC)
- Cloud-Native Design Patterns, chapter 5: [Event-Driven](https://patterncatalyst.github.io/cloud-native-design-patterns/docs/05-event-driven/) and chapter 6: [Stream Processing](https://patterncatalyst.github.io/cloud-native-design-patterns/docs/06-stream-processing/)
