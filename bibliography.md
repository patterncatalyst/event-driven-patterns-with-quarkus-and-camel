---
title: "Bibliography"
layout: plan
permalink: /bibliography/
description: "The books, articles and documentation this site's coverage and rigor draw on: nothing here is reproduced from them."
---

This site teaches event-driven patterns on Quarkus and Apache Camel with Kafka, and
then runs them on Kubernetes with Strimzi. The books and articles below shaped what
the chapters cover and how rigorously they treat each pattern; the project and
runtime documentation is the authority for every API the examples use. These are
reference works, not sources: no text or code from any of them is reproduced here.
Lawrence's code is Node.js and TypeScript, and none of it is ported.

## Books

| Title | Author(s) | Publisher | Year | ISBN | Focus |
|---|---|---|---|---|---|
| [*Message- and Event-Driven Systems with CQRS and Event Sourcing: Web and Cloud Architecture*](https://www.informit.com/store/message-and-event-driven-systems-with-cqrs-and-event-9780137998616) | Alex Lawrence | Addison-Wesley | 2026 | 978-0-13-799861-6 | Message anatomy, CQRS, event sourcing, long-running processes, evolution of messages; Vernon Signature Series |
| [*Building Event-Driven Microservices, Second Edition*](https://www.oreilly.com/library/view/building-event-driven-microservices/9798341622180/) | Adam Bellemare | O'Reilly | 2025 | 979-8-3416-2218-0 | Events and event streams, event design, schemas and data contracts, integrating existing systems, denormalization, stateful and deterministic stream processing, workflows, producer and consumer microservices, eventual consistency, bad data, testing, deployment |
| [*Enterprise Integration Patterns*](https://www.enterpriseintegrationpatterns.com/) | Gregor Hohpe & Bobby Woolf | Addison-Wesley | 2003 | 978-0-321-20068-6 | Idempotent Receiver, Content Enricher, Aggregator, Dead Letter Channel, Process Manager, Resequencer, Claim Check, Event Message, Command Message, Document Message |

## Articles and pattern references

| Title | Author | Link | Used for |
|---|---|---|---|
| Event Sourcing | Martin Fowler | [martinfowler.com/eaaDev/EventSourcing.html](https://martinfowler.com/eaaDev/EventSourcing.html) | The foundations of event sourcing |
| What do you mean by "Event-Driven"? | Martin Fowler | [martinfowler.com/articles/201701-event-driven.html](https://martinfowler.com/articles/201701-event-driven.html) | Names for event notification, event-carried state transfer and event sourcing |
| Event Sourcing pattern | Microsoft Azure Architecture Center | [learn.microsoft.com/azure/architecture/patterns/event-sourcing](https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing) | Structural guidance on event sourcing, as a pattern reference only |
| Claim Check | Gregor Hohpe, Bobby Woolf | [enterpriseintegrationpatterns.com/patterns/messaging/StoreInLibrary.html](https://www.enterpriseintegrationpatterns.com/patterns/messaging/StoreInLibrary.html) | Passing a reference instead of a large payload |
| Resequencer | Gregor Hohpe, Bobby Woolf | [enterpriseintegrationpatterns.com/patterns/messaging/Resequencer.html](https://www.enterpriseintegrationpatterns.com/patterns/messaging/Resequencer.html) | Restoring order to out-of-sequence messages |
| Competing Consumers | Gregor Hohpe, Bobby Woolf | [enterpriseintegrationpatterns.com/patterns/messaging/CompetingConsumers.html](https://www.enterpriseintegrationpatterns.com/patterns/messaging/CompetingConsumers.html) | Several consumers sharing one channel, as in a share group |
| Polling Consumer | Gregor Hohpe, Bobby Woolf | [enterpriseintegrationpatterns.com/patterns/messaging/PollingConsumer.html](https://www.enterpriseintegrationpatterns.com/patterns/messaging/PollingConsumer.html) | A consumer that fetches when it is ready, as the Kafka poll loop does |

## The Strimzi blog

The Kubernetes part follows the Strimzi project's blog. Each post below was opened
and its title and date checked before any chapter cites it.

| Topic | Post |
|---|---|
| Exactly-once | [Exactly-once semantics with Kafka transactions](https://strimzi.io/blog/2023/05/03/kafka-transactions/) |
| Producers and consumers | [Optimizing Kafka producers](https://strimzi.io/blog/2020/10/15/producer-tuning/), [Optimizing Kafka consumers](https://strimzi.io/blog/2021/01/07/consumer-tuning/), [Developing Kafka client applications: A simple producer](https://strimzi.io/blog/2023/10/03/kafka-producer-client-essentials/), [Developing Kafka client applications: A simple consumer](https://strimzi.io/blog/2023/11/09/kafka-consumer-client-essentials/), [Optimizing Kafka broker configuration](https://strimzi.io/blog/2021/06/08/broker-tuning/) |
| Queues | [Using Queues for Apache Kafka with Strimzi](https://strimzi.io/blog/2025/08/20/queues-for-kafka/) |
| Connector offsets | [Managing Connector Offsets](https://strimzi.io/blog/2024/11/04/managing-connector-offsets/), [Auto-restarting connectors in Apache Kafka Connect and Mirror Maker 2](https://strimzi.io/blog/2023/01/25/auto-restarting-connectors/) |
| KRaft and node pools | [Kafka Node Pools: Introduction](https://strimzi.io/blog/2023/08/14/kafka-node-pools-introduction/), [Kafka Node Pools: Supporting KRaft](https://strimzi.io/blog/2023/09/11/kafka-node-pools-supporting-kraft/), [Kafka Node Pools: Storage & Scheduling](https://strimzi.io/blog/2023/08/28/kafka-node-pools-storage-and-scheduling/), [From ZooKeeper to KRaft: How the Kafka migration works](https://strimzi.io/blog/2024/03/21/kraft-migration/) |
| Topics as code | [Introducing the Unidirectional Topic Operator](https://strimzi.io/blog/2023/11/02/unidirectional-topic-operator/) |
| Server-side apply | [Server-Side Apply in Strimzi](https://strimzi.io/blog/2026/01/26/server-side-apply-in-strimzi/) |
| Security context | [Configuring security context of Strimzi-managed pods](https://strimzi.io/blog/2022/09/09/configuring-security-context-in-pods-managed-by-strimzi/), [Writing custom Pod Security Providers](https://strimzi.io/blog/2023/04/05/writing-custom-pod-security-providers/) |
| Monitoring and metrics | [Using Strimzi Metrics Reporter to expose Kafka metrics in Prometheus format](https://strimzi.io/blog/2025/10/06/strimzi-metrics-reporter/), [Monitoring of custom resources](https://strimzi.io/blog/2025/10/01/monitoring-of-custom-resources/), [Tracing in Apache Kafka: from OpenTracing to OpenTelemetry](https://strimzi.io/blog/2023/03/01/opentelemetry/) |
| Cruise Control | [Auto-rebalancing on cluster scaling](https://strimzi.io/blog/2024/11/25/autorebalancing-on-scaling/), [Moving data between JBOD disks using Cruise Control](https://strimzi.io/blog/2025/02/13/moving-data-between-JBOD-disks-using-cruise-control/), [Video: Make your Kafka cluster production-ready - Cluster Balancing](https://strimzi.io/blog/2023/01/19/make-your-kafka-cluster-production-ready-cluster-balancing/) |
| MirrorMaker 2 | [Migrating Kafka clusters with MirrorMaker2 and Strimzi](https://strimzi.io/blog/2021/11/22/migrating-kafka-with-mirror-maker2/) |

## Primary documentation

| Source | Link | Used for |
|---|---|---|
| Apache Kafka | [kafka.apache.org/documentation](https://kafka.apache.org/documentation/) | The broker, clients, Kafka Streams and transactions |
| KIP-848: The Next Generation of the Consumer Rebalance Protocol | [cwiki.apache.org](https://cwiki.apache.org/confluence/display/KAFKA/KIP-848%3A+The+Next+Generation+of+the+Consumer+Rebalance+Protocol) | The consumer group protocol |
| KIP-1030: Change constraints and default values for various configurations | [cwiki.apache.org](https://cwiki.apache.org/confluence/display/KAFKA/KIP-1030%3A+Change+constraints+and+default+values+for+various+configurations) | Producer defaults such as linger.ms |
| KIP-932: Queues for Kafka | [cwiki.apache.org](https://cwiki.apache.org/confluence/display/KAFKA/KIP-932%3A+Queues+for+Kafka) | Share groups |
| KIP-875: First-class offsets support in Kafka Connect | [cwiki.apache.org](https://cwiki.apache.org/confluence/display/KAFKA/KIP-875%3A+First-class+offsets+support+in+Kafka+Connect) | Connector offsets |
| Strimzi | [strimzi.io/docs](https://strimzi.io/documentation/) | Kafka on Kubernetes: KRaft, topics, Connect, Cruise Control, MirrorMaker 2 |
| Debezium | [debezium.io/documentation](https://debezium.io/documentation/) | Log-based change data capture and the outbox event router |
| Apicurio Registry | [apicur.io/registry](https://www.apicur.io/registry/docs/) | Schema registry and compatibility rules |
| Quarkus | [quarkus.io/guides](https://quarkus.io/guides/) | The Quarkus runtime, Dev Services, messaging and Kafka Streams |
| Apache Camel | [camel.apache.org/docs](https://camel.apache.org/docs/) | The route DSL, components and the Saga EIP |
| Camel Quarkus | [camel.apache.org/camel-quarkus](https://camel.apache.org/camel-quarkus/latest/) | The Quarkus runtime for Camel |
| Grafana LGTM | [github.com/grafana/docker-otel-lgtm](https://github.com/grafana/docker-otel-lgtm) | The all-in-one Loki, Grafana, Tempo and Mimir image the examples send telemetry to |
| CloudNativePG | [cloudnative-pg.io/documentation](https://cloudnative-pg.io/documentation/) | The PostgreSQL operator and operand image |
| SDKMAN | [sdkman.io](https://sdkman.io/) | Installing JDK and Maven |

## Related projects

These are our own companion projects, reused and adapted for layout, infrastructure and
precedent.

| Project | Link | Relationship |
|---|---|---|
| Enterprise Integration Patterns with Camel | [github.com/patterncatalyst/enterprise-integration-patterns-with-camel](https://github.com/patterncatalyst/enterprise-integration-patterns-with-camel) | The model for this site; the broader pattern catalogue |
| Cloud-Native Design Patterns | [github.com/patterncatalyst/cloud-native-design-patterns](https://github.com/patterncatalyst/cloud-native-design-patterns) | Sibling book on cloud-native patterns |
| Data Mesh Reference Architecture on Quarkus | [github.com/patterncatalyst/datamesh-reference-arch-quarkus](https://github.com/patterncatalyst/datamesh-reference-arch-quarkus) | Orchestration styles, rules and AI classification, PostgreSQL operator manifests |
| Quarkus Observability | [github.com/patterncatalyst/quarkus-observability](https://github.com/patterncatalyst/quarkus-observability) | Observability precedent |
| OpenTelemetry Observability Tutorial | [github.com/patterncatalyst/otel-observability-tutorial](https://github.com/patterncatalyst/otel-observability-tutorial) | OpenTelemetry wiring and Grafana dashboards |
| Domain-Driven Design Observability Workshop | [github.com/patterncatalyst/domain-driven-design-observability-workshop](https://github.com/patterncatalyst/domain-driven-design-observability-workshop) | Observability of domain events |

These titles inform this site's coverage and rigor only. Nothing in these chapters
or the runnable examples copies text or code from them.
