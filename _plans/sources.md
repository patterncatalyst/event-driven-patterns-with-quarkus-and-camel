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
