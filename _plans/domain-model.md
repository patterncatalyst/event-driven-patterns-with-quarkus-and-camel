---
layout: plan
title: "Domain model"
render_with_liquid: false
---

# Shipping domain model

Mirror of the series standard (Notion 40561a5f-8a3b-812b-a811-ca7ce7aead93, "Shipping Domain Model & Per-Chapter Examples"), as the PRD fixes it for this book. The Notion page is the source; this file only mirrors the part this book uses.

## Rules

- Never invent a new entity or a new area. If a pattern seems not to fit, the chapter's framing is wrong, not the domain.
- Entity names are fixed (the 15 below). Reuse across chapters is the point.
- Carriers are FASTLINE and AIRCO.
- No example introduces a UI. Each example lives at `examples/NN-slug/` with a README and `verify.sh`.
- Shared records live once, in `examples/domain-model/`, package `com.patterncatalyst.shipping`.

## Areas and entities

| Area | Entities |
|---|---|
| Consignment | `Consignment` |
| Carrier | `Carrier` |
| Rating | `RateQuote`, `Tariff` |
| Routing | `Sailing`, `Vessel`, `PortCall`, `SlotId` |
| Tracking | `HandlingEvent`, `TrackingHistory`, `ConsignmentStatus` |
| Documentation | `CustomsDeclaration`, `Manifest` |
| Billing | `Invoice`, `Settlement` |

Seven areas, 15 entities. Message names used in examples (for example `ConsignmentBooked`, `CarrierChanged`) are events about these entities, not new entities.

## Per-chapter allocation (PRD section 5)

| Ch | Topic | Entities used |
|---|---|---|
| 00 | Prerequisites and the two stacks | `Consignment` (smoke test, invalid rejected) |
| 01 | Events, commands, documents; CRUD vs event sourcing | `Consignment`, `ConsignmentStatus` (history served as a `StatusHistory` read view, not `TrackingHistory`, which is built from `HandlingEvent`s) |
| 02 | Event notification | `Consignment`, `Invoice` (Billing fetches the `Consignment`) |
| 03 | Event-carried state transfer | `Tariff`, `RateQuote` |
| 04 | Ordering and partitioning | `HandlingEvent`, `Consignment`, `PortCall`, `TrackingHistory` |
| 05 | Optimizing publishers | `HandlingEvent` |
| 06 | Optimizing subscribers | `HandlingEvent` |
| 07 | Queues for Kafka (share groups) | `CustomsDeclaration` |
| 08 | Transactional outbox | `Invoice` |
| 09 | Log tailing with Debezium | `Carrier` |
| 10 | Managing connector offsets | `Carrier` |
| 11 | Idempotent consumer and inbox | `Settlement` |
| 12 | Exactly-once with Kafka transactions | `RateQuote`, `Invoice` |
| 13 | Retry and dead letter | `CustomsDeclaration` |
| 14 | Aggregate root and the event store | `Consignment` |
| 15 | Snapshots | `TrackingHistory` |
| 16 | CQRS and projections | `ConsignmentStatus`, `TrackingHistory` |
| 17 | Aggregate replay or CQRS projection | `Manifest`, `Vessel`, `HandlingEvent` |
| 18 | Schema evolution and upcasting | `HandlingEvent`, `PortCall` |
| 19 | Enrich | `HandlingEvent`, `Vessel` |
| 20 | Join | `HandlingEvent`, `CustomsDeclaration` |
| 21 | Aggregate (stream aggregation) | `HandlingEvent`, `PortCall`, `Manifest` |
| 22 | Saga: the pattern | `RateQuote`, `SlotId`, `Sailing`, `Invoice` |
| 23 | Orchestrated sagas | `RateQuote`, `SlotId`, `Sailing`, `Invoice` |
| 24 | Decisions on events: Drools, LangChain4j | `CustomsDeclaration` |
| 25 | Tracing across async hops | `Invoice` |
| 26 | Metrics for event flows | `HandlingEvent` |
| 27 | Correlated logs and traces | the chapter 22 booking (`RateQuote`, `SlotId`, `Sailing`, `Invoice`) |
| 28 | Strimzi and KRaft | the chapter 02 to 04 apps unchanged |
| 29 | Unidirectional Topic Operator | every shipping topic |
| 30 | Server-side apply | Strimzi and app manifests |
| 31 | Security context and pod security | Strimzi, CloudNativePG and app pods |
| 32 | Monitoring Strimzi | broker, controller, Connect metrics beside flow dashboards |
| 33 | Cruise Control | `KafkaRebalance` over the shipping topics |
| 34 | MirrorMaker 2 | `TrackingHistory` topics |
| 35 | Debezium and Connect on Strimzi | `Invoice` outbox, `Carrier` |
| A | Testing event-driven code | entities from the chapters tested |
| B | Compose to Kubernetes | Kafka and PostgreSQL configuration |
| C | Querying streams without ksqlDB | `HandlingEvent`, `PortCall` |
| D | Pattern map, glossary, EIP Camel cross-reference | all |
| E | OpenShift Local: outbox on CRC | `Invoice` (chapter 08) |
