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
- Status for every Bellemare chapter reference: **owner to verify** in the owner's O'Reilly account. The PRD gives topics only, and no chapter numbers are asserted here.

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

## r02 topics, Bellemare (cited by topic only, no chapter numbers)

| Topic | Status |
|---|---|
| (a) events, event notification vs state | owner to verify (search returned no section titles) |
| (b) data contracts, event-carried state | owner to verify (search returned no section titles) |
| (c) partitioning and ordering | owner to verify (search returned no section titles) |
| (d) large events, claim check | owner to verify (search returned no section titles) |

## PRD section 5 mapping, Bellemare (topic as given in the PRD)

| PRD chapter | Topic | Status |
|---|---|---|
| 02 | events | owner to verify |
| 03 | data contracts | owner to verify |
| 04 | partitioning | owner to verify |
| 05 | producers | owner to verify |
| 06 | consumers | owner to verify |
| 08 | CDC/outbox | owner to verify |
| 09 | CDC | owner to verify |
| 11 | delivery guarantees | owner to verify |
| 12 | EOS | owner to verify |
| 13 | error handling | owner to verify |
| 14 | event sourcing | owner to verify |
| 16 | materialized views | owner to verify |
| 17 | reprocessing | owner to verify |
| 18 | schemas | owner to verify |
| 19 | stateful streaming | owner to verify |
| 20 | joins | owner to verify |
| 21 | windowing | owner to verify |
| 22, 23 | workflows | owner to verify |

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
