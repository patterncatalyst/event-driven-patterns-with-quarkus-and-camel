---
title: "Appendix D: Pattern map, glossary and EIP Camel cross-reference"
order: 39
marker: "D"
label: "Appendix D"
part: appendices
permalink: /docs/ad-pattern-map-and-glossary/
description: "Every pattern of the book on one map by part, the terms the chapters use in a short glossary, and where Enterprise Integration Patterns with Camel covers the same pattern, by chapter and by event."
duration: "20 minutes"
---

Thirty-five chapters name about forty patterns and a larger set of Kafka, Debezium, Kubernetes and Camel terms. A reader who holds a problem needs the pattern, a reader who meets a word needs its meaning, and a reader who knows Enterprise Integration Patterns with Camel (EIP Camel) needs to know which of its chapters goes with which of these. Three tables answer those questions.

## The map

{% include excalidraw.html file="ad-pattern-map" alt="Seven columns, one per part, each listing the patterns of its chapters as boxes each with its chapter number" caption="Figure D.1: Every pattern of the book, in the part that teaches it" %}

The parts move outward from one service. Part 1 is the message and how it travels between two services. Part 2 makes publishing and consuming safe against a crash. Part 3 stores the history and reads it back in other shapes. Part 4 combines streams and coordinates services that share no transaction. Part 5 makes the flow visible. Part 6 runs the same services on Kubernetes.

Pick a pattern by the symptom:

| Symptom | Pattern | Chapter |
|---|---|---|
| The consumer needs facts the event omits | Event Notification, Event-carried state, Content Enricher | 2, 3, 19 |
| Events of one order arrive out of order | Partitioned ordering, Resequencer | 4 |
| One slow job holds back the jobs behind it | Competing Consumers on a share group | 7 |
| The row commits and the event is lost | Transactional Outbox, Transaction Log Tailing | 8, 9 |
| The same event is applied twice | Idempotent Consumer | 11 |
| A crash between write and offset commit | Transactional Client | 12 |
| One record fails every time | Dead Letter Channel | 13 |
| Load grows with the length of the history | Snapshot, Projection from history | 15, 17 |
| A stored event has the wrong shape | Upcaster, Compatible evolution | 18 |
| Two facts on two topics must meet | Stream-stream join | 20 |
| A step failed after others committed | Saga, Orchestrated saga | 22, 23 |
| Every service is healthy and orders wait | Measure the flow | 26 |

## Glossary

| Term | Meaning | Chapter |
|---|---|---|
| Acknowledgement | The producer setting that says how many replicas must hold a record before the send succeeds; in a share group, the per-record answer from a worker | 5, 7 |
| Aggregate | One object, reached through its root, that owns every rule about one entity and rebuilds from its own events | 14 |
| Backlog | Changes in the replication slot that the connector has not yet confirmed | 26 |
| Claim check | A reference to a large payload kept in a store outside the message | 3 |
| Compaction | Topic cleanup that keeps the newest record for each key | 3, 19 |
| Compensation | A new event or command that undoes the business effect of an earlier committed step | 22 |
| Connector | A Kafka Connect task that moves records between Kafka and another system | 9, 10 |
| Consumer group | Consumers that share a topic's partitions, one partition to one member | 6 |
| Dead letter | A record the receiver cannot process, kept with its original bytes on a separate topic | 13 |
| Debezium | The change data capture project that reads the PostgreSQL log | 9 |
| Event time | The time stamped when the event happened, as opposed to the time it arrived | 20, 21 |
| Fencing | Aborting the open transaction of an older producer instance that has the same transactional id | 12 |
| Field manager | The name under which a client applies a resource; the API server records it as the owner of the fields it sets | 30 |
| Fold | Applying events one after another to produce state | 14, 15 |
| Grace period | How long a window stays open for late records after its end | 21 |
| Idempotent | Applying it twice has the effect of applying it once | 11 |
| Inbox | The table of applied event ids, written in the same transaction as the effect | 11 |
| KRaft | Kafka's own controller quorum, which replaces ZooKeeper | 28 |
| Lag | The distance between a group's committed offset and the end of a partition | 26 |
| LRA | Long Running Action: a saga held by a coordinator that calls each participant's registered compensation | 23 |
| Node pool | A set of Kafka nodes with one role, controller or broker, that Strimzi runs together | 28 |
| Offset | A record's position in a partition; a connector's offset is its position in the database log | 6, 10 |
| Optimistic append | Writing an event at the version that was loaded and failing if another writer got there first | 14 |
| Outbox | A table whose rows are events, written in the same transaction as the state change | 8 |
| Partition | One ordered log of a topic; the only unit in which Kafka orders records | 4 |
| Poison record | A record that fails every time it is processed | 13 |
| Projection | A read model built by folding the history and kept current by applying each new event once | 16, 17 |
| Read committed | The consumer isolation level that hides records of open and aborted transactions | 12 |
| Reconcile | An operator's loop that moves the cluster toward the declared resource | 28, 29 |
| Record key | The value that selects a record's partition | 4 |
| Replica identity | What PostgreSQL writes to the log about a row before an update or delete | 9 |
| Replication slot | The database's record of how far a log reader has confirmed | 9, 10 |
| Share group | Consumers that take any record and acknowledge it one at a time | 7 |
| Snapshot | Aggregate state stored at one version; also a connector's initial copy of a table | 9, 15 |
| Stream time | The newest event time seen, which advances only when a record arrives | 21 |
| Tombstone | A record with a key and no value that marks a deletion | 9 |
| Trace context | The trace and span ids carried in a message so the next hop can continue or link the trace | 25 |
| Upcast | Convert a stored event to the current shape when it is read | 18 |
| Watermark | A bound on lateness: records older than it no longer join an open window | 21 |

## EIP Camel by pattern and event

EIP Camel teaches the patterns in Camel on one runtime; this book repeats those that Kafka changes and goes deeper on one stack. The relation is one word: deeper means this book adds Kafka specifics, contrasts means the Kafka form and the Camel form differ in behaviour, and extends means the pattern goes past what EIP Camel covers.

| Pattern | Chapter | EIP Camel chapter | Relation |
|---|---|---|---|
| Event, Command, Document Message | 1 | [7 Message Types](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/07-message-types/) | same |
| Event Notification | 2 | [7 Message Types](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/07-message-types/) | deeper |
| Resequencer | 4 | [11 Advanced Routing](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/11-advanced-routing/) | deeper |
| Competing Consumers | 7 | [14 Consumer Patterns](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/14-consumer-patterns/) | deeper |
| Publisher and subscriber tuning | 5, 6 | [Appendix O: Kafka Producer Tuning](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/33-appendix-kafka-producer-tuning/), [Appendix N: Kafka Consumer Tuning](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/32-appendix-kafka-consumer-tuning/) | deeper |
| Share groups | 7 | [Appendix P: Kafka Share Groups](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/34-appendix-kafka-share-groups/) | deeper |
| Transactional Outbox | 8 | [15 Producer and Transactional Patterns](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/15-producer-patterns/) | deeper |
| Connector offsets | 10 | [Appendix R: Managing Kafka Connect Offsets](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/36-appendix-kafka-connect-offsets/) | deeper |
| Idempotent Consumer | 11 | [15 Producer and Transactional Patterns](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/15-producer-patterns/) | deeper |
| Transactional Client | 12 | [15 Producer and Transactional Patterns](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/15-producer-patterns/) | deeper |
| Dead Letter Channel | 13 | [5 Channel Reliability](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/05-channel-reliability/) | deeper |
| Content Enricher | 19 | [12 Transformation Fundamentals](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/12-transformation-fundamentals/) | contrasts |
| Join, Windowed aggregation | 20, 21 | [13 Structural Transformation](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/13-structural-transformation/) | contrasts |
| Saga, Orchestrated saga | 22, 23 | [10 Composed Routing](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/10-composed-routing/) | extends |
| Quarkus Flow orchestration | 23 | [Appendix G: Quarkus Flow](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/25-appendix-quarkus-flow/) | deeper |
| Decisions with rules | 24 | [Appendix F: Drools and Business Rules](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/24-appendix-drools/) | deeper |
| Trace context, metrics | 25, 26 | [17 Observability](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/17-observability/) | extends |
| Correlation identifier | 27 | [8 Message Metadata](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/08-message-metadata/) | extends |
| Running on Kubernetes | 28 to 35 | [Appendix T: Deploying Camel to Kubernetes](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/38-appendix-kubernetes-deploy/) | extends |

Event sourcing, CQRS and schema evolution (chapters 14 to 18) and the claim check have no chapter of their own in EIP Camel.

The same events appear in many chapters. The catalog below lists the chapters in which each appears.

| Event | Topic | Chapters |
|---|---|---|
| OrderPlaced | edp.orders.placed | 2, 3, 5, 8, 11, 13, 16, 17, 18, 26 |
| OrderCancelled | edp.orders.cancelled | 22, 23 |
| InventoryReserved | edp.inventory.reserved | 12, 20, 22, 23 |
| InventoryInsufficient | edp.inventory.insufficient | 22, 23 |
| PaymentProcessed | edp.payments.processed | 12, 20, 22, 23 |
| PaymentFailed | edp.payments.failed | 22, 23 |
| ShipmentScheduled | edp.shipping.scheduled | 4, 16, 19, 21 |
| ShipmentInTransit | edp.shipping.in-transit | 4, 16 |
| ShipmentDelivered | edp.shipping.delivered | 4, 16 |

## References

- Gregor Hohpe and Bobby Woolf, [*Enterprise Integration Patterns*](https://www.enterpriseintegrationpatterns.com/patterns/messaging/), Addison-Wesley, 2003: the pattern catalog the map follows
- [*Enterprise Integration Patterns with Camel*](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/): the chapters linked above
- *Cloud-Native Design Patterns*, [Appendix D: Sagas](https://patterncatalyst.github.io/cloud-native-design-patterns/docs/17-appendix-d-sagas/): the pattern in a sibling book
- Apache Kafka, [Documentation](https://kafka.apache.org/documentation/): consumer groups, offsets, transactions, compaction
- Debezium, [Documentation](https://debezium.io/documentation/reference/stable/): slots, snapshots, tombstones, replica identity
- Strimzi, [Documentation](https://strimzi.io/docs/operators/latest/): node pools, operators, resources
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025: event time, stream time and watermarks
