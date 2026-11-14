---
layout: plan
title: Sources
render_with_liquid: false
---

# Sources: confirmation record (r01 step 3, 2026-11-10)

Status values: confirmed (opened and checked), owner to verify (could not be checked from a script).

## Books

### Lawrence

Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing: Web and Cloud Architecture*, Addison-Wesley, 2026 (hub books.yml: publisher Addison-Wesley, isbn 9780137998616).

- Print ISBN-13 (InformIT publisher page): 978-0-13-799861-6 (ISBN-10 0-13-799861-9). Owner decision: this is the ISBN cited.
- O'Reilly listing ISBN (record only): 9780137998654.
- Publisher page: https://www.informit.com/store/message-and-event-driven-systems-with-cqrs-and-event-9780137998616 (InformIT shows publication Aug 22, 2026; the O'Reilly listing shows 2026-09-24).
- Chapter list confirmed from the InformIT table of contents (no part titles listed):

| # | Title |
|---|---|
| 1 | Business Strategy Meets Message-Driven Systems |
| 2 | Anatomy of Message-Driven Systems |
| 3 | In-Context Architectures |
| 4 | Tactical Building Blocks |
| 5 | CQRS |
| 6 | Event Sourcing |
| 7 | Long-Running Processes |
| 8 | Evolution of Messages and Message Streams |
| 9 | Composing Data from Different Sources |
| 10 | Message-Driven User Interfaces (out of scope, PRD non-goal) |

### Bellemare

Adam Bellemare, *Building Event-Driven Microservices, Second Edition*, O'Reilly, 2025 (O'Reilly listing date 2025-09-16, 484 pages).

- ISBN: hub books.yml has `isbn: null`; the ISBN implied by its URL is 979-8-3416-2218-0 (O'Reilly listing id 9798341622180, matches the PRD).
- URL (books.yml): https://www.oreilly.com/library/view/building-event-driven-microservices/9798341622180/
- O'Reilly MCP search confirms the title, author, date and ISBN. Its results carry no chapter list.
- oreilly.com returned HTTP 403 to scripted fetch, and ebooks.com also 403. The 2nd edition chapter list could not be obtained.
- r02 re-check: two further O'Reilly MCP searches (keywords: events, event notification, state, data contracts, partitioning, ordering, claim check, large events) returned only catalog records for the book, with no chapter or section titles. Every topic below stays **owner to verify**.
- **Table of contents confirmed by the owner from their O'Reilly account, 2026-11-10** (pasted into the session; the owner's link is the learning.oreilly.com edition of the same ISBN). Chapter numbers and titles below may be cited. Section titles are known only for chapters 20 and 21 (listed below); cite every other chapter by its title, for the topic its title names.

| Part | Ch | Title |
|---|---|---|
| I Introduction to Event-Driven Microservices | 1 | Why Event-Driven Microservices |
| | 2 | Fundamentals of Events and Event Streams |
| | 3 | Fundamentals of Event-Driven Microservices |
| II Events and Event Streams | 4 | Schemas and Data Contracts |
| | 5 | Designing Events |
| | 6 | Integrating Event-Driven Architectures with Existing Systems |
| | 7 | Denormalization and Eventification |
| | 8 | Stateful Event-Driven Microservices |
| | 9 | Deterministic Stream Processing |
| | 10 | Building Workflows with Microservices |
| III Event-Driven Microservices Frameworks | 11 | Basic Producer and Consumer Microservices |
| | 12 | Heavyweight Framework Microservices |
| | 13 | Lightweight Framework Microservices |
| | 14 | Streaming SQL |
| | 15 | Microservices Using Functions as a Service |
| IV Consistency, Bad Data, and Supportive Tooling | 16 | Eventual Consistency |
| | 17 | Integrating Event-Driven and Request-Response Microservices |
| | 18 | Handling Bad Data in Event Streams |
| | 19 | Supportive Tooling |
| | 20 | Testing Event-Driven Microservices |
| | 21 | Deploying Event-Driven Microservices |
| | 22 | Conclusion |

Sections confirmed by the owner:
- Ch 20: General Testing Principles; Unit-Testing Microservice Functions; Testing the Full Event-Driven Topology; Testing Schema Evolution and Compatibility; Integration Testing of Event-Driven Microservices; Local Integration Testing; Testing Using Fully Remote Cloud Services; Choosing Your Full-Remote Integration Testing Strategy.
- Ch 21: Principles of Microservice Deployment; Architectural Components of Microservice Deployment (Continuous Integration, Delivery, and Deployment Systems; Container Management Systems and Commodity Hardware); The Basic Full-Stop Deployment Pattern; The Rolling Update Pattern; The Breaking Schema Change Pattern; The Blue-Green Deployment Pattern.

## PRD section 5 mapping, Lawrence

All chapter numbers below exist in the confirmed table of contents; the fit is by title.

| Book chapter(s) | Lawrence title | PRD chapters citing it | Status |
|---|---|---|---|
| 2 | Anatomy of Message-Driven Systems | 02, 04, 08, 11, 13 | confirmed |
| 4 | Tactical Building Blocks | 14 | confirmed |
| 5 | CQRS | 16 | confirmed |
| 6 | Event Sourcing | 08, 14, 15, 16, 17 | confirmed |
| 7 | Long-Running Processes | 22, 23 | confirmed |
| 8 | Evolution of Messages and Message Streams | 18 | confirmed |
| 9 | Composing Data from Different Sources | 03, 09, 19, 20, 21 | confirmed |

## Bellemare mapping by chapter title (TOC confirmed 2026-11-10)

Fit is by chapter title. A chapter is cited for the topic its title names, never for a specific claim inside it.

| Book chapter | This book |
|---|---|
| 2 Fundamentals of Events and Event Streams | 02 event notification; 04 ordering and partitioning |
| 5 Designing Events | 02, 03 (what an event carries) |
| 4 Schemas and Data Contracts | 03 event-carried state transfer; 18 schema evolution |
| 7 Denormalization and Eventification | 03 (state carried on the event) |
| 17 Integrating Event-Driven and Request-Response Microservices | 02 (the fetch back over HTTP) |
| 11 Basic Producer and Consumer Microservices | 05 publishers; 06 subscribers |
| 6 Integrating Event-Driven Architectures with Existing Systems | 08 outbox; 09 log tailing |
| 18 Handling Bad Data in Event Streams | 13 retry and dead letter |
| 16 Eventual Consistency | 16 CQRS read lag |
| 8 Stateful Event-Driven Microservices | 19 to 21 |
| 9 Deterministic Stream Processing | 17 reprocessing; 19 to 21 |
| 14 Streaming SQL | Appendix C |
| 10 Building Workflows with Microservices | 22, 23 sagas |
| 20 Testing Event-Driven Microservices | Appendix A |
| 21 Deploying Event-Driven Microservices | the deployment appendix |

Chapters 11 and 12 (delivery guarantees, exactly-once) and 14, 15 (aggregate root, snapshots) have no chapter title that names their topic; confirm at those iterations or cite no Bellemare chapter.

## Strimzi blog posts

The PRD says 21 posts; its list contains 22 entries (listed below). All 22 were fetched from strimzi.io; every title and date (from the URL path) matches the PRD. Status: confirmed for all.

| # | Title (actual) | Date | URL | Status |
|---|---|---|---|---|
| 1 | Exactly-once semantics with Kafka transactions | 2023-05-03 | https://strimzi.io/blog/2023/05/03/kafka-transactions/ | confirmed |
| 2 | Developing Kafka client applications: A simple producer | 2023-10-03 | https://strimzi.io/blog/2023/10/03/kafka-producer-client-essentials/ | confirmed |
| 3 | Developing Kafka client applications: A simple consumer | 2023-11-09 | https://strimzi.io/blog/2023/11/09/kafka-consumer-client-essentials/ | confirmed |
| 4 | Optimizing Kafka broker configuration | 2021-06-08 | https://strimzi.io/blog/2021/06/08/broker-tuning/ | confirmed |
| 5 | Using Queues for Apache Kafka with Strimzi | 2025-08-20 | https://strimzi.io/blog/2025/08/20/queues-for-kafka/ | confirmed |
| 6 | Managing Connector Offsets | 2024-11-04 | https://strimzi.io/blog/2024/11/04/managing-connector-offsets/ | confirmed |
| 7 | Auto-restarting connectors in Apache Kafka Connect and Mirror Maker 2 | 2023-01-25 | https://strimzi.io/blog/2023/01/25/auto-restarting-connectors/ | confirmed |
| 8 | Kafka Node Pools: Introduction | 2023-08-14 | https://strimzi.io/blog/2023/08/14/kafka-node-pools-introduction/ | confirmed |
| 9 | Kafka Node Pools: Supporting KRaft (ZooKeeper-less Apache Kafka) | 2023-09-11 | https://strimzi.io/blog/2023/09/11/kafka-node-pools-supporting-kraft/ | confirmed (PRD title abbreviated) |
| 10 | Kafka Node Pools: Storage & Scheduling | 2023-08-28 | https://strimzi.io/blog/2023/08/28/kafka-node-pools-storage-and-scheduling/ | confirmed |
| 11 | From ZooKeeper to KRaft: How the Kafka migration works | 2024-03-21 | https://strimzi.io/blog/2024/03/21/kraft-migration/ | confirmed |
| 12 | Introducing the Unidirectional Topic Operator | 2023-11-02 | https://strimzi.io/blog/2023/11/02/unidirectional-topic-operator/ | confirmed |
| 13 | Server-Side Apply in Strimzi | 2026-01-26 | https://strimzi.io/blog/2026/01/26/server-side-apply-in-strimzi/ | confirmed |
| 14 | Configuring security context of Strimzi-managed pods | 2022-09-09 | https://strimzi.io/blog/2022/09/09/configuring-security-context-in-pods-managed-by-strimzi/ | confirmed |
| 15 | Writing custom Pod Security Providers | 2023-04-05 | https://strimzi.io/blog/2023/04/05/writing-custom-pod-security-providers/ | confirmed |
| 16 | Using Strimzi Metrics Reporter to expose Kafka metrics in Prometheus format | 2025-10-06 | https://strimzi.io/blog/2025/10/06/strimzi-metrics-reporter/ | confirmed |
| 17 | Monitoring of custom resources | 2025-10-01 | https://strimzi.io/blog/2025/10/01/monitoring-of-custom-resources/ | confirmed |
| 18 | Tracing in Apache Kafka: from OpenTracing to OpenTelemetry | 2023-03-01 | https://strimzi.io/blog/2023/03/01/opentelemetry/ | confirmed |
| 19 | Auto-rebalancing on cluster scaling | 2024-11-25 | https://strimzi.io/blog/2024/11/25/autorebalancing-on-scaling/ | confirmed |
| 20 | Moving data between JBOD disks using Cruise Control | 2025-02-13 | https://strimzi.io/blog/2025/02/13/moving-data-between-JBOD-disks-using-cruise-control/ | confirmed |
| 21 | Video: Make your Kafka cluster production-ready - Cluster Balancing | 2023-01-19 | https://strimzi.io/blog/2023/01/19/make-your-kafka-cluster-production-ready-cluster-balancing/ | confirmed |
| 22 | Migrating Kafka clusters with MirrorMaker2 and Strimzi | 2021-11-22 | https://strimzi.io/blog/2021/11/22/migrating-kafka-with-mirror-maker2/ | confirmed |
| 23 | Optimizing Kafka producers | 2020-10-15 | https://strimzi.io/blog/2020/10/15/producer-tuning/ | confirmed (r03, fetched 2026-11-10) |
| 24 | Optimizing Kafka consumers | 2021-01-07 | https://strimzi.io/blog/2021/01/07/consumer-tuning/ | confirmed (r03, fetched 2026-11-10) |

The blog has no Debezium or CDC post; chapter 09 rests on the Debezium docs.

## Articles and pattern references

| Source | Title (actual) | Date | URL | Status |
|---|---|---|---|---|
| Fowler | Event Sourcing | 2005-12-12 (page notes an unrevised mid-2000s draft) | https://martinfowler.com/eaaDev/EventSourcing.html | confirmed |
| Fowler | What do you mean by "Event-Driven"? | 2017-02-07 (updated 2017-02-08) | https://martinfowler.com/articles/201701-event-driven.html | confirmed (PRD "2017" correct); re-fetched 2026-11-10: event notification is small, "often just some id information and a link back to the sender", the receiver "issues a request back to the sender"; names four patterns: Event Notification, Event-Carried State Transfer, Event Sourcing, CQRS |
| Microsoft | Event Sourcing Pattern - Azure Architecture Center (page ms.date 2026-03-27) | 2026-03-27 | https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing | confirmed |
| Hohpe and Woolf | Enterprise Integration Patterns, Addison-Wesley, 2003, ISBN 9780321200686 (hub books.yml) | 2003 | https://www.enterpriseintegrationpatterns.com/ | confirmed (reached via EIP Camel bibliography; not re-fetched) |
| Hohpe and Woolf | Claim Check (page title "Claim Check - Enterprise Integration Patterns"; "Store message data in a persistent store and pass a Claim Check to subsequent components.") | n/a | https://www.enterpriseintegrationpatterns.com/patterns/messaging/StoreInLibrary.html | confirmed (fetched 2026-11-10) |
| Hohpe and Woolf | Resequencer (page title "Resequencer - Enterprise Integration Patterns"; stateful filter that buffers out-of-sequence messages and publishes them in order) | n/a | https://www.enterpriseintegrationpatterns.com/patterns/messaging/Resequencer.html | confirmed (fetched 2026-11-10) |
| Apache Kafka | Introduction, section "Main Concepts and Terminology" (kafka.apache.org/documentation redirects to versioned pages; read at /43/getting-started/introduction/): "Kafka guarantees that any consumer of a given topic-partition will always read that partition's events in exactly the same order as they were written", with events of the same key written to the same partition. The page states order per partition; it has no cross-partition order claim | n/a | https://kafka.apache.org/documentation/ | confirmed (4.3 docs; the same page exists under /44/) |
| Apache Kafka | Broker configuration `message.max.bytes`: "The largest record batch size allowed by Kafka (after compression if compression is enabled)"; default 1048588; per-topic override `max.message.bytes` | n/a | https://kafka.apache.org/43/configuration/broker-configs/ | confirmed (default seen: 1048588) |
| Apache Kafka | Producer configuration `max.request.size`: "The maximum size of a request in bytes"; also effectively a cap on the maximum uncompressed record batch size; default 1048576 | n/a | https://kafka.apache.org/43/configuration/producer-configs/ | confirmed (default seen: 1048576) |
| Apache Camel | Resequence EIP (title "Resequence"): batch mode (default) collects, sorts and sends, up to 100 messages per batch or timeout every second (`size()`, `timeout()`); stream mode sends messages as soon as the next expected sequence number arrives, needs integer sequence numbers 1..N, backlog capacity default 1000; on timeout the resequencer "disregards the current expected sequence number" and moves on | n/a | https://camel.apache.org/components/latest/eips/resequence-eip.html | confirmed (fetched 2026-11-10) |
| Apache Camel | Claim Check EIP (title "Claim Check"): "stores the message content temporarily in an internal memory store"; the page does not say storage is per exchange, so that is not claimed | n/a | https://camel.apache.org/components/latest/eips/claimCheck-eip.html | confirmed for in-memory store; per-exchange scope not stated on the page |
| Hohpe and Woolf | Event Message (page title "Event Message - Enterprise Integration Patterns"): "Many events are empty; their mere occurrance tells the observer to react." | n/a | https://www.enterpriseintegrationpatterns.com/patterns/messaging/EventMessage.html | confirmed (fetched 2026-11-10) |
| Apache Camel | Kafka component, option `breakOnFirstError`: with the default NoopCommitManager "the consumer [does] not commit the offset so that the message is re-attempted"; with sync or async commit the consumer "will seek back to the offset of the message that caused a failure, and then re-attempt"; warns this "can lead to endless processing of the same message if it's bound to fail every time, e.g., a poison message" | n/a | https://camel.apache.org/components/latest/kafka-component.html | confirmed (fetched 2026-11-10) |
| Apache Camel | Claim Check EIP catalog entry (Camel 4.22.1, camel-catalog via Camel MCP): "Temporarily stores the message content and replaces it with a claim check key, allowing the content to be retrieved later in the route."; the web page says "stores the message content temporarily in an internal memory store" | n/a | https://camel.apache.org/components/latest/eips/claimCheck-eip.html | confirmed (catalog text and page, fetched 2026-11-10) |
| KIP-932 | KIP-932: Queues for Kafka | n/a | https://cwiki.apache.org/confluence/display/KAFKA/KIP-932%3A+Queues+for+Kafka | confirmed (HTTP 200, title matches) |
| KIP-875 | KIP-875: First-class offsets support in Kafka Connect | n/a | https://cwiki.apache.org/confluence/display/KAFKA/KIP-875%3A+First-class+offsets+support+in+Kafka+Connect | confirmed (HTTP 200, title matches) |

Reference entries for Strimzi, Debezium, Apicurio, Quarkus, Camel, Camel Quarkus and Kafka docs are root documentation URLs taken from the project sites and the EIP Camel bibliography; the Debezium and Apicurio and Strimzi docs URLs were not fetched in this step (owner or a later link check to verify).

## Sibling projects

| Project | URL | Status |
|---|---|---|
| enterprise-integration-patterns-with-camel | https://github.com/patterncatalyst/enterprise-integration-patterns-with-camel | confirmed (gh repo view) |
| cloud-native-design-patterns | https://github.com/patterncatalyst/cloud-native-design-patterns | confirmed |
| datamesh-reference-arch-quarkus | https://github.com/patterncatalyst/datamesh-reference-arch-quarkus | confirmed |
| quarkus-observability | https://github.com/patterncatalyst/quarkus-observability | confirmed |
| otel-observability-tutorial | https://github.com/patterncatalyst/otel-observability-tutorial | confirmed |
| domain-driven-design-observability-workshop | https://github.com/patterncatalyst/domain-driven-design-observability-workshop | confirmed |

## r03 sources (step 1A, fetched 2026-11-10)

All rows fetched 2026-11-10 (date fetched). Status confirmed unless noted. Quotes are short phrases from the page.

### Kafka 4.3 configuration pages

| Source | URL | Facts recorded | Backs |
|---|---|---|---|
| Kafka 4.3 producer configs | https://kafka.apache.org/43/configuration/producer-configs/ | `linger.ms` default 5 ("the upper bound on the delay for batching"); `batch.size` 16384 bytes; `acks` default all; `enable.idempotence` default true; `compression.type` default none; `max.in.flight.requests.per.connection` 5. Idempotence requires `acks` all, `retries` greater than 0 (default 2147483647) and in-flight 5 or lower; conflicting values without explicit idempotence disable it, an explicit `enable.idempotence=true` with conflicts throws `ConfigException` | 05 defaults, tuned route, acks=1 recreate |
| Kafka 4.3 consumer configs | https://kafka.apache.org/43/configuration/consumer-configs/ | `max.poll.records` 500, "does not impact the underlying fetching behavior", "The consumer will cache the records from each fetch request and returns them incrementally from each poll"; `fetch.min.bytes` 1; `fetch.max.wait.ms` 500; `max.poll.interval.ms` 300000; `max.partition.fetch.bytes` 1048576; `group.protocol` default classic, values classic or consumer; `group.remote.assignor` null, applies only with consumer | 06 knobs, protocol comparison |
| Kafka 4.3 group configs | https://kafka.apache.org/43/configuration/group-configs/ | `share.record.lock.duration.ms` 30000; `share.delivery.count.limit` 5; `share.auto.offset.reset` latest ("the share-partition start offset"); `share.session.timeout.ms` 45000; also `share.isolation.level` read_uncommitted, `share.heartbeat.interval.ms` 5000 | 07 share group settings |
| Kafka 4.3 broker configs | https://kafka.apache.org/43/configuration/broker-configs/ | `group.share.min.record.lock.duration.ms` default 15000, valid range [1000, 30000] (the 15 s lock used in the ch07 lock experiment) | 07 lock experiment |
| Kafka 4.3 design page, section "The Share Consumer" | https://kafka.apache.org/43/design/design/ | Page exists under Design (no separate share-group docs page: /43/design/share-groups and /queues-for-kafka are 404). "partitions may be assigned to multiple consumers"; "can exceed the number of partitions"; "Delivery attempts ... are counted"; ack types accept, release, reject, renew, or do nothing (lock releases on expiry); lock default 30 seconds via `share.record.lock.duration.ms`; broker cap `group.share.partition.max.record.locks`; consumer-group partitions are "consumed by exactly one consumer within each subscribing consumer group". It states no ordering guarantee for share groups, no numeric delivery limit, and does not mention KIP-848 | 07 definition of share groups |

### KIPs

| Source | URL | Facts recorded | Backs |
|---|---|---|---|
| KIP-848: The Next Generation of the Consumer Rebalance Protocol (Accepted) | https://cwiki.apache.org/confluence/display/KAFKA/KIP-848%3A+The+Next+Generation+of+the+Consumer+Rebalance+Protocol | Classic protocol "relies on a group-wide synchronization barrier"; new protocol "truly incremental and cooperative", no global barrier; coordinator computes assignment with a server-side assignor (range, uniform); opt in via `group.protocol`; GA in Kafka 4.0. First 100000 of 163678 characters read | 06 rebalance protocol |
| KIP-1030: Change constraints and default values for various configurations | https://cwiki.apache.org/confluence/display/KAFKA/KIP-1030%3A+Change+constraints+and+default+values+for+various+configurations | `linger.ms` default 0 to 5, targeted 4.0; at 0 "Kafka effectively sends one message per batch"; 5 ms "a reasonable compromise" between latency and batching | 05 default linger is 5 ms |
| KIP-932: Queues for Kafka (Accepted) | https://cwiki.apache.org/confluence/display/KAFKA/KIP-932%3A+Queues+for+Kafka | Share groups; more consumers than partitions; individual acknowledgement; accept, release, reject; lock default 30 s; delivery count limit 5, then archived; at-least-once, count approximate; "no guarantees about the ordering of offsets between different batches"; `KafkaShareConsumer`, `share.acknowledgement.mode` implicit or explicit. First 100000 of 225772 characters read | 07 |

### Strimzi blog

| Source | URL | Date | Facts recorded | Backs |
|---|---|---|---|---|
| Optimizing Kafka producers (Paul Mellor) | https://strimzi.io/blog/2020/10/15/producer-tuning/ (HTTP 200; not on the blog front page) | 2020-10-15 | `batch.size` "(default 16384)"; `linger.ms` "(default 0 or no delay)" (true in 2020, superseded by KIP-1030, so never cite it for the default); `acks=all` raises latency; compression "improves throughput and reduces the load on storage" at a CPU cost, "may not suit low-latency"; idempotence "can (and should)" be enabled; `buffer.memory` at least batch size; in-flight default 5, set 1 for order without idempotence | 05 knob explanations |
| Optimizing Kafka consumers (Paul Mellor) | https://strimzi.io/blog/2021/01/07/consumer-tuning/ (HTTP 200) | 2021-01-07 | `fetch.min.bytes` "a minimum threshold for size-based batching"; "first of either threshold is reached" with `fetch.max.wait.ms`; `max.poll.records` caps records per poll; missed `max.poll.interval.ms` means "considered to be failed, causing a rebalance"; session timeout and heartbeat (about a third); static membership `group.instance.id` cuts rebalances; "Adding more consumers than partitions will not increase throughput", extras "partition-free and idle" | 06 |
| Developing Kafka client applications: A simple producer (re-read) | https://strimzi.io/blog/2023/10/03/kafka-producer-client-essentials/ | 2023-10-03 | `send` "buffers messages for batching"; `linger.ms` and `batch.size` batch more; `acks=all` (default) for durability; `retries`; `compression.type` reduces network use. Does NOT mention idempotence or the partitioner | 05 (do not cite for idempotence) |
| Developing Kafka client applications: A simple consumer (re-read) | https://strimzi.io/blog/2023/11/09/kafka-consumer-client-essentials/ | 2023-11-09 | poll loop; `max.poll.records` "limits the number of records returned"; `max.poll.interval.ms` "more critical than heartbeat.interval.ms and session.timeout.ms"; slow work to a worker pool; no messages consumed during assignment; `group.instance.id`; cooperative sticky. Does NOT cover fetch settings or the new group protocol | 06 (do not cite for fetch settings or KIP-848) |
| Using Queues for Apache Kafka with Strimzi (re-read) | https://strimzi.io/blog/2025/08/20/queues-for-kafka/ | 2025-08-20 | Share groups can exceed partition count, a third regular consumer sat idle while three share consumers read; locks "applied per record even within a fetched batch"; no ordering guarantee; ACCEPT, RELEASE, REJECT with explicit mode; `group.share.delivery.count.limit` default 5, record archived after five attempts; written against Kafka 4.0 early access (Strimzi 0.46), "not recommended for production" at 4.0.0. Does not use the term head-of-line blocking, so do not attribute it | 07 |

Strimzi blog additions: the two tuning posts are approved (owner review 2026-11-10) and added to the table above as #23 (producers) and #24 (consumers).

### Hohpe and Woolf

| Page | URL | Facts | Backs |
|---|---|---|---|
| Competing Consumers ("Competing Consumers - Enterprise Integration Patterns") | https://www.enterpriseintegrationpatterns.com/patterns/messaging/CompetingConsumers.html | Problem: "How can a messaging client process multiple messages concurrently?" Solution: "Create multiple Competing Consumers on a single channel so that the consumers can process multiple messages concurrently." | 07 |
| Polling Consumer ("Polling Consumer - Enterprise Integration Patterns") | https://www.enterpriseintegrationpatterns.com/patterns/messaging/PollingConsumer.html | Problem: "wants to control when it consumes each message." Solution: "one that explicitly makes a call when it wants to receive a message." | 06 |

### SmallRye, Quarkus and Camel

| Source | URL | Facts recorded | Backs |
|---|---|---|---|
| SmallRye Reactive Messaging, "Receiving Kafka Records Using Kafka Queues (Share Groups)" | https://smallrye.io/smallrye-reactive-messaging/latest/kafka/receiving-kafka-records/ | Marked Experimental. The latest page documents 5.0.0-RC1 (Javadoc links), not the pinned 4.37.0. It uses `share-group.enabled` (default false), says `share-group` is deprecated in its favour; `share-group.failure-acknowledgement-type` default release; `share-group.failure-deserialization-acknowledgement-type` default reject; `share-group.unprocessed-record-max-age.ms` default 60000, 0 disables. Ack maps to ACCEPT, nack to RELEASE, `ack.reject()` is permanent; RENEW managed by the connector. No delivery count metadata is mentioned on the page. Cross-check: the pinned 4.37.0 jar's `KafkaConnector` contains both `share-group` and `share-group.enabled` and the failure and max-age attributes, so both names work on the pin | 07 share side |
| Quarkus Kafka guide section 4.7 "Share Groups (Kafka Queues)" (Quarkus 3.40 labeled latest) | https://quarkus.io/guides/kafka | "require Apache Kafka 4.2+ brokers and are an early access feature in Kafka"; `mp.messaging.incoming.queue.share-group=true`; accept, release, reject; lock renewal via `share-group.unprocessed-record-max-age.ms`; `batch=true` allowed; `pattern`, `assign-seek`, `partitions`, `commit-strategy`, `failure-strategy`, `consumer-rebalance-listener.name` ignored with share groups. Note this guide uses the `share-group` name while SmallRye latest prefers `share-group.enabled`; pick one that works on the pin and say nothing about the other. First 100000 of 222865 characters read | 07 |
| Camel Kafka component page | https://camel.apache.org/components/latest/kafka-component.html | `groupProtocol` default classic, values classic or consumer; consumer "enables the new KIP-848 consumer rebalance protocol", classic-only `heartbeatIntervalMs`, `sessionTimeoutMs`, `partitionAssignor` excluded automatically; `groupRemoteAssignor` no default, broker default assignor applies; `consumersCount` default 1, each consumer "is run on a separate thread", same `groupId` across processes forms one group; `maxPollRecords` default 500; `lingerMs` table default 5 but the description says "This setting defaults to 0 (i.e., no delay)" (stale text; Kafka 4.3 and KIP-1030 say 5, follow Kafka, EIP appendix 33 also says 0); no mention of share groups. The page names no Camel version. First 100000 of 147066 characters read | 05, 06, 07 (no share option claim backed by the catalog) |

### Bellemare chapters cited by the back-fill (r03)

02 cites chapters 2, 5, 17; 03 cites 4, 5, 7; 04 cites 2 (plus Lawrence chapter 2). Evidence for all: the owner-confirmed table of contents above, fit by title.

### Share consumer internals (r03 repair round 2, fetched 2026-11-11)

| Source | URL | Quote | Used in |
|---|---|---|---|
| Apache Kafka clients 4.2.1, ShareConsumerImpl | https://github.com/apache/kafka/blob/4.2.1/clients/src/main/java/org/apache/kafka/clients/consumer/internals/ShareConsumerImpl.java | `throw new IllegalStateException("All records must be acknowledged in explicit acknowledgement mode.");` | 07 poll in explicit mode |
| SmallRye Reactive Messaging 4.37.0, ReactiveKafkaShareConsumer | https://github.com/smallrye/smallrye-reactive-messaging/blob/4.37.0/smallrye-reactive-messaging-kafka/src/main/java/io/smallrye/reactive/messaging/kafka/impl/ReactiveKafkaShareConsumer.java | `.onFailure(IllegalStateException.class).retry().withBackOff(Duration.ofMillis(100), retryMaxWait)` | 07 retry-max-wait |
| SmallRye Reactive Messaging 4.37.0, KafkaShareGroupCommit | https://github.com/smallrye/smallrye-reactive-messaging/blob/4.37.0/smallrye-reactive-messaging-kafka/src/main/java/io/smallrye/reactive/messaging/kafka/commit/KafkaShareGroupCommit.java | `c.acknowledge((ConsumerRecord) rec, AcknowledgeType.RENEW);` | 07 lock renewal |
| SmallRye Kafka connector attributes | https://smallrye.io/smallrye-reactive-messaging/latest/kafka/receiving-kafka-records/ | "retry-max-wait: The max delay (in seconds) between 2 reconnects", default 30; "poll-timeout: The polling timeout in milliseconds", default 1000 | 07 connector settings |
| Kafka 4.3 producer configs, batch.size | https://kafka.apache.org/43/configuration/producer-configs/ | "a batch size of zero will disable batching entirely" | 07 seed |

## r04 sources (step 1A, fetched 2026-11-11)

All rows fetched 2026-11-11, HTTP 200, status confirmed unless noted. Quotes are a few words from the page; facts are paraphrased. Pages are the `stable` Debezium docs (3.7), Kafka 4.3, PostgreSQL 18, Camel 4.22.x and Camel Quarkus 3.40.x.

### Debezium

| Source (page title) | Author/org | URL | Facts the chapters rely on |
|---|---|---|---|
| Outbox Event Router :: Debezium Documentation | Debezium (Red Hat) | https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html | `route.by.field` default `aggregatetype`: the column whose value becomes part of the topic name. `route.topic.replacement` default `outbox.event.${routedByValue}` (so `outbox.event.<aggregatetype>` by default; `${routedByValue}` alone gives the raw value). `table.expand.json.payload` default false: JSON expansion of a String payload; content kept as is on empty or parse error. `table.fields.additional.placement`: comma-separated `column:placement[:alias]`, placement `header` or `envelope` (example `id:header,my-field:envelope:my-alias`; `partition` also appears). `table.field.event.id` default `id`: the id "will be stored in the emitted event's headers under the `id` key", usable to drop duplicates. `table.field.event.key` default `aggregateid`; `table.field.event.payload` default `payload`; `table.field.event.timestamp` has no default (event timestamp is the Debezium event timestamp). `route.tombstone.on.empty.payload` default false. "The SMT automatically filters out DELETE operations on an outbox table." The page does not tell the reader to delete outbox rows. |
| Outbox Quarkus Extension :: Debezium Documentation | Debezium (Red Hat) | https://debezium.io/documentation/reference/stable/integrations/outbox.html | `io.debezium:debezium-quarkus-outbox:3.7.1.Final` (reactive variant `debezium-quarkus-outbox-reactive`). Event class implements `io.debezium.outbox.quarkus.ExportedEvent` and is fired with CDI `Event#fire()`; all implementations in one app must use the same type parameters or the build fails. Default table `OutboxEvent`: id UUID NOT NULL, aggregateid, aggregatetype, type (VARCHAR(255) NOT NULL), timestamp TIMESTAMP NOT NULL, payload VARCHAR(8000), tracingspancontext VARCHAR(256). `quarkus.debezium-outbox.table-name`, `.id.name`, `.id.column-definition` are build-time. `quarkus.debezium-outbox.remove-after-insert` runtime, default true; removing rows does not stop the connector emitting events and is for keeping storage from growing. `quarkus.debezium-outbox.additional-fields` is `name:javaType[:columnDefinition[:converter]]`, values from overriding `getAdditionalFieldValues()`. Changing default column names needs matching Event Router config. No connector properties on the page. |
| Debezium connector for PostgreSQL :: Debezium Documentation (3.7) | Debezium (Red Hat) | https://debezium.io/documentation/reference/stable/connectors/postgresql.html | Snapshot modes: `initial` (default), `always`, `initial_only`, `no_data` ("never performs snapshots"), `when_needed`, `configuration_based`, `custom`. REPLICA IDENTITY: DEFAULT gives only the primary-key columns with changed values for an UPDATE and no UPDATE/DELETE events without a primary key; FULL gives previous values of all columns; `before` availability "is dependent on the REPLICA IDENTITY". "WAL disk space consumption" section: slot `confirmed_flush_lsn` vs `restart_lsn` in pg_replication_slots; WAL grows when the tracked tables are quiet while other tables in the same database, or other databases, take heavy writes (slots are per database and Debezium is not invoked to confirm an LSN); remedy `heartbeat.interval.ms`, optionally `heartbeat.action.query`; heartbeat table must be in the publication. `heartbeat.interval.ms` default 0 (no heartbeats). `heartbeat.action.query` no default. `topic.heartbeat.prefix` default `__debezium-heartbeat`, topic `<prefix>.<topic.prefix>`; `topic.heartbeat.name` overrides with a full name. `slot.name` default `debezium`, unique per connector, slot created at start if absent (REPLICATION privilege, plus CREATE on the database for pgoutput). `slot.drop.on.stop` default false ("for testing only"). `publication.name` default `dbz_publication`. `publication.autocreate.mode` default `all_tables`; `filtered` is recommended for pgoutput. `tombstones.on.delete` default true (a delete event followed by a tombstone). `signal.data.collection` no default; `signal.enabled.channels` default `source`; `incremental.snapshot.chunk.size` default 1024. `lsn.flush.mode` default `connector` (flushes after each processed event; `manual` leaves WAL accumulating). New `offset.mismatch.strategy` (default `no_validation`, Technology Preview) handles a stored offset LSN that differs from the slot's confirmed flush LSN; with `no_validation` on PostgreSQL 15+ the server starts from `confirmed_flush_lsn`; `trust_offset` fails if the offset is behind the slot. Default topic name `topicPrefix.schemaName.tableName`. Source block fields in an event: version, connector, name, ts_ms, ts_us, ts_ns, snapshot, db, schema, table, txId, lsn, xmin. The page does not list the offset keys `lsn_proc`, `lsn_commit`, `ts_usec` (see the source-code row below). The docs mention no KIP-875 page. |
| Debezium PostgresOffsetContext and SourceInfo (source, tag v3.7.1.Final) | Debezium (Red Hat) | https://raw.githubusercontent.com/debezium/debezium/v3.7.1.Final/debezium-connector-postgres/src/main/java/io/debezium/connector/postgresql/PostgresOffsetContext.java | The connector's stored offset map holds `ts_usec`, `txId`, `lsn`, `xmin`, `lsn_proc` (last completely processed LSN), `lsn_commit`, `lsn_events_processed`, plus snapshot, last_snapshot_record, snapshot_completed and messageType keys. SourceInfo defines `ts_usec`, `txId`, `xmin`, `lsn`. Confirmed from source (HTTP 200), not from the docs page. |
| Signaling and Notifications :: Debezium Documentation | Debezium (Red Hat) | https://debezium.io/documentation/reference/stable/configuration/signalling.html | Signal table has three columns id (string, required), type, data. Channels: `source` (enabled by default, required for incremental snapshot signals) and `kafka`; also jmx and file; only `source` has retry. `execute-snapshot` signal data takes `data-collections` and `type` (incremental or blocking). Kafka channel needs `signal.kafka.topic`. |
| Debezium PostgreSQL page, "Incremental snapshots" section (same URL as above) | Debezium (Red Hat) | https://debezium.io/documentation/reference/stable/connectors/postgresql.html | Streaming continues during an incremental snapshot; snapshot window per chunk buffers READ events and drops a buffered row when a streamed event has the same primary key (the streamed event wins); remaining READ events are emitted after the window closes; one signal INSERT into the signal table starts it; an empty `data-collections` array does nothing; write permission to the signal table needed except where read-only incremental snapshots are configurable (PostgreSQL is listed). |
| New Record State Extraction :: Debezium Documentation | Debezium (Red Hat) | https://debezium.io/documentation/reference/stable/transformations/event-flattening.html | `delete.tombstone.handling.mode` default `tombstone` (the SMT keeps delete tombstone behaviour: the tombstone passes); `drop` removes both the delete event and the tombstone; `rewrite` flattens the delete and adds `__deleted`. |

### Kafka and Strimzi

| Source (page title) | Author/org | URL | Facts the chapters rely on |
|---|---|---|---|
| KIP-875: First-class offsets support in Kafka Connect | Apache Kafka (Chris Egerton) | https://cwiki.apache.org/confluence/display/KAFKA/KIP-875%3A+First-class+offsets+support+in+Kafka+Connect | GET `/connectors/{c}/offsets`; PATCH (alter) and DELETE (reset) require the connector to exist and be in the STOPPED state; new STOPPED target state; requests are rejected during a rebalance or when the connector is not stopped. Page says created by Chris Egerton, last updated 2023-04-11. |
| Kafka Connect user guide, REST API (Kafka 4.3) | Apache Kafka | https://kafka.apache.org/43/kafka-connect/user-guide/ | `PUT /connectors/{name}/stop` stops the connector and its tasks and frees resources (offsets can be modified only when stopped); `PUT /connectors/{name}/resume` resumes a paused or stopped connector; `PUT .../pause`; `GET /connectors/{name}/offsets`; `PATCH /connectors/{name}/offsets` and `DELETE /connectors/{name}/offsets` need the stopped state; `POST /connectors/{name}/restart?includeTasks=<bool>&onlyFailed=<bool>` (both default false). |
| Kafka Connect configs (Kafka 4.3) | Apache Kafka | https://kafka.apache.org/43/configuration/kafka-connect-configs/ | `offset.flush.interval.ms` default 60000 (1 minute); `offset.flush.timeout.ms` default 5000. |
| Managing Connector Offsets (Strimzi blog, 2024-11-04) | Kate Stanley, Strimzi | https://strimzi.io/blog/2024/11/04/managing-connector-offsets/ | The REST trio: GET list, PATCH alter, DELETE reset on `/connectors/{connector}/offsets`. From Strimzi 0.44 the same is driven from the KafkaConnector resource (annotation `strimzi.io/connector-offsets`, ConfigMap with `offsets.json`); altering and resetting need the connector stopped (`state: stopped`) and a manual resume (`state: running`). The Kubernetes mechanics belong to r12. |

### PostgreSQL 18

| Source (page title) | Author/org | URL | Facts the chapters rely on |
|---|---|---|---|
| 47.2. Logical Decoding Concepts | PostgreSQL Global Development Group | https://www.postgresql.org/docs/18/logicaldecoding-explanation.html | Slots persist independently of connections and are crash-safe; a slot streams one database; a logical slot knows nothing about its receivers, only one consumes at a time; its position is persisted only at checkpoint so after a crash recent changes may be sent again; slots prevent removal of required WAL and catalog rows "even when there is no connection", which consumes storage, so an unneeded slot should be dropped. |
| 53.4. Streaming Replication Protocol (START_REPLICATION ... LOGICAL) | PostgreSQL Global Development Group | https://www.postgresql.org/docs/18/protocol-replication.html | The server starts streaming "at either WAL location XXX/XXX or the slot's confirmed_flush_lsn ... whichever is greater"; the client "may wish to check that confirmed_flush_lsn matches its expectations before issuing START_REPLICATION". |
| 53.20. pg_replication_slots | PostgreSQL Global Development Group | https://www.postgresql.org/docs/18/view-pg-replication-slots.html | `restart_lsn` oldest WAL still possibly needed; `confirmed_flush_lsn`; `wal_status` values reserved (within max_wal_size), extended (max_wal_size exceeded but retained), unreserved (some files removed at the next checkpoint; can return to reserved or extended), lost (slot unusable); `safe_wal_size` bytes writable before the slot goes lost, NULL for lost slots or when `max_slot_wal_keep_size` is -1. |
| 19.6. Replication (runtime config) | PostgreSQL Global Development Group | https://www.postgresql.org/docs/18/runtime-config-replication.html | `max_slot_wal_keep_size` default -1 (unlimited retention), megabytes without units, set in postgresql.conf or on the command line (reloadable, no restart); `max_replication_slots` default 10, server start only; new in 18: `idle_replication_slot_timeout` default 0 (disabled), invalidation at checkpoint. |
| 29.1. Publication | PostgreSQL Global Development Group | https://www.postgresql.org/docs/18/logical-replication-publication.html | A publication is a set of changes from tables; a published table needs a replica identity (default primary key; FULL if no key) to replicate UPDATE and DELETE; DEFAULT without a primary key or NOTHING cannot support them. |

### Camel

| Source (page title) | Author/org | URL | Facts the chapters rely on |
|---|---|---|---|
| Debezium PostgreSQL Connector (component, 4.22.x) | Apache Camel | https://camel.apache.org/components/4.22.x/debezium-postgres-component.html | URI `debezium-postgres:name`. Options (name, default): `offsetStorage` org.apache.kafka.connect.storage.FileOffsetBackingStore; `offsetStorageFileName` required with the file store; `offsetStorageTopic`; `offsetFlushIntervalMs` 60000; `databaseHostname`; `slotName` debezium; `pluginName`; `publicationName`; `publicationAutocreateMode` all_tables; `snapshotMode` initial; `tableIncludeList`; `topicPrefix`; `heartbeatIntervalMs` 0. Headers: CamelDebeziumSourceMetadata (Map; db, table, lsn...), CamelDebeziumIdentifier, CamelDebeziumKey, CamelDebeziumOperation (c, u, d, r), CamelDebeziumTimestamp, CamelDebeziumBefore, CamelDebeziumDdlSQL; the body is the row state after the event (Struct or Map), null for tombstones. The example route also uses CamelDebeziumSourceOperation. |
| Debezium PostgreSQL Connector (Camel Quarkus 3.40.x extension) | Apache Camel Quarkus | https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/debezium-postgres.html | Artifact `org.apache.camel.quarkus:camel-quarkus-debezium-postgres`; JVM since 1.0.0, native since 1.0.0; in native mode only FileOffsetBackingStore and MemoryOffsetBackingStore are supported (any offset store in JVM mode). |
| Download (supported releases) | Apache Camel | https://camel.apache.org/download/ | 4.22.2 is marked LTS, Java 17, 21 and 25, end of life Aug 2027; 4.18.5 LTS end of life Feb 2027. LTS gets fixes "up to one year". |

### Richardson

| Source (page title) | Author/org | URL | Facts the chapters rely on |
|---|---|---|---|
| Pattern: Transactional outbox | Chris Richardson, microservices.io | https://microservices.io/patterns/data/transactional-outbox.html | Author and title confirmed (footer "Chris Richardson"). Problem: "How to atomically update the database and send messages to a message broker?" Solution: store the message in the database in the same transaction as the business change; a separate message relay sends it. Participants: sender, database, message outbox, message relay. Benefits: no 2PC; messages sent if and only if the transaction commits; order preserved. Drawbacks: the developer might forget to publish; the relay might publish a message more than once. |
| Pattern: Transaction log tailing | Chris Richardson, microservices.io | https://microservices.io/patterns/data/transaction-log-tailing.html | Author and title confirmed. Solution: tail the database transaction log (MySQL binlog, Postgres WAL, DynamoDB streams) and publish each outbox insert to the broker. Benefits: no 2PC, accurate. Drawbacks: database-specific, "tricky to avoid duplicate publishing". Related: Transactional outbox creates the need; Polling publisher is the alternative. |

### Books (titles taken from the tables above; O'Reilly and InformIT not re-fetched)

| Book | Chapter titles for chapters 08 to 10 |
|---|---|
| Bellemare, Building Event-Driven Microservices, 2nd ed. | Ch 6 Integrating Event-Driven Architectures with Existing Systems (08 outbox, 09 log tailing, 10 as the offsets and operations context). From the owner-confirmed TOC of 2026-11-10. |
| Lawrence, Message- and Event-Driven Systems with CQRS and Event Sourcing | Ch 2 Anatomy of Message-Driven Systems (08); Ch 9 Composing Data from Different Sources (09). From the InformIT TOC row. Lawrence ch 6 stays dropped for 08 (owner decision 11). |

Not fetched in this step: Strimzi "Auto-restarting connectors" (confirmed in r01, used in r12); Debezium incremental snapshot details for read-only signalling beyond the sentence above.

## r05 sources (step 1A, fetched 2026-11-11)

All rows fetched 2026-11-11 (date fetched) and read from the page, except where a row says otherwise. Status confirmed unless noted. Quotes are short phrases from the page. Authors are the project or organisation when no person is named.

### Kafka 4.3

| Source | Author/org | URL | Facts the plan relies on | Backs |
|---|---|---|---|---|
| Kafka 4.3 design, "Message Delivery Semantics" and "Using Transactions" | Apache Kafka | https://kafka.apache.org/43/design/design/ | "Exactly once" defined as "Each message is processed once and only once". The consumer's position is "stored as a message in an internal topic", written in the same transaction as the output. Consumer config "must include isolation.level=read_committed and enable.auto.commit=false"; producer sets `transactional.id`. "Advisable to use one producer instance for each consumer instance." Writing to an external system needs the consumer position stored "in the same place as its output", since many outputs "will not support a two-phase commit". Without read-committed a consumer sees aborted and open transactions. Transactions guarantee for Kafka-to-Kafka only; other destinations need cooperation | 12, 11 |
| Kafka 4.3 producer configs | Apache Kafka | https://kafka.apache.org/43/configuration/producer-configs/ | `transactional.id` default null, valid values non-empty string, "guarantee that transactions using the same TransactionalId have been completed". `transaction.timeout.ms` default 60000, coordinator "proactively aborts it". `enable.idempotence` default true. `transaction.two.phase.commit.enable` default false: "transactions that this client starts never expire". Page last modified May 22, 2026 | 12 |
| Kafka 4.3 consumer configs | Apache Kafka | https://kafka.apache.org/43/configuration/consumer-configs/ | `isolation.level` default read_uncommitted; read_committed returns only committed transactional messages; read_uncommitted returns "even transactional messages which have been aborted"; messages "always be returned in offset order", so read_committed stops at the first open transaction. `max.poll.interval.ms` default 300000; missed poll means the consumer "is considered failed" and the group rebalances. `session.timeout.ms` default 45000 (45 seconds), the broker removes a member that sends no heartbeat before it expires; not used with `group.protocol=consumer` (re-fetched 2026-11-11) | 11, 12, 13 |
| KIP-98: Exactly Once Delivery and Transactional Messaging (Adopted) | Apache Kafka | https://cwiki.apache.org/confluence/display/KAFKA/KIP-98+-+Exactly+Once+Delivery+and+Transactional+Messaging | Producer epoch "ensure that there is only one legitimate active instance" per TransactionalId; a new instance bumps the epoch so a zombie "is fenced off"; `sendOffsets` persists offsets via TxnOffsetCommit in `__consumer_offsets` | 12 |
| KIP-447: Producer scalability for exactly once semantics (Adopted, 2.6.0) | Apache Kafka | https://cwiki.apache.org/confluence/display/KAFKA/KIP-447%3A+Producer+scalability+for+exactly+once+semantics | New `sendOffsetsToTransaction` variant takes consumer group state ("generation.id, member.id and group.instance.id"); `initTransactions` fences producers with the same transactional.id. Used for: the offsets call carries group metadata | 12 |
| KIP-890: Transactions Server-Side Defense (Approved) | Apache Kafka | https://cwiki.apache.org/confluence/display/KAFKA/KIP-890%3A+Transactions+Server-Side+Defense | Page opened; status "Approved". Cited only if ch 12 explains hanging transactions or the server-side epoch bump; no plan fact depends on it | 12 (optional) |
| KIP-939: Support Participation in 2PC (Accepted) | Apache Kafka | https://cwiki.apache.org/confluence/display/KAFKA/KIP-939%3A+Support+Participation+in+2PC | Status "Accepted". Proposes `.prepareTransaction` returning a prepared state and `.completeTransaction`; InitProducerId gains `Enable2Pc` and `KeepPreparedTxn`. The KIP describes a proposal; the shipped 4.3.2 client does not expose the two methods (see versions.md) | not cited (removed from ch 12 and the bibliography in r05) |
| KIP-664: Provide tooling to detect and abort hanging transactions (Adopted) | Apache Kafka | https://cwiki.apache.org/confluence/display/KAFKA/KIP-664%3A+Provide+tooling+to+detect+and+abort+hanging+transactions | "There will be a new tool: `kafka-transactions.sh`", with `find-hanging` and `abort`. This is the documentation of the tool: the Kafka 4.3 docs "operations" pages (basic-kafka-operations, monitoring, kraft) do not mention `kafka-transactions.sh` (0 hits, checked), so the plan's "operations doc" source is KIP-664 plus the tool's own `--help` | 12 |
| kafka-transactions.sh, console producer, console consumer (`--help`) | Apache Kafka (image apache/kafka:4.3.2) | `docker run --rm apache/kafka:4.3.2 /opt/kafka/bin/<tool> --help` | Commands `list`, `describe`, `describe-producers`, `abort`, `find-hanging`, `forceTerminateTransaction`; `--reader-property parse.headers=true` with `headers.delimiter`, `headers.separator` `,`, `headers.key.separator` `:`; consumer `--isolation-level` and `--formatter-property print.headers=true`. Full detail in versions.md "Tool syntax" | 12, 13 |
| Kafka 4.3 javadoc: KafkaProducer, PreparedTxnState | Apache Kafka | https://kafka.apache.org/43/javadoc/org/apache/kafka/clients/producer/KafkaProducer.html | Page returns 200 and has no `prepareTransaction`/`completeTransaction`. Cross-check: `javap` on kafka-clients-4.3.2.jar finds neither on `KafkaProducer` or `Producer`. Answer: not public | 12 |

### Strimzi

| Source | Author/org | URL | Facts the plan relies on | Backs |
|---|---|---|---|---|
| Exactly-once semantics with Kafka transactions (2023-05-03, re-read) | Federico Valeri, Strimzi | https://strimzi.io/blog/2023/05/03/kafka-transactions/ | "Transactions are only supported inside a single Kafka cluster", and "The transactional outbox pattern could help". Producers with the same `transactional.id` "are considered zombies, and are fenced off". Each producer "must configure its own static and unique transactional.id". Offsets sent with group metadata (KIP-447). Aborted messages "are never observed by the client application". "Enabling EOS has an effect on throughput, which can be significant." LSO defined as "all lower offsets have been decided". Has a "Hanging transactions" section | 12 |

### SmallRye Reactive Messaging

Versioned 4.37.0 pages exist and were fetched (the `latest` alias is 5.0.0-RC1). The Transactions page text is identical on 4.37.0 and latest. The Receiving page was read from 4.37.0.

| Source | Author/org | URL | Facts the plan relies on | Backs |
|---|---|---|---|---|
| Kafka Transactions and Exactly-Once Processing (4.37.0) | SmallRye | https://smallrye.io/smallrye-reactive-messaging/4.37.0/kafka/transactions/ | `KafkaTransactions` custom emitter, `withTransaction` receives a `TransactionalEmitter`; "single transactional producer and a global lock, meaning only one transaction" at a time; `withTransactionAndAck` acks and nacks "but will not stop the reactive stream"; `commit-strategy` "needs to be ignore"; "Any strategy can be employed for the failure-strategy"; on abort "position is reset to the last committed offset"; with no committed offset "reset to the beginning of the topic, even if the offset reset policy is latest"; readers must set `isolation.level` to read_committed. The page does not name `markForAbort`; confirmed by javap in the plan, not by this page. Confirmed on 4.37.0 | 12 |
| Receiving records (4.37.0): Failure Management, Dead Letter Queue, Deserialization | SmallRye | https://smallrye.io/smallrye-reactive-messaging/4.37.0/kafka/receiving-kafka-records/ | Strategies: `fail` "(default)", offset not committed; `ignore`; `dead-letter-queue` commits the offset and writes the record to a DLQ topic; `delayed-retry-topic` marked "Experimental" ("Delayed retry topic feature is experimental"). `dead-letter-queue.topic` default `dead-letter-topic-$channel`. Headers listed: `dead-letter-reason`, `-cause`, `-topic`, `-partition`, `-offset`. **Not on the page** (found by javap constants on the 4.37.0 jar): `dead-letter-exception-class-name`, `dead-letter-cause-class-name`. Retry topics named `<channel>_retry_<ms>`. Without a handler a deserialization failure marks the app "unhealthy"; with `fail-on-deserialization-failure` false and DLQ, the record is "sent to the corresponding dead letter queue topic" with the "original key and value". **Plan correction**: the page lists headers `deserialization-failure-key-data` and `-value-data` ("not able to be deserialized"); the jar also defines `deserialization-failure-data`. Spike S4 decides which one the chapter shows | 13 |

### Quarkus

| Source | Author/org | URL | Facts the plan relies on | Backs |
|---|---|---|---|---|
| Quarkus Kafka guide (3.40, "latest"), sections 4.1, 4.3, 4.4, 4.4.1, 5.8, 7.2, 29.8 | Quarkus | https://quarkus.io/guides/kafka | 4.1: with `@Transactional` the method "will be considered blocking automatically". 4.3: `throttled` "guarantees at-least-once delivery". 4.4: failure strategies as SmallRye, DLQ headers as above. 4.4.1: `@Retry(delay = 10, maxRetries = 5)` on an `@Incoming` method. 5.8: "one transaction at a time per producer"; `isolation.level` read_committed on the incoming channel; abort resets to last committed offset or the beginning even with `latest`; `failure-strategy=ignore` resets and resumes. 7.2: "managing consumer offsets inside a transaction, together with produced messages"; "recommended to use exactly-once processing along with the batch consumption mode", unbatched has "significant performance impact"; `withTransactionAndAck`. 29.8 (Hibernate ORM): "the database transaction (inner) will commit followed by the Kafka transaction (outer)". The guide makes no atomicity claim across the two. Versioned guides are not offered; 3.40 is labelled latest | 11, 12, 13 |
| Quarkus SmallRye Fault Tolerance guide | Quarkus | https://quarkus.io/guides/smallrye-fault-tolerance | `@Retry(maxRetries = 4)`, retry-on/abort-on style exception filters, `maxRetries` overridable by config. **Does not confirm** the plan's interceptor-order point (`@Transactional` outside `@Retry`): the guide does not mention transactions or ordering. Spike S4 is the evidence | 13 |
| Quarkus Hibernate ORM with Panache | Quarkus | https://quarkus.io/guides/hibernate-orm-panache | "direct support for database locking ... using findById(Object, LockModeType) or find().withLock(LockModeType)"; example `PESSIMISTIC_WRITE`. **Does not confirm** a lock-timeout hint or a native-query form: the page has no hint or native-query text (grep, 0 hits). Spike evidence needed for those | 11 |
| Quarkus Using transactions | Quarkus | https://quarkus.io/guides/transaction | `@Transactional` and `QuarkusTransaction`; "A RuntimeException crossing the transaction boundaries will roll back the transaction"; `REQUIRES_NEW` suspends the existing one and starts a new one; `rollbackOn`/`dontRollbackOn`; `setRollbackOnly()` | 11, 13 |

### Camel 4.22.x and Camel Quarkus 3.40.x

| Source | Author/org | URL | Facts the plan relies on | Backs |
|---|---|---|---|---|
| Idempotent Consumer EIP (4.22.x LTS) | Apache Camel | https://camel.apache.org/components/4.22.x/eips/idempotentConsumer-eip.html | `eager` "Eager is default enabled" (true); `removeOnFailure` "remove the key on failure" (true); `skipDuplicate` true; `completionEager` false. Repositories listed: `JdbcMessageIdRepository`, `JpaMessageIdRepository`, `KafkaIdempotentRepository`. "Camel will add the message id eagerly to the repository" | 11 |
| JPA component (4.22.x) | Apache Camel | https://camel.apache.org/components/4.22.x/jpa-component.html | `JpaMessageIdRepository` configured with an `EntityManagerFactory`; consumer option `transacted` defaults false | 11 |
| Camel Quarkus JPA extension (3.40.x) | Apache Camel Quarkus | https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/jpa.html | "leverages Quarkus TX API to provide TransactionStrategy"; `JpaMessageIdRepository` takes `EntityManagerFactory` and `TransactionStrategy` from the CDI container. **Does not say** whether `add()` joins an existing JTA transaction; spike S1 decides | 11 |
| Camel Quarkus JTA extension (3.40.x) | Apache Camel Quarkus | https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/jta.html | Needed for `transacted()`; "leverages the transaction capabilities provided by the narayana-jta extension"; `.transacted("PROPAGATION_REQUIRED")` | 11, 12 |
| Transactional Client EIP (4.22.x) | Apache Camel | https://camel.apache.org/components/4.22.x/eips/transactional-client.html | Routes are enabled with `transacted` right after `from`; convention over configuration with a single JTA or Spring transaction manager; other propagations need extra configuration | 11, 12 |
| Kafka component (4.22.x) | Apache Camel | https://camel.apache.org/components/4.22.x/kafka-component.html | "Kafka Transaction" section: `transactionalId=<tx-id>` or `transacted=true`; the id "is made from the endpoint id and route id"; if both are set "the latter takes precedence"; the producer commits or aborts at end of exchange routing. "Kafka does not support transactions in multi threads", `ProducerFencedException` on a second producer. With JTA "risk with the data consistency" because Kafka "does not support JTA/XA". `isolationLevel` default read_uncommitted. `breakOnFirstError` default false; true with `NoopCommitManager` leaves the offset uncommitted so the message is re-attempted, which can loop ("endless processing of the same message"). `pollOnError` default ERROR_HANDLER: "use Camel's error handler ... and afterwards continue to poll". `KafkaIdempotentRepository` documented on the page. No page text on `sendOffsetsToTransaction` | 11, 12, 13 |
| Dead Letter Channel (4.22.x) | Apache Camel | https://camel.apache.org/components/4.22.x/eips/dead-letter-channel.html | `useOriginalMessage` "routing the original input message instead of the current message"; redelivery policy before the dead letter | 13 |
| Error Handler | Apache Camel | https://camel.apache.org/manual/error-handler.html | `DefaultErrorHandler` is the default and "does not support a dead letter queue"; `TransactionErrorHandler` is the default for transacted routes. Unversioned manual page | 13 |
| Exception Clause | Apache Camel | https://camel.apache.org/manual/exception-clause.html | `onException(...)` per exception type; `handled(true)`; handled versus continued. Unversioned manual page | 13 |
| KafkaProducer.java and KafkaTransactionSynchronization.java at tag camel-4.22.2 | Apache Camel (source) | https://github.com/apache/camel/blob/camel-4.22.2/components/camel-kafka/src/main/java/org/apache/camel/component/kafka/KafkaProducer.java and https://github.com/apache/camel/blob/camel-4.22.2/components/camel-kafka/src/main/java/org/apache/camel/component/kafka/KafkaTransactionSynchronization.java | The plan's path `producer/support/KafkaTransactionSynchronization.java` is wrong; the file is in the component's top package (the raw URL under `producer/support/` returned 404). `KafkaProducer` calls `initTransactions()` and `beginTransaction()` and registers `new KafkaTransactionSynchronization(transactionId, kafkaProducer)` on the unit of work. The synchronization's `onDone` calls `abortTransaction()` when the exchange has an exception or is rollback-only, else `commitTransaction()`; a `KafkaException` closes the producer. Neither file contains `sendOffsetsToTransaction` | 12 |

### PostgreSQL 18

| Source | Author/org | URL | Facts the plan relies on | Backs |
|---|---|---|---|---|
| INSERT (ON CONFLICT) | PostgreSQL Global Development Group | https://www.postgresql.org/docs/18/sql-insert.html | ON CONFLICT is "an alternative action to raising a unique violation"; `DO NOTHING` allows an omitted conflict_target; insert into a table with unique indexes "might block if concurrent sessions" hold the same key | 11 |
| 19.11. Client Connection Defaults (lock_timeout) | PostgreSQL Global Development Group | https://www.postgresql.org/docs/18/runtime-config-client.html | `lock_timeout`: "this timeout can only occur while waiting for locks"; pointless at or above `statement_timeout` | 11 |
| SELECT, The Locking Clause | PostgreSQL Global Development Group | https://www.postgresql.org/docs/18/sql-select.html | `FOR` with `UPDATE`, `NO KEY UPDATE`, `SHARE` or `KEY SHARE`, then optionally `NOWAIT` or `SKIP LOCKED`; the statement "locks the selected rows against concurrent updates" | 11 |

### Hohpe and Woolf, Richardson

| Source | Author/org | URL | Facts the plan relies on | Backs |
|---|---|---|---|---|
| Idempotent Receiver | Gregor Hohpe, Bobby Woolf | https://www.enterpriseintegrationpatterns.com/patterns/messaging/IdempotentReceiver.html | Problem: "How can a message receiver deal with duplicate messages?" Solution: "Design a receiver to be an Idempotent Receiver" | 11 |
| Transactional Client | Gregor Hohpe, Bobby Woolf | https://www.enterpriseintegrationpatterns.com/patterns/messaging/TransactionalClient.html | Problem: "How can a client control its transactions with the messaging system?" Solution: make the session transactional so the client "can specify transaction boundaries" | 12 |
| Dead Letter Channel | Gregor Hohpe, Bobby Woolf | https://www.enterpriseintegrationpatterns.com/patterns/messaging/DeadLetterChannel.html | Problem: "What will the messaging system do with a message it cannot deliver?" | 13 |
| Invalid Message Channel | Gregor Hohpe, Bobby Woolf | https://www.enterpriseintegrationpatterns.com/patterns/messaging/InvalidMessageChannel.html | Problem: "How can a messaging receiver gracefully handle receiving a message that makes no sense?" | 13 |
| Pattern: Idempotent Consumer | Chris Richardson, microservices.io | https://microservices.io/patterns/communication-style/idempotent-consumer.html | URL returns 200, title "Pattern: Idempotent Consumer". Problem: "How does a message consumer handle duplicate messages correctly?" Solution: record "the IDs of processed messages in the database". Two options: a separate PROCESSED_MESSAGES table (insert the id in the handler's transaction; primary key (subscriberId, messageID); a failed INSERT means a duplicate, roll back and ignore) or store ids in the business entities. Related: Eventuate framework | 11 |

### Books and sibling links

| Source | Author/org | URL | Facts the plan relies on | Backs |
|---|---|---|---|---|
| Bellemare, Building Event-Driven Microservices, 2nd ed., ch 18 | Adam Bellemare, O'Reilly | https://www.oreilly.com/library/view/building-event-driven-microservices/9798341622180/ (not re-fetched; O'Reilly refuses scripts) | Title taken from the owner-confirmed TOC row above: "18 Handling Bad Data in Event Streams". Fit by title only, for ch 13. Chapters 11 and 12 have no TOC title naming their topic, so none is cited for 11 or 12 | 13 |
| Lawrence, Message- and Event-Driven Systems with CQRS and Event Sourcing, ch 2 | Alex Lawrence, Addison-Wesley | https://www.informit.com/store/message-and-event-driven-systems-with-cqrs-and-event-9780137998616 (TOC row above; not re-fetched) | Title taken from the existing TOC row: "2 Anatomy of Message-Driven Systems". Fit by title only, for ch 11 and 13. No chapter cited for 12 | 11, 13 |
| EIP Camel ch 5, Channel Reliability | Patterncatalyst | https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/05-channel-reliability/ | HTTP 200, title "Channel Reliability". Source `_docs/05-channel-reliability.md` (permalink /docs/:name/): `deadLetterChannel(...)` with `.useOriginalMessage()` and `.onPrepareFailure(...)`; a `.dlq` topic suffix; Invalid Message Channel with `eip.orders.invalid`; a guaranteed-delivery table row "Kafka transactions + idempotent producer" | 13 |
| EIP Camel ch 15, Producer and Transactional Patterns | Patterncatalyst | https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/15-producer-patterns/ | HTTP 200. Idempotent Receiver backed by Redis or JDBC, `event_id` as the key, a warning about repositories without TTL, and "XA/2PC ... Kafka (which doesn't support XA)" with the outbox as the alternative | 11, 12 |
| CNDP ch 5, Event-Driven | Patterncatalyst | https://patterncatalyst.github.io/cloud-native-design-patterns/docs/05-event-driven/ | HTTP 200. Source line: "Exactly-once" is "at-least-once plus a dedupe you wrote" | 12 |
| CNDP ch 6, Stream Processing | Patterncatalyst | https://patterncatalyst.github.io/cloud-native-design-patterns/docs/06-stream-processing/ | HTTP 200. Source: `processing.guarantee=exactly_once_v2` wraps consume-transform-produce | 12 |

### r05 sources: facts that did not confirm

- Kafka docs have no page for `kafka-transactions.sh` operations; KIP-664 and the tool help stand in.
- SmallRye 4.37.0 Receiving page omits `dead-letter-exception-class-name` and `dead-letter-cause-class-name` (in the jar) and documents `deserialization-failure-key-data`/`-value-data` rather than the single `deserialization-failure-data` the plan names (jar has all three).
- Quarkus Fault Tolerance guide says nothing on `@Transactional` and `@Retry` ordering; Panache page has no lock-timeout hint or native-query text; Camel Quarkus JPA page does not say whether the id repository joins the JTA transaction.
- Plan path for `KafkaTransactionSynchronization.java` was wrong (corrected above).

## r06 sources (step 1A, fetched 2026-11-12)

### Books

| Source | Author, publisher | URL | Confirmed | Used for |
|---|---|---|---|---|
| Vernon, Implementing Domain-Driven Design | Vaughn Vernon, Addison-Wesley, 2013 (hub books.yml: print ISBN 9780133039894; O'Reilly catalog urn 9780133039900, 656 pages, published 2013-02-06) | https://www.informit.com/store/implementing-domain-driven-design-9780133039894 | Table of contents read from the InformIT page, 2026-11-12. The O'Reilly search tool returned catalog metadata only (title, author, date, pages), no chapter text, so the InformIT TOC is the confirmation. Chapters: 1 Getting Started with DDD; 2 Domains, Subdomains, and Bounded Contexts; 3 Context Maps; 4 Architecture; 5 Entities; 6 Value Objects; 7 Services; **8 Domain Events**; 9 Modules; **10 Aggregates**; 11 Factories; 12 Repositories; 13 Integrating Bounded Contexts; 14 Application. **Appendix A: Aggregates and Event Sourcing: A+ES** | 14 (Aggregates, Appendix A), 15 |
| Kleppmann and Riccomini, Designing Data-Intensive Applications, 2nd ed. | Martin Kleppmann and Chris Riccomini, O'Reilly, 2026 (hub books.yml: print ISBN 9781098119065, URL carries ebook id 9781098119058; O'Reilly catalog urn 9781098119058, published 2026-02-18, 672 pages) | https://www.oreilly.com/library/view/designing-data-intensive-applications/9781098119058/ | Edition, ISBN and date confirmed (catalog search and hub). **Chapter title NOT confirmed**: the O'Reilly search tool returns no chapter text, oreilly.com answers 403 to scripts, dataintensive.net links to the O'Reilly TOC without listing it, and no sibling repo records a chapter title. Only chapter 1, "Trade-offs in Data Systems Architecture", appeared in a search result. Owner to paste the 2nd edition TOC; until then the bibliography row cites the book and no chapter | 14, 15 (book only) |

### Greg Young, CQRS Documents

- URL: https://cqrs.wordpress.com/wp-content/uploads/2010/11/cqrs_documents.pdf (HTTP 200, 1.5 MB PDF, title "CQRS Documents by Greg Young", footer cqrsinfo.com). Text extracted with pdftotext and read.
- Contents entries with the page numbers printed in the document: "Events as a Storage Mechanism" 25; "Rolling Snapshots" 33 (inside that part) and 44 (inside "Building an Event Storage", which starts at 41); "Event Storage as a Queue" 46. The plan's page numbers all confirmed.
- "Rolling Snapshots" (p. 33 part): called "a heuristic" and the event stream "is still viewed in its entirety". The tuning point is that the maximum number of events loaded can be controlled. Confirmed.
- p. 44 part: a Snapshots table holds the serialized aggregate and a version number; a "[SnapShotter]" process runs outside the application server, asynchronously, and queries for aggregates "gone past the allowed number of events". Confirmed.
- Status: confirmed.

### Fowler

| Source | URL | Confirmed |
|---|---|---|
| DDD Aggregate, Martin Fowler, 23 April 2013 | https://martinfowler.com/bliki/DDD_Aggregate.html | HTTP 200. Defines an aggregate as "a cluster of domain objects that can be treated as a single unit" |
| Event Sourcing, Martin Fowler, 12 December 2005 | https://martinfowler.com/eaaDev/EventSourcing.html | HTTP 200. "Capture all changes to an application state as a sequence of events"; state can be rebuilt "by re-running the events from the event log"; an overnight snapshot is the example of a starting point |

### Microsoft Azure Architecture Center, Event Sourcing pattern

- URL: https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing (ms.date 2026-03-27, updated 2026-09-25).
- Snapshot paragraph, under "Entity state re-creation", confirmed: snapshots at intervals such as every N events; rehydrate by loading the latest snapshot and replaying only later events; frequency trades snapshot storage against rehydration time; the note says snapshots are "an optimization, not a replacement for the eventstream" and can be regenerated from it. The same page says an event store rejects an append through optimistic concurrency control if the stream changed since it was read, and that a message broker such as Kafka is not a substitute for an event store.

### RFC 9110 (HTTP Semantics, June 2022)

- URL: https://www.rfc-editor.org/rfc/rfc9110.html (text read from https://www.rfc-editor.org/rfc/rfc9110.txt).
- Section 13.1.1 "If-Match" confirmed: makes the method conditional on an entity tag match; the origin server "MUST use the strong comparison function".
- Section 15.5.13 "412 Precondition Failed" confirmed: "one or more conditions given in the request header fields evaluated to false", preventing the method from being applied when the resource is in an unexpected state.

### Quarkus, Camel and PostgreSQL pages (HTTP status and content checked 2026-11-12)

| Page | URL | Result |
|---|---|---|
| Quarkus Hibernate ORM with Panache | https://quarkus.io/guides/hibernate-orm-panache | 200. `PanacheQuery` exposes `.stream()` (page example `Person.find(...).page(...).nextPage().stream()`). No native-query text on this page or on https://quarkus.io/guides/hibernate-orm: not confirmed there (Jakarta Persistence `EntityManager.createNativeQuery` is the API) |
| Quarkus SmallRye Fault Tolerance | https://quarkus.io/guides/smallrye-fault-tolerance | 200. `@Retry` demonstrated on a resource method; `maxRetries` appears. `retryOn` is not on this page: it is in the MicroProfile Fault Tolerance specification, https://download.eclipse.org/microprofile/microprofile-fault-tolerance-4.1/microprofile-fault-tolerance-spec-4.1.html (200), which lists `Retry/maxRetries`, `Retry/maxDuration` and `retryOn`. The version of the spec the platform implements was not checked |
| Quarkus Kafka guide | https://quarkus.io/guides/kafka | 200. `@Blocking` described for database work in message processing; acknowledgement covered |
| Quarkus REST guide | https://quarkus.io/guides/rest | 200. Conditional-request example uses `jakarta.ws.rs.core.EntityTag`, `Request` and `request.evaluatePreconditions(...)` |
| Camel exception clause | https://camel.apache.org/manual/exception-clause.html | 200. `onException` and redelivery covered. The `/components/4.22.x/eips/exception-clause.html` URL is 404; the manual URL (already in the bibliography) is the right one |
| Camel Scheduler component 4.22.x | https://camel.apache.org/components/4.22.x/scheduler-component.html | 200. `delay` and `initialDelay` (default 1000 ms) options |
| Camel SQL component 4.22.x | https://camel.apache.org/components/4.22.x/sql-component.html | 200. Named parameters with the `:#name` syntax |
| Camel Split EIP 4.22.x | https://camel.apache.org/components/4.22.x/eips/split-eip.html | 200. `streaming` option present |
| Camel Quarkus scheduler and SQL extensions 3.40.x | https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/scheduler.html ; https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/sql.html | 200 both |
| PostgreSQL 18 INSERT | https://www.postgresql.org/docs/18/sql-insert.html | 200. `ON CONFLICT`, `DO UPDATE`, `conflict_target`; the `DO UPDATE ... WHERE` condition clause is part of the synopsis (the page has 18 `DO UPDATE` mentions) |
| PostgreSQL 18 constraints | https://www.postgresql.org/docs/18/ddl-constraints.html | 200. Unique constraints, including multi-column |
| PostgreSQL 18 set returning functions | https://www.postgresql.org/docs/18/functions-srf.html | 200. `generate_series` |
| PostgreSQL 18 EXPLAIN | https://www.postgresql.org/docs/18/sql-explain.html | 200. `EXPLAIN` and `ANALYZE` |

### Lawrence, Message- and Event-Driven Systems

- Not re-fetched. Rows already above: 4 "Tactical Building Blocks", 5 "CQRS", 6 "Event Sourcing" (InformIT TOC, 2026-11-10). The plan's "ch 4 and ch 6" refer to these titles.

### r06 facts that did not confirm

- Kleppmann and Riccomini 2nd edition: no chapter title obtained (above). Do not cite a chapter until the owner pastes the TOC.
- Native-query text is not in the Panache or Hibernate ORM guides.
- `retryOn` is documented in the MicroProfile specification, not the Quarkus guide.
- Evans, Domain-Driven Design: not fetched; the plan makes it conditional on the owner, who has not asked for it.

## r07 sources (step 1A, fetched 2026-11-12)

Every page below was fetched by script on 2026-11-12 (HTTP 200) and read as text. Facts are paraphrased; nothing is copied into reader-facing files. oreilly.com was not fetched (it refuses scripts).

### Pattern references (CQRS, read models)

| Source | URL | Fetched | Facts confirmed that the chapters rely on |
|---|---|---|---|
| Fowler, "CQRS", Martin Fowler bliki (dated 14 July 2011) | https://martinfowler.com/bliki/CQRS.html | 2026-11-12 | CQRS is a pattern Fowler first heard from Greg Young. Core idea: a different model to update information than the model used to read it. Separate models may share one database or use separate databases (the query side then acts as a reporting database fed by some communication mechanism). It fits event-based models and Event Sourcing, and separate models raise the likelihood of eventual consistency. Fowler warns that for most systems it adds risky complexity, should apply to a bounded context rather than a whole system, and that a ReportingDatabase is the lighter alternative for demanding queries. Confirmed |
| Microsoft Learn, "CQRS pattern" (Azure Architecture Center; ms.date 2025-02-20, page marked last updated 2025-02-21) | https://learn.microsoft.com/en-us/azure/architecture/patterns/cqrs | 2026-11-12 | Separate read and write models, with a single data store or with separate stores. With separate stores the write model publishes events the read model uses to refresh; because a broker and a database cannot share one distributed transaction, use the Transactional Outbox and make the read-model consumer idempotent. The read store can hold a materialized view. Costs named: added complexity, and eventual consistency (the read side can serve stale data and the application must handle a user acting on it). With Event Sourcing the same events feed the read model, and views can be regenerated by replaying history. Confirmed |
| Microsoft Learn, "Materialized View pattern" (Azure Architecture Center; ms.date 2026-09-21, last updated 2026-09-25) | https://learn.microsoft.com/en-us/azure/architecture/patterns/materialized-view | 2026-11-12 | A materialized view is completely disposable because it can be entirely rebuilt from the source data stores; consumers do not update it, a dedicated component maintains it. Design points named: refresh strategy (event-driven or scheduled), full rebuild versus incremental, whether refresh blocks reads, refresh-signal reliability (a lost signal silently serves stale results, so monitor view age), refresh compute cost, and consistency during refresh (maximum staleness follows the refresh interval or event lag). Rebuild safely by serving the last complete view while a new one is built, then swapping. Confirmed |
| Young, *CQRS Documents* (PDF, 57 pages; the PDF page index equals the printed page number) | https://cqrs.wordpress.com/wp-content/uploads/2010/11/cqrs_documents.pdf | 2026-11-12 (local text copy re-read) | Contents: "Command and Query Responsibility Segregation" p. 17; "CQRS and Event Sourcing" p. 50. p. 17 to 19: CQRS splits a service into a write (command) service and a read (query) service. p. 19: the command side wants consistent, normalised data; the query side can be eventually consistent and wants denormalised data to minimise joins; reads are often two or more orders of magnitude more numerous than writes, so scaling is mostly a query-side need. p. 23: with reads separated, the domain handles only commands, repositories need few query methods beyond GetById, and the two sides can be treated as two integrated systems kept in sync consistently or eventually. p. 50 to 52: CQRS and Event Sourcing combine well; event handlers update the read model, so the impedance mismatch sits between events and the relational read model, and Young judges it small. p. 54: the read model consumes events and produces DTOs. Confirmed |
| Young, *Versioning in an Event Sourced System* (Leanpub; "90% complete", last updated 2017-04-09; pay what you want, minimum price) | https://leanpub.com/esversioning | 2026-11-12 | Only the table of contents is readable without buying. Chapter titles: Why Version? (sub-heads include Why can't I update an event?, Immutability, Consumers, Audit); Basic Type Based Versioning (Define a Version of an Event, Double Write); Weak Schema (Mapping, Wrapper); Negotiation; General Versioning Concerns (Versioning of Behaviour, Changing Semantic Meaning, Snapshots); Whoops, I Did It Again (compensating actions); Copy and Replace (Simple Copy-Replace, With a System Live, Stream Boundaries are Wrong); Cheating (Two Aggregates One Stream, One Aggregate Two Streams, Copy-Transform, Versioning Bankruptcy); Internal vs External Models (External Integrations, Granularity, Rate of Change, How to Implement); Versioning Process Managers (Basic Versioning, **Upcasting State**, Take Over). **The word "upcasting" appears only as one sub-heading under Versioning Process Managers**, so the plan's earlier "not confirmed" is refined: the term is in the TOC, applied to process-manager state. The body was not read. Cite by author, title and chapter titles only; no content claim rests on it. Confirmed (TOC only) |

### Avro and Apicurio Registry

| Source | URL | Fetched | Facts confirmed that the chapters rely on |
|---|---|---|---|
| Apache Avro specification, "Schema Resolution" (docs 1.12.0, the latest docs version listed; `/docs/current/` redirects to a placeholder path and is not citable) | https://avro.apache.org/docs/1.12.0/specification/ | 2026-11-12 | The reader's schema may differ from the writer's. Record fields are matched by name, order may differ. A writer field absent from the reader's record is ignored. A reader field absent from the writer's data takes the reader field's default; with no default it is an error. Enums: a writer symbol missing from the reader's enum falls back to the reader's enum default, else an error. The `doc` fields are ignored. The writer's schema must travel with the data. The plan's two facts (defaults for missing writer fields; unknown writer fields ignored) both confirmed. The text was read from the unversioned docs page, which matches the 1.12 line the Avro 1.12.2 library implements |
| Apicurio Registry 3.3.x, "Apicurio Registry content rule reference" | https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-rule-reference.html | 2026-11-12 | Rule types VALIDITY, COMPATIBILITY, INTEGRITY. COMPATIBILITY values: FULL, FULL_TRANSITIVE, BACKWARD (new artifact's clients read data written with the most recently added version), BACKWARD_TRANSITIVE (all previous versions), FORWARD, FORWARD_TRANSITIVE, NONE. Avro has full maturity for all three rules. Artifact-specific rules override the equivalent global rule. Confirmed |
| Apicurio Registry 3.3.x, "Apicurio Registry content rules" (introduction) | https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-intro-to-registry-rules.html | 2026-11-12 | Rules apply only when content is added (adding an artifact, adding an artifact version). On violation the registry returns an HTTP error whose body names the violated rule and a message. **Precedence in 3.3.x has three levels**: artifact-specific, then group-specific, then global (the plan named two). To disable a higher-level rule at a lower level, configure the same rule there with value NONE. Rules receive the current version and the new one. Default global rules can be set with application properties. Confirmed; the status code is not on this page |
| Apicurio Registry 3.3.x, "Apicurio Registry schema compatibility modes" | https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-registry-compatibility-modes.html | 2026-11-12 | Avro change table: adding a field with a default is backward and forward compatible; adding one without a default is not backward compatible; removing a field that has a default is both; removing one without a default is not forward compatible; adding a union type is backward only; removing one is forward only; renaming without an alias and changing a type break both. The transitive modes compare against every prior version. **The page's worked example is the chapter 18 case**: v1 `name`; v2 adds `email` with a default; v3 removes that default. v3 passes BACKWARD (checked against v2 only) and fails BACKWARD_TRANSITIVE (v1 has no `email` and v3 has no default). Confirmed |
| Apicurio Registry 3.3.4, REST API v3 OpenAPI document (`common/src/main/resources/META-INF/openapi.json` at tag 3.3.4, info version 3.3.x) | https://raw.githubusercontent.com/Apicurio/apicurio-registry/3.3.4/common/src/main/resources/META-INF/openapi.json | 2026-11-12 | Global rules: `/admin/rules` (GET, POST, DELETE) and `/admin/rules/{ruleType}` (GET, PUT, DELETE). Group rules: `/groups/{groupId}/rules`. Artifact rules: `/groups/{groupId}/artifacts/{artifactId}/rules` and `.../rules/{ruleType}`. Adding a version: `POST /groups/{groupId}/artifacts/{artifactId}/versions` with documented responses 200, 400, 401, 403, 404, 409, 422, 500. **The operation text says content that violates a configured rule returns HTTP 400**, with a `RuleViolationProblemDetails` body (status, title, `causes` list of description and context); 409 is the conflict response (for example an artifact that already exists). So the violation status is 400 in the v3 API, not 409. Confirmed from the specification; spike S4 observes it live |
| Apicurio Registry 3.3.x, "Managing content using the REST API" | https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-managing-registry-artifacts-api.html | 2026-11-12 | Core Registry API v3 base path `/apis/registry/v3`; `/groups/{groupId}/artifacts` creates an artifact, versions live under `/groups/{groupId}/artifacts/{artifactId}/versions/{version}`; group `default` is used when none is given; the first version number is 1 unless one is given. Confirmed |
| Apicurio Registry 3.3.x, "Configuring Kafka client serializers and deserializers" | https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-configuring-kafka-client-serdes.html | 2026-11-12 | `apicurio.registry.auto-register` (serializer only, default false). `apicurio.registry.find-latest` (default false). `apicurio.registry.artifact-resolver-strategy` (default `TopicIdStrategy`: the topic name is the artifactId). `apicurio.registry.use-id` options globalId and contentId, **default contentId**. `apicurio.registry.id-handler` default is the 4-byte handler (`Default4ByteIdHandler`; `Legacy8ByteIdHandler` is the alternative), and the id is written in the payload after a magic byte unless `apicurio.registry.headers.enabled` moves it to headers; the deserializer finds the id location by checking for the magic byte. `apicurio.registry.avro.encoding` BINARY (default) or JSON. `apicurio.registry.use-specific-avro-reader`. The Avro serializer verifies the registered schema structurally matches the record's schema. Confirmed |
| Quarkus, "Dev Services for Apicurio Registry" and the all-config reference | https://quarkus.io/guides/apicurio-registry-dev-services ; https://quarkus.io/guides/all-config | 2026-11-12 | Property `quarkus.apicurio-registry.devservices.image-name`; only Apicurio Registry 3.x images whose repository ends in `apicurio/apicurio-registry` are supported; the default in the Quarkus 3.40 line is `quay.io/apicurio/apicurio-registry:3.1.7` (not the 3.3.4 server pin, so each example must set it). Sharing is on in dev mode and off in test mode; the port can be set with `quarkus.apicurio-registry.devservices.port`; Kafka channels are configured with the chosen port. Confirmed |
| Quarkus, "Using Apache Kafka with Schema Registry and Avro" | https://quarkus.io/guides/kafka-schema-registry-avro | 2026-11-12 | `src/main/avro/*.avsc` is compiled to classes by the `quarkus-avro` extension; Apicurio Registry serde classes (`AvroKafkaSerializer`, `AvroKafkaDeserializer`) are autodetected from the `@Channel` declaration and the type; `mp.messaging.outgoing.<channel>.apicurio.registry.auto-register=true` registers the schema; the specific Avro reader is configured automatically; the Confluent analogue is `auto.register.schemas`. The page has no section on compatibility rules. Confirmed |

### Debezium, Camel, Quarkus, SmallRye, PostgreSQL

| Source | URL | Fetched | Facts confirmed that the chapters rely on |
|---|---|---|---|
| Debezium Outbox Event Router, "Using Apache Avro as the payload format" | https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html | 2026-11-12 | With Avro the payload column must be a binary type such as `bytea` on PostgreSQL; `value.converter=io.debezium.converters.BinaryDataConverter` passes the payload bytes through as the Kafka value. How the application produces the Avro bytes is out of Debezium's scope (a registry serializer before the insert is the documented direction). Heartbeat, transaction metadata and schema change events cannot go through BinaryDataConverter, so a delegate converter is configured with `value.converter.delegate.converter.type` (the page's example uses JsonConverter with schemas disabled). Confirmed |
| Debezium Outbox Quarkus extension | https://debezium.io/documentation/reference/stable/integrations/outbox.html | 2026-11-12 | The payload column is configured by `quarkus.debezium-outbox.payload.name` (default `payload`), `.column-definition`, `.converter` (a JPA AttributeConverter) and `.type` (a Hibernate user type); the `ExportedEvent<K, V>` parameter types must be the same for every event class in one application. The page's examples use `JsonNode` payloads. **Not confirmed:** that the extension can write a `byte[]` payload into a `bytea` column; the page documents the column definition, converter and type hooks only. Spike S4 or the example tests settle it |
| Camel Kafka component 4.22.x | https://camel.apache.org/components/4.22.x/kafka-component.html | 2026-11-12 | A consumer endpoint takes a comma-separated list of topics; a producer sends to one topic only. `additionalProperties` passes Kafka properties not mapped to options (prefix per the page). `valueDeserializer` is a consumer option. **The topic header is `CamelKafkaTopic` (constant `KafkaConstants.TOPIC`), not `kafka.TOPIC`**; the page's own examples read `header(KafkaConstants.TOPIC)` and `${headers[CamelKafkaTopic]}`. Confirmed, with the header name correcting the plan |
| Quarkus Scheduler reference, "Concurrent Execution" | https://quarkus.io/guides/scheduler-reference | 2026-11-12 | By default a scheduled method can run concurrently with itself; `@Scheduled(concurrentExecution = SKIP)` (`io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP`) skips an overlapping execution and fires a CDI `io.quarkus.scheduler.SkippedExecution` event. Only executions within one application instance are considered; it does not work across a cluster. Confirmed |
| SmallRye Reactive Messaging 4.37.0, "Pausable Channels" | https://smallrye.io/smallrye-reactive-messaging/4.37.0/concepts/pausable-channels/ | 2026-11-12 | Enable with `mp.messaging.incoming.<channel>.pausable=true`. Options: `pausable.initially-paused` (default false), `pausable.late-subscription` (default false), `pausable.buffer-size`, `pausable.buffer-strategy` (BUFFER default, DROP, IGNORE). Control with an injected `@Channel("name") PausableChannel` or `ChannelRegistry.getPausable("name")`, calling `pause()` and `resume()`. The page is for 4.37.0, the version quarkus-bom 3.40.1.2 manages. The Quarkus Kafka guide has no pausable-channel section, so the SmallRye page is the source. Confirmed |
| PostgreSQL 18, "Transaction ID and Snapshot Information Functions" | https://www.postgresql.org/docs/18/functions-info.html | 2026-11-12 | `pg_current_xact_id() -> xid8` (assigns an id if none yet; top-level id inside a subtransaction); `pg_current_xact_id_if_assigned()`; `pg_current_snapshot()` returns a `pg_snapshot` listing in-progress top-level transactions; `pg_snapshot_xmin(pg_snapshot) -> xid8`; `pg_snapshot_xmax`; `pg_snapshot_xip`; `pg_visible_in_snapshot(xid8, pg_snapshot)`. xmin is the lowest transaction id still active: every id below it is committed and visible or rolled back and dead. `xid8` is 64-bit and does not wrap. Confirmed |
| Quarkus Hibernate ORM with Panache, PostgreSQL 18 INSERT and SELECT, Camel exception clause, Scheduler component, SQL component, Split EIP, Camel Quarkus extensions | as the r06 table above | 2026-11-12 (r06) | Fetched the same day in r06 (Panache `PanacheQuery.stream()` re-seen on the page; `ON CONFLICT DO UPDATE ... WHERE` in the INSERT synopsis; `FOR UPDATE` in SELECT). Not re-fetched |
| Apache Camel Choice EIP 4.22.x | https://camel.apache.org/components/4.22.x/eips/choice-eip.html | 2026-11-12 | The first when clause that matches determines the route branch. The otherwise clause runs when none of the when predicates matched. Otherwise is optional; without it a message matching no predicate selects no branch. |
| Apache Kafka 4.3, Managing consumer groups | https://kafka.apache.org/43/operations/basic-kafka-operations/ | 2026-11-12 | kafka-consumer-groups.sh can list, describe or delete groups; --describe --group prints one row per partition with CURRENT-OFFSET, LOG-END-OFFSET and LAG (lag as log end minus current offset is read from the sample output, not defined in the text); --members and --state are further describe options. |
| Apache Kafka 4.3, Topic configs, retention.ms | https://kafka.apache.org/43/configuration/topic-configs/ | 2026-11-12 | retention.ms is the maximum time a log is retained before old segments are discarded under the delete policy; -1 means no time limit; default 604800000 (7 days). retention.bytes default is -1 (no size limit). |

### Books

- Lawrence chapters 5 (CQRS), 6 (Event Sourcing), 8 (Evolution of Messages and Message Streams) and Bellemare chapters 4, 7, 9, 16: titles only, from the TOC rows recorded above (InformIT, 2026-11-10; Bellemare TOC pasted by the owner 2026-11-10). Not re-fetched.
- Kleppmann and Riccomini, *Designing Data-Intensive Applications*, 2nd ed.: book level only; no chapter title is cited.

### r07 facts that did not confirm or that differ from the plan

- **Apicurio violation status**: no 3.3.x docs page states it. The REST v3 OpenAPI document says 400 for a rule violation on adding a version (409 is the conflict response). Treat 400 as expected; S4 observes the live status.
- **Apicurio rule precedence has a group level** in 3.3.x (artifact, group, global). The plan assumed two levels.
- **Camel topic header is `CamelKafkaTopic`**, not `kafka.TOPIC`.
- **Debezium Quarkus outbox with `byte[]` payload**: not confirmed on the extension page (JsonNode only in the examples).
- **Young, Versioning**: body not read; "upcasting" appears only as a TOC sub-heading under process managers. No content claim.
- **Avro spec URL**: the `/docs/current/` page redirects to a placeholder path; cite the 1.12.0 docs URL.
- **Dev Services default Apicurio image is 3.1.7**, not the 3.3.4 pin.

## r08 sources (step 1A, fetched 2026-11-13)

Every page below was fetched by script on 2026-11-13 (HTTP 200) and read as text. Facts are paraphrased; nothing is copied into reader-facing files. oreilly.com was not fetched (it refuses scripts). Kafka docs are cited at the `/42/` path (the Kafka version the platform manages is 4.2.1); the `/documentation/streams/...` redirect pages carry no content for a script.

### Hohpe and Woolf

| Source | URL | Fetched | Facts confirmed that the chapters rely on |
|---|---|---|---|
| Hohpe and Woolf, "Content Enricher" | https://www.enterpriseintegrationpatterns.com/patterns/messaging/DataEnricher.html | 2026-11-13 | Problem: the sender of a message lacks some data the receiving system needs; how can the two systems still communicate. Solution: a specialized transformer that uses data in the incoming message to fetch the missing details from an external source and attach them to the message. (The URL slug is `DataEnricher`; the page title is Content Enricher.) |
| Hohpe and Woolf, "Aggregator" | https://www.enterpriseintegrationpatterns.com/patterns/messaging/Aggregator.html | 2026-11-13 | Problem: combine the results of several related messages so they can be processed as one unit. Solution: a stateful filter that stores incoming related messages until it has a complete set, then publishes one message built from them. Completion strategies named: Wait for All, Time Out, First Best, Time Out with Override, External Event (a business event completes the group). |

### Kafka Streams 4.2 documentation

| Source | URL | Fetched | Facts confirmed that the chapters rely on |
|---|---|---|---|
| Kafka Streams DSL, joins and windowing | https://kafka.apache.org/42/streams/developer-guide/dsl-api/ | 2026-11-13 | Equi-joins need co-partitioned inputs (same key, same partition count); Streams checks the partition counts at runtime and fails the assignment when they differ. KStream-GlobalKTable joins need no co-partitioning because every instance gets all partitions of the global table's changelog. "The GlobalKTable is fully bootstrapped upon (re)start of a KafkaStreams instance ... The actual data processing begins only once the bootstrapping has completed." Stream-stream joins are windowed with `JoinWindows.ofTimeDifferenceWithNoGrace(...)` in the page's examples; left and outer results for unmatched records are emitted after the specified grace period passed, and the deprecated `JoinWindows.of(...).grace(...)` can emit them eagerly. **`JoinWindows.ofTimeDifferenceAndGrace` does not appear on the page** (only `ofTimeDifferenceWithNoGrace` for joins; `SlidingWindows.ofTimeDifferenceAndGrace` for sliding windows); take the join-window grace factory name from the Javadoc or the compiler, not this page. `TimeWindows.ofSizeAndGrace(size, grace)` with `.advanceBy(size)` is a tumbling window; `ofSizeWithNoGrace` is the no-grace form. `suppress(Suppressed.untilWindowCloses(unbounded()))` emits nothing for a window until it closes, then the final result; the page's example is a 1-hour window with 10 minutes grace, accepting records until 10:10 for the 09:00 window. Table-table and stream-table joins without versioned tables do not handle out-of-order records (core concepts page). **`EmitStrategy` is not on this page**; see the upgrade guide row. |
| Kafka Streams core concepts | https://kafka.apache.org/42/streams/core-concepts/ | 2026-11-13 | Event time, processing time, ingestion time defined. Stream time is data-driven: it advances only when a new record arrives at the processor, and its semantics come from the `TimestampExtractor`. A grace period bounds how long a window waits for out-of-order records; a record is discarded when its timestamp belongs to a window and the current stream time is greater than the window end plus the grace period. An aggregation's output is a KTable, so a late-but-accepted record emits an updated aggregate that overwrites the earlier value. Stream-stream joins (inner, left, outer) handle out-of-order records correctly; stream-table and table-table joins on unversioned stores do not. |
| Kafka Streams config reference | https://kafka.apache.org/42/streams/developer-guide/config-streams/ | 2026-11-13 | `group.protocol` default `classic` (`streams` needs broker enablement, labelled Early Access on this page). `processing.guarantee` default `at_least_once`, other value `exactly_once_v2`. `commit.interval.ms` default 30000 (at-least-once) or 100 (exactly-once). `statestore.cache.max.bytes` default 10485760. `default.timestamp.extractor` default `FailOnInvalidTimestamp`. `rocksdb.config.setter` configures the default RocksDB persistent store. |
| Kafka Streams upgrade guide | https://kafka.apache.org/42/streams/upgrade-guide/ | 2026-11-13 | 4.2.0: General Availability of a core feature set of the Streams Rebalance Protocol (KIP-1071); migration from classic to streams groups is discouraged on 4.2.0 (KAFKA-20254, fixed in 4.2.1). 3.3.0 (KIP-825): `suppress()` is in-memory only; `TimeWindowedKStream.emitStrategy(EmitStrategy)` and `SessionWindowedKStream.emitStrategy(EmitStrategy)` choose between emit on window update (default) and emit on window close, using the existing RocksDB store. So `EmitStrategy.onWindowClose()` exists as the alternative to `suppress`; the method name itself is not spelled out on the page (confirm in the Javadoc or the compiler). |
| Kafka Streams testing | https://kafka.apache.org/42/streams/developer-guide/testing/ | 2026-11-13 | `kafka-streams-test-utils` provides `TopologyTestDriver`, which pipes data through a topology without a broker; `TestInputTopic.pipeInput(key, value)` feeds it; event-time punctuations fire from record timestamps and wall-clock punctuations by `testDriver.advanceWallClockTime(Duration)`. |
| Kafka Streams Javadoc, TimeWindows | https://kafka.apache.org/42/javadoc/org/apache/kafka/streams/kstream/TimeWindows.html | 2026-11-13 | Class description: "The fixed-size time-based window specifications used for aggregations." `ofSizeAndGrace(Duration size, Duration afterWindowEnd)` and `ofSizeWithNoGrace(Duration size)` are both listed, each summarised as "Return a window definition with the given window size, and with the advance interval being equal to the window size." `advanceBy(Duration advance)` is listed: "Return a window definition with the original size, but advance (\"hop\") the window by the given interval". Cited in chapter 21 for `TimeWindows.ofSizeAndGrace`; `Suppressed.untilWindowCloses` has its own Javadoc page, not fetched. |
| Kafka monitoring, Kafka Streams metrics | https://kafka.apache.org/42/operations/monitoring/ | 2026-11-13 | Task-level metrics group `kafka.streams:type=stream-task-metrics`: `dropped-records-rate` and `dropped-records-total` ("total number of records dropped within this task"), both at recording level info (everything else in that table is debug); also `record-lateness-avg` and `record-lateness-max`. |

### Quarkus

| Source | URL | Fetched | Facts confirmed that the chapters rely on |
|---|---|---|---|
| Quarkus, "Using Apache Kafka Streams" (3.40 docs) | https://quarkus.io/guides/kafka-streams | 2026-11-13 | With `quarkus-smallrye-health`, `quarkus-kafka-streams` adds a readiness check that every topic in `quarkus.kafka-streams.topics` exists and a liveness check on the `KafkaStreams` state. `quarkus.kafka-streams.topics`: the application waits for all listed topics before launching the engine. "All the properties within the kafka-streams namespace are passed through as-is to the Kafka Streams engine"; `bootstrap-servers` can fall back to `kafka.bootstrap.servers`. Interactive queries through the `KafkaStreams` bean. The guide documents no switch to disable the pipeline, no TopologyTestDriver approach and no Streams metrics binding. |
| Quarkus, "Micrometer Metrics" (telemetry-micrometer) | https://quarkus.io/guides/telemetry-micrometer | 2026-11-13 | `quarkus.micrometer.binder.kafka.enabled` binds Kafka metrics "if the Kafka Consumer or Producer interface is on the classpath". Kafka Streams is not mentioned on the page (spike S1 settles the Streams metrics). |
| Quarkus, "Dev Services for Kafka" | https://quarkus.io/guides/kafka-dev-services | 2026-11-13 | `quarkus.kafka.devservices.topic-partitions.<topic>=<n>` creates topics with that many partitions and one replica; `quarkus.kafka.devservices.topic-partitions-timeout` defaults to 2 seconds; the default provider is the upstream Apache Kafka native image, and `quarkus.kafka.devservices.image-name` overrides it. |
| Quarkus, "Apache Kafka reference guide", testing section | https://quarkus.io/guides/kafka | 2026-11-13 | `quarkus-test-kafka-companion` (test scope) provides `KafkaCompanionResource` for `@QuarkusTestResource` and `@InjectKafkaCompanion` for the `KafkaCompanion` API, the companion for producing and consuming in tests. |

### Apache Camel and Camel Quarkus

| Source | URL | Fetched | Facts confirmed that the chapters rely on |
|---|---|---|---|
| Camel 4.22.x, Enrich EIP | https://camel.apache.org/components/4.22.x/eips/enrich-eip.html | 2026-11-13 | `enrich` calls a resource endpoint with a producer; `pollEnrich` uses a polling consumer (see the pollEnrich page). The URI is computed from an expression, including dynamic URIs built from exchange data such as `http:myserver/${header.orderId}/order`. Without an AggregationStrategy, enrich with a constant URI is the same as a plain `to`. `aggregateOnException` defaults to false: when retrieving the resource throws, the strategy is not called. Options `cacheSize`, `ignoreInvalidEndpoint`, `shareUnitOfWork`. |
| Camel 4.22.x, Aggregate EIP | https://camel.apache.org/components/4.22.x/eips/aggregate-eip.html | 2026-11-13 | Completion options `completionSize`, `completionPredicate`, `completionTimeout` ("time that an aggregated exchange should be inactive before its complete"; cannot be used with `completionInterval`), `completionInterval`, `eagerCheckCompletion`, `discardOnCompletionTimeout`, `forceCompletionOnStop`. `completionTimeoutCheckerInterval` default 1000 ms. All completions are per correlation key and "the first that triggers wins". Exchange property `CamelAggregatedCompletedBy` is an enum that says how a group completed; its values are not listed on the page (S3 records them). |
| Camel 4.22.x, SQL component, aggregation repository | https://camel.apache.org/components/4.22.x/sql-component.html | 2026-11-13 | `JdbcAggregationRepository` persists aggregated messages so they are not lost on restart (the default repository is in memory). Two tables per aggregator: the aggregation table and the same name suffixed `_COMPLETED`. Exchanges are stored serialized into a binary column by `JdbcCodec`. A Spring `PlatformTransactionManager` is required. `maximumRedeliveries` with `deadLetterUri` limit recovery redelivery. `optimisticLocking` supports clustered use. PostgreSQL marks a connection invalid after a 23505 integrity violation, so `PostgresAggregationRepository extends JdbcAggregationRepository` and uses `INSERT .. ON CONFLICT ..` for the optimistic locking. |
| Camel Quarkus 3.40.x, SQL extension | https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/sql.html | 2026-11-13 | Coordinates `camel-quarkus-sql`; the extension uses Quarkus Agroal for the `DataSource`, configured with `quarkus.datasource.*`. The page says nothing about a transaction manager for the aggregation repository (S3 settles the wiring). |
| Camel Quarkus 3.40.x, HTTP extension | https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/http.html | 2026-11-13 | Coordinates `camel-quarkus-http`; JVM and native since 1.0.0; SSL is auto-enabled in native mode. |
| Camel 4.22.x, Kafka component | https://camel.apache.org/components/4.22.x/kafka-component.html | 2026-11-13 | `breakOnFirstError` (consumer): instead of polling the next message after an exception, Camel commits the offset so that the message that caused the exception is retried; compare the RETRY polling strategy. The record key header is `CamelKafkaKey` (`KafkaConstants.KEY`); a `key` endpoint option takes precedence over the header. |

### Books and sibling links

| Source | URL | Fetched | Facts confirmed |
|---|---|---|---|
| Lawrence ch 9, "Composing Data from Different Sources" | https://www.informit.com/store/message-and-event-driven-systems-with-cqrs-and-event-9780137998616 | not re-fetched | Title only, from the InformIT TOC row recorded in r01 (this file, Lawrence mapping table). |
| Bellemare ch 7 "Denormalization and Eventification", ch 8 "Stateful Event-Driven Microservices", ch 9 "Deterministic Stream Processing" | https://www.oreilly.com/library/view/building-event-driven-microservices/9798341622180/ (not fetched; O'Reilly refuses scripts) | not fetched | Titles only, from the owner-confirmed table of contents (2026-11-10), recorded above. Cited by title for the topic the title names. |
| EIP Camel ch 12 "Transformation Fundamentals" (`## Pattern: Content Enricher`) and ch 13 "Structural Transformation" (`## Pattern: Aggregator`) | https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/12-transformation-fundamentals/ ; .../13-structural-transformation/ | 2026-11-13 | HTTP 200 on both; titles and pattern headings match the sibling repository's chapter files in the local clone. |
| CNDP ch 6 "Stream Processing" | https://patterncatalyst.github.io/cloud-native-design-patterns/docs/06-stream-processing/ | 2026-11-13 | HTTP 200; its Kafka Streams code uses `TimeWindows.ofSizeWithNoGrace` (tumbling windows, no grace). |

### r08 facts that did not confirm or that differ from the plan

- **`JoinWindows.ofTimeDifferenceAndGrace`**: not on the 4.2 DSL page. The page uses `ofTimeDifferenceWithNoGrace` for joins; `ofTimeDifferenceAndGrace` appears there only for `SlidingWindows`. Confirm the join-window grace factory in the compiler before code or prose names it.
- **`EmitStrategy.onWindowClose()`**: the name is not on any fetched page. The upgrade guide (3.3.0, KIP-825) confirms `emitStrategy(EmitStrategy)` on time and session windowed streams with "emit on window close", and that `suppress()` is in-memory only.
- **Records after grace are dropped**: confirmed on the core concepts page (discarded, not processed in that window); the DSL page does not say it. The dropped-records metric is `dropped-records-total` in `stream-task-metrics`, recording level info.
- **KIP-1071 status**: the config page still says Early Access while the 4.2 upgrade guide says General Availability of a core feature set. Neither is cited in prose; the book keeps `classic`.
- **Quarkus Kafka Streams metrics**: the Micrometer and Kafka Streams guides say nothing about binding Streams metrics.
- **Camel completion values**: the Aggregate page does not list the values of `CamelAggregatedCompletedBy`.
- **Camel Quarkus SQL**: no transaction-manager guidance on the extension page.

### r08 repair 1 (evidence gaps), fetched 2026-11-13

| Source | URL | Fetched | Facts confirmed |
|---|---|---|---|
| Kafka topic configs | https://kafka.apache.org/42/configuration/topic-configs/ | 2026-11-13 | `message.timestamp.type` default `CreateTime` (the alternative is `LogAppendTime`); `message.timestamp.after.max.ms` default 3600000 (1 hour): under CreateTime a message whose timestamp differs from the broker time by more than that is rejected (server default property `log.message.timestamp.after.max.ms`). |
| Kafka Streams config reference (re-read) | https://kafka.apache.org/42/streams/developer-guide/config-streams/ | 2026-11-13 | `statestore.cache.max.bytes`: "Maximum number of memory bytes to be used for record caches across all threads", default 10485760. |
| Kafka Streams config reference, `windowstore.changelog.additional.retention.ms` | https://kafka.apache.org/42/streams/developer-guide/config-streams/ | 2026-11-13 | Importance Low. Description: "Added to a windows maintainMs to ensure data is not deleted from the log prematurely. Allows for clock drift." Default 86400000 (1 day). Backs chapter 21's changelog retention of a window store plus one day. |
| Kafka Streams Javadoc, Suppressed | https://kafka.apache.org/42/javadoc/org/apache/kafka/streams/kstream/Suppressed.html | 2026-11-13 | `Suppressed.untilWindowCloses(Suppressed.StrictBufferConfig)`: "Configure the suppression to emit only the \"final results\" from the window." `StrictBufferConfig` is a buffer configuration that strictly enforces the time bound and never emits early. The page says nothing about a changelog topic for the buffer (the changelog is shown by a run, `<scratchpad>/g-suppress21.log`). |
| Camel 4.22.x, Kafka component (re-read) | https://camel.apache.org/components/4.22.x/kafka-component.html | 2026-11-13 | `breakOnFirstError` option: with `false` the consumer continues to the next message; with `true` it breaks out. With the default `NoopCommitManager` the offset is not committed "so that the message is re-attempted"; with the Sync or Async commit manager the consumer "will seek back to the offset of the message that caused a failure, and then re-attempt". The longer section says Camel "will instead commit the offset so that the message that caused the exception will be retried". The book states only that the failed record is retried and the offset stays on it. |

Runs recorded for the same repair: `<scratchpad>/g-future20.log` (a payload event time 3 hours ahead of the clock: `InvalidTimestampException ... out of range`, Kafka Streams state ERROR, liveness 503 on the chapter 20 stack), `<scratchpad>/g-suppress21.log` (`kafka-topics --list` after `verify.sh` 21: `shipping-pickups-KTABLE-SUPPRESS-STATE-STORE-0000000007-changelog`, `shipping-pickups-by-carrier-repartition`, `shipping-pickups-pickup-lists-changelog`), `<scratchpad>/w20-copart1.log` to `w20-copart3.log` (`COPART streams=14 camel=30` in each of three runs, state RUNNING), `<scratchpad>/r08-spikes/logs/s3.log` section S3.9 (`Restored 2 CompletionTimeout conditions`; the `PaymentOverdue` of a 45-second timeout arrived 44.5 s after the recreated service was ready).
