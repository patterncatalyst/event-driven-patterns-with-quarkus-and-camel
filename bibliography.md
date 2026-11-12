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

<table class="bib bib--books">
  <colgroup><col style="width:62%"><col style="width:24%"><col style="width:14%"></colgroup>
  <thead><tr><th>Title</th><th>Author(s)</th><th>Year</th></tr></thead>
  <tbody>
    <tr><td><a href="https://www.informit.com/store/message-and-event-driven-systems-with-cqrs-and-event-9780137998616" rel="noopener"><em>Message- and Event-Driven Systems with CQRS and Event Sourcing: Web and Cloud Architecture</em></a><br><small>Addison-Wesley, ISBN 978-0-13-799861-6. Message anatomy, CQRS, event sourcing, long-running processes, evolution of messages; Vernon Signature Series</small></td><td>Alex Lawrence</td><td>2026</td></tr>
    <tr><td><a href="https://www.oreilly.com/library/view/building-event-driven-microservices/9798341622180/" rel="noopener"><em>Building Event-Driven Microservices, Second Edition</em></a><br><small>O'Reilly, ISBN 979-8-3416-2218-0. Events and event streams, event design, schemas and data contracts, integrating existing systems, denormalization, stateful and deterministic stream processing, workflows, producer and consumer microservices, eventual consistency, bad data, testing, deployment</small></td><td>Adam Bellemare</td><td>2025</td></tr>
    <tr><td><a href="https://www.enterpriseintegrationpatterns.com/" rel="noopener"><em>Enterprise Integration Patterns</em></a><br><small>Addison-Wesley, ISBN 978-0-321-20068-6. Idempotent Receiver, Content Enricher, Aggregator, Dead Letter Channel, Process Manager, Resequencer, Claim Check, Event Message, Command Message, Document Message</small></td><td>Gregor Hohpe, Bobby Woolf</td><td>2003</td></tr>
  </tbody>
</table>

## Articles and pattern references

<table class="bib">
  <colgroup><col style="width:36%"><col style="width:24%"><col style="width:40%"></colgroup>
  <thead><tr><th>Title</th><th>Author</th><th>Used for</th></tr></thead>
  <tbody>
    <tr><td><a href="https://martinfowler.com/eaaDev/EventSourcing.html" rel="noopener">Event Sourcing</a></td><td>Martin Fowler</td><td>The foundations of event sourcing</td></tr>
    <tr><td><a href="https://martinfowler.com/articles/201701-event-driven.html" rel="noopener">What do you mean by "Event-Driven"?</a></td><td>Martin Fowler</td><td>Names for event notification, event-carried state transfer and event sourcing</td></tr>
    <tr><td><a href="https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing" rel="noopener">Event Sourcing pattern</a></td><td>Microsoft Azure Architecture Center</td><td>Structural guidance on event sourcing, as a pattern reference only</td></tr>
    <tr><td><a href="https://www.enterpriseintegrationpatterns.com/patterns/messaging/StoreInLibrary.html" rel="noopener">Claim Check</a></td><td>Gregor Hohpe, Bobby Woolf</td><td>Passing a reference instead of a large payload</td></tr>
    <tr><td><a href="https://www.enterpriseintegrationpatterns.com/patterns/messaging/Resequencer.html" rel="noopener">Resequencer</a></td><td>Gregor Hohpe, Bobby Woolf</td><td>Restoring order to out-of-sequence messages</td></tr>
    <tr><td><a href="https://www.enterpriseintegrationpatterns.com/patterns/messaging/CompetingConsumers.html" rel="noopener">Competing Consumers</a></td><td>Gregor Hohpe, Bobby Woolf</td><td>Several consumers sharing one channel, as in a share group</td></tr>
    <tr><td><a href="https://www.enterpriseintegrationpatterns.com/patterns/messaging/PollingConsumer.html" rel="noopener">Polling Consumer</a></td><td>Gregor Hohpe, Bobby Woolf</td><td>A consumer that fetches when it is ready, as the Kafka poll loop does</td></tr>
    <tr><td><a href="https://microservices.io/patterns/data/transactional-outbox.html" rel="noopener">Pattern: Transactional outbox</a></td><td>Chris Richardson</td><td>Writing the message in the same transaction as the business change, and the message relay</td></tr>
    <tr><td><a href="https://microservices.io/patterns/data/transaction-log-tailing.html" rel="noopener">Pattern: Transaction log tailing</a></td><td>Chris Richardson</td><td>Publishing outbox rows by reading the database transaction log</td></tr>
    <tr><td><a href="https://www.enterpriseintegrationpatterns.com/patterns/messaging/IdempotentReceiver.html" rel="noopener">Idempotent Receiver</a></td><td>Gregor Hohpe, Bobby Woolf</td><td>A receiver that can safely receive the same message more than once</td></tr>
    <tr><td><a href="https://www.enterpriseintegrationpatterns.com/patterns/messaging/TransactionalClient.html" rel="noopener">Transactional Client</a></td><td>Gregor Hohpe, Bobby Woolf</td><td>Making the client's session with the messaging system transactional</td></tr>
    <tr><td><a href="https://www.enterpriseintegrationpatterns.com/patterns/messaging/DeadLetterChannel.html" rel="noopener">Dead Letter Channel</a></td><td>Gregor Hohpe, Bobby Woolf</td><td>Where a message goes when it cannot be delivered or processed</td></tr>
    <tr><td><a href="https://www.enterpriseintegrationpatterns.com/patterns/messaging/InvalidMessageChannel.html" rel="noopener">Invalid Message Channel</a></td><td>Gregor Hohpe, Bobby Woolf</td><td>Separating messages that make no sense from messages that failed</td></tr>
    <tr><td><a href="https://microservices.io/patterns/communication-style/idempotent-consumer.html" rel="noopener">Pattern: Idempotent Consumer</a></td><td>Chris Richardson</td><td>Recording processed message ids in the database, in the handler's transaction</td></tr>
  </tbody>
</table>

## The Strimzi blog

The Kubernetes part follows the Strimzi project's blog. Each post below was opened
and its title and date checked before any chapter cites it.

<table class="bib">
  <colgroup><col style="width:26%"><col style="width:74%"></colgroup>
  <thead><tr><th>Topic</th><th>Post</th></tr></thead>
  <tbody>
    <tr><td>Exactly-once</td><td><a href="https://strimzi.io/blog/2023/05/03/kafka-transactions/" rel="noopener">Exactly-once semantics with Kafka transactions</a></td></tr>
    <tr><td>Producers and consumers</td><td><a href="https://strimzi.io/blog/2020/10/15/producer-tuning/" rel="noopener">Optimizing Kafka producers</a> (Paul Mellor), <a href="https://strimzi.io/blog/2021/01/07/consumer-tuning/" rel="noopener">Optimizing Kafka consumers</a> (Paul Mellor), <a href="https://strimzi.io/blog/2023/10/03/kafka-producer-client-essentials/" rel="noopener">Developing Kafka client applications: A simple producer</a>, <a href="https://strimzi.io/blog/2023/11/09/kafka-consumer-client-essentials/" rel="noopener">Developing Kafka client applications: A simple consumer</a>, <a href="https://strimzi.io/blog/2021/06/08/broker-tuning/" rel="noopener">Optimizing Kafka broker configuration</a></td></tr>
    <tr><td>Queues</td><td><a href="https://strimzi.io/blog/2025/08/20/queues-for-kafka/" rel="noopener">Using Queues for Apache Kafka with Strimzi</a></td></tr>
    <tr><td>Connector offsets</td><td><a href="https://strimzi.io/blog/2024/11/04/managing-connector-offsets/" rel="noopener">Managing Connector Offsets</a> (Kate Stanley), <a href="https://strimzi.io/blog/2023/01/25/auto-restarting-connectors/" rel="noopener">Auto-restarting connectors in Apache Kafka Connect and Mirror Maker 2</a></td></tr>
    <tr><td>KRaft and node pools</td><td><a href="https://strimzi.io/blog/2023/08/14/kafka-node-pools-introduction/" rel="noopener">Kafka Node Pools: Introduction</a>, <a href="https://strimzi.io/blog/2023/09/11/kafka-node-pools-supporting-kraft/" rel="noopener">Kafka Node Pools: Supporting KRaft</a>, <a href="https://strimzi.io/blog/2023/08/28/kafka-node-pools-storage-and-scheduling/" rel="noopener">Kafka Node Pools: Storage &amp; Scheduling</a>, <a href="https://strimzi.io/blog/2024/03/21/kraft-migration/" rel="noopener">From ZooKeeper to KRaft: How the Kafka migration works</a></td></tr>
    <tr><td>Topics as code</td><td><a href="https://strimzi.io/blog/2023/11/02/unidirectional-topic-operator/" rel="noopener">Introducing the Unidirectional Topic Operator</a></td></tr>
    <tr><td>Server-side apply</td><td><a href="https://strimzi.io/blog/2026/01/26/server-side-apply-in-strimzi/" rel="noopener">Server-Side Apply in Strimzi</a></td></tr>
    <tr><td>Security context</td><td><a href="https://strimzi.io/blog/2022/09/09/configuring-security-context-in-pods-managed-by-strimzi/" rel="noopener">Configuring security context of Strimzi-managed pods</a>, <a href="https://strimzi.io/blog/2023/04/05/writing-custom-pod-security-providers/" rel="noopener">Writing custom Pod Security Providers</a></td></tr>
    <tr><td>Monitoring and metrics</td><td><a href="https://strimzi.io/blog/2025/10/06/strimzi-metrics-reporter/" rel="noopener">Using Strimzi Metrics Reporter to expose Kafka metrics in Prometheus format</a>, <a href="https://strimzi.io/blog/2025/10/01/monitoring-of-custom-resources/" rel="noopener">Monitoring of custom resources</a>, <a href="https://strimzi.io/blog/2023/03/01/opentelemetry/" rel="noopener">Tracing in Apache Kafka: from OpenTracing to OpenTelemetry</a></td></tr>
    <tr><td>Cruise Control</td><td><a href="https://strimzi.io/blog/2024/11/25/autorebalancing-on-scaling/" rel="noopener">Auto-rebalancing on cluster scaling</a>, <a href="https://strimzi.io/blog/2025/02/13/moving-data-between-JBOD-disks-using-cruise-control/" rel="noopener">Moving data between JBOD disks using Cruise Control</a>, <a href="https://strimzi.io/blog/2023/01/19/make-your-kafka-cluster-production-ready-cluster-balancing/" rel="noopener">Video: Make your Kafka cluster production-ready - Cluster Balancing</a></td></tr>
    <tr><td>MirrorMaker 2</td><td><a href="https://strimzi.io/blog/2021/11/22/migrating-kafka-with-mirror-maker2/" rel="noopener">Migrating Kafka clusters with MirrorMaker2 and Strimzi</a></td></tr>
  </tbody>
</table>

## Primary documentation

<table class="bib">
  <colgroup><col style="width:45%"><col style="width:55%"></colgroup>
  <thead><tr><th>Source</th><th>Used for</th></tr></thead>
  <tbody>
    <tr><td><a href="https://kafka.apache.org/documentation/" rel="noopener">Apache Kafka</a></td><td>The broker, clients, Kafka Streams and transactions</td></tr>
    <tr><td><a href="https://cwiki.apache.org/confluence/display/KAFKA/KIP-848%3A+The+Next+Generation+of+the+Consumer+Rebalance+Protocol" rel="noopener">KIP-848: The Next Generation of the Consumer Rebalance Protocol</a></td><td>The consumer group protocol</td></tr>
    <tr><td><a href="https://cwiki.apache.org/confluence/display/KAFKA/KIP-1030%3A+Change+constraints+and+default+values+for+various+configurations" rel="noopener">KIP-1030: Change constraints and default values for various configurations</a></td><td>Producer defaults such as linger.ms</td></tr>
    <tr><td><a href="https://cwiki.apache.org/confluence/display/KAFKA/KIP-932%3A+Queues+for+Kafka" rel="noopener">KIP-932: Queues for Kafka</a></td><td>Share groups</td></tr>
    <tr><td><a href="https://cwiki.apache.org/confluence/display/KAFKA/KIP-875%3A+First-class+offsets+support+in+Kafka+Connect" rel="noopener">KIP-875: First-class offsets support in Kafka Connect</a></td><td>Connector offsets</td></tr>
    <tr><td><a href="https://strimzi.io/documentation/" rel="noopener">Strimzi</a></td><td>Kafka on Kubernetes: KRaft, topics, Connect, Cruise Control, MirrorMaker 2</td></tr>
    <tr><td><a href="https://debezium.io/documentation/" rel="noopener">Debezium</a></td><td>Log-based change data capture and the outbox event router</td></tr>
    <tr><td><a href="https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html" rel="noopener">Debezium Outbox Event Router</a></td><td>Routing outbox rows to topics, the event id header and payload expansion</td></tr>
    <tr><td><a href="https://debezium.io/documentation/reference/stable/integrations/outbox.html" rel="noopener">Debezium Outbox Quarkus Extension</a></td><td>Writing the outbox row from a Quarkus transaction</td></tr>
    <tr><td><a href="https://debezium.io/documentation/reference/stable/connectors/postgresql.html" rel="noopener">Debezium connector for PostgreSQL</a></td><td>Snapshots, replica identity, heartbeats, replication slots and incremental snapshots</td></tr>
    <tr><td><a href="https://debezium.io/documentation/reference/stable/transformations/event-flattening.html" rel="noopener">Debezium New Record State Extraction</a></td><td>The flattening transform and its delete and tombstone handling</td></tr>
    <tr><td><a href="https://debezium.io/documentation/reference/stable/configuration/signalling.html" rel="noopener">Debezium signaling</a></td><td>The signal table and ad hoc incremental snapshots</td></tr>
    <tr><td><a href="https://kafka.apache.org/43/kafka-connect/user-guide/" rel="noopener">Kafka Connect user guide, REST API</a></td><td>Stopping a connector and reading or changing its offsets</td></tr>
    <tr><td><a href="https://kafka.apache.org/43/configuration/kafka-connect-configs/" rel="noopener">Kafka Connect configs</a></td><td>offset.flush.interval.ms and offset.flush.timeout.ms</td></tr>
    <tr><td><a href="https://kafka.apache.org/43/configuration/producer-configs/" rel="noopener">Kafka producer configs</a></td><td>max.block.ms and the other producer settings</td></tr>
    <tr><td><a href="https://www.postgresql.org/docs/18/runtime-config-replication.html" rel="noopener">PostgreSQL replication settings</a></td><td>max_slot_wal_keep_size, max_replication_slots and idle_replication_slot_timeout</td></tr>
    <tr><td><a href="https://www.postgresql.org/docs/18/logical-replication-publication.html" rel="noopener">PostgreSQL logical replication publications</a></td><td>Replica identity and the UPDATE and DELETE a publication can publish</td></tr>
    <tr><td><a href="https://www.postgresql.org/docs/18/logicaldecoding-explanation.html" rel="noopener">PostgreSQL logical decoding concepts</a></td><td>Replication slots and the WAL they retain</td></tr>
    <tr><td><a href="https://www.postgresql.org/docs/18/view-pg-replication-slots.html" rel="noopener">PostgreSQL pg_replication_slots</a></td><td>Slot positions, wal_status and safe_wal_size</td></tr>
    <tr><td><a href="https://www.postgresql.org/docs/18/protocol-replication.html" rel="noopener">PostgreSQL streaming replication protocol</a></td><td>Where START_REPLICATION begins streaming a logical slot</td></tr>
    <tr><td><a href="https://kafka.apache.org/43/design/design/" rel="noopener">Kafka design: message delivery semantics and transactions</a></td><td>Exactly-once from a transactional producer and a read-committed consumer</td></tr>
    <tr><td><a href="https://kafka.apache.org/43/configuration/consumer-configs/" rel="noopener">Kafka consumer configs</a></td><td>isolation.level and max.poll.interval.ms</td></tr>
    <tr><td><a href="https://cwiki.apache.org/confluence/display/KAFKA/KIP-98+-+Exactly+Once+Delivery+and+Transactional+Messaging" rel="noopener">KIP-98: Exactly Once Delivery and Transactional Messaging</a></td><td>The transactional producer, the producer epoch and fencing</td></tr>
    <tr><td><a href="https://cwiki.apache.org/confluence/display/KAFKA/KIP-447%3A+Producer+scalability+for+exactly+once+semantics" rel="noopener">KIP-447: Producer scalability for exactly once semantics</a></td><td>Sending consumer group metadata with the offsets of a transaction</td></tr>
    <tr><td><a href="https://cwiki.apache.org/confluence/display/KAFKA/KIP-939%3A+Support+Participation+in+2PC" rel="noopener">KIP-939: Support Participation in 2PC</a></td><td>Kafka as a participant in an external two-phase commit</td></tr>
    <tr><td><a href="https://cwiki.apache.org/confluence/display/KAFKA/KIP-664%3A+Provide+tooling+to+detect+and+abort+hanging+transactions" rel="noopener">KIP-664: Provide tooling to detect and abort hanging transactions</a></td><td>kafka-transactions.sh</td></tr>
    <tr><td><a href="https://smallrye.io/smallrye-reactive-messaging/4.37.0/kafka/transactions/" rel="noopener">SmallRye Kafka transactions and exactly-once processing</a></td><td>KafkaTransactions, offsets inside a transaction and the abort behaviour</td></tr>
    <tr><td><a href="https://smallrye.io/smallrye-reactive-messaging/4.37.0/kafka/receiving-kafka-records/" rel="noopener">SmallRye receiving Kafka records</a></td><td>Failure strategies, the dead letter queue and deserialization failures</td></tr>
    <tr><td><a href="https://quarkus.io/guides/kafka" rel="noopener">Quarkus Apache Kafka guide</a></td><td>Blocking processing, error handling, transactions and chaining with Hibernate ORM</td></tr>
    <tr><td><a href="https://quarkus.io/guides/smallrye-fault-tolerance" rel="noopener">Quarkus SmallRye Fault Tolerance</a></td><td>Retrying work with @Retry</td></tr>
    <tr><td><a href="https://camel.apache.org/components/4.22.x/eips/idempotentConsumer-eip.html" rel="noopener">Camel Idempotent Consumer EIP</a></td><td>The eager, removeOnFailure and skipDuplicate options and the key repositories</td></tr>
    <tr><td><a href="https://camel.apache.org/components/4.22.x/kafka-component.html" rel="noopener">Camel Kafka component</a></td><td>Transacted producers, isolationLevel, breakOnFirstError and pollOnError</td></tr>
    <tr><td><a href="https://camel.apache.org/components/4.22.x/eips/dead-letter-channel.html" rel="noopener">Camel Dead Letter Channel</a></td><td>Redelivery and routing the original message to a dead letter endpoint</td></tr>
    <tr><td><a href="https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/jpa.html" rel="noopener">Camel Quarkus JPA extension</a></td><td>The Quarkus transaction strategy and JpaMessageIdRepository</td></tr>
    <tr><td><a href="https://camel.apache.org/components/4.22.x/jpa-component.html" rel="noopener">Camel JPA component</a></td><td>JpaMessageIdRepository and the transacted consumer option</td></tr>
    <tr><td><a href="https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/jta.html" rel="noopener">Camel Quarkus JTA extension</a></td><td>Transacted routes on the Quarkus transaction manager</td></tr>
    <tr><td><a href="https://camel.apache.org/components/4.22.x/eips/transactional-client.html" rel="noopener">Camel Transactional Client EIP</a></td><td>The transacted() route boundary</td></tr>
    <tr><td><a href="https://camel.apache.org/manual/error-handler.html" rel="noopener">Camel error handler</a></td><td>The default error handler and redelivery</td></tr>
    <tr><td><a href="https://camel.apache.org/manual/exception-clause.html" rel="noopener">Camel exception clause</a></td><td>onException overrides per exception type</td></tr>
    <tr><td><a href="https://quarkus.io/guides/transaction" rel="noopener">Quarkus transactions guide</a></td><td>@Transactional and the JTA transaction manager</td></tr>
    <tr><td><a href="https://quarkus.io/guides/hibernate-orm-panache" rel="noopener">Quarkus Hibernate ORM with Panache</a></td><td>Entities, queries and pessimistic locking</td></tr>
    <tr><td><a href="https://www.postgresql.org/docs/18/sql-select.html" rel="noopener">PostgreSQL SELECT</a></td><td>The locking clause, FOR UPDATE</td></tr>
    <tr><td><a href="https://www.postgresql.org/docs/18/runtime-config-client.html" rel="noopener">PostgreSQL client connection defaults</a></td><td>lock_timeout</td></tr>
    <tr><td><a href="https://www.postgresql.org/docs/18/sql-insert.html" rel="noopener">PostgreSQL INSERT</a></td><td>ON CONFLICT DO NOTHING</td></tr>
    <tr><td><a href="https://camel.apache.org/components/4.22.x/debezium-postgres-component.html" rel="noopener">Camel Debezium PostgreSQL component</a></td><td>The embedded change-data-capture consumer and its headers</td></tr>
    <tr><td><a href="https://www.apicur.io/registry/docs/" rel="noopener">Apicurio Registry</a></td><td>Schema registry and compatibility rules</td></tr>
    <tr><td><a href="https://quarkus.io/guides/" rel="noopener">Quarkus</a></td><td>The Quarkus runtime, Dev Services, messaging and Kafka Streams</td></tr>
    <tr><td><a href="https://smallrye.io/smallrye-reactive-messaging/" rel="noopener">SmallRye Reactive Messaging</a></td><td>The Kafka connector Quarkus messaging uses, including share-group consumers</td></tr>
    <tr><td><a href="https://camel.apache.org/docs/" rel="noopener">Apache Camel</a></td><td>The route DSL, components and the Saga EIP</td></tr>
    <tr><td><a href="https://camel.apache.org/camel-quarkus/latest/" rel="noopener">Camel Quarkus</a></td><td>The Quarkus runtime for Camel</td></tr>
    <tr><td><a href="https://github.com/grafana/docker-otel-lgtm" rel="noopener">Grafana LGTM</a></td><td>The all-in-one Loki, Grafana, Tempo and Mimir image the examples send telemetry to</td></tr>
    <tr><td><a href="https://cloudnative-pg.io/documentation/" rel="noopener">CloudNativePG</a></td><td>The PostgreSQL operator and operand image</td></tr>
    <tr><td><a href="https://sdkman.io/" rel="noopener">SDKMAN</a></td><td>Installing JDK and Maven</td></tr>
  </tbody>
</table>

## Related projects

These are our own companion projects, reused and adapted for layout, infrastructure and
precedent.

<table class="bib">
  <colgroup><col style="width:42%"><col style="width:58%"></colgroup>
  <thead><tr><th>Project</th><th>Relationship</th></tr></thead>
  <tbody>
    <tr><td><a href="https://github.com/patterncatalyst/enterprise-integration-patterns-with-camel" rel="noopener">Enterprise Integration Patterns with Camel</a></td><td>The model for this site; the broader pattern catalogue</td></tr>
    <tr><td><a href="https://github.com/patterncatalyst/cloud-native-design-patterns" rel="noopener">Cloud-Native Design Patterns</a></td><td>Sibling book on cloud-native patterns</td></tr>
    <tr><td><a href="https://github.com/patterncatalyst/datamesh-reference-arch-quarkus" rel="noopener">Data Mesh Reference Architecture on Quarkus</a></td><td>Orchestration styles, rules and AI classification, PostgreSQL operator manifests</td></tr>
    <tr><td><a href="https://github.com/patterncatalyst/quarkus-observability" rel="noopener">Quarkus Observability</a></td><td>Observability precedent</td></tr>
    <tr><td><a href="https://github.com/patterncatalyst/otel-observability-tutorial" rel="noopener">OpenTelemetry Observability Tutorial</a></td><td>OpenTelemetry wiring and Grafana dashboards</td></tr>
    <tr><td><a href="https://github.com/patterncatalyst/domain-driven-design-observability-workshop" rel="noopener">Domain-Driven Design Observability Workshop</a></td><td>Observability of domain events</td></tr>
  </tbody>
</table>

These titles inform this site's coverage and rigor only. Nothing in these chapters
or the runnable examples copies text or code from them.
