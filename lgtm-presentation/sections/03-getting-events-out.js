// Getting events out reliably: outbox, log tailing, offsets, inbox,
// exactly-once read-process-write, failure classes and the dead letter topic.

"use strict";

module.exports = function (lib) {
  lib.divider(
    "Getting events out reliably",
    "The database and the broker share no transaction",
    `The patterns here are about the gap between a database commit and a Kafka record. A service that changes its own state and announces the change writes to two systems that cannot commit together, and every failure between the two writes leaves them disagreeing. The patterns here close the gap from both sides. The transactional outbox and log tailing carry changes out of PostgreSQL through its write-ahead log. Offset management decides where the reader resumes. The idempotent consumer, Kafka transactions and the dead letter channel decide what a receiver does with a duplicate, a batch that crashes midway and a record that can never be processed.`,
  );

  lib.bulletSlide(
    "TRANSACTIONAL OUTBOX",
    "Transactional outbox",
    [
      ["A transactional outbox", " stores the event as a row in the same database transaction as the state change, and a separate relay publishes the row to the broker."],
      ["Problem.", " PostgreSQL and Kafka share no transaction. A crash between save and send leaves an order with no event; reversing the writes leaves an event with no order."],
      ["Relay.", " Debezium reads the outbox insert from the write-ahead log, and its EventRouter publishes the record keyed by the aggregate id."],
      ["Guarantee.", " At least once from the database to the log. The row id travels as a header so a consumer can drop duplicates."],
      ["Measured.", " With Kafka stopped, the outbox answers 201 in about 10 ms. The commit-first route returns 500 after 60 seconds."],
    ],
    `order-service stores an order and publishes OrderPlaced. The obvious code saves the row and sends the record, and it works until the process dies between the two lines. Retrying does not help when the process is gone, and Kafka transactions cannot enlist a database, so the atomic unit has to be something the database alone can commit. Chris Richardson names the parts: the sender writes the change and the message in one transaction, the message sits in an outbox table, and a relay sends it. The event exists if and only if the order does. A polling relay queries the table on a schedule. A log tailer reads the inserts from the transaction log, adds no query load and needs no published flag.`,
  );

  lib.figSlide(
    "TRANSACTIONAL OUTBOX",
    "The two orders of a dual write",
    "08-dual-write-gap",
    "Commit then publish loses the event on a crash; publish then commit announces an order that never committed",
    `The figure shows the two orderings of a dual write and how each one fails. Commit first and a crash between the two lines leaves an order that exists with no event, so inventory never reserves the stock. Publish first and a failed commit leaves an event announcing an order that does not exist, and a published record cannot be taken back. The comparison code is two Camel routes, one per ordering, with a crash switch that halts the JVM right after the commit, which is what a power cut or an out-of-memory kill looks like to the code. The outbox path runs the same crash and loses nothing. Both comparison routes exist only to show what the pattern prevents.`,
  );

  lib.figSlide(
    "TRANSACTIONAL OUTBOX",
    "The log carries the event out",
    "08-outbox-wal-router",
    "OrderService writes the order and the outbox row in one transaction; Debezium reads the insert and routes it to edp.orders.placed",
    `The write side is the Debezium Outbox extension for Quarkus. Firing an exported event as a CDI event inside a transaction inserts one row into the outbox table, with the aggregate id that becomes the Kafka key, the event type, the payload and a topic column. Flyway owns the table, and remove-after-insert deletes the row in the same transaction, because the insert in the log is all the connector needs. The read side is configuration, not code: the connector captures only the outbox table and the EventRouter publishes each row to the topic named in its topic column, with the payload as a JSON object and the row id as a header. If the connector or Connect is down, orders placed meanwhile arrive once it returns.`,
  );

  lib.codeSlide(
    "TRANSACTIONAL OUTBOX",
    "One transaction holds both writes",
    "JAVA",
    "08-transactional-outbox/order-service/src/main/java/com/patterncatalyst/shipping/outbox/OrderService.java",
    26, 31,
    "    /** Stores the order and the event that announces it. They commit together or not at all. */",
    "order-service · OrderService.java",
    `The method is transactional. It saves the order and fires the event as a CDI event, and the Debezium Outbox extension turns the fired event into an insert on the outbox table inside the same transaction. A duplicate order id fails at commit with a unique violation, the whole transaction rolls back, and the refused request leaves no order row, no outbox row and no event. Nothing in the method talks to Kafka, so the request never waits for the broker. An outbox costs a table, a connector and a replication slot to operate, and a slot that nobody reads keeps the write-ahead log on disk, so a stopped connector is an alarm and not a harmless state.`,
  );

  lib.bulletSlide(
    "LOG TAILING",
    "Transaction log tailing",
    [
      ["Transaction log tailing", " reads the database's own transaction log and publishes each committed change as an event, without the application taking part."],
      ["Problem.", " A service needs changes to data it does not own and cannot modify. Polling misses deletes and intermediate updates, and triggers add work to the legacy transactions."],
      ["Mechanism.", " Logical decoding turns the write-ahead log into row changes. A publication names the tables. A replication slot remembers the last confirmed position."],
      ["Cost.", " Events describe rows, not business facts, so consumers are coupled to column names the table's owner can change."],
      ["Risk.", " A slot that stops advancing makes PostgreSQL keep its log until the disk fills."],
    ],
    `The warehouse system keeps its stock in a table another team owns. Nothing in it publishes anything, and a job that selects rows changed since the last run never sees a delete and sees only the last of several updates between two runs. PostgreSQL writes every change to its write-ahead log before the table, to recover after a crash. Debezium is the client that reads the slot and turns each change into an envelope with an operation, the row before and after, and the log position. It starts with a snapshot, published as r events, and then streams. Every slot needs an owner who watches it, a heartbeat for a quiet table in a busy database, and a decision for when the reader is gone for good.`,
  );

  lib.figSlide(
    "LOG TAILING",
    "The log holds every change, the slot holds a place",
    "09-wal-publication-slot",
    "Writes land in the write-ahead log; the publication filters three tables; the slot records how far the connector has confirmed",
    `Writes to the captured table, to a busy table the connector does not capture, and to the heartbeat table all land in the write-ahead log. The publication filters three tables for the connector, and the replication slot records how far it has confirmed. When the captured table is quiet and another table is busy, the log grows with changes the connector never receives, the slot stays where it was, and so does the log behind it. A heartbeat action query that writes a table in the publication brings a change back through the slot, so the connector confirms a position and the retained log falls. Inside the service, Camel reads the connector's change topic, maps the envelope to a domain document and publishes it keyed by SKU, with one tombstone for a delete.`,
  );

  lib.bulletSlide(
    "CONNECTOR OFFSETS",
    "Offsets and replication slots",
    [
      ["Connector offsets", " are a source connector's position, stored by Kafka Connect in an offsets topic. The offsets API lists, alters and resets them, only while the connector is stopped."],
      ["Problem.", " After a bad batch or a day of maintenance, where does the connector start reading, and who decides?"],
      ["The slot wins.", " PostgreSQL streams from the greater of the offset and the slot's confirmed position. Altering forward skips changes. Altering backward does not rewind."],
      ["Reset.", " With snapshot.mode=initial a reset reads the current rows as snapshot events, not the history of how they got there."],
      ["Lost slot.", " The connector stays RUNNING with no task, and the retry loop lasts about 30 minutes. Recover with stop, drop the slot, delete the offsets, resume."],
    ],
    `A source connector has no consumer group. Connect writes its position on a timer, 60 seconds by default, and a Connect process that dies between flushes restarts from the last stored position, so a consumer of the topic has to tolerate duplicates anyway. Alter is the tool for skipping a bad batch, and a reset is the tool for rebuilding the downstream copy from the table as it is now. Neither can rewind. While a connector is stopped, PostgreSQL keeps every log file from the slot's position onward, including log written by tables the connector does not capture. Setting max_slot_wal_keep_size caps the retention, and its price is a lost slot, so alert on the slot's status and the retained bytes rather than on failed tasks.`,
  );

  lib.figSlide(
    "CONNECTOR OFFSETS",
    "The server streams from the greater position",
    "10-offset-vs-slot",
    "An offset ahead of the slot skips the changes between; an offset behind the slot is ignored",
    `The offset is where the connector asks to start, and the slot is where the server is willing to start. When a client starts streaming from a slot, PostgreSQL begins at the requested position or at the slot's confirmed position, whichever is greater. An offset altered ahead of the slot makes streaming start at the offset, and everything written in between is never published. An offset altered behind the slot is clamped to the slot, so nothing before the confirmed position can be asked for again. It is not free of duplicates, though: the altered offset holds only a log position, so the last transaction delivered before the stop arrives a second time. Do not alter an offset to repair one consumer, because the change goes into the connector for every reader of the topic.`,
  );

  lib.bulletSlide(
    "IDEMPOTENT CONSUMER",
    "Idempotent consumer and inbox",
    [
      ["An idempotent consumer", " records the id of every event it has applied in the same transaction as the effect, and skips an event whose id is already recorded."],
      ["Problem.", " Kafka commits the position apart from the consumer's writes. Acknowledge first and a crash loses the event; process first and it repeats. Subtracting stock is not idempotent."],
      ["Solution.", " An inbox table. The insert uses ON CONFLICT DO NOTHING, so zero rows inserted means a duplicate. The id comes from the event, not the transport."],
      ["Order.", " The transaction commits before the acknowledgement, so a crash between them is harmless."],
      ["Cost.", " A table, an insert per event and a cleanup job. An effect in another system cannot join the transaction."],
    ],
    `inventory-service reserves stock for each OrderPlaced. The outbox publishes at least once, so a restarted connector can put the same event on the topic twice, and the consumer can commit the stock and die before it tells Kafka. Remembering ids in memory fails at the restart that causes the duplicate, and remembering them in another store has the dual-write problem on the receiving side. The id comes from the outbox row id that travels in a record header, so it survives a relay that republishes. If the decrement throws, the transaction rolls back and the inbox row goes with it, so an id is recorded only for an effect that happened. When the effect is an upsert keyed by the order id, natural idempotence needs no table.`,
  );

  lib.figSlide(
    "IDEMPOTENT CONSUMER",
    "One transaction holds the id and the effect",
    "11-inbox-same-transaction",
    "First delivery: inbox insert and decrement commit before the acknowledgement. Second delivery: insert returns zero rows",
    `On the first delivery, the insert into the inbox returns one row, the stock is decremented, and both commit before the acknowledgement. If the JVM halts between the commit and the acknowledgement, the record is delivered again after the restart. The insert then returns zero rows, nothing is decremented, and the record is acknowledged. The replacement consumer has to wait until the broker expires the dead member's session, which is 45 seconds by default, so the channel sets a 10 second session timeout to keep a run short. The same pattern is available as Camel's Idempotent Consumer EIP with a JPA repository, but only inside a transacted route, where the key row and the decrement are again one commit.`,
  );

  lib.codeSlide(
    "IDEMPOTENT CONSUMER",
    "Insert the id, then apply the effect",
    "JAVA",
    "11-idempotent-consumer-and-inbox/inventory-service/src/main/java/com/patterncatalyst/shipping/inbox/ReservationService.java",
    19, 34,
    "    /**",
    "inventory-service · ReservationService.java",
    `One transactional method answers the question of whether this is the first time. The native insert reports how many rows it wrote, and zero means the event id is already in the inbox, so the method returns false and the caller counts a duplicate. Otherwise the decrement runs in the same transaction. If reserve throws because the SKU is unknown or the stock would go negative, the transaction rolls back and the inbox row goes with it. A plain insert that waits for a unique violation would do the same job by failing the whole transaction, which turns every duplicate into an error. The decrement is one database update, not a value read beforehand.`,
  );

  lib.tableSlide(
    "IDEMPOTENT CONSUMER",
    "Where the idempotency key lives",
    ["Approach", "Commits with the effect", "Use it when"],
    [
      ["Inbox table", "Yes, one transaction", "The effect is a database write in a handler method"],
      ["Camel JPA repository, transacted route", "Yes, joins the JTA transaction", "The flow is a Camel route and the effect is a database write"],
      ["Camel JPA repository, not transacted", "No, the key commits first", "Never for a database effect"],
      ["Camel JDBC repository under JTA", "Unusable: fails on every message", "Outside JTA only"],
      ["Camel Kafka repository", "No, never", "The effect is outside any database transaction and a rare duplicate is tolerable"],
      ["Natural idempotence", "Not needed", "The effect is an upsert or a state set keyed by the order id"],
    ],
    [3.6, 3.4, 5.09],
    `A key stored apart from the effect leaves a gap. Take the transaction off a Camel route that uses the JPA repository and the key commits first. If the reservation then fails, the key is stored and the effect is not, so the redelivered record is skipped and the reservation is lost. The same gap exists for any key store that cannot commit with the effect: a key in a Kafka topic or a cache can never share a transaction with a database. The inbox pays a table and an insert per event, and it protects only effects that can join a database transaction. A consumer that only reads from Kafka and writes to Kafka belongs to Kafka's own transactions.`,
    { fontSize: 15, rowH: 0.62 },
  );

  lib.bulletSlide(
    "EXACTLY-ONCE",
    "Exactly-once read-process-write",
    [
      ["A transactional client", " groups its reads and writes into one transaction, so they all take effect or none of them do."],
      ["Problem.", " The output record and the consumer position live in different places. Commit the position first and the payment is never written. Write first and a restart writes a second payment."],
      ["Rules.", " One transactional.id belongs to one producer, and a newer epoch fences the old one. A read_committed reader stops at the first open transaction. Aborted records stay in the log."],
      ["Quarkus.", " KafkaTransactions sends the outputs and the batch's offsets in one transaction. The channel commits nothing itself."],
      ["Scope.", " Kafka to Kafka only. A database cannot enlist, and the transaction costs throughput."],
    ],
    `payment-service reads InventoryReserved and writes one PaymentProcessed for each. The producer's idempotence setting removes duplicates caused by its own retries, but a restarted process is a new producer with no memory of what the old one wrote. A producer with a transactional id writes the outputs and sends the input offsets to the same transaction, and the commit applies both. A read_committed consumer receives records in offset order and stops at the first record of an open transaction, so a producer that stalls inside one stalls every reader behind it until the coordinator's timeout aborts it. A record that always fails the batch also stalls the loop, because the batch aborts and is read again. Keep the bad record out before the transaction.`,
  );

  lib.figSlide(
    "EXACTLY-ONCE",
    "Output records and input offsets together",
    "12-read-process-write",
    "PaymentProcessor reads a batch; the outputs and the consumer offsets commit in one Kafka transaction",
    `PaymentProcessor reads a batch from edp.inventory.reserved. The output records and the consumer offsets are written inside one Kafka transaction, and a commit marker on both partitions makes them take effect together. If the process dies in the middle of a burst, the transaction aborts, the consumer position goes back to the last committed offset, and the restart produces every output exactly once for a reader that uses read_committed. The default isolation level, read_uncommitted, still returns the aborted copies. Chaining a Hibernate transaction with a Kafka transaction commits the database first and the Kafka transaction after it, which orders two commits and does not make them atomic. For a database on either side, use the outbox on the way out and the inbox on the way in.`,
  );

  lib.codeSlide(
    "EXACTLY-ONCE",
    "Batch and offsets in one transaction",
    "JAVA",
    "12-exactly-once-with-kafka-transactions/payment-service/src/main/java/com/patterncatalyst/shipping/payments/PaymentProcessor.java",
    30, 41,
    '    @Incoming("inventory-reserved")',
    "payment-service · PaymentProcessor.java",
    `The method takes a whole batch per poll and hands it to withTransactionAndAck. That call begins the transaction, runs the function, adds the batch's consumer offsets to the transaction, commits and acknowledges the batch. Every output goes out through the transaction. If the function fails, the transaction aborts and the position returns to the last committed offset. The crash switch in the middle of the loop is demonstration code that halts the JVM for one order id; it is not part of the pattern. Batch mode is the recommended form, because the Quarkus guide warns of a significant performance cost when each record is its own transaction. Each running instance needs its own transactional id.`,
  );

  lib.bulletSlide(
    "DEAD LETTER CHANNEL",
    "Failure classes and the dead letter topic",
    [
      ["A dead letter channel", " sends a record the receiver cannot process to a separate topic with its original content, after retrying only the failures that can still succeed."],
      ["Problem.", " Stopping holds the partition. Retrying helps only if the next attempt can succeed. The unsound option logs the failure and commits the offset."],
      ["Classify.", " A truncated JSON document never parses, and a quantity of 0 never becomes valid. A locked row is free again in a moment."],
      ["Keep.", " The original key and bytes. SmallRye adds headers for the exception, the reason, and the source topic, partition and offset."],
      ["Cost.", " A retry in place holds the partition, and the dead letter topic needs an owner."],
    ],
    `Six records arrive on one partition. One is cut off in the middle of its JSON, one is not JSON at all, one asks for a quantity of 0, one is a good order for a SKU whose row another transaction holds, and two are ordinary. Each needs a different answer. Retrying the first three wastes time while the partition waits, and dead-lettering the locked row throws away an order that would have succeeded. With nothing configured, the SmallRye channel stops at a bad record and reports readiness DOWN, and the default Camel error handler logs the record, commits and carries on, so it is kept nowhere. Skipping a record is a decision about ordering: when one record depends on the record before it, stopping the channel is correct.`,
  );

  lib.figSlide(
    "DEAD LETTER CHANNEL",
    "Retry what can succeed, dead-letter the rest",
    "13-failure-classes",
    "An invalid record is tried once and dead-lettered; a locked row is retried; a record still failing after the last retry is dead-lettered",
    `The figure sorts the failures by a single question: can trying again change the outcome. An unparseable or invalid record is tried once and goes to the dead letter topic. A record that fails on a locked row is retried after a delay, and succeeds once the lock is released. A record still failing after the last retry goes to the dead letter topic as well. A lock timeout of two seconds on the row turns the wait into an ordinary failure the retry policy acts on. Without it, the attempt waits until the holder releases the row and succeeds on its first and only attempt, so the policy never runs. A retry in place suits failures that clear in seconds; a dependency that is down for minutes holds the partition for as long.`,
  );

  lib.codeSlide(
    "DEAD LETTER CHANNEL",
    "Redeliver or dead-letter",
    "JAVA",
    "13-retry-and-dead-letter/inventory-service/src/main/java/com/patterncatalyst/shipping/deadletter/DeadLetterRoute.java",
    27, 45,
    "    @Override",
    "inventory-service · DeadLetterRoute.java",
    `The Camel route says the same things in its own terms. The error handler redelivers every failure up to the limit, one second apart, and then writes the original body to the dead letter topic. The onException clause overrides it for the two exceptions that cannot succeed, with no redelivery. useOriginalBody has to be on the clause as well as on the error handler: without it the dead letter channel is handed the unmarshalled object, cannot serialize it as bytes, and the record is lost. What Camel does not do is describe the failure: it writes the original value and no reason, source topic, partition or offset. The SmallRye channel writes those as headers, and reads bytes so the dead letter carries what arrived.`,
  );
};
