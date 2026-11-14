---
layout: plan
title: "Domain model"
render_with_liquid: false
---

# Order-to-delivery domain model

Switched 2026-11-10 from the r01 domain to EIP Camel's order-to-delivery domain. The model is the running example of Enterprise Integration Patterns with Camel; this file states the facts this book uses, in its own words.

## Rules

- Never invent a new entity or a new area. If a pattern seems not to fit, the chapter's framing is wrong, not the domain.
- Entity names are fixed (the records and enums below). Reuse across chapters is the point.
- Event names used in examples are messages about these entities, never new entities.
- Carriers are FASTLINE (road) and AIRCO (air), both fictional. Never a real carrier name.
- No example introduces a UI. Each example lives at `examples/NN-slug/` with a README and `verify.sh`.
- Shared entity records live once, in `examples/domain-model/`, package `com.patterncatalyst.shipping`. Event payload records live in each example, not in domain-model, because payload shape is the pattern decision in chapters 02, 03 and 18 (id only, full state, v2).

## The business

A customer orders an item online. The order is recorded, stock is reserved, the payment is taken, a carrier is booked and the parcel is delivered. The customer is told at each step. Five services own one step each. They share no tables and talk only through events on Kafka, with one exception: event notification's fetch-back.

## Services

| Service | Owns | Reacts to | Emits |
|---|---|---|---|
| order-service | `Order`, its lifecycle | InventoryInsufficient, PaymentProcessed, PaymentFailed, ShipmentScheduled, ShipmentDelivered | OrderPlaced, OrderCancelled |
| inventory-service | `InventoryItem` stock | OrderPlaced, OrderCancelled, PaymentFailed | InventoryReserved, InventoryInsufficient |
| payment-service | `Payment` | InventoryReserved | PaymentProcessed, PaymentFailed |
| shipping-service | `Shipment`, carrier choice | PaymentProcessed | ShipmentScheduled, ShipmentInTransit, ShipmentDelivered |
| notification-service | `Notification` | every event | none |

## Records and enums

`examples/domain-model` version 2.3.0, plain Java 25, compact-constructor validation, one JUnit negative witness per rule.

| Type | Fields | Validation |
|---|---|---|
| `Order` | `id`, `customerId`, `itemSku`, `quantity`, `amount` | text fields non-blank; quantity > 0; amount > 0; amount is `BigDecimal` with 2 decimals, HALF_UP |
| `InventoryItem` | `sku`, `quantityOnHand` | quantity >= 0 |
| `Payment` | `id`, `orderId`, `amount`, `status` | amount > 0 |
| `Shipment` | `id`, `orderId`, `carrier`, `trackingNumber`, `status` | carrier non-null |
| `Notification` | `id`, `orderId`, `customerId`, `eventType` | text fields non-blank |
| `Topics` | string constants for the catalog topics | none; no example hard-codes a topic name |

| Enum | Values |
|---|---|
| `OrderStatus` | PLACED, PAID, SHIPPED, DELIVERED, CANCELLED |
| `PaymentStatus` | PENDING, COMPLETED, FAILED, REFUNDED |
| `ShipmentStatus` | PENDING, SCHEDULED, IN_TRANSIT, DELIVERED |
| `Carrier` | FASTLINE, AIRCO (an unknown carrier fails at unmarshal; witness test) |

Order ids are text, for example `O-1001`. Amounts are `BigDecimal` with 2 decimals (HALF_UP): 19.999 becomes 20.00.

## Event catalog

Topic convention: `edp.<domain>.<event>`. Names use dots and hyphens only, never underscores.

| Event | Producer | Consumers | Topic |
|---|---|---|---|
| OrderPlaced | order | inventory, notification | `edp.orders.placed` |
| OrderCancelled | order | inventory, notification | `edp.orders.cancelled` |
| InventoryReserved | inventory | payment, notification | `edp.inventory.reserved` |
| InventoryInsufficient | inventory | order, notification | `edp.inventory.insufficient` |
| PaymentProcessed | payment | shipping, order, notification | `edp.payments.processed` |
| PaymentFailed | payment | inventory, order, notification | `edp.payments.failed` |
| ShipmentScheduled | shipping | order, notification | `edp.shipping.scheduled` |
| ShipmentInTransit | shipping | notification | `edp.shipping.in-transit` |
| ShipmentDelivered | shipping | order, notification | `edp.shipping.delivered` |

Chapter 04 adds one ordered stream topic, `edp.shipping.shipments`, keyed by order id (3 partitions).

Chapter 09 adds two more non-catalog topics: `edp.inventory.item-changes` (raw Debezium change envelopes of the legacy `inventory_item` table) and `edp.inventory.items` (compacted `InventoryItem` state documents keyed by sku). Neither is in `Topics.ALL`.

Chapter 13 adds `edp.orders.placed-dlq`, not in `Topics.ALL`.

Chapters 19 to 21 add seven topics, none in `Topics.ALL`: `edp.shipping.carriers` (compacted carrier reference records keyed by carrier, published by shipping-service), `edp.shipping.scheduled-enriched` and `edp.shipping.scheduled-enriched-camel`, `edp.shipping.order-readiness` and `edp.shipping.order-readiness-camel`, `edp.shipping.pickup-lists` and `edp.shipping.pickup-lists-camel`. The `-camel` twins exist only because both forms run side by side in chapters 19 to 21.

Kafka Streams internal topics (changelog, repartition) are named by the application id and are not in `Topics`.

`CarrierReference` (mode ROAD or AIR, tracking URL template on a `.example` host), ReadyToShip, PaymentOverdue and PickupList are payload records about existing entities.

Chapter 09 also writes helper topics that no service reads: `edp.inventory.signals` (events of the Debezium signal table), `edp.inventory.heartbeats` (events of the heartbeat table the heartbeat query writes), `edp.inventory.interval-only-signals` (the interval-only witness connector's signal table) and the heartbeat record topics, set with `topic.heartbeat.name`: `edp.inventory.connector-heartbeats` (main connector) and `edp.inventory.interval-only-heartbeats` (witness). "heartbeat" is not a domain, so no topic is named `edp.heartbeat.*`.

Chapter 10 reuses `edp.inventory.item-changes` and routes its signal table to `edp.inventory.signals`. It has no heartbeat.

Chapter 15 note: StockReceived and StockReserved are internal ledger events of the `InventoryItem` aggregate, messages about an existing entity and not new entities. They are named apart from the public catalog event InventoryReserved (`edp.inventory.reserved`) and reach no topic. No domain-model code change; the version stays 2.2.0.

## Lifecycles

- Order: PLACED to PAID to SHIPPED to DELIVERED, one step forward at a time. CANCELLED is allowed from PLACED or PAID only. DELIVERED and CANCELLED are terminal.
- Shipment: PENDING to SCHEDULED to IN_TRANSIT to DELIVERED.

## Carriers

FASTLINE (road) and AIRCO (air), both fictional.

## Failure paths

- Out of stock: InventoryInsufficient, the order is cancelled, the customer is told.
- Payment declined: PaymentFailed, stock is released, the order is cancelled, the customer is told.
- Late delivery: no ShipmentDelivered in time, escalate.
- The same event delivered twice: a consumer must not apply it twice.
- A malformed event: dead letter topic.
- A service down: its events wait in Kafka until it returns.

## Persistence

One `edp` database with a schema per service (`orders`, `inventory`, `payments`, `shipping`, `notifications`), created by each service's Flyway (`quarkus.flyway.schemas`). This states the production shape of one database each. The table for orders is `orders`, because `order` is reserved.

## Multi-service examples

Aggregator `pom.xml` at `examples/NN-slug/`, one module per service. Application ports 8080 and 8082; replicas are not published.

## Chapter remap

| Ch | Topic | Domain content |
|---|---|---|
| 00 | Prerequisites and the two stacks | `POST /orders` validates an `Order` and publishes to `edp.orders.placed` keyed by order id; an invalid order (quantity 0, negative amount) gets 400 and the topic does not grow |
| 01 | Events, commands, documents; CRUD vs event sourcing | one `Order` as an `orders` row updated in place vs `order_event` appended (OrderPlaced, OrderPaid, OrderShipped, OrderDelivered, OrderCancelled); replay equals row; illegal transition 409; UPDATE, DELETE and TRUNCATE refused |
| 02 | Event notification | OrderPlaced carries the order id only; notification-service fetches the `Order` from order-service over HTTP; source down means no notification until it returns |
| 03 | Event-carried state transfer, with Claim Check | OrderPlaced and OrderCancelled carry the full `Order`; notification-service keeps `order_copy` and answers with order-service stopped. Second section `## Pattern: Claim Check`: the large part of the payload is stored once in PostgreSQL and the event carries a reference; an oversized full-state record is rejected by the producer while the claim-check variant passes; decision table full state vs claim check vs notification |
| 04 | Ordering and partitioning, with Resequencer | shipment status events keyed by order id vs event type (reordered: the order is stuck at SHIPPED) vs carrier (ordered but skewed); the stall is shown and tested. Second section `## Pattern: Resequencer`: a per-order sequence number and Camel's Resequencer put events back in order at the consumer; the same paused-replica run ends at DELIVERED with it; latency and memory trade-off |
| 05 | Optimizing publishers | a flash sale of OrderPlaced bursts from order-service over three publish routes (unbatched control, Kafka defaults, tuned); batching, `linger.ms`, compression, `acks`, idempotence; throughput and latency measured |
| 06 | Optimizing subscribers | notification-service draining three catalog topics (`edp.orders.placed`, `edp.payments.processed`, `edp.shipping.scheduled`, 3 partitions each); fetch sizing, `max.poll.records`, concurrency, the new rebalance protocol; lag measured |
| 07 | Queues for Kafka | `Notification` delivery jobs taken from `edp.orders.placed` and shared by three workers through a share group (SmallRye Reactive Messaging) with per-record acknowledgement, against a Camel consumer group with head-of-line blocking; a slow email gateway |
| 08 | Transactional outbox | `Order` placed plus an outbox row (debezium-quarkus-outbox), then the Debezium Outbox Event Router, then `edp.orders.placed`, keyed by order id, with `id` and `eventType` headers |
| 09 | Log tailing with Debezium | the legacy `inventory.inventory_item` table (`item_sku`, `qty_on_hand`, `last_changed`) tailed by Debezium to `edp.inventory.item-changes`; a Camel route maps the envelopes to `InventoryItem` documents on `edp.inventory.items` |
| 10 | Managing connector offsets | stop, list, alter and reset the `inventory-cdc` connector offsets through Connect REST on compose (the Strimzi half arrives in r12) |
| 11 | Idempotent consumer and inbox | inventory-service reserves stock once per OrderPlaced event id (the `id` header); the inbox row commits in the same transaction as the decrement; a duplicate without the inbox double-decrements `quantityOnHand`; Camel's idempotent consumer with a repository is the alternative |
| 12 | Exactly-once with Kafka transactions | payment-service reads InventoryReserved and publishes PaymentProcessed (carrying the `Payment`) with its consumed offsets in one Kafka transaction; `read_committed` readers; no database write, because a Kafka transaction covers Kafka only (the database edges are chapters 08 and 11); a Camel transacted route is the comparison |
| 13 | Retry and dead letter | a malformed or invalid OrderPlaced goes to `edp.orders.placed-dlq`; a transient failure (the stock row locked past the lock timeout) is retried and succeeds; SmallRye failure strategy versus Camel deadLetterChannel |
| 14 | Aggregate root and the event store | the `Order` aggregate rebuilt from its own stream in an append-only `events` table; commands from REST and from PaymentProcessed and ShipmentScheduled are checked against the replayed state; optimistic concurrency on `(stream_id, version)` with retry by re-deciding or `If-Match` reported to the caller; OrderPlaced and OrderCancelled published through the chapter 08 outbox in the same transaction, OrderPaid and OrderShipped stay internal; a Camel route delivering PaymentProcessed as the comparison |
| 15 | Snapshots | an `InventoryItem` stock ledger (StockReceived, StockReserved) for one popular SKU with 100,000 events; a snapshot every N events in the command's transaction; rebuild cost measured with and without; a stale snapshot schema is discarded; a Camel scheduled snapshotter as the out-of-band comparison |
| 16 | CQRS and projections | an order-status read model per customer in PostgreSQL (`order_view`), projected by order-service's query side from OrderPlaced, OrderCancelled, PaymentProcessed, ShipmentScheduled, ShipmentInTransit and ShipmentDelivered on their catalog topics; each column set by its owner's event; read lag made visible (`asOf`, group lag) and asserted with a paused channel; out-of-order arrival across topics guarded by a forward-only upsert; a single Camel consumer over the six topics as the comparison |
| 17 | Aggregate replay or CQRS projection | shipments per carrier per day (FASTLINE vs AIRCO) answered by replaying every `Order` aggregate from an append-only `events` table and by a new projection rebuilt from position 0 of the same table, measured at 1,000, 10,000 and 100,000 orders; the projection's checkpoint advances only below the oldest open transaction; decision table |
| 18 | Schema evolution and upcasting | OrderPlaced v1 to v2 adds `shippingPriority` (STANDARD or EXPRESS, default STANDARD); stored v1 events upcast on read and never rewritten; the published event as Avro in Apicurio through the outbox, read by notification-service with the v2 schema; a BACKWARD_TRANSITIVE rule refuses a v3 that drops the default. STANDARD and EXPRESS are values of a payload field, not an entity; customer ids `C-16xx` in 16 |
| 19 | Enrich | ShipmentScheduled enriched with the carrier's mode and tracking URL by notification-service; shipping-service owns the carrier reference data (its table, its lookup endpoint and the compacted `edp.shipping.carriers` topic); a Camel Content Enricher looks it up per record, a Kafka Streams left join against a GlobalKTable keeps a local copy; a reference change and an owner outage show the difference; decision table |
| 20 | Join | shipping-service releases an order when InventoryReserved and PaymentProcessed have both arrived: a Kafka Streams left join by event time within 30 minutes plus grace, and a Camel Aggregator correlated by order id with a completion timeout by arrival time, its state in PostgreSQL; an unmatched reservation becomes PaymentOverdue; two crafted pairs where the forms disagree; state kept across a restart; decision table |
| 21 | Aggregate (stream aggregation) | ShipmentScheduled grouped per carrier into pickup lists from one input: Kafka Streams tumbling 15-minute event-time windows with grace and suppression (one final list and count per carrier per slot, late records dropped and counted) and a Camel Aggregator batching by size and completion timeout; decision table |
| 22 | Saga: the pattern | the order-to-delivery flow choreographed over Kafka; PaymentFailed compensates (release stock, cancel order); a timeout; saga state in PostgreSQL |
| 23 | Orchestrated sagas | the same order flow via the Camel Saga EIP (LRA) and Quarkus Flow; decision table against chapter 22 |
| 24 | Decisions on events: Drools, LangChain4j | a free-text delivery-exception note from a carrier on a `Shipment`; LangChain4j extracts the reason, Drools decides RESHIP or REFUND (settled at r09) |
| 25 | Tracing across async hops | one `Order` traced from `POST /orders` through the outbox, Debezium, Kafka and the inventory consumer |
| 26 | Metrics for event flows | consumer lag, event age, outbox backlog and DLQ rate for order-flow traffic; dashboard and alert |
| 27 | Correlated logs and traces | the chapter 22 order saga followed by order id across services |
| 28 | Strimzi and KRaft | the chapter 02 to 04 apps redeployed unchanged |
| 29 | Unidirectional Topic Operator | `KafkaTopic` for every `edp.*` topic; drift and deletion |
| 30 | Server-side apply | Strimzi and app manifests |
| 31 | Security context and pod security | Strimzi, CloudNativePG and app pods |
| 32 | Monitoring Strimzi | broker, controller and Connect metrics beside the order-flow dashboards |
| 33 | Cruise Control | `KafkaRebalance` over the `edp.*` topics |
| 34 | MirrorMaker 2 | `edp.shipping.*` topics replicated; notification-service consumer failover |
| 35 | Debezium and Connect on Strimzi | the order outbox and inventory CDC connectors as `KafkaConnect` and `KafkaConnector` against a CNPG `Cluster` |
| A | Testing event-driven code | the chapters' own examples, tested: Dev Services against real Kafka and PostgreSQL (pinned images, partition counts, shared containers); proving a check bites (break the guard, confirm the check fails, restore); asserting on topics by key, partition and offset, draining for the full wait instead of stopping at the first match; deterministic ordering tests (pause a replica, assert the precondition, compute placement with Kafka's murmur2); concurrency tests (two threads released together, repeated) and their limit (a probabilistic catch); Camel route tests (camel-quarkus-junit5, AdviceWith, an HTTP dependency stubbed with a test resource); event contract tests (schema compatibility in Apicurio, after ch 18); end-to-end verify.sh checks and trace, metric and log assertions in LGTM |
| B | Compose to Kubernetes | Kafka and PostgreSQL configuration |
| C | Querying streams without ksqlDB | ShipmentDelivered counts per carrier per window, three ways |
| D | Pattern map, glossary, EIP Camel cross-reference | same domain as EIP Camel, so the cross-reference is by event |
| E | Resequencing across partitions, in depth | shipment events from several partitions, producers and topics put back in per-order order: per-order buffer vs Camel Resequencer (batch and stream mode, global sequence, funnel topic), gap timeouts, lost events, restart, memory bounds |
| F | Deploying event-driven services | on minikube: full-stop deployment, rolling update of a consumer group (rebalances, the cooperative protocol, in-flight records), a breaking schema change (new topic and a migration consumer, or a compatible v2 first), blue-green for consumers (offsets, a second group, cut-over and rollback); order-service and notification-service redeployed under load with nothing lost or applied twice (Bellemare ch 21). Figures: several per section, one per stage of each deployment pattern (before, during, after; for blue-green also cut-over and rollback), so every pattern can be followed from the figures alone |
| G | Write-forward cache with an outbox and CDC (added 2026-11-11, owner) | order-service writes the order and an outbox row in one PostgreSQL transaction; a Redis cache on the read side (same image as the sibling books, re-surveyed at r13) is kept current from the outbox and change events, not by the service's own dual write; what the cache may show stale, how it is rebuilt after a loss, and the dual write it avoids (Kafka transactions stop at the cluster, chapter 12) |
| H | OpenShift Local: outbox on CRC | the chapter 08 `Order` outbox |
