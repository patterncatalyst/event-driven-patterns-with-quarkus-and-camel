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
    <tr><td><a href="https://www.oreilly.com/library/view/building-event-driven-microservices/9798341622180/" rel="noopener"><em>Building Event-Driven Microservices, Second Edition</em></a><br><small>O'Reilly, ISBN 979-8-3416-2218-0. Events and event streams, event design, schemas and data contracts, integrating existing systems, denormalization, stateful and deterministic stream processing, streaming SQL, workflows, producer and consumer microservices, eventual consistency, bad data, testing, deployment</small></td><td>Adam Bellemare</td><td>2025</td></tr>
    <tr><td><a href="https://www.enterpriseintegrationpatterns.com/" rel="noopener"><em>Enterprise Integration Patterns</em></a><br><small>Addison-Wesley, ISBN 978-0-321-20068-6. Idempotent Receiver, Content Enricher, Aggregator, Dead Letter Channel, Process Manager, Resequencer, Claim Check, Event Message, Command Message, Document Message</small></td><td>Gregor Hohpe, Bobby Woolf</td><td>2003</td></tr>
    <tr><td><a href="https://www.informit.com/store/implementing-domain-driven-design-9780133039894" rel="noopener"><em>Implementing Domain-Driven Design</em></a><br><small>Addison-Wesley, ISBN 978-0-13-303989-4. Aggregates, domain events, and aggregates with event sourcing</small></td><td>Vaughn Vernon</td><td>2013</td></tr>
    <tr><td><a href="https://www.manning.com/books/microservices-patterns" rel="noopener"><em>Microservices Patterns</em></a><br><small>Manning, ISBN 978-1-61729-454-9. Sagas, choreography and orchestration, countermeasures for the lack of isolation, the transactional outbox</small></td><td>Chris Richardson</td><td>2018</td></tr>
    <tr><td><a href="https://www.oreilly.com/library/view/designing-data-intensive-applications/9781098119058/" rel="noopener"><em>Designing Data-Intensive Applications, Second Edition</em></a><br><small>O'Reilly, ISBN 978-1-098-11906-5. Data systems design; further reading</small></td><td>Martin Kleppmann, Chris Riccomini</td><td>2026</td></tr>
  </tbody>
</table>

## Articles and pattern references

<table class="bib">
  <colgroup><col style="width:36%"><col style="width:24%"><col style="width:40%"></colgroup>
  <thead><tr><th>Title</th><th>Author</th><th>Used for</th></tr></thead>
  <tbody>
    <tr><td><a href="https://martinfowler.com/eaaDev/EventSourcing.html" rel="noopener">Event Sourcing</a></td><td>Martin Fowler</td><td>The foundations of event sourcing</td></tr>
    <tr><td><a href="https://martinfowler.com/articles/201701-event-driven.html" rel="noopener">What do you mean by "Event-Driven"?</a></td><td>Martin Fowler</td><td>Names for event notification, event-carried state transfer and event sourcing</td></tr>
    <tr><td><a href="https://martinfowler.com/bliki/DDD_Aggregate.html" rel="noopener">DDD Aggregate</a></td><td>Martin Fowler</td><td>An aggregate as one unit reached through a single root</td></tr>
    <tr><td><a href="https://cqrs.wordpress.com/wp-content/uploads/2010/11/cqrs_documents.pdf" rel="noopener">CQRS Documents</a></td><td>Greg Young</td><td>The event store operations, optimistic concurrency on append, and rolling snapshots</td></tr>
    <tr><td><a href="https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing" rel="noopener">Event Sourcing pattern</a></td><td>Microsoft Azure Architecture Center</td><td>Structural guidance on event sourcing, as a pattern reference only</td></tr>
    <tr><td><a href="https://martinfowler.com/bliki/CQRS.html" rel="noopener">CQRS</a></td><td>Martin Fowler</td><td>Separate models for updating and reading, and when the split is worth its cost</td></tr>
    <tr><td><a href="https://learn.microsoft.com/en-us/azure/architecture/patterns/cqrs" rel="noopener">CQRS pattern</a></td><td>Microsoft Azure Architecture Center</td><td>Read and write models in one store or in separate stores, and the eventual consistency between them</td></tr>
    <tr><td><a href="https://learn.microsoft.com/en-us/azure/architecture/patterns/materialized-view" rel="noopener">Materialized View pattern</a></td><td>Microsoft Azure Architecture Center</td><td>A disposable view rebuilt from its source, and the refresh and staleness trade-offs</td></tr>
    <tr><td><a href="https://learn.microsoft.com/en-us/azure/architecture/patterns/cache-aside" rel="noopener">Cache-Aside pattern</a></td><td>Microsoft Azure Architecture Center</td><td>Loading a cache on demand, as the contrast to a write-forward cache</td></tr>
    <tr><td><a href="https://quarkus.io/version/3.40/guides/redis-reference" rel="noopener">Redis extension reference guide</a></td><td>Quarkus</td><td>The Redis client and its Dev Services image setting, for the write-forward cache</td></tr>
    <tr><td><a href="https://leanpub.com/esversioning" rel="noopener"><em>Versioning in an Event Sourced System</em></a></td><td>Greg Young</td><td>The topics of its chapters on versioning events and process managers (chapter titles only)</td></tr>
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
    <tr><td><a href="https://www.enterpriseintegrationpatterns.com/patterns/messaging/DataEnricher.html" rel="noopener">Content Enricher</a></td><td>Gregor Hohpe, Bobby Woolf</td><td>Fetching the missing data from an external source and attaching it to the message</td></tr>
    <tr><td><a href="https://www.enterpriseintegrationpatterns.com/patterns/messaging/Aggregator.html" rel="noopener">Aggregator</a></td><td>Gregor Hohpe, Bobby Woolf</td><td>A stateful filter that combines related messages into one, and the ways a group is completed</td></tr>
    <tr><td><a href="https://www.enterpriseintegrationpatterns.com/patterns/messaging/ProcessManager.html" rel="noopener">Process Manager</a></td><td>Gregor Hohpe, Bobby Woolf</td><td>A central unit that keeps the state of a sequence and picks the next step from intermediate results</td></tr>
    <tr><td><a href="https://www.enterpriseintegrationpatterns.com/patterns/messaging/CorrelationIdentifier.html" rel="noopener">Correlation Identifier</a></td><td>Gregor Hohpe, Bobby Woolf</td><td>A unique identifier that ties a reply to its request, carried here as the order id and the trace id</td></tr>
    <tr><td><a href="https://www.w3.org/TR/trace-context/" rel="noopener">Trace Context</a>, W3C Recommendation, 2021</td><td>W3C</td><td>The traceparent and tracestate headers</td></tr>
    <tr><td><a href="https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/" rel="noopener">Semantic conventions for messaging spans</a></td><td>OpenTelemetry</td><td>Process spans, links and parents for messages</td></tr>
    <tr><td><a href="https://learn.microsoft.com/en-us/azure/architecture/patterns/saga" rel="noopener">Saga design pattern</a>, Azure Architecture Center</td><td>Microsoft</td><td>Compensable, pivot and retryable transactions; the semantic lock and commutative updates as countermeasures</td></tr>
    <tr><td><a href="https://microservices.io/patterns/data/saga.html" rel="noopener">Pattern: Saga</a></td><td>Chris Richardson</td><td>A saga as a sequence of local transactions with compensating transactions, choreography and orchestration, and the lack of isolation</td></tr>
    <tr><td><a href="https://doi.org/10.1145/38713.38742" rel="noopener">Sagas</a>, Proceedings of the 1987 ACM SIGMOD International Conference on Management of Data, pages 249 to 259</td><td>Hector Garcia-Molina, Kenneth Salem</td><td>The origin of the saga</td></tr>
    <tr><td><a href="https://martinfowler.com/bliki/ParallelChange.html" rel="noopener">Parallel Change</a></td><td>Danilo Sato</td><td>Expand, migrate, contract: changing an interface in three phases, 2014</td></tr>
    <tr><td><a href="https://doi.org/10.1109/C-M.1978.218136" rel="noopener">Hints on Test Data Selection: Help for the Practicing Programmer</a>, IEEE Computer 11(4), 1978</td><td>R. A. DeMillo, R. J. Lipton, F. G. Sayward</td><td>Mutation testing, the idea behind proving a check by breaking the code it guards</td></tr>
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
    <tr><td>Monitoring and metrics</td><td><a href="https://strimzi.io/blog/2025/10/06/strimzi-metrics-reporter/" rel="noopener">Using Strimzi Metrics Reporter to expose Kafka metrics in Prometheus format</a> (Owen Corrigan), <a href="https://strimzi.io/blog/2025/10/01/monitoring-of-custom-resources/" rel="noopener">Monitoring of custom resources</a> (Sebastian Gaiser), <a href="https://strimzi.io/blog/2023/03/01/opentelemetry/" rel="noopener">Tracing in Apache Kafka: from OpenTracing to OpenTelemetry</a></td></tr>
    <tr><td>Cruise Control</td><td><a href="https://strimzi.io/blog/2024/11/25/autorebalancing-on-scaling/" rel="noopener">Auto-rebalancing on cluster scaling</a> (Paolo Patierno), <a href="https://strimzi.io/blog/2025/02/13/moving-data-between-JBOD-disks-using-cruise-control/" rel="noopener">Moving data between JBOD disks using Cruise Control</a>, <a href="https://strimzi.io/blog/2023/01/19/make-your-kafka-cluster-production-ready-cluster-balancing/" rel="noopener">Video: Make your Kafka cluster production-ready - Cluster Balancing</a></td></tr>
    <tr><td>MirrorMaker 2</td><td><a href="https://strimzi.io/blog/2021/11/22/migrating-kafka-with-mirror-maker2/" rel="noopener">Migrating Kafka clusters with MirrorMaker2 and Strimzi</a> (Roman Martin, Manuel Schindler)</td></tr>
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
    <tr><td><a href="https://strimzi.io/docs/operators/latest/deploying.html" rel="noopener">Strimzi, Deploying and Managing Strimzi</a></td><td>The Metrics Reporter, Cruise Control and auto-rebalancing, MirrorMaker 2, the Connect build, connector offsets and kube-state-metrics</td></tr>
    <tr><td><a href="https://strimzi.io/docs/operators/latest/configuring.html" rel="noopener">Strimzi, Configuring Strimzi</a></td><td>The resource schemas, and the Metrics Reporter allowList that applies without a rolling update</td></tr>
    <tr><td><a href="https://github.com/strimzi/strimzi-kafka-operator/blob/1.3.1/CHANGELOG.md" rel="noopener">Strimzi changelog</a></td><td>Resource state metrics removed from the operator, the v1 KafkaMirrorMaker2, stopped connectors</td></tr>
    <tr><td><a href="https://github.com/strimzi/proposals/blob/main/087-monitoring-of-custom-resources.md" rel="noopener">Strimzi proposal 087: Monitoring of custom resources</a></td><td>Why resource state moved to kube-state-metrics</td></tr>
    <tr><td><a href="https://github.com/strimzi/proposals/blob/main/114-use-buildah-instead-of-kaniko.md" rel="noopener">Strimzi proposal 114: Use Buildah instead of Kaniko</a></td><td>The builder behind the KafkaConnect build</td></tr>
    <tr><td><a href="https://cwiki.apache.org/confluence/display/KAFKA/KIP-382%3A+MirrorMaker+2.0" rel="noopener">KIP-382: MirrorMaker 2.0</a></td><td>The connectors, offset syncs and checkpoints of MirrorMaker 2</td></tr>
    <tr><td><a href="https://cwiki.apache.org/confluence/spaces/KAFKA/pages/311627566/KIP-1066+Mechanism+to+cordon+brokers+and+log+directories" rel="noopener">KIP-1066: Mechanism to cordon brokers and log directories</a></td><td>Keeping new partitions off a broker that is being removed</td></tr>
    <tr><td><a href="https://kafka.apache.org/43/operations/geo-replication-cross-cluster-data-mirroring/" rel="noopener">Kafka geo-replication</a></td><td>Replication policies, topic naming across clusters and the MirrorMaker 2 metrics</td></tr>
    <tr><td><a href="https://github.com/apache/kafka/blob/4.3/connect/mirror/src/main/java/org/apache/kafka/connect/mirror/MirrorCheckpointConfig.java" rel="noopener">Kafka MirrorCheckpointConfig</a></td><td>The checkpoint connector's defaults: group sync, intervals and group filter</td></tr>
    <tr><td><a href="https://github.com/apache/kafka/blob/4.3/connect/mirror-client/src/main/java/org/apache/kafka/connect/mirror/IdentityReplicationPolicy.java" rel="noopener">Kafka IdentityReplicationPolicy</a></td><td>A replication policy that keeps topic names and cannot prevent cycles</td></tr>
    <tr><td><a href="https://github.com/linkedin/cruise-control/wiki/Overview" rel="noopener">Cruise Control wiki, Overview</a></td><td>The load monitor and the cluster model behind a rebalance proposal</td></tr>
    <tr><td><a href="https://github.com/kubernetes/kube-state-metrics/blob/main/docs/metrics/extend/customresourcestate-metrics.md" rel="noopener">kube-state-metrics, custom resource state metrics</a></td><td>Turning the status of a custom resource into series</td></tr>
    <tr><td><a href="https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/prometheusreceiver" rel="noopener">OpenTelemetry Collector Prometheus receiver</a></td><td>A Prometheus scrape inside the collector, with Kubernetes service discovery</td></tr>
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
    <tr><td><a href="https://cwiki.apache.org/confluence/display/KAFKA/KIP-664%3A+Provide+tooling+to+detect+and+abort+hanging+transactions" rel="noopener">KIP-664: Provide tooling to detect and abort hanging transactions</a></td><td>kafka-transactions.sh</td></tr>
    <tr><td><a href="https://smallrye.io/smallrye-reactive-messaging/4.37.0/kafka/transactions/" rel="noopener">SmallRye Kafka transactions and exactly-once processing</a></td><td>KafkaTransactions, offsets inside a transaction and the abort behaviour</td></tr>
    <tr><td><a href="https://smallrye.io/smallrye-reactive-messaging/4.37.0/kafka/receiving-kafka-records/" rel="noopener">SmallRye receiving Kafka records</a></td><td>Failure strategies, the dead letter queue and deserialization failures</td></tr>
    <tr><td><a href="https://quarkus.io/guides/kafka" rel="noopener">Quarkus Apache Kafka guide</a></td><td>Blocking processing, error handling, transactions and chaining with Hibernate ORM</td></tr>
    <tr><td><a href="https://quarkus.io/guides/smallrye-fault-tolerance" rel="noopener">Quarkus SmallRye Fault Tolerance</a></td><td>Retrying work with @Retry</td></tr>
    <tr><td><a href="https://camel.apache.org/components/4.22.x/eips/idempotentConsumer-eip.html" rel="noopener">Camel Idempotent Consumer EIP</a></td><td>The eager, removeOnFailure and skipDuplicate options and the key repositories</td></tr>
    <tr><td><a href="https://camel.apache.org/components/4.22.x/kafka-component.html" rel="noopener">Camel Kafka component</a></td><td>Transacted producers, isolationLevel, breakOnFirstError and pollOnError</td></tr>
    <tr><td><a href="https://kafka.apache.org/42/streams/developer-guide/dsl-api/" rel="noopener">Kafka Streams DSL</a></td><td>Joins, co-partitioning, the GlobalKTable, windows, grace and suppress</td></tr>
    <tr><td><a href="https://kafka.apache.org/42/streams/core-concepts/" rel="noopener">Kafka Streams core concepts</a></td><td>Event time, stream time, out-of-order records and the grace period</td></tr>
    <tr><td><a href="https://kafka.apache.org/42/streams/developer-guide/config-streams/" rel="noopener">Kafka Streams configuration</a></td><td>processing.guarantee, commit.interval.ms, the timestamp extractor and group.protocol</td></tr>
    <tr><td><a href="https://kafka.apache.org/42/streams/developer-guide/testing/" rel="noopener">Testing a Streams application</a></td><td>TopologyTestDriver and advancing time without a broker</td></tr>
    <tr><td><a href="https://kafka.apache.org/42/operations/monitoring/" rel="noopener">Kafka monitoring</a></td><td>The Kafka Streams task metrics, including dropped-records-total</td></tr>
    <tr><td><a href="https://quarkus.io/guides/kafka-streams" rel="noopener">Quarkus Apache Kafka Streams guide</a></td><td>The Kafka Streams extension, its topics setting, health checks and interactive queries</td></tr>
    <tr><td><a href="https://quarkus.io/guides/kafka-dev-services" rel="noopener">Quarkus Dev Services for Kafka</a></td><td>Topic partitions in the Dev Services broker</td></tr>
    <tr><td><a href="https://camel.apache.org/components/4.22.x/eips/enrich-eip.html" rel="noopener">Camel Enrich EIP</a></td><td>Enriching a message from a resource endpoint with an aggregation strategy</td></tr>
    <tr><td><a href="https://camel.apache.org/components/4.22.x/eips/aggregate-eip.html" rel="noopener">Camel Aggregate EIP</a></td><td>Correlation, completion size, predicate and timeout, and the completed-by property</td></tr>
    <tr><td><a href="https://camel.apache.org/components/4.22.x/sql-component.html" rel="noopener">Camel SQL component</a></td><td>JdbcAggregationRepository and PostgresAggregationRepository</td></tr>
    <tr><td><a href="https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/sql.html" rel="noopener">Camel Quarkus SQL extension</a></td><td>The Agroal DataSource for the SQL component</td></tr>
    <tr><td><a href="https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/http.html" rel="noopener">Camel Quarkus HTTP extension</a></td><td>Calling an HTTP endpoint from a route</td></tr>
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
    <tr><td><a href="https://camel.apache.org/components/4.22.x/eips/saga-eip.html" rel="noopener">Camel Saga EIP</a></td><td>Propagation, completion mode, timeout, compensation and completion routes, and the in-memory and LRA saga services</td></tr>
    <tr><td><a href="https://camel.apache.org/components/4.22.x/others/lra.html" rel="noopener">Camel LRA component</a></td><td>The binding of the Saga EIP to a MicroProfile LRA coordinator</td></tr>
    <tr><td><a href="https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/lra.html" rel="noopener">Camel Quarkus LRA extension</a></td><td>The LRA saga service on Quarkus</td></tr>
    <tr><td><a href="https://download.eclipse.org/microprofile/microprofile-lra-2.0/microprofile-lra-spec-2.0.html" rel="noopener">MicroProfile Long Running Actions 2.0</a></td><td>The participant state model, compensate and complete callbacks, time limits and joining an LRA</td></tr>
    <tr><td><a href="https://quay.io/repository/jbosstm/lra-coordinator" rel="noopener">Narayana LRA coordinator image</a></td><td>The coordinator container the sagas run against</td></tr>
    <tr><td><a href="https://docs.quarkiverse.io/quarkus-flow/dev/index.html" rel="noopener">Quarkus Flow</a></td><td>Workflows in Java, messaging with CloudEvents, persistence and resuming after a restart</td></tr>
    <tr><td><a href="https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md" rel="noopener">Serverless Workflow DSL reference</a></td><td>Timeouts, the try and catch tasks, listen, and the standard error types</td></tr>
    <tr><td><a href="https://docs.quarkiverse.io/quarkus-langchain4j/dev/ai-services.html" rel="noopener">Quarkus LangChain4j AI services</a></td><td>Mapping a model response to a typed return value</td></tr>
    <tr><td><a href="https://docs.quarkiverse.io/quarkus-langchain4j/dev/ollama-chat-model.html" rel="noopener">Quarkus LangChain4j Ollama chat model</a></td><td>Model, temperature, seed, response format and Dev Services settings</td></tr>
    <tr><td><a href="https://docs.ollama.com/capabilities/structured-outputs" rel="noopener">Ollama structured outputs</a></td><td>Constraining a response to a JSON schema</td></tr>
    <tr><td><a href="https://github.com/ollama/ollama/blob/main/docs/api.md" rel="noopener">Ollama API</a></td><td>The format and options request fields, seed and keep-alive</td></tr>
    <tr><td><a href="https://kie.apache.org/docs/10.2.x/drools/drools/KIE/index.html" rel="noopener">Drools rule units</a></td><td>A rule unit as a module of rules over typed data sources</td></tr>
    <tr><td><a href="https://camel.apache.org/components/4.22.x/eips/resequence-eip.html" rel="noopener">Camel Resequence EIP</a></td><td>Batch and stream resequencing, capacity, timeout and rejectOld</td></tr>
    <tr><td><a href="https://camel.apache.org/camel-quarkus/3.40.x/user-guide/testing.html" rel="noopener">Camel Quarkus testing</a></td><td>CamelQuarkusTestSupport and AdviceWith for route tests</td></tr>
    <tr><td><a href="https://quarkus.io/version/3.40/guides/redis" rel="noopener">Quarkus Redis client guide</a></td><td>The Redis client extension and its Dev Services</td></tr>
    <tr><td><a href="https://quarkus.io/version/3.40/guides/redis-dev-services" rel="noopener">Quarkus Dev Services for Redis</a></td><td>Overriding the Redis image with quarkus.redis.devservices.image-name</td></tr>
    <tr><td><a href="https://quarkus.io/version/3.40/guides/all-config" rel="noopener">Quarkus configuration reference</a></td><td>quarkus.shutdown.timeout and quarkus.shutdown.delay for graceful shutdown</td></tr>
    <tr><td><a href="https://kafka.apache.org/43/streams/developer-guide/interactive-queries/" rel="noopener">Kafka Streams interactive queries</a></td><td>Querying the local and remote state stores of a Streams application</td></tr>
    <tr><td><a href="https://kafka.apache.org/43/configuration/broker-configs/" rel="noopener">Kafka broker configuration</a></td><td>The broker keys compared between compose and Kubernetes</td></tr>
    <tr><td><a href="https://kafka.apache.org/43/operations/basic-kafka-operations/" rel="noopener">Kafka basic operations</a></td><td>kafka-consumer-groups.sh and resetting offsets with --from-file</td></tr>
    <tr><td><a href="https://cloudnative-pg.io/docs/1.30/postgresql_conf/" rel="noopener">CloudNativePG PostgreSQL configuration</a></td><td>Default, user-provided and fixed parameters</td></tr>
    <tr><td><a href="https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/dev/table/sql/queries/window-tvf/" rel="noopener">Apache Flink windowing table-valued functions</a></td><td>TUMBLE and the other window functions</td></tr>
    <tr><td><a href="https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/connectors/table/kafka/" rel="noopener">Apache Flink Kafka SQL connector</a></td><td>Reading a Kafka topic in SQL, per-partition watermarks</td></tr>
    <tr><td><a href="https://nightlies.apache.org/flink/flink-docs-release-2.2/docs/connectors/table/upsert-kafka/" rel="noopener">Apache Flink upsert-kafka connector</a></td><td>Writing a keyed changelog back to a topic</td></tr>
    <tr><td><a href="https://docs.risingwave.com/ingestion/sources/kafka" rel="noopener">RisingWave Kafka source</a></td><td>Creating a source over a Kafka topic</td></tr>
    <tr><td><a href="https://docs.risingwave.com/processing/sql/time-windows" rel="noopener">RisingWave time windows</a></td><td>The tumble() function</td></tr>
    <tr><td><a href="https://docs.risingwave.com/processing/emit-on-window-close" rel="noopener">RisingWave emit on window close</a></td><td>Final window results written once</td></tr>
    <tr><td><a href="https://docs.risingwave.com/get-started/premium-features" rel="noopener">RisingWave premium features</a></td><td>Premium features need a licence key and are not used; the Community Edition is Apache-2.0</td></tr>
    <tr><td><a href="https://pinot.apache.org/" rel="noopener">Apache Pinot</a> and <a href="https://druid.apache.org/" rel="noopener">Apache Druid</a></td><td>Analytical databases over event data, named as a different job from a windowed count</td></tr>
    <tr><td><a href="https://github.com/confluentinc/ksql/blob/master/LICENSE" rel="noopener">ksqlDB LICENSE</a></td><td>ksqlDB is under the Confluent Community License, which excludes it from the engines used</td></tr>
    <tr><td><a href="https://raw.githubusercontent.com/MaterializeInc/materialize/main/LICENSE" rel="noopener">Materialize LICENSE</a></td><td>The Business Source License, which excludes Materialize from the engines used</td></tr>
    <tr><td><a href="https://pitest.org/" rel="noopener">PIT</a></td><td>Mutation testing for Java and the JVM</td></tr>
    <tr><td><a href="https://redis.io/legal/licenses/" rel="noopener">Redis licences</a></td><td>The Redis 8 tri-licence and the AGPLv3 option</td></tr>
    <tr><td><a href="https://kubernetes.io/docs/concepts/workloads/controllers/deployment/" rel="noopener">Kubernetes Deployments</a></td><td>RollingUpdate and Recreate strategies</td></tr>
    <tr><td><a href="https://www.apicur.io/registry/docs/" rel="noopener">Apicurio Registry</a></td><td>Schema registry and compatibility rules</td></tr>
    <tr><td><a href="https://avro.apache.org/docs/1.12.0/specification/" rel="noopener">Apache Avro specification</a></td><td>Schema resolution between a writer's and a reader's schema</td></tr>
    <tr><td><a href="https://www.rfc-editor.org/rfc/rfc9110.html" rel="noopener">RFC 9110: HTTP Semantics</a></td><td>If-Match (section 13.1.1) and 412 Precondition Failed (section 15.5.13)</td></tr>
    <tr><td><a href="https://quarkus.io/guides/" rel="noopener">Quarkus</a></td><td>The Quarkus runtime, Dev Services, messaging and Kafka Streams</td></tr>
    <tr><td><a href="https://smallrye.io/smallrye-reactive-messaging/" rel="noopener">SmallRye Reactive Messaging</a></td><td>The Kafka connector Quarkus messaging uses, including share-group consumers</td></tr>
    <tr><td><a href="https://camel.apache.org/docs/" rel="noopener">Apache Camel</a></td><td>The route DSL, components and the Saga EIP</td></tr>
    <tr><td><a href="https://camel.apache.org/camel-quarkus/latest/" rel="noopener">Camel Quarkus</a></td><td>The Quarkus runtime for Camel</td></tr>
    <tr><td><a href="https://github.com/grafana/docker-otel-lgtm" rel="noopener">Grafana LGTM</a></td><td>The all-in-one Loki, Grafana, Tempo and Mimir image the examples send telemetry to</td></tr>
    <tr><td><a href="https://debezium.io/documentation/reference/stable/integrations/tracing.html" rel="noopener">Debezium tracing</a></td><td>Spans written at the outbox and read by Connect</td></tr>
    <tr><td><a href="https://opentelemetry.io/docs/zero-code/java/agent/" rel="noopener">OpenTelemetry Java agent</a></td><td>The agent that gives Kafka Connect its tracing SDK</td></tr>
    <tr><td><a href="https://grafana.com/docs/tempo/latest/" rel="noopener">Grafana Tempo</a></td><td>The trace store, its HTTP API and TraceQL</td></tr>
    <tr><td><a href="https://grafana.com/docs/loki/latest/" rel="noopener">Grafana Loki</a></td><td>Log storage, OTLP ingestion, structured metadata and LogQL</td></tr>
    <tr><td><a href="https://grafana.com/docs/grafana/latest/alerting/" rel="noopener">Grafana alerting</a></td><td>Provisioned alert rules</td></tr>
    <tr><td><a href="https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/v0.165.0/receiver/kafkametricsreceiver" rel="noopener">OpenTelemetry Collector Kafka metrics receiver</a></td><td>Consumer group lag and partition offsets from the broker</td></tr>
    <tr><td><a href="https://cloudnative-pg.io/documentation/" rel="noopener">CloudNativePG</a></td><td>The PostgreSQL operator and operand image</td></tr>
    <tr><td><a href="https://sdkman.io/" rel="noopener">SDKMAN</a></td><td>Installing JDK and Maven</td></tr>
    <tr><td><a href="https://crc.dev/docs/introducing/" rel="noopener">Introducing CRC :: CRC Documentation</a></td><td>Appendix H: the single node, the ephemeral cluster, no production use</td></tr>
    <tr><td><a href="https://crc.dev/docs/networking/" rel="noopener">Networking :: CRC Documentation</a></td><td>Appendix H: user-mode networking, the DNS domains and what <code>crc setup</code> adjusts</td></tr>
    <tr><td><a href="https://crc.dev/docs/using/" rel="noopener">Using CRC :: CRC Documentation</a></td><td>Appendix H: the <code>crc-admin</code> context, stopping and deleting the instance</td></tr>
    <tr><td><a href="https://olm.operatorframework.io/docs/concepts/crds/subscription/" rel="noopener">Subscription | Operator Lifecycle Manager</a></td><td>Appendix H: <code>startingCSV</code>, <code>installPlanApproval</code> and approving an InstallPlan</td></tr>
    <tr><td><a href="https://docs.redhat.com/en/documentation/openshift_container_platform/4.22/html/registry/securing-exposing-registry" rel="noopener">Chapter 5. Exposing the registry | Registry | OpenShift Container Platform 4.22</a></td><td>Appendix H: the default route of the image registry</td></tr>
    <tr><td><a href="https://docs.redhat.com/en/documentation/openshift_container_platform/4.22/html/registry/accessing-the-registry" rel="noopener">Chapter 4. Accessing the registry | Registry | OpenShift Container Platform 4.22</a></td><td>Appendix H: the roles and the token that push to the registry</td></tr>
    <tr><td><a href="https://docs.redhat.com/en/documentation/openshift_container_platform/4.22/html/authentication_and_authorization/managing-pod-security-policies" rel="noopener">Chapter 17. Managing security context constraints | OpenShift Container Platform 4.22</a></td><td>Appendix H: restricted-v2 and the UID range of a project</td></tr>
    <tr><td><a href="https://docs.redhat.com/en/documentation/openshift_container_platform/4.22/html/operators/administrator-tasks" rel="noopener">Chapter 4. Administrator tasks | Operators | OpenShift Container Platform 4.22</a></td><td>Appendix H: installing an operator from the CLI and removing one</td></tr>
    <tr><td><a href="https://docs.redhat.com/en/documentation/red_hat_streams_for_apache_kafka/3.2/html-single/streams_for_apache_kafka_api_reference" rel="noopener">Streams for Apache Kafka API Reference | Streams for Apache Kafka 3.2</a></td><td>Appendix H: <code>KafkaConnect</code> build output to an ImageStream</td></tr>
    <tr><td><a href="https://docs.redhat.com/en/documentation/red_hat_streams_for_apache_kafka/3.2/html-single/streams_for_apache_kafka_on_openshift_overview" rel="noopener">Streams for Apache Kafka on OpenShift Overview | Streams for Apache Kafka 3.2</a></td><td>Appendix H: the product is provided through a software subscription</td></tr>
    <tr><td><a href="https://cloudnative-pg.io/docs/1.30/installation_upgrade/" rel="noopener">Installation and upgrades | CloudNativePG</a></td><td>Appendix H: installing through OLM and the certified build</td></tr>
    <tr><td><a href="https://cloudnative-pg.io/docs/1.30/declarative_role_management/" rel="noopener">PostgreSQL Role management | CloudNativePG</a></td><td>Appendix H: declaring the application role in <code>managed.roles</code></td></tr>
    <tr><td><a href="https://cloudnative-pg.io/docs/1.30/security/" rel="noopener">Security | CloudNativePG</a></td><td>Appendix H: pods under security context constraints</td></tr>
    <tr><td><a href="https://raw.githubusercontent.com/containers/skopeo/main/docs/skopeo-copy.1.md" rel="noopener">skopeo-copy(1)</a></td><td>Appendix H: <code>--dest-cert-dir</code>, <code>--dest-registry-token</code> and <code>--preserve-digests</code></td></tr>
    <tr><td><a href="https://raw.githubusercontent.com/containers/buildah/main/docs/buildah-build.1.md" rel="noopener">buildah-build(1)</a></td><td>Appendix H: building from a Containerfile and why a rebuild changes the digest</td></tr>
    <tr><td><a href="https://quarkus.io/version/3.40/guides/opentelemetry" rel="noopener">Using OpenTelemetry - 3.40 - Quarkus</a></td><td>Appendix H: <code>quarkus.otel.sdk.disabled</code> as a runtime property</td></tr>
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
    <tr><td><a href="https://github.com/patterncatalyst/otel-observability-tutorial" rel="noopener">OpenTelemetry Observability Tutorial</a></td><td>OpenTelemetry wiring and Grafana dashboards</td></tr>
    <tr><td><a href="https://github.com/patterncatalyst/domain-driven-design-observability-workshop" rel="noopener">Domain-Driven Design Observability Workshop</a></td><td>Observability of domain events</td></tr>
  </tbody>
</table>

These titles inform this site's coverage and rigor only. Nothing in these chapters
or the runnable examples copies text or code from them.
