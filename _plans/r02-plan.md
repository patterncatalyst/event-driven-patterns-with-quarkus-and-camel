---
layout: plan
title: r02 plan
render_with_liquid: false
---

# r02 plan: event-driven-patterns-with-quarkus-and-camel (Opus planner, 2026-11-10)

I read: CLAUDE.md, every `_plans` file, bibliography.md, scripts/validate.py (the domain check, the word counter, the examples checks), the CI `examples.yml` matrix, `_infra`, `domain-model`, both r01 examples (all source, tests, verify.sh), both chapters, the example pages, the diagram specs, the PRD in Notion, the EIP Camel running-example chapter and its 04/07 examples, and the hub `books.yml`.

Facts that shaped the plan:
- Chapter 00 has 1,252 prose words and chapter 01 has 1,555 (the validate.py counter).
- The CI matrix only builds `examples/NN-*` directories that have a top-level `pom.xml`.
- The Camel Kafka consumer has no static partition `assign` option. It does have `groupInstanceId`, `seekTo` and `partitionKey`.
- The EIP repo itself names FedEx, UPS and DHL in prose and examples (52 hits). That is outside our scope, but it is why "copy facts, not paragraphs" matters here.
- The tag r01.0 exists, but `_plans/iteration-plan.md` still shows r01 as "in progress" with step 13 pending. Fix this in r02.

## Approach

Rework first, then build. Three gates:
- **Phase 1:** domain decisions, the `domain-model` module and a new validate.py domain-name check.
- **Phase 2:** chapters 00 and 01, with their examples, pages and figures, moved onto the order-to-delivery domain. All r01 guarantees and house fixes stay. Gate G1 is an orchestrator serial verify from a clean Docker state, plus every static gate.
- **Phase 3:** chapters 02 to 04 in parallel, with disjoint files. Then the final serial verify from clean, claims and log, Opus validation, and ship.

Multi-service examples use a Maven aggregator `pom.xml` at `examples/NN-slug/`, with one module per service. This keeps the CI matrix and validate.py working unchanged.

Rejected alternatives:
- Keep the freight model beside the new one: two domains break the "fixed domain" rule.
- Rename the Java package: `com.patterncatalyst.shipping` still fits "shipping domain", and renaming would churn every import for nothing.
- Build chapters 02 to 04 before the rework passes: they would build on a moving base.
- Copy EIP's running-example paragraphs: the owner forbids it, and the book must say it in its own words.
- Separate example directories per service (`02a`, `02b`): this breaks "one example per chapter".

## Domain

This is what goes into `_plans/domain-model.md` and the chapter 00 section "The running example".

**The business.** A customer orders an item online. The order is recorded, stock is reserved, the payment is taken, a carrier is booked and the parcel is delivered. The customer is told at each step. Five services own one step each. They share no tables and talk only through events on Kafka, with one exception: event notification's fetch-back, shown in chapter 02.

**Services**

| Service | Owns | Reacts to | Emits |
|---|---|---|---|
| order-service | `Order`, its lifecycle | InventoryInsufficient, PaymentProcessed, PaymentFailed, ShipmentScheduled, ShipmentDelivered | OrderPlaced, OrderCancelled |
| inventory-service | `InventoryItem` stock | OrderPlaced, OrderCancelled, PaymentFailed | InventoryReserved, InventoryInsufficient |
| payment-service | `Payment` | InventoryReserved | PaymentProcessed, PaymentFailed |
| shipping-service | `Shipment`, carrier choice | PaymentProcessed | ShipmentScheduled, ShipmentInTransit, ShipmentDelivered |
| notification-service | `Notification` | every event | none |

**Records** (`examples/domain-model`, package `com.patterncatalyst.shipping`, version bumped to **2.0.0**). Plain Java 25, compact-constructor validation through `Checks`, and a JUnit negative witness per rule:
- `Order(String id, String customerId, String itemSku, int quantity, BigDecimal amount)`: text fields non-blank, quantity > 0, amount > 0.
- `OrderStatus { PLACED, PAID, SHIPPED, DELIVERED, CANCELLED }`.
- `InventoryItem(String sku, int quantityOnHand)`: quantity >= 0.
- `Payment(String id, String orderId, BigDecimal amount, PaymentStatus status)`: amount > 0.
- `PaymentStatus { PENDING, COMPLETED, FAILED, REFUNDED }`.
- `Shipment(String id, String orderId, Carrier carrier, String trackingNumber, ShipmentStatus status)`: carrier non-null.
- `ShipmentStatus { PENDING, SCHEDULED, IN_TRANSIT, DELIVERED }`.
- `Carrier { FASTLINE, AIRCO }` becomes an enum, so an unknown carrier fails at unmarshal. That is a witness test.
- `Notification(String id, String orderId, String customerId, String eventType)`.
- `Topics`: string constants for the catalog topics, so no example hard-codes a typo.

Event payload records are not in domain-model. They live in each example (recommended, owner decision 2), because payload shape is the pattern decision in chapters 02, 03 and 18: id-only versus full state versus v2. Domain-model fixes the entities, the event names and the topics.

Rule carried over from r01: event names used in examples are messages about these entities, never new entities.

**Event catalog** (prefix `edp.`)

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

- ShipmentInTransit is added because EIP has the IN_TRANSIT shipment status (owner decision 4).
- Topic names use dots and hyphens only, never underscores.
- Chapter 04 adds one ordered stream topic, `edp.shipping.shipments`, keyed by order id, as its lesson (owner decision 3).

**Lifecycles**
- Order: PLACED to PAID to SHIPPED to DELIVERED, one step forward at a time. CANCELLED is allowed from PLACED or PAID only (owner decision 5). DELIVERED and CANCELLED are terminal.
- Shipment: PENDING to SCHEDULED to IN_TRANSIT to DELIVERED.
- Order ids are text, for example `O-1001`, not EIP's SERIAL integer (owner decision 7).

**Carriers.** FASTLINE (road) and AIRCO (air), both fictional. Never FedEx, UPS or DHL.

**Failure paths** (one short list in chapter 00, no chapter pointers):
- Out of stock: InventoryInsufficient, the order is cancelled, the customer is told.
- Payment declined: PaymentFailed, stock is released, the order is cancelled, the customer is told.
- Late delivery: no ShipmentDelivered in time, escalate.
- The same event delivered twice: a consumer must not apply it twice.
- A service down: its events wait in Kafka until it returns.

**Persistence.** One `edp` database with a schema per service (`orders`, `inventory`, `payments`, `shipping`, `notifications`), created by each service's Flyway (`quarkus.flyway.schemas`). This states the production shape (one database each). The table for orders is `orders`, because `order` is reserved.

## Design decisions

- **Service split:** separate Quarkus apps in one example directory, under an aggregator `pom.xml` (modules inherit the BOMs from it). Each module has its own Containerfile; the build context stays `examples/`, and the Containerfile copies the parent pom. Reason: the pattern has to be visible as two processes exchanging events, and one compose `stop` must be able to take one side down. The aggregator keeps CI and validate.py working unchanged.
- **Chapter 02 fetch-back:**
  - Mechanism: the Camel `http` component (`camel-quarkus-http`) via `toD("{{order-service.url}}/orders/${header.orderId}")`, with compose env `ORDER_SERVICE_URL`. Reason: the hop stays in the route, and opentelemetry2 propagates `traceparent`, so one trace spans both services in Tempo.
  - Consumer settings: the consumer route sets `breakOnFirstError=true` plus redelivery, so a failed fetch is re-polled, not committed past.
  - Negative witness (deterministic, in verify.sh): stop notification-service, place order B, stop order-service, start notification-service. There is no notification for B, and its record is not committed. Start order-service and B's notification appears.
  - Experiment: removing `breakOnFirstError` makes B's notification never appear.
  - Freshness check: cancel C before the consumer starts, and its notification shows CANCELLED, because fetch-back reads current state.
  - Commit race: order-service publishes after commit (`@Observes(during = AFTER_SUCCESS)`) so the fetch never races the transaction.
  - Tests: a test-scoped stub resource on the Quarkus test port stands in for order-service. No WireMock.
- **Chapter 03 local copy:**
  - Design: order-service publishes OrderPlaced and OrderCancelled carrying the full Order plus status. notification-service upserts `notifications.order_copy` with Panache, one route per topic, and serves `GET /order-copies/{id}`.
  - Witness: with order-service stopped, the copy answers 200 with every field, and a cancelled order shows CANCELLED. order-service records zero `GET /orders/{id}` requests (Prometheus `http_server_requests` for that route has no series or 0), and no HTTP client span leaves notification-service.
  - Variant: with `ORDER_EVENTS_CARRY_STATE=false` (an experiment, recreate order-service), the thin event fails validation, is logged and skipped, and the copy answers 404. The notification-only design cannot answer without its source.
  - Tests: they need no order-service at all.
- **Chapter 04 out-of-order demonstration (not timing-dependent):**
  - Setup: shipping-service publishes ShipmentScheduled, ShipmentInTransit and ShipmentDelivered to `edp.shipping.shipments` (3 partitions). The key strategy is set by `SHIPPING_PARTITION_KEY` = `order-id` (default), `carrier` or `event-type`.
  - Consumers: two order-service replicas in one group update `orders.order_status` (last write wins) and `order_status_history` (arrival sequence). With 3 partitions and 2 members, the range assignor always puts p0 and p2 on different members.
  - Stall: verify.sh finds the owner of the Scheduled record's partition with `kafka-consumer-groups.sh --describe` (`clientId` = container hostname) and `docker compose pause`s it. This stays well under the session timeout, so no rebalance happens. It then publishes the three events, waits for the unpaused replica, and unpauses.
  - Assertion: with the order-id key the final status is DELIVERED and the history is SCHEDULED, IN_TRANSIT, DELIVERED. With the event-type key the final status is SHIPPED and the history starts with DELIVERED; this is the negative witness, run in verify.sh by recreating shipping-service with that env. The script asserts its precondition (Scheduled and Delivered partitions owned by different replicas) and fails loudly if it does not hold.
  - Partition placement: my preliminary murmur2 port gives `ShipmentScheduled` partition 2, `ShipmentDelivered` and `ShipmentInTransit` partition 0, `FASTLINE` 0 and `AIRCO` 2 (mod 3). The executor must confirm this with Kafka's `Utils.murmur2` in a test.
  - Tests (Dev Services, `quarkus.kafka.devservices.topic-partitions."edp.shipping.shipments"=3`), read from record metadata:
    - order-id key: one order's events sit in one partition, in increasing offsets;
    - event-type key: one order spans at least two partitions;
    - carrier key: any number of orders use at most two of three partitions. The carrier key keeps per-order order but caps parallelism and skews load, and the chapter shows that too.
- **JSON vs Avro:** JSON with Camel Jackson now. Reason: Apicurio and Avro are the lesson in chapter 18, and EIP made the same call.
- **Camel vs SmallRye:** Camel everywhere in r02 (`ProducerTemplate` into a `direct:` route, Kafka consumer routes, one route per RouteBuilder). Reason: the book is Quarkus + Camel. SmallRye Reactive Messaging appears only where the PRD puts it (chapters 11 and 13 failure strategies, compared in a table).
- **Topic prefix:** `edp.<domain>.<event>`. Reason: it matches the `edp-` container, network and compose-project prefix already in `_infra`. It cannot collide with EIP's `eip.` on a shared broker, and it keeps EIP's convention so readers of both books transfer directly.
- **Ports:** first app on 8080, second on 8082, replicas unpublished. This avoids 8081, 8083, 9092, 5432, 19000, 3000, 3100, 3200, 4317, 4318 and 19090.

## Chapter remap (for the PRD and `_plans/domain-model.md`)

- 00: compose stack; `POST /orders` validates an `Order` and publishes to `edp.orders.placed` keyed by order id; an invalid order (quantity 0, negative amount) gets 400 and the topic does not grow.
- 01: one `Order` as an `orders` row updated in place vs `order_event` appended (OrderPlaced, OrderPaid, OrderShipped, OrderDelivered, OrderCancelled as typed payloads); replay equals row; history only from events; illegal transition 409; UPDATE, DELETE and TRUNCATE refused; duplicate 409; amount rounded to 2 decimals (19.999 becomes 20.00) and replay equals row.
- 02 Event notification: OrderPlaced (order id only) to notification-service, which fetches the `Order` from order-service over HTTP; source down means no notification until it returns.
- 03 Event-carried state transfer: OrderPlaced and OrderCancelled carry the full `Order`; notification-service keeps `order_copy` and answers with order-service stopped.
- 04 Ordering and partitioning: Shipment status events keyed by order id vs by event type (reordered: the order is stuck at SHIPPED) vs by carrier (ordered but skewed); the stall is shown and tested.
- 05 Optimizing publishers: OrderPlaced bursts (a flash sale) from order-service; batching, `linger.ms`, compression, `acks`, idempotence; throughput and latency measured.
- 06 Optimizing subscribers: notification-service consuming every catalog topic; fetch sizing, `max.poll.records`, concurrency, the new rebalance protocol; lag measured.
- 07 Queues for Kafka: `Notification` delivery jobs shared across workers with per-record acknowledgement (a slow email gateway) vs a consumer group's head-of-line blocking.
- 08 Transactional outbox: `Order` placed plus an outbox row, then the Debezium Outbox Event Router, then `edp.orders.placed`.
- 09 Log tailing with Debezium: a legacy `inventory_item` stock table tailed by Debezium (slots, publications, snapshots, signals, heartbeats, SMTs, tombstones); a Camel route maps the envelope to an `InventoryItem` state document.
- 10 Managing connector offsets: stop, list, alter and reset the inventory connector's offsets (Connect REST on compose, `KafkaConnector` on Strimzi).
- 11 Idempotent consumer and inbox: inventory-service reserves stock once per OrderPlaced event id; a duplicate without the inbox double-decrements `quantityOnHand`.
- 12 Exactly-once: payment-service reads InventoryReserved, writes `Payment` and publishes PaymentProcessed in one Kafka transaction; `read_committed`; a crash mid-batch shows no duplicate PaymentProcessed.
- 13 Retry and dead letter: a malformed OrderPlaced goes to a DLQ topic; a transient payment-gateway failure is redelivered.
- 14 Aggregate root and event store: the `Order` aggregate (place, pay, ship, deliver, cancel) rebuilt from its stream; commands checked against state; optimistic concurrency on append.
- 15 Snapshots: an `InventoryItem` stock ledger (thousands of reservations and releases for one popular SKU); snapshot every N events; rebuild cost measured.
- 16 CQRS and projections: an order-status read model per customer in PostgreSQL, projected from order and shipment events; read lag made visible and asserted.
- 17 Replay vs projection: "shipments per carrier per day" (FASTLINE vs AIRCO) answered by aggregate replay and by a new projection rebuilt from history; decision table.
- 18 Schema evolution: OrderPlaced v1 to v2 (adds a `shippingPriority` field with a default); Apicurio compatibility rule; upcaster on read.
- 19 Enrich: ShipmentScheduled enriched with carrier reference data (FASTLINE, AIRCO service details); Camel Content Enricher vs a Kafka Streams stream-table join.
- 20 Join: InventoryReserved joined with PaymentProcessed per order within a window ("ready to ship"); Kafka Streams windowed join vs a Camel correlated Aggregator with completion timeout.
- 21 Aggregate: ShipmentDelivered counts per carrier per window (Kafka Streams); a Camel Aggregator batches ShipmentScheduled into a per-carrier pickup list.
- 22 Saga, the pattern: the order-to-delivery flow choreographed over Kafka; PaymentFailed compensates (release stock, cancel order); a timeout; saga state in PostgreSQL.
- 23 Orchestrated sagas: the same order flow via the Camel Saga EIP (LRA) and Quarkus Flow; decision table against chapter 22.
- 24 Drools and LangChain4j: a free-text delivery-exception note from a carrier on a `Shipment`; LangChain4j extracts the reason, Drools decides RESHIP or REFUND, both recorded as events (owner decision 11, settle at r09).
- 25 Tracing across async hops: one `Order` traced from `POST /orders` through the outbox, Debezium, Kafka and the inventory consumer.
- 26 Metrics for event flows: consumer lag, event age, outbox backlog and DLQ rate for order-flow traffic; dashboard and alert.
- 27 Correlated logs and traces: the chapter 22 order saga followed by order id across services.
- 28 Strimzi and KRaft: the chapter 02 to 04 apps redeployed unchanged.
- 29 Unidirectional Topic Operator: `KafkaTopic` for every `edp.*` topic; drift and deletion.
- 30 Server-side apply: Strimzi and app manifests (unchanged scope).
- 31 Security context: Strimzi, CloudNativePG and app pods (unchanged scope).
- 32 Monitoring Strimzi: broker, controller and Connect metrics beside the order-flow dashboards.
- 33 Cruise Control: `KafkaRebalance` over the `edp.*` topics.
- 34 MirrorMaker 2: `edp.shipping.*` topics replicated; notification-service consumer failover.
- 35 Debezium on Strimzi: the order outbox and inventory CDC connectors as `KafkaConnect` and `KafkaConnector` against a CNPG `Cluster`.
- A Testing: the entities from the chapters tested (unchanged).
- B Compose to Kubernetes: Kafka and PostgreSQL configuration (unchanged).
- C Querying streams without ksqlDB: ShipmentDelivered counts per carrier per window, three ways.
- D: pattern map, glossary, EIP cross-reference; now on the same domain as EIP, so the cross-reference is by event.
- E OpenShift Local: the chapter 08 `Order` outbox on CRC.

## Steps

**0. Preflight** (orchestrator). Confirm the branch is `release/r02` and Docker is clean (no `edp-` containers or volumes). Mark r01 done in `iteration-plan.md`. Re-check Maven's HOLD as `versions.md` asks: quarkusio/quarkus #57216. Keep 3.9.16 unless it is fixed on the 3.40 LTS line; record the result in versions.md only.

**Group 1** (parallel, disjoint files):
- **1A Docs** (lgtm-tutorial): rewrite `_plans/domain-model.md` with the Domain section above, the remap table and the rules. Update CLAUDE.md's "Fixed domain" rule, the Structure line for `examples/domain-model` (order-to-delivery records), the topic convention, and a 2026-11-10 r02 decisions entry. Add the r02 detail table to `iteration-plan.md`. Write `_plans/r02-plan.md`.
- **1B `examples/domain-model`** (lgtm-quarkus conventions, plain Java): delete the 15 freight records. Add the records, enums and `Topics` above, bump to 2.0.0, rewrite `DomainModelTest` with one witness per validation rule (including an unknown carrier string failing Jackson-free `Carrier.valueOf`), and rewrite the README. `mvn -B verify` then `install`.
- **1C `scripts/validate.py`**: add `check_domain_names`, a case-sensitive banned-identifier list over the reader-facing globs plus `examples/**` and `diagrams/*`, excluding `_plans/r01-plan.md` and `verification-log.md`. Add a planted defect to `--self-test`. Do not run the full tree check yet.
- **1D Sources** (lgtm-tutorial):
  - `bibliography.md`: add an ISBN column; use the hub titles, URLs, ISBNs and years. Bellemare's URL becomes `https://www.oreilly.com/library/view/building-event-driven-microservices/9798341622180/`, with ISBN 979-8-3416-2218-0 as the hub URL implies. Hohpe and Woolf gets 978-0-321-20068-6. Lawrence gets 978-0-13-799861-6 and the InformIT URL. Note the Vernon series in the Focus column.
  - `sources.md`: confirm, through the O'Reilly MCP search, that Bellemare treats events, data contracts and partitioning. Record each as confirmed or "owner to verify". A topic that is not confirmed is not cited in a chapter.

**Gate**: orchestrator installs domain-model 2.0.0.

**Group 2** (parallel; depends on 1B; container starts and Dev Services runs under the flock lock):
- **2E Chapter 00** (lgtm-camel, lgtm-quarkus, lgtm-diagram-generator, lgtm-professional-voice):
  - Example: `examples/00-stack-check` moves to `OrderIntakeRoute` and `OrderLogRoute`, `POST /orders` and `edp.orders.placed`, Loki text "order received". verify.sh keeps 17 checks with an invalid Order as the witness. Update the test and README.
  - Chapter: `_docs/00` gets "## The running example" after "One stack at a time" and before "Where the signals land", about 650 to 750 words: the business, the services table, the catalog table, the lifecycles, the carriers, the failure list.
  - Figures: new `00-running-example` (five services, topics) and `00-order-lifecycle`; regenerate `00-compose-stack`.
  - Also: `_example_pages/00`, and the domain lines in `README.md` and `index.html`.
  - Re-run both r01 experiments (remove `onException`; rename the topic).
- **2F Chapter 01**:
  - Example: `examples/01-crud-vs-events` moves to `OrderRow` and `OrderEvent` (`@Immutable`), the `OrderEventPayload` sealed records (OrderPlaced, OrderPaid, OrderShipped, OrderDelivered, OrderCancelled with reason), `ChangeOrderStatus`, `Lifecycle` with the cancel rule, `OrderDocument`, `StatusHistory`, `OrderService` and `OrderResource`. Flyway V1 (`orders`, `order_event`, unique `(stream_id, version)`, triggers) and V2 (TRUNCATE).
  - Tests: all nine r01 tests ported, plus cancel-from-PAID allowed and cancel-after-SHIPPED 409.
  - verify.sh: 12 or more checks (PAID, SHIPPED, DELIVERED; illegal DELIVERED to PAID).
  - Chapter: `_docs/01` keeps its spine and "How Quarkus models it". `_example_pages/01`; regenerate the two `01-*` specs.
  - Experiments: re-run all five.
  - Each executor returns its claim rows and run records to the orchestrator and does not edit `claims.md` or `verification-log.md`.

**Gate G1** (orchestrator, serial from clean, under the lock): domain-model, then 00 (`mvn -B verify` and `verify.sh`), then 01. Then validate.py (including the domain check and self-test), voice-scan, idiomatic-scan, host_ids, site-build, and the grep list below. Merge the claims for 00 and 01 (rewrite the rows; mark rows for removed prose as cut) and the log entries. Commit `refactor(r02.x): order-to-delivery domain for chapters 00 and 01`. No new chapter starts before this passes.

**Group 3** (parallel; depends on G1; each owns `examples/NN-*`, `_docs/NN-*`, `_example_pages/NN-*` and `diagrams/specs/NN-*`):
- **3H Chapter 02** `02-event-notification`: order-service (REST, Panache, Camel producer route) and notification-service (Camel Kafka, `http` and Panache). Figures `02-fetch-back`, `02-source-down`. Citations: Fowler 2017, Hohpe Event Message, Lawrence ch 2, Bellemare events if confirmed.
- **3I Chapter 03** `03-event-carried-state-transfer`: order-service and notification-service with `order_copy`. Figures `03-local-copy` and `03-notification-vs-state-transfer`. Citations: Fowler 2017, Lawrence ch 9, Bellemare data contracts if confirmed.
- **3J Chapter 04** `04-ordering-and-partitioning`: shipping-service and two order-service replicas. Figures `04-keys-to-partitions` and `04-stalled-partition`. Citations: Kafka docs on partitions and ordering (fetched), Lawrence ch 2, Bellemare partitioning if confirmed.
- **Each chapter:**
  - `## Pattern:` layout, about 2,000 words (2,500 max), exactly one callout, no versions, no forward references.
  - Every example has an aggregator pom, a Containerfile per module (user 185, four fast-jar layers), compose with health checks on `/q/health/ready`, `CAMEL_COMPONENT_KAFKA_BROKERS`, `QUARKUS_*` env, and OTel to `lgtm:4318`.
  - Tests use Dev Services with the pinned images. verify.sh has the negative witness. Run every experiment and return the claim rows.

**4. Final serial verify** (orchestrator): from clean, `scripts/verify-all.sh` plus `mvn -B verify` for domain-model and examples 00 to 04, under the lock. Log the pass counts and experiments in `verification-log.md`. Merge claims for 02 to 04 (all verified or cut). Run every static gate. Commit per chapter: `docs(§02)` with `demo-02`, and so on.

**5. Opus validation** (lgtm-relay validator):
- validate, voice, idiomatic (Quarkus and Camel), host_ids, site build, and a curl of each new page.
- Claims audit: every prose claim maps to a row.
- Fresh-clone run with no domain-model preinstalled.
- Grep list.
- Originality check against EIP's `01-running-example.md`: no shared sentence of 10 or more consecutive words.
- Each example readable in one sitting; word counts.
- Repair rounds until clean, re-measured.

**6. Ship** (lgtm-github, orchestrator):
- PR `release/r02` to `main`; CI green (content-ok, examples-ok, Pages); squash-merge on the owner's OK; tag `r02.0`; notes-only release; delete the branch.
- Update the PRD in Notion: §4 domain, §5 tables with the remap, §13 "Domain" row (now EIP's domain), §10 r02 shipped.
- Mark r02 shipped in `iteration-plan.md`. Hub books.yml needs no change (all three books already list this repo in `referenced_by`).

## Acceptance criteria

- **Step 1:**
  - `_plans/domain-model.md` lists exactly the records, enums, catalog, lifecycles and remap above.
  - The domain-model tests pass with one witness per validation rule, and the version is 2.0.0.
  - The validate.py self-test catches a planted freight name.
  - The bibliography's three book rows match books.yml (URL, ISBN, year, title).
  - Bellemare topics are recorded as confirmed or not.
- **Chapter 00:**
  - verify.sh reaches N/N with N of 17 or more (witness: invalid Order gives 400 and the topic does not grow); `mvn -B verify` is green.
  - The running-example section sits after the stack section and has the five-service table, the catalog table with topics, both lifecycles, FASTLINE and AIRCO, the failure list and at least one figure.
  - The chapter has at most 2,500 prose words.
- **Chapter 01:**
  - verify.sh reaches N/N with N of 12 or more, with replay equal to row, history PLACED, PAID, SHIPPED, DELIVERED, illegal 409 appending nothing, and UPDATE, DELETE and TRUNCATE refused.
  - Tests include duplicate 409, amount 19.999 stored as 20.00 with replay equal to the row, a stale expected version giving 409, and an immutable entity.
  - All five experiments are re-run with their failing check numbers recorded.
- **Chapter 02:** verify.sh N/N. The OrderPlaced record carries only `orderId`. The notification holds the fetched customer and amount. One Tempo trace spans both services. The source-down witness holds: no notification while order-service is down, then it appears. The cancel-before-fetch notification shows CANCELLED. The experiment without `breakOnFirstError` is recorded.
- **Chapter 03:** verify.sh N/N. The copy answers with order-service stopped. The cancelled copy shows CANCELLED. order-service records zero `GET /orders/{id}` requests. The thin-event experiment gives 404 and is recorded.
- **Chapter 04:** verify.sh N/N. With the order-id key under a paused replica, the final status is DELIVERED in lifecycle order. With the event-type key, the final status is SHIPPED with DELIVERED first. The precondition is asserted. Tests assert the partition placement for all three key strategies, and the carrier key uses at most two of three partitions.
- **Every chapter 02 to 04:**
  - `## Pattern:` spine; one callout of the required shape; at most 2,500 words; at least one figure from a spec (two planned).
  - No versions, test names, self-reference, dashes or forward references.
  - Every claim row verified or cut.
  - Example README and page present; Containerfile, compose and env conventions as in CLAUDE.md.
- **Iteration:**
  - The command below returns 0 hits (excluding `_plans/r01-plan.md`, `_plans/verification-log.md` and `_plans/r02-plan.md`):

    `grep -rnE '\b(Consignment\w*|consignment\w*|HandlingEvent|TrackingHistory|Tariff|RateQuote|Invoice|Settlement|CustomsDeclaration|Vessel|Sailing|PortCall|SlotId|Manifest|BOOKED|LOADED|CLEARED|NLRTM|SGSIN|Rotterdam|Singapore|weightKg|weight_kg|freight|FedEx|UPS|DHL)\b' _docs _example_pages _parts index.html chapters.html examples.html diagrams.html README.md bibliography.md CLAUDE.md examples diagrams _plans/domain-model.md _plans/claims.md _plans/iteration-plan.md --exclude-dir=target`

    Lower-case "manifests", meaning Kubernetes manifests, is allowed.
  - validate.py, including the domain check, passes. Voice scan has 0 ban-tier hits; idiomatic scan has 0 high findings; host_ids is clean; the site builds; all three CI checks are green.
  - `verification-log.md` has the final clean serial run for domain-model and examples 00 to 04.
  - Tag `r02.0` and its release exist. The PRD and the iteration plan are updated.

## Risks (and detection)

- **A stale freight `domain-model:1.0.0` in `~/.m2` satisfies a build.** Bumping to 2.0.0 prevents it, and the fresh-clone validator run detects it.
- **Camel Kafka `breakOnFirstError` semantics on this Camel version:** a record could be skipped or looped. The chapter 02 test and the source-down check in verify.sh detect it.
- **OrderPlaced consumed before the order row commits:** the fetch gets 404. Publishing on AFTER_SUCCESS prevents it; the test asserts no 404 in the notification route logs.
- **The chapter 04 pause triggers a rebalance,** or assignment differs from expectation. Keep the pause short of the session timeout, describe the group before and after, and let the precondition check fail loudly.
- **The murmur2 placement differs from my port.** The executor computes it with Kafka `Utils` in a test; if Scheduled and Delivered collide, change the partition count. The test detects it.
- **Memory:** chapter 04 runs three apps plus Kafka, PostgreSQL and LGTM. Set app `mem_limit: 384m` and watch OOMKilled in the log.
- **The CI matrix skips multi-module examples:** the aggregator pom at the example root prevents it, and the CI matrix output confirms it.
- **Chapter 00 goes over budget:** check validate.py word counts at G1.
- **Running-example text too close to EIP:** the n-gram check in step 5.
- **Forward references in the chapter 00 failure list** ("Process Manager in Part 5"): validator review; the list names no chapter or part.
- **Bellemare topic not confirmed:** cite the book only where it is confirmed; claims audit.
- **The domain-name check gives false positives** (Kubernetes "manifests", "groups"): case-sensitive word boundaries plus a self-test.
- **Parallel Dev Services runs exhaust memory:** the flock lock covers `mvn verify` as well as compose.
- **Executors writing the shared `claims.md`:** forbidden; the orchestrator merges.

## Owner decisions needed (each with a recommendation)

1. **Topic prefix:** `edp.<domain>.<event>` (rec).
2. **Event payload records:** per example, with domain-model holding entities, enums, `Carrier` and `Topics` (rec), rather than shared event records. Payload shape is the lesson in chapters 02, 03 and 18.
3. **Catalog layout:** per-event topics as in EIP, with chapter 04 adding the ordered `edp.shipping.shipments` stream keyed by order id (rec). The alternative is entity-stream topics from chapter 00 on.
4. **Add ShipmentInTransit** on `edp.shipping.in-transit` (rec yes; EIP has the IN_TRANSIT status).
5. **CANCELLED only from PLACED or PAID** (rec), rather than EIP's "at any point".
6. **Chapter 04 wrong key = event type** (it reorders); the carrier key is shown as skew, not reordering (rec). The carrier key alone cannot reorder one order's events.
7. **Text order ids** (`O-1001`) rather than EIP's SERIAL integer (rec).
8. **domain-model version 2.0.0** (rec).
9. **Series domain page** (Notion 40561a5f): add a note there and in the PRD that this book now uses EIP's order-to-delivery domain; do not rewrite the series page (rec).
10. **Hub books.yml Bellemare `isbn: null`:** leave the hub alone this iteration (rec); our bibliography carries 979-8-3416-2218-0 from the O'Reilly URL.
11. **Chapter 24 free-text source:** a carrier delivery-exception note on `Shipment` (rec), settled at r09 planning.
12. **Maven:** stay on 3.9.16 unless #57216 is fixed on the 3.40 LTS line (rec).

### Critical Files for Implementation
- ~/Dev/event-driven-patterns-with-quarkus-and-camel/_plans/domain-model.md
- ~/Dev/event-driven-patterns-with-quarkus-and-camel/examples/domain-model/src/main/java/com/patterncatalyst/shipping/ (new Order, OrderStatus, InventoryItem, Payment, PaymentStatus, Shipment, ShipmentStatus, Carrier, Notification, Topics)
- ~/Dev/event-driven-patterns-with-quarkus-and-camel/examples/01-crud-vs-events/src/main/java/com/patterncatalyst/shipping/crudvsevents/ConsignmentService.java (becomes OrderService; the template for the guarantees)
- ~/Dev/event-driven-patterns-with-quarkus-and-camel/_docs/00-prerequisites-and-stacks.md
- ~/Dev/event-driven-patterns-with-quarkus-and-camel/scripts/validate.py
- ~/Dev/event-driven-patterns-with-quarkus-and-camel/.github/workflows/examples.yml (why each example needs an aggregator pom)

## Owner review 2026-11-10: approved with all recommendations, plus two additions

**Chapter 03 adds Claim Check as a secondary pattern.** Event-carried state transfer makes messages as large as the state they carry, and variable, growing payloads are a cost: broker and consumer limits (Kafka `message.max.bytes` / `max.request.size`, about 1 MB by default), memory, network, retention, and every consumer paying for fields it never reads. Second pattern section `## Pattern: Claim Check` (Hohpe and Woolf): the large part of the payload (for example an order's full line detail or a document attachment) is stored once and the event carries a reference; consumers that need it fetch it by claim id. Store: PostgreSQL (a `claim_check` table owned by the producer, read through a small endpoint, or a shared schema) on the CNPG image; no vendor object store. Camel's own Claim Check EIP is in-memory and per exchange, so it does not cross services: state that in the chapter and use it only if it fits. Negative witness: an oversized full-state record is rejected by the producer (RecordTooLargeException or the configured limit) while the claim-check variant passes; assert in a test and in verify.sh. Decision table: full state vs claim check vs notification.

**Chapter 04 adds order across partitions and reordering.** Kafka orders records only within a partition; there is no order across partitions. Second pattern section `## Pattern: Resequencer` (Hohpe and Woolf): events spread across partitions (the event-type key) are put back in per-order sequence at the consumer using a per-order sequence number carried in each event, with Camel's Resequencer EIP (stream mode on the sequence number, with a timeout/capacity bound) or an equivalent consumer-side buffer; show what the timeout costs (a gap holds later events; a lost event releases on timeout). Negative witness: the same paused-replica run that leaves the order at SHIPPED without the resequencer ends at DELIVERED with it. State the trade-off: keying by entity id is the first answer; resequencing is for when the key cannot change (several producers, several topics) and costs latency and memory. If chapter 04 then exceeds 2,500 prose words, report it to the owner before splitting.

## Owner review 2026-11-10 (second): chapter 04 uses a per-order buffer

Owner: "use the per-order buffer instead, keep it simple; tag the resequencer for an appendix in depth about resequencing when messages are out of order coming from multiple partitions". Chapter 04's second pattern stays Resequencer (Hohpe and Woolf) but is implemented as a small per-order buffer in order-service (events held in PostgreSQL until the next expected per-order sequence number arrives, then applied in order). No Camel resequence EIP, no global stream sequence, no funnel topic in chapter 04. The Camel Resequencer and the hard cases (gaps, timeouts, lost events, restarts, memory bounds, several producers and topics) move to a new Appendix E, "Resequencing across partitions, in depth"; the OpenShift Local outbox appendix becomes Appendix F and stays last.
