---
layout: plan
title: r09 plan
render_with_liquid: false
---

# r09 plan: chapters 22 to 24, saga, orchestrated sagas, decisions on events (Opus planner, 2026-11-13)

Status: **approved by the owner 2026-11-14** (all 24 recommendations; decision 1: an Apache-2.0 model chosen by S4; decision 2: one external model volume `edp-ollama-models`; merge when validation and CI pass). Branch `release/r09`, cut from main after r08.0; r08 housekeeping committed at 7f9568f ("r09 in progress").

Planner note: made with read-only commands only. Sources: the repo (CLAUDE.md, `_plans/*`, examples 08, 11, 13, 14, 16, 19 to 21, `_infra`, `scripts/validate.py`); the local Maven repository (quarkus-bom and quarkus-camel-bom 3.40.1.2, quarkus-langchain4j-bom 3.40.1, drools 10.2.0, quarkus-flow 1.1.3); Maven Central metadata; quay.io, Docker Hub, GitHub, Hugging Face and Crossref APIs; and web fetches of the Camel 4.22.x Saga EIP and LRA pages, the Camel Quarkus LRA extension page, the Quarkus Flow docs (intro, messaging, persistence, DSL cheatsheet, fault tolerance), the quarkus-langchain4j Ollama page, the Serverless Workflow DSL page and the Ollama library tags. Sibling projects read: datamesh-reference-arch-quarkus (ch 13, 14, 21, `examples/ai-rules-service`), enterprise-integration-patterns-with-camel (ch 10 Process Manager, Appendix F Drools, Appendix G Quarkus Flow and its examples), cloud-native-design-patterns (Appendix D "Saga State & Compensation"). No stack or container was started, no file was written and no git command changed state.

## Findings

### Repo state that binds this iteration

- **Part 4 continues.** All three chapters take `part: processing-and-coordinating-streams`. The part blurb already says "sagas that coordinate work across services". Chapter 24 fits it as coordination by decision.
- **No earlier chapter mentions sagas, compensation or process managers.** A grep of `_docs` finds none, so there is no forward reference to clean up. Chapters 22 to 24 can link back to these:
  - 08 (outbox), 11 (inbox, idempotent consumer) and 12 ("a Kafka transaction covers Kafka only": this is the reason a saga exists).
  - 13 (dead letter), 14 (outbox from the aggregate), 19 (`breakOnFirstError` while the owner is down) and 20 (a timeout is a guess at arrival spread).
- **Domain rows** in `_plans/domain-model.md`:
  - 22: "the order-to-delivery flow choreographed over Kafka; PaymentFailed compensates (release stock, cancel order); a timeout; saga state in PostgreSQL".
  - 23: "the same order flow via the Camel Saga EIP (LRA) and Quarkus Flow; decision table against chapter 22".
  - 24: "a free-text delivery-exception note from a carrier on a `Shipment`; LangChain4j extracts the reason, Drools decides RESHIP or REFUND (settled at r09)".
  - Chapter 27 (r10) reuses "the chapter 22 order saga followed by order id across services". Chapter 22's example must stay usable for it: order id on every record and in every log line.
- **The Services table** says payment-service reacts only to InventoryReserved. The chapter 22 countermeasure (decision 6) adds OrderCancelled to it. This is a message about an existing entity: no new entity, and a domain-model.md table edit only.
- **Topics.** `Topics` (domain-model 2.3.0) has every catalog topic chapter 22 needs. Chapter 23 needs command topics and chapter 24 needs three shipping topics. Neither set exists, so **domain-model 2.4.0** is needed (decision 15; r05 and r08 precedent: every example pom is bumped and `mvn -B verify`'d at G0).
- **Payment status** already has `REFUNDED`, and `Shipment` has `carrier` and `trackingNumber`. Chapter 24's REFUND and RESHIP decisions are payload values about existing entities.
- **Banned names** (`BANNED_DOMAIN`): the planned names below were checked against the regex. Avoid "Settlement", "Invoice" and "Manifest" in rule names and prose (for example, never "refund settlement").
- **Ports.** The multi-service rule names app ports 8080 and 8082, but chapters 22 and 23 have three services each. New host ports: 8084 (third service), 8091 (LRA coordinator, if published) and 11434 (Ollama). CLAUDE.md's port list gains them at ship (decision 16).
- **Fresh-start rule.** `validate.py` (lines 331 to 336) requires `down -v --remove-orphans` and `up -d --build --force-recreate --renew-anon-volumes --wait` in every `verify.sh`. A model cache conflicts with "volumes are never reused" (decision 2).
- **Compose include pattern.** `examples/_infra/compose-{kafka,postgres,connect,lgtm,apicurio}.yaml` are included per example, with topics-init as a `service_completed_successfully` dependency. New infra files `compose-lra.yaml` and `compose-ollama.yaml` go in `_infra` (an orchestrator-owned directory).

### r08 lessons carried into r09 (verification log, G0 decisions)

- **Event times relative to now** (G0-22). This applies to chapter 24 note timestamps and any `occurredAt`. Saga timeouts run on the wall clock, so no event-time arithmetic is needed. Never a future timestamp.
- **Fresh containers every run.** In-run recreates use `up -d --no-deps --force-recreate --wait <svc>` and wait for liveness, never with fixed sleeps (G0-24). Consumers in 22 and 23 take `session.timeout.ms=10000` and `heartbeat.interval.ms=3000` so they rejoin quickly after a recreate.
- **Per-run state.** r08 found a shared Kafka Streams state dir corrupting tests. The r09 equivalents are:
  - the Quarkus Flow JPA tables and the LRA coordinator object store, fresh per run (container filesystem, no named volume);
  - test runs that never share a database (Dev Services per test class profile).
- **Timing witnesses** run 15 times from fresh containers, or the README states the measured rate. In r09 these are the saga timeouts (22, 23) and every model-dependent check (24).
- **Prove each check bites.** Every negative witness is run with the guard removed: a demo switch or a temporary edit restored from `git checkout`. The run is logged with the failing check numbers, which must be the ones the README states.
- **Claims verified or cut. At most two Opus validation rounds.** Never cite an experiment the final script did not run. Check numbering is frozen after the runtime window, and "check N" is grepped after any renumbering.
- Other r06 to r08 rules still apply:
  - one Prometheus check, with `service_name`; `max_over_time` for short bursts;
  - `--reader-property` and `--formatter-property` on the 4.3 console tools;
  - group lag from one `--describe --all-groups`;
  - never `grep -q` under pipefail;
  - `KafkaConstants.KEY` header for the Camel producer key (G0-26);
  - `%profile.camel.component.kafka.brokers` repeated in Camel profiles.

### Versions re-survey (preliminary, 2026-11-13; 1A completes and records it in versions.md)

| Item | Found | Source | Change / proposal |
|---|---|---|---|
| quarkus-bom, quarkus-camel-bom | 3.40.1.2 (r08) | Central metadata, code.quarkus.io streams | none expected |
| camel-bom | 4.22.2 imported first (platform carries 4.22.1; camel-lra 4.22.1 in quarkus-camel-bom line 3546) | local pom | camel-lra resolves to 4.22.2 through the first import |
| `camel-quarkus-lra` | 3.40.0 in quarkus-camel-bom. Depends on `camel-quarkus-rest`, `camel-quarkus-saga`, `camel-lra`, `quarkus-resteasy-client` | https://repo1.maven.org/maven2/org/apache/camel/quarkus/camel-quarkus-lra/3.40.0/camel-quarkus-lra-3.40.0.pom | new, platform-managed |
| Narayana LRA (Central) | `lra-coordinator-jar` 2.0.1.Final is newest final (2026-10-02). quarkus-bom 3.40.1.2 manages narayana-lra and lra-coordinator-jar **1.2.0.Final** (only for `quarkus-narayana-lra`, which is not used) | https://repo1.maven.org/maven2/org/jboss/narayana/lra/lra-coordinator-jar/maven-metadata.xml | informational |
| LRA coordinator image | `quay.io/jbosstm/lra-coordinator`. Release-named tags (`<narayana>-<quarkus>`) **stop at `7.2.2.Final-3.29.3` (2025-11-14)**. Since then only date tags, newest `2026-10-26`, digest sha256:b8b2c8fac32095fbce6a0033e23165974223c6837d3f90fb0c1c5cdc12ee6745. The `jbosstm/lra-coordinator-quarkus` main pom builds LRA 2.0.1.Final on Quarkus 3.40.1.2 under version `1.0.0-SNAPSHOT`. **Unconfirmed:** which LRA version and Quarkus the `2026-10-26` image contains (1A reads the image labels or manifest) | https://quay.io/api/v1/repository/jbosstm/lra-coordinator/tag/ ; https://raw.githubusercontent.com/jbosstm/lra-coordinator-quarkus/main/pom.xml | **decision 3** |
| Quarkus Flow | `io.quarkiverse.flow:quarkus-flow` **1.3.0** (2026-10-23) is newest final (1.2.0.CR1 to CR3 skipped). Built on Quarkus 3.39.0, serverlessworkflow 7.35.3.Final, quarkus-langchain4j 1.14.2. Not in the Quarkus platform. `quarkus-flow-jpa`, `quarkus-flow-db-migration` and `quarkus-flow-messaging` are also at 1.3.0. Sibling datamesh pinned 1.1.3 | https://repo1.maven.org/maven2/io/quarkiverse/flow/quarkus-flow/maven-metadata.xml ; quarkus-flow-parent-1.3.0.pom | new pin 1.3.0 through `quarkus-flow-bom`, imported **after** the platform BOMs; S2 checks convergence |
| Drools | `drools-quarkus-ruleunits` and `drools-quarkus` **10.2.0** (2026-04-26) are newest. **Built against Quarkus 3.27.2** (kie parent). Both siblings used plain embedded `drools-engine` + `drools-mvel` 10.2.0 (KieHelper or KieContainer), not the Quarkus extension | https://repo1.maven.org/maven2/org/drools/drools-quarkus-ruleunits/maven-metadata.xml ; local `org/drools` | new pin 10.2.0 through `drools-bom`; **S3 proves the extension works on Quarkus 3.40.1.2** (decision 10) |
| quarkus-langchain4j | platform `quarkus-langchain4j-bom` 3.40.1.2 manages **quarkus-langchain4j 1.13.4, langchain4j 1.19.3**. Upstream final 1.15.1; 2.0.0.Beta4 is a prerelease (skipped) | https://repo1.maven.org/maven2/io/quarkus/platform/quarkus-langchain4j-bom/3.40.1.2/quarkus-langchain4j-bom-3.40.1.2.pom | keep platform-managed (same rule as kafka-clients); import the langchain4j platform BOM after quarkus-bom (S4 confirms the order; datamesh imported it first) |
| Ollama image | `docker.io/ollama/ollama` **0.40.16** (GitHub final 2026-11-09; Docker Hub digest sha256:910d0d8107b538fb0894e63163eb195de0cc7aefcbc666dc62d3b18f71124592). 0.40.17-rc0 skipped. Sibling used 0.40.2 | https://api.github.com/repos/ollama/ollama/releases ; Docker Hub tags API | new pin 0.40.16 (1A re-reads it on the day) |
| Model `qwen2.5:3b` | Ollama tag `qwen2.5:3b` = `3b-instruct-q4_K_M`, digest 357c53fb659c, 1.9 GB. **Licence: Hugging Face card `license: other`, `license_name: qwen-research`** (Qwen Research License, not an open-source licence) | https://ollama.com/library/qwen2.5/tags ; https://huggingface.co/api/models/Qwen/Qwen2.5-3B-Instruct | **conflicts with "open source only"**: decision 1 |
| Apache-2.0 alternatives | `qwen2.5:1.5b` (Qwen2.5-1.5B-Instruct, apache-2.0); `qwen3:4b-instruct-2507-q4_K_M` (2.5 GB, digest 0edcdef34593, apache-2.0, non-thinking); `granite3.3:2b` (1.5 GB, digest 07bd1f170855, apache-2.0 per HF). Gemma 3 rejected (Gemma licence) | HF API; Ollama library tags | S4 compares them |
| `spring-jdbc` / `spring-tx` (row 9 re-check) | camel-bom 4.22.2 resolves 7.0.9. Newest 7.0.x final 7.0.10. Metadata `latest` 7.1.0-RC2 (prerelease) | https://repo1.maven.org/maven2/org/springframework/spring-jdbc/maven-metadata.xml | unchanged. No r09 example uses camel-sql (1A confirms with `dependency:tree`) |
| Debezium Connect, debezium-quarkus-outbox | 3.7.1.Final in use (08, 14) | versions.md | re-read in 1A; only chapter 22 uses them |
| Not yet checked (1A) | otel-lgtm 0.41.0 digest; ubi10 openjdk-25 1.24-20; apache/kafka 4.3.2 digest; CNPG operand and postgres 18.6 (held, record only); Temurin; Maven #57216; Strimzi | as r08 | |

No version appears in the 22 to 24 prose.

### What the sources say (confirmed in this session)

- **Camel 4.22.x Saga EIP** (fetched):
  - Propagation: REQUIRED (default), REQUIRES_NEW, MANDATORY, SUPPORTS, NOT_SUPPORTED and NEVER.
  - Completion mode: AUTO (default), or MANUAL, where "the user must complete or compensate the saga using the saga:complete or saga:compensate endpoints". With MANUAL, steps can run over asynchronous channels.
  - `timeout`: "There is no default timeout on Sagas". On expiry the saga is compensated. "Always set an explicit timeout, especially when using `MANUAL` completion mode."
  - `option` values become headers of the compensation or completion exchange.
  - The `Long-Running-Action` header carries the saga id and can travel in transport headers.
  - "any compensating action must be idempotent". Compensations should be commutative with their action; completions can run more than once.
  - The in-memory saga service "is not recommended for production" (no persistence, no remote propagation).
  - LRA service: `coordinatorUrl` is required. `localParticipantUrl` is how the coordinator reaches this app. It exposes `/lra-participant` and needs a REST context. Also `allowedCoordinatorUrls` and `callbackSecret`.
  - **Not on the page:** Kafka, and the Quarkus property names. S1 confirms these.
- **Camel Quarkus LRA extension page:** coordinates `org.apache.camel.quarkus:camel-quarkus-lra`, JVM and native. No configuration listed.
- **Quarkus Flow docs:**
  - Workflows extend `io.quarkiverse.flow.Flow` and override `descriptor()`. DSL: `FlowWorkflowBuilder.workflow(..)`, `FlowDSL.function/listen/emitJson/switchWhen/set/forEach`, plus `withInstanceId` and `consume`.
  - Messaging: the bridge registers when a Quarkus Messaging connector is present, with `quarkus.flow.messaging.defaults-enabled=true`. Default channels are `flow-in` and `flow-out`.
  - CloudEvents travel in binary (`ce_*` headers) or structured mode. Outbound events carry `flowinstanceid` and `flowtaskid`, and a callback carrying `flowinstanceid` wakes the waiting instance.
  - Persistence: `quarkus-flow-jpa` (with `quarkus-flow-db-migration` for Flyway), MVStore or Redis. State is written "every time a task completes or pauses", with auto-restore at startup.
  - **Not confirmed:** listen timeouts, try/catch in the Java DSL, and whether a paused `listen` resumes after a restart (S2).
- **Serverless Workflow DSL:** workflows and tasks can time out, raising error type `.../errors/timeout`, status 408. `try`/`catch` with `retry` exist. No compensation construct was found on the page (1A confirms against dsl-reference.md). The SonataFlow (0.8 spec) `compensatedBy` is not in Quarkus Flow's 1.0 DSL (unconfirmed).
- **quarkus-langchain4j Ollama:**
  - `quarkus.langchain4j.ollama.base-url`; `chat-model.model-id` (default llama3.2); `chat-model.temperature` (default 0.8); `seed`; `top-k` (40); `top-p` (0.9); `num-predict`.
  - `chat-model.format` ("json or a JSON schema"); `ollama.timeout` (default 10s); `log-requests`.
  - `devservices.enabled` (default **true**) and `devservices.image-name` (default `ollama/ollama:latest`). Dev Services pulls the configured model, so tests must disable it.
  - Whether AI-service record return types send a JSON schema to Ollama automatically: **unconfirmed** (S4).
- **Sibling lessons:**
  - Datamesh 14: `qwen2.5:3b` drifts without a worked example. It sometimes wraps JSON in fences; `ResponseFormat.JSON` forces JSON syntax but not keys.
  - `camel-quarkus-support-langchain4j` forces the JAX-RS HTTP client JVM-wide, which broke tool calling (datamesh DEF-001). Single-shot calls worked.
  - The datamesh Flow workflow and Camel route had **no** compensation (ch 21 says so).
  - EIP Camel ch 10 shows the Saga EIP as prose only; its Appendix G example is a CDI state machine, not quarkus-flow.
  - CNDP App D teaches a DB-backed orchestrator state machine with "state + command commit together".
  - This book must not copy any of them. It links to EIP Camel ch 10 and Appendix G, and to CNDP Appendix D.
- **Sagas paper confirmed:** Hector Garcia-Molina and Kenneth Salem, "Sagas", Proceedings of SIGMOD '87, pp. 249 to 259, DOI 10.1145/38713.38742 (Crossref). Cite the paper; the text is not read, so no content claim beyond the definition.
- **URLs answering 200:** Hohpe and Woolf Process Manager (`.../ProcessManager.html`), Richardson "Pattern: Saga" (`microservices.io/patterns/data/saga.html`), MicroProfile LRA 2.0 spec.

## Approach

Same gate shape as r08:

1. G0: re-survey, sources, spikes and domain-model 2.4.0, in parallel.
2. Three authoring executors on disjoint files, with `mvn verify` under the lock.
3. Serial exclusive runtime windows (23, then 22, then 24), including the 15-run loops.
4. The orchestrator re-runs every prose experiment.
5. Final serial run 00 to 24.
6. Opus validation (at most two rounds), then ship.

**The three chapters tell one story on one flow.** The order flow is order, then stock, then payment, with the same participants and the same failures. Three cases recur in every chapter: a declined payment, a payment service that does not answer, and a late answer after the saga gave up.

- **22 Saga (choreography).** No coordinator. Each service reacts to the previous event and publishes its own (the domain's catalog topics, through the outbox).
  - order-service keeps the saga state (`orders.order_saga`) with a deadline, and a sweeper cancels expired sagas.
  - Compensation is a new event, never a rollback: PaymentFailed or OrderCancelled makes inventory release the stock.
  - The late-reply hazard is shown and closed by a countermeasure (payment-service refuses a payment for an order it has seen cancelled). The chapter states the kinds of step: compensatable (reserve), pivot (payment) and retriable (after).
- **23 Orchestrated sagas.** One order-service hosts two orchestrators side by side, driving the **same** inventory and payment participants with commands over Kafka:
  - **Camel Saga EIP with LRA.** The Narayana coordinator holds the saga. Participants join it with `saga().propagation(SUPPORTS)` and register their compensation. Timeout and cancel make the coordinator call every registered compensation. A participant that joins after cancel is refused.
  - **Quarkus Flow.** The workflow holds the sequence and its state (JPA in PostgreSQL). Compensation is explicit workflow tasks (release, refund, cancel) after a failed or timed-out `listen`.
  - A decision table compares both with chapter 22.
- **24 Decisions on events.** shipping-service receives a carrier's free-text delivery-exception note.
  - **Stage 1 (analysis):** an AI service (quarkus-langchain4j, local Ollama, temperature 0, fixed seed, schema-constrained output) extracts a reason from a closed enum. It is recorded as `DeliveryExceptionAnalysed`, with the model and the prompt version.
  - **Stage 2 (decision):** a Drools rule unit decides RESHIP or REFUND from the recorded analysis and the carrier's attempt number. It is recorded as `DeliveryExceptionDecided`, with the rule name.
  - A replay re-runs stage 2 over the recorded analyses: same decisions, zero model calls. "The model analyses, the rules decide."

**Rejected alternatives (one line each):**

- **22 orchestrated by order-service with a DB state machine (CNDP App D shape):** that is chapter 23's subject. 22 stays pure choreography with one saga-state owner.
- **22 SmallRye emit after commit, no outbox:** a crash between commit and send strands a saga step. The book taught the fix in chapter 08 (decision 4 keeps the fallback).
- **22 includes shipping-service to SHIPPED:** a fourth service for a retriable step that teaches nothing new. The prose names it as the retriable tail (decision 5).
- **23 commands over HTTP with MicroProfile LRA annotations (`quarkus-narayana-lra`):** this breaks the domain rule "talk only through events on Kafka" and moves the lesson to JAX-RS (decision 7).
- **23 two examples (one per orchestrator):** doubles the participants and loses the same-run comparison (r08 precedent).
- **23 Flow `emitJson` to `flow-out`:** one outbound channel cannot address two command topics. A `function` task using the instance id plus a SmallRye `Emitter` per topic does (S2 confirms; else one command topic per participant still works with `emitJson` if S2 shows per-type routing).
- **23 split into two chapters:** renumbers 24 to 35, the PRD, claims and the domain table for a word overrun that a budget avoids (decision 9).
- **24 camel-quarkus-langchain4j-chat:** pulls `camel-quarkus-support-langchain4j` (datamesh DEF-001). An `@RegisterAiService` interface called from `.bean()` is the Quarkus idiom and returns a typed record.
- **24 the model decides directly:** non-reproducible and unauditable. This is the chapter's negative example, shown only as a measured variance in S4, not in code.
- **24 plain KieSession (siblings):** the brief asks for rule units. Kept as the fallback if S3 fails (decision 10).
- **24 PostgreSQL for decision history:** the carrier's attempt number on the note makes the decision a pure function of the recorded analysis, and replay needs no state (decision 14).
- **24 calling the model again on replay:** it is the negative witness `REPLAY_MODE=reanalyse`.

## Per-chapter design

Shared conventions:

- **Extensions.** camel-quarkus-kafka, -jackson, -bean, -opentelemetry2, quarkus-opentelemetry, quarkus-micrometer-opentelemetry, quarkus-smallrye-health and quarkus-rest-jackson. Hibernate ORM Panache, jdbc-postgresql and flyway where a service has tables.
- **Tests.** quarkus-junit, quarkus-junit-mockito, rest-assured, awaitility and quarkus-test-kafka-companion.
- **Partitions and keys.** Topics have 1 partition each (topics-init), keyed by order id, so per-order order holds and command/refund ordering is deterministic (23).
- **Order ids** `O-22xx`, `O-23xx` and `O-24xx`; shipment ids `S-24xx`; event ids are UUIDs in an `id` header (ch 08 and 11 convention).
- **Demo switches** are environment variables, labelled demo code in code comments, README and prose.
- **Consumer timeouts.** `session.timeout.ms=10000` and `heartbeat.interval.ms=3000` on every consumer that is recreated in `verify.sh`.

### 22 Saga: the pattern (`examples/22-saga`, `_docs/22-saga.md`)

**Headings.**

- `## Pattern: Saga`, with problem, solution and `### How Quarkus and Camel model it`.
- `## Compensation is a new event`.
- `## Timeouts and late replies`.
- `## Where the saga state lives`.
- Then `## Build, run, observe`, `## When not to use it`, `## References`.

**Opening.** One order touches three databases: the order, the stock and the payment. No transaction spans them, and chapter 12 showed that a Kafka transaction stops at the cluster. If the payment is declined after the stock is reserved, the reservation must be undone by someone, later, on purpose.

**Services (aggregator pom, three modules).**

- **order-service** (port 8080, package `com.patterncatalyst.shipping.saga.order`), the saga state owner.
  - `POST /orders` writes the `Order` (PLACED), an `order_saga` row (`order_id` pk, `state`, `deadline`, `version`) and OrderPlaced through the outbox, in one transaction.
  - It consumes InventoryInsufficient, PaymentProcessed and PaymentFailed with SmallRye `@Incoming` + `@Transactional`, plus the inbox (ch 11).
  - A `@Scheduled(every = "1s", concurrentExecution = SKIP)` sweeper selects expired sagas with `FOR UPDATE SKIP LOCKED`, sets CANCELLED and writes OrderCancelled through the outbox.
  - Every transition is a conditional update (`WHERE state = :expected`), so a late reply and the sweeper cannot both win.
- **inventory-service** (port 8082, `...saga.inventory`).
  - OrderPlaced reserves stock or answers InventoryInsufficient.
  - PaymentFailed or OrderCancelled releases a reservation **once and commutatively**: a release with no reservation records `released` so a later OrderPlaced for that order is refused.
  - Inbox from ch 11, and a `inventory.reservation` table (order id, sku, qty, state).
- **payment-service** (port 8084, `...saga.payment`), a **Camel route** participant.
  - `from(kafka:edp.inventory.reserved)` goes to `.bean(PaymentService)`, which takes the payment in one transaction with the outbox row. Declined above `PAYMENT_DECLINE_ABOVE` (demo, default 500.00) gives PaymentFailed.
  - A second route on `edp.orders.cancelled` records the cancellation (`payments.cancelled_order`). PaymentService refuses InventoryReserved for a cancelled order: the **countermeasure**, a semantic lock.
  - `PAYMENT_CANCEL_GUARD=off` (demo) disables the refusal.
- **Outbox.** `debezium-quarkus-outbox` in each service, with `additional-fields=topic:string`. **One** Debezium connector captures `orders.outboxevent`, `inventory.outboxevent` and `payments.outboxevent` (route by `topic`, ch 14 config shape; S5).
- **Compose:** kafka, postgres, connect, lgtm, topics-init (the seven catalog topics used, 1 partition) and the three services. `SAGA_PAYMENT_TIMEOUT` (default 15s) applies to order-service.

**Payload records (per example).** OrderPlaced(Order, occurredAt), InventoryReserved, InventoryInsufficient, PaymentProcessed(Payment), PaymentFailed(orderId, reason) and OrderCancelled(orderId, reason: OUT_OF_STOCK, PAYMENT_DECLINED or TIMED_OUT).

**Metric.** `saga.outcomes` counter, tag `outcome` (`completed`, `compensated`, `timed-out`), giving Prometheus `saga_outcomes_total`.

**mvn tests (Dev Services PostgreSQL 18.6 and Kafka 4.3.2), at least 14 across the modules.**

- order: a happy-path transition to PAID; InventoryInsufficient goes to CANCELLED with no compensation; PaymentFailed goes to CANCELLED.
- order: the sweeper cancels an expired saga and writes OrderCancelled to the outbox in the same transaction (rollback test: no outbox row without the state change).
- order: **race test**. A sweeper tick and a PaymentProcessed are released together 50 times, and exactly one transition wins every time (a probabilistic catch, stated as such in the README).
- order: a late PaymentProcessed for a CANCELLED order changes nothing.
- inventory: reserve then release restores stock; release twice releases once (idempotent); release before reserve, then reserve, is refused (commutative); the inbox drops a duplicate PaymentFailed.
- payment: a decline above the threshold; OrderCancelled then InventoryReserved gives no payment (countermeasure); with the guard off, a payment is taken for the cancelled order (asserts the bad outcome).

**verify.sh (about 26 checks; "P" marks a precondition).**

1. P: three services ready; connector RUNNING; topics with 1 partition; stock seeded (`SKU-22` = 10).

Happy path:

2. O-2201: order PAID within 15 s; saga row COMPLETED.
3. Stock 9; payment COMPLETED.
4. The events of O-2201 appear in causal order across topics: OrderPlaced, InventoryReserved, PaymentProcessed (offset and timestamp per topic).

Out of stock:

5. O-2202, quantity 50: InventoryInsufficient, order CANCELLED (OUT_OF_STOCK), no payment row, stock unchanged.

Declined:

6. O-2203, amount 900.00: PaymentFailed, then order CANCELLED (PAYMENT_DECLINED).
7. Stock back to 9 (the reservation released); reservation state RELEASED.
8. Duplicate PaymentFailed for O-2203, re-produced with the same `id` header: stock still 9 (compensation applied once).

Timeout and late reply:

9. P: payment-service stopped; health unreachable.
10. O-2204 reserved (stock 8); after `SAGA_PAYMENT_TIMEOUT` the order is CANCELLED (TIMED_OUT) within 15 to 18 s of the reservation (timing witness, 15-run rule).
11. OrderCancelled O-2204 published; stock back to 9.
12. payment-service started; its group catches up (lag 0 on `edp.inventory.reserved` and `edp.orders.cancelled`).
13. No PaymentProcessed for O-2204 and no payment row: the countermeasure held.
14. The order is still CANCELLED.

State survives a restart:

15. P: payment-service stopped again; O-2205 reserved; the saga row is AWAITING_PAYMENT with a deadline.
16. order-service recreated (`--force-recreate`); after ready, the sweeper cancels O-2205 at its original deadline (+3 s), from PostgreSQL state.
17. payment-service started; O-2205 has no payment.

Bench (before any mutation, right after check 4):

18. 200 orders (O-22100 to O-22299, half above the decline threshold) in one burst: 100 PAID and 100 CANCELLED, stock net 100 reserved. NOTE: time to last terminal state, and compensations per second. 3 runs, median of medians.

LGTM:

19. Prometheus `saga_outcomes_total{outcome="compensated",service_name="order-service"}` at least 101.
20. Trace in Tempo for order-service `POST /orders`.
21. Log in Loki with `O-2203`.

**Experiments:**

- `PAYMENT_CANCEL_GUARD=off`: the payment for O-2204 is taken after cancellation (money taken, no goods). Expected failures: 13 and 17. The main negative witness.
- `SAGA_PAYMENT_TIMEOUT=60s`: O-2204 is not cancelled in time and the late payment completes the order instead. Expected failures: 10, 11 and 13 (the order goes PAID once payment-service returns). Shows that the timeout is a business decision.
- Inbox removed (temporary edit, bite proof only, not a Try bullet): check 8 fails (stock 10).

**Figures.**

- `22-saga-choreography`: the three services and the catalog topics, with the happy path in one colour and the compensation path (PaymentFailed or OrderCancelled to release) in another.
- `22-timeout-and-late-reply`: a timeline. The reservation, the deadline, the sweeper's OrderCancelled, the release, and payment-service returning to find the cancellation; a dashed branch shows "guard off: payment taken".
- `22-saga-state-machine`: `order_saga` states (AWAITING_STOCK, AWAITING_PAYMENT, COMPLETED, CANCELLED) with the event or deadline on each edge, and the conditional update shown on the race edge.

**Step table (prose).** One table of step, action, compensation and kind: reserve stock is compensatable (release, idempotent and commutative); take payment is the pivot (refund only by a new business decision, chapter 24); schedule shipment is retriable (not in the example).

**Callout.** Names `order_saga`, `PaymentService`, `PAYMENT_CANCEL_GUARD` and `edp.orders.cancelled`. Rule: every step that can be undone publishes how; every compensation is idempotent and commutative; every wait has a deadline owned by one service; a participant that can act after the saga gave up needs a countermeasure.

**Citations.**

- Garcia-Molina and Salem, "Sagas" (1987); Richardson, "Pattern: Saga"; Hohpe and Woolf, Process Manager (for the contrast with choreography).
- Lawrence ch 7 "Long-Running Processes"; Bellemare ch 10 "Building Workflows with Microservices".
- Camel Saga EIP page (the idempotent and commutative guidance, cited here and in 23).
- CNDP Appendix D (sibling link).
- Backward links: 08, 11, 12, 13.

### 23 Orchestrated sagas: Camel Saga EIP and Quarkus Flow (`examples/23-orchestrated-sagas`, `_docs/23-orchestrated-sagas.md`)

**Headings.**

- `## Pattern: Orchestrated saga`, with problem, solution and how Quarkus and Camel model it.
- `## Camel Saga with an LRA coordinator`.
- `## The same saga as a Quarkus Flow workflow`.
- `## When the payment service does not answer`.
- `## Three ways compared` (the decision table, including chapter 22).
- Then build, run and observe; when not to use it; references.

**Opening.** In chapter 22 nobody holds the whole sequence: each service knows only its own next step. Change the order of steps, add one or audit a stuck order, and three codebases are involved. An orchestrator holds the sequence in one place and tells each participant what to do.

**Services (aggregator pom, three modules) plus the coordinator.**

- **order-service** (8080, `...orchestration.order`), with both orchestrators.
  - `POST /orders/camel` and `POST /orders/flow` (one `Order` body). The order row records which orchestrator owns it.
  - **`CamelOrderSagaRoute`** (start): `saga().propagation(REQUIRES_NEW).completionMode(MANUAL).timeout({{saga.timeout}})`, with `compensation("direct:cancel-order")`, `completion("direct:complete-order")` and `option("orderId", ...)`. It sends ReserveStock to `edp.inventory.commands`; `Long-Running-Action` travels as a Kafka header.
  - **`CamelSagaRepliesRoute`** reads the four reply topics, filters records that carry `Long-Running-Action`, and joins with `saga().propagation(MANDATORY)` (S1).
    - InventoryReserved sends TakePayment to `edp.payments.commands`.
    - PaymentProcessed goes to `saga:complete`.
    - InventoryInsufficient or PaymentFailed goes to `saga:compensate`.
  - `direct:cancel-order` sets CANCELLED and publishes OrderCancelled. It is idempotent: a conditional update, and it publishes only on change.
  - **`OrderSagaWorkflow`** (extends `Flow`), with these tasks:
    - `function` with instance id: send ReserveStock (header `ce_flowinstanceid`);
    - `listen` for InventoryReserved or InventoryInsufficient;
    - `switchWhen` insufficient, which cancels the order;
    - `function`: send TakePayment;
    - `listen` for PaymentProcessed or PaymentFailed with a timeout (S2);
    - on PaymentFailed: release stock, then cancel the order;
    - on timeout: RefundPayment, then release stock, then cancel the order;
    - on success: mark PAID.
    - State is in PostgreSQL via `quarkus-flow-jpa` (Flyway via `quarkus-flow-db-migration`). The `flow-in` channel maps to the four reply topics.
  - **Order ids.** `/orders/camel` takes O-2301 to O-2309; `/orders/flow` takes O-2311 to O-2319.
- **inventory-service** (8082) and **payment-service** (8084), the participants, shared by both orchestrators.
  - One command route each: `from(kafka:edp.inventory.commands)` with `choice` on the `type` header. ReserveStock runs inside `saga().propagation(SUPPORTS).compensation("direct:release")`: with an LRA header it joins the coordinator's saga, and without one it runs plain.
  - ReleaseStock (the Flow path) calls the same `direct:release`. Release is idempotent and commutative (as in 22).
  - Replies go to the catalog topics, copying `Long-Running-Action` and `ce_flowinstanceid`, setting `ce_type` to the event type (plus `ce_id`, `ce_source`, `ce_specversion` and content type) so Flow can correlate (S2).
  - payment-service: TakePayment joins with SUPPORTS plus `compensation("direct:refund")`. RefundPayment (Flow path) sets REFUNDED, or records a refusal if no payment exists yet. **Joining a cancelled LRA fails** (S1 confirms the coordinator status), so a late TakePayment on the Camel path takes nothing.
  - Persistence: Panache tables `inventory.reservation` and `payments.payment`; inbox by event id.
- **LRA coordinator** (`examples/_infra/compose-lra.yaml`): `quay.io/jbosstm/lra-coordinator:<decision 3>`, container `edp-lra-coordinator`, host port 8091, `mem_limit: 384m`, healthcheck per S1. Services set `CAMEL_LRA_COORDINATOR_URL=http://lra-coordinator:8080` and `CAMEL_LRA_LOCAL_PARTICIPANT_URL=http://<service>:<port>` (property names per S1).
- **mvn tests** use Camel's `InMemorySagaService` (PRD Q11) through a test profile that disables LRA (S1 names the switch).

**New topics (domain-model 2.4.0):** `edp.inventory.commands` and `edp.payments.commands`.

**Payload records.** ReserveStock, ReleaseStock, TakePayment and RefundPayment (orderId plus the fields needed); replies as in 22.

**Metric.** `saga.outcomes` with tags `orchestrator` (`camel-lra`, `flow`) and `outcome`.

**mvn tests, at least 14.**

- Camel (in-memory saga service, KafkaCompanion):
  - happy path completes and no compensation runs;
  - PaymentFailed compensates and both compensation routes run once;
  - a saga timeout compensates;
  - a compensation run twice changes state once;
  - `completionMode(AUTO)` completes when the first route ends, before any reply (asserts the bad outcome: the reason for MANUAL).
- Flow (Dev Services PostgreSQL and Kafka; messaging devservices per S2):
  - happy path;
  - declined path releases stock and cancels;
  - the listen timeout sends RefundPayment, ReleaseStock and cancel in that order;
  - an instance row exists while waiting and is gone (or COMPLETED) after.
- Participants:
  - SUPPORTS without a header runs plain;
  - release is idempotent and commutative;
  - RefundPayment before TakePayment leads to refusal;
  - TakePayment then RefundPayment leaves REFUNDED.

**verify.sh (about 28 checks).**

1. P: services ready; coordinator ready; topics 1 partition; Flow tables exist; stock `SKU-23` = 20.

Happy path, both forms:

2. O-2301 (camel): PAID; the coordinator lists no active LRA for it (closed).
3. O-2311 (flow): PAID; the workflow instance is completed.
4. Both commands and replies for each order are on the topics in order; Camel records carry `Long-Running-Action` and Flow records carry `ce_flowinstanceid`.

Declined:

5. O-2302 (camel, 900.00): CANCELLED; inventory release ran via the coordinator callback (participant log line and reservation RELEASED).
6. O-2312 (flow, 900.00): CANCELLED; ReleaseStock command on the topic; reservation RELEASED.
7. Stock equals 20 minus the two PAID orders.

Payment service down:

8. P: payment-service stopped.
9. O-2303 (camel): after the saga timeout (20 s) the coordinator cancels. Order CANCELLED and stock released within 20 to 25 s (timing witness, 15 runs).
10. O-2313 (flow): after the listen timeout (20 s), RefundPayment and ReleaseStock are on the command topics and the order is CANCELLED (timing witness).
11. payment-service started; group lag 0 on `edp.payments.commands`.
12. O-2303: no payment row (the join after cancel was refused; the coordinator answer is logged).
13. O-2313: a payment row with status REFUNDED (taken, then refunded in command order).
14. Neither order changed state after the late processing.

Orchestrator restart:

15. P: payment-service stopped; O-2304 (camel) and O-2314 (flow) reserved; a Flow instance row is waiting; the coordinator lists one active LRA.
16. order-service recreated; payment-service started within the timeout.
17. O-2304 PAID (the reply joined the saga from the Kafka header; the coordinator closed it).
18. O-2314 PAID (the workflow resumed from PostgreSQL; S2 decides whether this holds; if not, the README states the behaviour and the claim is cut).

Bench (after check 4):

19. 100 orders per form in one burst: all PAID. NOTE: per form, the time to the last PAID and the median per-order latency; coordinator requests per order (from its log or metrics, S1). 3 runs.

LGTM:

20. Prometheus `saga_outcomes_total{orchestrator="flow",outcome="compensated",service_name="order-service"}` at least 2.
21. Trace in Tempo for `POST /orders/camel`.
22. Log in Loki.

**Experiments:**

- `PAYMENT_JOIN=off` (demo; payment's TakePayment runs NOT_SUPPORTED instead of SUPPORTS): the coordinator never calls the payment compensation, and a late TakePayment for O-2303 takes money. Expected failure: 12.
- `FLOW_COMPENSATION=off` (demo; the timeout branch only cancels the order): O-2313 keeps a COMPLETED payment and the stock stays reserved. Expected failures: 10 and 13.
- `SAGA_TIMEOUT=0` is not offered: the Camel page says there is no default timeout, and an unbounded saga is stated in prose only.

**Figures.**

- `23-camel-saga-lra`: order-service, the coordinator and the two participants. Start, join, command and reply over Kafka, complete or cancel, and the coordinator's compensation callbacks over HTTP.
- `23-flow-workflow`: the workflow as tasks: send, listen, switch, send, listen with timeout, and the explicit compensation branch; the state store under it.
- `23-three-ways`: chapter 22 choreography, the Camel and LRA orchestrator, and the Flow orchestrator, side by side, marking where the sequence, the state and the compensation trigger live.

**Decision table (rows):**

- who knows the sequence;
- where saga state lives (three services' tables; coordinator plus Kafka headers; workflow instance in PostgreSQL);
- what triggers compensation (consumers of failure events; coordinator callbacks to registered participants; workflow tasks);
- the timeout owner (sweeper; LRA time limit; listen timeout);
- a late participant (semantic lock; join refused; taken then refunded);
- added infrastructure (outbox plus Connect; a coordinator container; workflow tables);
- extra hops per order (measured);
- bench latency per form (measured).

**Callout.** Names `CamelOrderSagaRoute`, `OrderSagaWorkflow`, `Long-Running-Action` and `edp.payments.commands`. Rule: choreograph short flows owned by one team; orchestrate when the sequence changes or must be audited; with LRA, participants register their own undo and the coordinator calls it; with a workflow, the undo is a step you write; every saga needs an explicit timeout.

**Citations.**

- Camel Saga EIP and LRA pages; Camel Quarkus LRA; MicroProfile LRA 2.0 spec (participant and coordinator roles; 1A confirms the join-after-cancel status).
- Narayana LRA coordinator README (quay page).
- Quarkus Flow docs (messaging, persistence, DSL); Serverless Workflow DSL (timeouts, try/catch).
- Hohpe and Woolf Process Manager; Garcia-Molina and Salem; Lawrence ch 7; Bellemare ch 10.
- EIP Camel ch 10 and Appendix G, and CNDP App D (sibling links).
- Backward links: 22, 11.

**Word budget (decision 9).** Pattern sections 500 words, Camel LRA 650, Flow 600, the timeout section 250, comparison 250, when not 150. Total about 2,400 plus BRO; code shown once per form. If the draft exceeds 2,500: cut the Flow persistence detail to the README first, then the timeout prose. No split.

### 24 Decisions on events: Drools rules, LangChain4j analysis (`examples/24-decisions-on-events`, `_docs/24-decisions-on-events.md`)

**Headings.**

- `## Pattern: Analyse, then decide` (the model extracts, the rules decide, both recorded), with problem, solution and how Quarkus and Camel model it.
- `## A closed answer from an open text` (schema, temperature 0, seed, enum).
- `## Rules as the decision` (rule unit, rule name recorded).
- `## Replay without the model`.
- Then build, run and observe; when not to use it; references.

**Opening.** A carrier reports that a parcel was not delivered, as a sentence its driver typed: "Box crushed at depot, contents leaking", or "Nobody home, card left, second attempt". shipping-service must decide whether to ship again or refund. The sentence is free text; the decision must be the same tomorrow, explainable, and never depend on how the model felt that day.

**Service.** shipping-service (8080, `...decisions`). No PostgreSQL (decision 14).

- **Compose:** kafka, lgtm, ollama (`examples/_infra/compose-ollama.yaml`), topics-init (three new topics, 1 partition) and shipping-service.
- **Topics (domain-model 2.4.0):**
  - `edp.shipping.delivery-exceptions` carries DeliveryExceptionReported(exceptionId, Shipment, attempt, note, occurredAt), keyed by order id. `verify.sh` stands in for the carrier integration, chapter 16 precedent.
  - `edp.shipping.exception-analyses` carries DeliveryExceptionAnalysed(exceptionId, orderId, shipmentId, carrier, attempt, reason, evidence, model, modelDigest, promptVersion, occurredAt).
  - `edp.shipping.exception-decisions` carries DeliveryExceptionDecided(exceptionId, orderId, decision RESHIP or REFUND, rule, rulesVersion, occurredAt).
- **Reason enum** (closed): DAMAGED, LOST, RECIPIENT_ABSENT, ADDRESS_PROBLEM, REFUSED, DELAYED, UNKNOWN.

**Code.**

- **`DeliveryExceptionAnalyst`** (`@RegisterAiService`, quarkus-langchain4j-ollama).
  - One method returns `ExceptionAnalysis(Reason reason, String evidence)`.
  - `@SystemMessage` and `@UserMessage` live in one versioned template (`PROMPT_VERSION = "v1"`), with one worked example, and say UNKNOWN when unsure.
  - Config: `temperature=0`, `seed=42`, `top-k=1`, `num-predict=96`, `format` = the record's JSON schema (or `json` plus the schema in the prompt, per S4), `timeout=60s`, `log-requests=false`.
  - An `@ApplicationScoped` wrapper validates the result: if the enum fails to parse, the reason is UNKNOWN (counted).
- **`ExceptionAnalysisRoute`** (one RouteBuilder):
  - `from(kafka:edp.shipping.delivery-exceptions?groupId=shipping-exception-analysis&breakOnFirstError=true)`, then unmarshal, then `.bean(analyst)`, then build DeliveryExceptionAnalysed (model id and digest from config), then `to(kafka:edp.shipping.exception-analyses)`.
  - Redelivery 3 times at 5 s. With the model down, the record waits (ch 19 lesson); no fallback decision is invented.
- **`DecisionUnit`** (`RuleUnitData`, `DataStore<DeliveryExceptionAnalysed>`, result store) and **`decisions.drl`** (`unit DecisionUnit;`). Each RHS records `drools.getRule().getName()`. Rules, guarded mutually exclusive:
  - "Damaged or lost, first attempt": RESHIP.
  - "Damaged or lost, later attempt": REFUND.
  - "Recipient absent or delayed, under three attempts": RESHIP.
  - "Third attempt": REFUND.
  - "Address problem or refused": REFUND.
  - "Unknown reason": REFUND (decision 12).
- **`ExceptionDecisionRoute`**: `from(kafka:edp.shipping.exception-analyses?groupId=shipping-exception-decision)`, `.bean(DecisionService)` (a `RuleUnit<DecisionUnit>` instance per record, S3), then `to(kafka:edp.shipping.exception-decisions)`.
- **`ReplayResource`** (demo code): `POST /demo/replay` reads `edp.shipping.exception-analyses` from offset 0 with a fresh consumer, runs the same `DecisionService`, and returns the decisions. It publishes nothing.
  - `REPLAY_MODE=reanalyse` (demo) instead re-reads the delivery exceptions and calls the analyst again. This is the negative witness.
- **Metrics:** `exception.model.calls` (counter, tag `outcome`: `ok`, `unparsed`, `error`) and `exception.decisions` (tag `decision`, `rule`).
- **Ollama service** (`compose-ollama.yaml`):
  - `docker.io/ollama/ollama:0.40.16`, container `edp-ollama`, port 11434.
  - The model volume `edp-ollama-models` is **external, read-only if S4 allows** (decision 2).
  - Env: `OLLAMA_NUM_PARALLEL=1`, `OLLAMA_KEEP_ALIVE=30m`, `OLLAMA_CONTEXT_LENGTH=2048` (names per S4).
  - `mem_limit: 4g`; healthcheck `ollama list` (S4; the image may lack curl).
  - **No GPU** (CPU only).
- **`scripts/ollama-model.sh pull`** (a one-time step, documented in chapter 00's setup surface at ship): fills the external volume with the pinned model and prints its digest. `verify.sh` never pulls.
- **No-model path:** `MODEL=off ./verify.sh` starts compose without the `ollama` profile and with `ANALYST=off`, so stage 1 is not started. `verify.sh` publishes the recorded analyses fixture (`fixtures/analyses.jsonl`, captured from a step 3 run) straight to `edp.shipping.exception-analyses`. The rule, replay and LGTM checks run; the model checks are not counted, and the summary says "model checks not run".

**Determinism with an LLM in the loop (how `verify.sh` stays reproducible).**

1. **Checks on the recorded event**, not the live text: every analysis has a reason in the enum, the pinned model digest and the prompt version.
2. **Checks on the rules:** every decision equals the rule table applied to its **recorded** reason and attempt, computed by a python oracle in `verify.sh`. This holds for any reason the model gave.
3. **The expected reason per corpus note** is a separate check, with its measured stability from the 15-run loop. A corpus note enters `verify.sh` only if S4 shows 15 of 15 identical reasons on the pinned model and image. Otherwise the note moves to the README as a measured example.
4. **Model-free checks:** a fixed-prompt golden test in mvn (the rendered prompt and the schema, byte for byte); replay with a zero model-call delta; the no-model path.
5. **Configuration:** temperature 0, fixed seed, top-k 1, schema-constrained output and one parallel request.

**mvn tests (no Ollama: `%test.quarkus.langchain4j.ollama.devservices.enabled=false`, `@InjectMock` of the analyst), at least 14.**

- Rules (rule unit, no Quarkus app needed if S3 allows): each rule fires on its row; exactly one rule per analysis; the rule name is recorded; the UNKNOWN row.
- The rules are exhaustive over reason x attempt 1 to 3 (a table-driven test, 21 cases).
- Prompt contract: golden prompt text and JSON schema (fixed-prompt test); the prompt version changes when the template changes (hash).
- Analyst wrapper: an unparseable model answer becomes UNKNOWN and is counted.
- Route (KafkaCompanion, mocked analyst): note to analysis to decision, end to end; with the analyst throwing, the record is retried and not committed (`breakOnFirstError`).
- Replay: decisions from recorded analyses equal the originals and the mock analyst is never invoked (Mockito `verifyNoInteractions`); `REPLAY_MODE=reanalyse` invokes it (asserts the bad outcome).

**verify.sh (about 24 checks with the model; about 12 with `MODEL=off`).**

1. P: the Ollama model volume holds the pinned model, digest matched; `/api/tags` lists it; one warm-up generate succeeds within 120 s (the model is loaded).
2. P: service ready; three topics with 1 partition.

Analysis, a corpus of 6 notes selected in S4 (S-2401 to S-2406; FASTLINE and AIRCO; attempts 1 to 3):

3. Six DeliveryExceptionAnalysed records within 6 x 30 s; each reason is in the enum, each carries the model digest and `promptVersion=v1`.
4. Each reason equals the corpus expectation (15-run rule; the measured rate goes to the README).
5. An off-topic note ("weather is nice") yields UNKNOWN.

Decision:

6. Seven DeliveryExceptionDecided records; each decision and rule name equals the oracle applied to the recorded analysis.
7. A second exception for the same order (attempt 2, DAMAGED) gives REFUND where attempt 1 gave RESHIP.

Replay:

8. P: `exception_model_calls_total` read (Prometheus or `/q/metrics`, S4) and Ollama request lines counted.
9. `POST /demo/replay` returns seven decisions identical to the recorded ones.
10. The model-call count and the Ollama request lines are unchanged; no new records on the analyses or decisions topics.

Model down:

11. P: ollama stopped.
12. Note S-2407: for 20 s no analysis exists, and the analysis group has lag 1.
13. ollama started (warm-up): the analysis and decision for S-2407 arrive within 90 s; lag 0.

Rules change without the model (Try bullet):

14. With `RESHIP_ATTEMPT_LIMIT=1` (demo, a rule-unit parameter) the replay changes the decisions for attempt-2 absent notes and the model-call count stays unchanged. Only in the experiment; the default run asserts the default limit.

Bench (one per run, before check 11):

15. Model latency per note, warm: median and range over the 6 notes; replay time for 1,000 recorded analyses (fixture-generated, published to a bench copy of the analyses topic or via the replay endpoint over an offset range, per S6). 3 runs.

LGTM:

16. Prometheus `exception_decisions_total{decision="REFUND",service_name="shipping-service"}` at least 3.
17. A trace in Tempo containing the langchain4j chat span (span name per S4; if quarkus-langchain4j emits none, the Camel route span).
18. Log in Loki.

**Experiments:**

- `REPLAY_MODE=reanalyse`: the model is called again. Expected failure: 10, deterministic, the main negative witness. Decisions may also differ: the measured rate goes to the README.
- `MODEL=off`: the no-model path, with the rule and replay checks green.
- `RESHIP_ATTEMPT_LIMIT=1`: replay decisions change with zero model calls; expected failure: 9.

**Figures.**

- `24-analyse-then-decide`: note, then the AI service (Ollama), then the recorded analysis, then the rule unit, then the recorded decision, with the replay path going from the analyses topic straight to the rules, bypassing the model.
- `24-closed-answer`: free text in, a schema-constrained enum out; temperature, seed and schema as three clamps; UNKNOWN as the exit.
- `24-rules-table`: reason x attempt grid coloured RESHIP or REFUND, each cell labelled with the rule name (generated from the same table as the oracle).

**Callout.** Names `DeliveryExceptionAnalyst`, `DecisionUnit`, `decisions.drl` and `edp.shipping.exception-analyses`. Rule: let a model turn text into a closed value and record it with the model and prompt version; let rules turn recorded values into decisions; replay decisions from the record, never from the model.

**Citations.**

- quarkus-langchain4j Ollama page and AI services guide; LangChain4j docs (structured outputs; 1A).
- Ollama API (format, options, seed; 1A); the model card and its licence.
- Drools rule units docs (Apache KIE 10.x; 1A finds the current URL; the drools.org page fetched shows 8.44).
- Hohpe and Woolf Content-Based Router? Not needed. Bellemare ch 9 "Deterministic Stream Processing" (replay gives the same output).
- EIP Camel Appendix F (Drools) and datamesh ch 14 (sibling links).
- Backward links: 13 (dead letter), 17 (replay), 19 (owner down).

## Claims rows to plan (claims.md, status pending until evidence)

Chapter 22:

| id | Claim | Evidence |
|---|---|---|
| 22-a | A declined payment is compensated by a new event that releases stock and cancels the order | verify 6, 7; mvn |
| 22-b | A compensation applied twice changes state once | verify 8; mvn |
| 22-c | Release is commutative: a release before the reservation makes the later reservation refuse | mvn |
| 22-d | A deadline owned by order-service cancels a saga whose participant never answers | verify 9 to 11 (15 runs) |
| 22-e | Without a countermeasure a late participant takes payment for a cancelled order | experiment `PAYMENT_CANCEL_GUARD=off`; mvn |
| 22-f | Saga state in PostgreSQL survives a restart of its owner | verify 15 to 17 |
| 22-g | A late reply and the deadline cannot both win (conditional update) | mvn race test (50 repetitions, stated as probabilistic) |
| 22-h | State change and event commit together through the outbox | mvn rollback test |
| 22-i | Bench numbers | verify 18 NOTE, 3 runs |
| 22-src-* | Garcia-Molina and Salem; Richardson saga; Hohpe Process Manager; Camel Saga EIP (idempotent, commutative); Lawrence ch 7; Bellemare ch 10 | sources.md |

Chapter 23:

| id | Claim | Evidence |
|---|---|---|
| 23-a | Both orchestrators drive the same participants to the same outcomes | verify 2 to 7 |
| 23-b | With LRA, the coordinator calls the compensation each participant registered | verify 5, 9; participant log |
| 23-c | A participant joining a cancelled LRA is refused | verify 12; S1 |
| 23-d | A Flow listen timeout runs the compensation tasks in order | verify 10; mvn |
| 23-e | Command order on one partition makes the Flow path take, then refund | verify 13 |
| 23-f | MANUAL completion is needed when replies arrive asynchronously | mvn (AUTO completes early) |
| 23-g | The Camel saga completes after an orchestrator restart (state in the coordinator) | verify 15 to 17 |
| 23-h | A Flow instance resumes from PostgreSQL after a restart | verify 18 (or cut per S2) |
| 23-i | Without a join the payment is not compensated | experiment `PAYMENT_JOIN=off` |
| 23-j | Without compensation tasks the Flow path keeps the payment and the reservation | experiment `FLOW_COMPENSATION=off` |
| 23-k | Bench numbers | verify 19 NOTE |
| 23-src-* | Camel Saga, LRA; MicroProfile LRA; Quarkus Flow messaging and persistence; SW DSL timeouts; Narayana coordinator | sources.md |

Chapter 24:

| id | Claim | Evidence |
|---|---|---|
| 24-a | Every analysis is a closed enum value recorded with model digest and prompt version | verify 3 |
| 24-b | The corpus notes give the expected reason (rate stated) | verify 4, 15-run loop |
| 24-c | Decisions equal the rule table applied to the recorded analysis | verify 6, 7; mvn exhaustive |
| 24-d | Replay gives the same decisions with zero model calls | verify 9, 10; mvn |
| 24-e | Re-analysing calls the model again (and may change decisions; rate stated) | experiment `REPLAY_MODE=reanalyse` |
| 24-f | With the model down, nothing is decided and nothing is lost | verify 11 to 13 |
| 24-g | A rule change re-decides history without the model | experiment `RESHIP_ATTEMPT_LIMIT=1` |
| 24-h | Tests and the no-model path run without Ollama | mvn in CI; `MODEL=off` run |
| 24-i | Model latency on a CPU-only host (method stated, class of CPU only) | verify 15 NOTE |
| 24-src-* | quarkus-langchain4j Ollama config; Ollama API options; model card and licence; Drools rule units; Bellemare ch 9 | sources.md |

## Spikes (1B, scratchpad only, under the stack lock, Dev Services first; budget about 290 minutes serial)

All spike code goes under `<scratchpad>/r09-spikes/`. Each live spike starts with `down -v`, then `up --force-recreate --renew-anon-volumes`, and ends with `down -v`. Results go to `<scratchpad>/r09-spikes/RESULTS.md`.

- **S1 Camel Saga with LRA (70 min).**
  - Coordinator image: the `2026-10-26` image labels (Narayana LRA version, Quarkus version); start time, RSS, health endpoint and whether curl exists; object-store location.
  - camel-quarkus-lra 3.40.0 with camel-bom 4.22.2 on Quarkus 3.40.1.2: the exact property names (`camel.lra.coordinator-url`, `local-participant-url`, `coordinator-context-path`?) and the env mapping; whether `/lra-participant` needs `camel-quarkus-platform-http`; protocol compatibility of the camel-lra client with the 2.0.1 coordinator.
  - `Long-Running-Action` as a Kafka header: the type on the consumer side (`byte[]` vs String; whether a header deserializer is needed); MANDATORY join in a route triggered by Kafka.
  - SUPPORTS join from another JVM (a participant service) registers its compensation; on `saga:compensate` the coordinator calls each participant once; compensations retried when a participant is down.
  - Timeout cancels; joining after cancel gives which status, and the route outcome.
  - Coordinator recreate mid-saga (record only).
  - How to run `@QuarkusTest` on `InMemorySagaService` with LRA disabled.
  - 15 runs of the timeout to compensation delay.
- **S2 Quarkus Flow 1.3.0 (70 min).**
  - `quarkus-flow-bom` import order with quarkus-bom, quarkus-camel-bom and camel-bom: `dependency:tree` convergence (langchain4j and Jackson versions; no Kogito); Flow and Camel in one app.
  - `flow-in` mapped to four topics; correlation by `ce_flowinstanceid` with binary CloudEvent headers set by a Camel producer (which `ce_*` headers are required, whether `ce_type` filtering works with `toOne(...)` or needs `toAny`).
  - Sending commands from a `function` with the instance id via `Emitter` per topic; `listen` with a timeout (how it is expressed in the Java DSL 1.3.0, which error is raised, how to branch on it with try/catch or switch); the alternative of a timer event if listen has no timeout.
  - `quarkus-flow-jpa` plus `quarkus-flow-db-migration` on PostgreSQL 18.6; whether a waiting instance resumes after `--force-recreate`.
  - Messaging devservices in tests versus our Dev Services Kafka.
  - Exported metric names.
- **S3 Drools rule units (40 min).**
  - `drools-quarkus-ruleunits` 10.2.0 (built on Quarkus 3.27.2) on 3.40.1.2: build, start, `RuleUnit<DecisionUnit>` injection; `DataStore` insert, fire and dispose per record; the rule name in the RHS; executable model codegen at build time; image size and startup.
  - Unit tests without `@QuarkusTest` (`RuleUnitProvider`).
  - Fallback if the extension fails: `drools-ruleunits-engine` without the extension, or plain KieSession (siblings), recorded with the reason.
- **S4 Ollama, model and LangChain4j (80 min).**
  - Image 0.40.16: healthcheck command; external volume pre-filled by a one-off container (pull), mounted read-only (does `serve` start and answer with `/root/.ollama` read-only?); `OLLAMA_*` env names; RSS and load time at a 4g cap.
  - **Model comparison** on a 12-note corpus (two per reason, plus off-topic), at temperature 0, seed 42, top-k 1 and schema format: `qwen2.5:3b` (licence reference only), `qwen2.5:1.5b`, `qwen3:4b-instruct-2507-q4_K_M` and `granite3.3:2b`. For each: accuracy against labels; 15-run stability per note; warm and cold latency; RSS.
  - quarkus-langchain4j 1.13.4 AI service with a record return type: is the schema sent as Ollama `format`, and are `seed` and `top-k` honoured (request log); the parse failure mode; OTel span name; Dev Services off in tests with `@InjectMock`; BOM import order (langchain4j platform BOM before or after quarkus-camel-bom).
  - Counting model calls: app counter versus Ollama log lines (`POST "/api/chat"`).
- **S5 Outbox for three services (25 min).** Three `debezium-quarkus-outbox` services and one connector over three tables (`route.by.field=topic`); the `id` header for the inbox; time from `up` to connector RUNNING; memory of the 22 stack (`docker stats`).
- **S6 Tooling (15 min).** Producing a duplicate with the same `id` header from the console producer (`parse.headers=true`); reading coordinator LRAs (`GET /lra-coordinator`); the bench input for the 24 replay timing; memory peaks of the 23 and 24 stacks.

If S1 fails the cross-JVM join over Kafka, decision 7's fallback applies before any executor starts: participants do not join, the orchestrator registers compensation routes that send ReleaseStock and RefundPayment commands, and the LRA coordinator still owns timeout and cancel. If S3 fails, decision 10's fallback applies. If S2 shows no listen timeout, the Flow path uses an emitted delayed timer event, or the 23 check is restated.

## Build, run, observe rule (owner, 2026-11-13, unchanged)

- One code block with the commands, and one or two sentences ending with the pass count.
- At most three one-sentence `Try` bullets naming a demo switch and its visible effect.
- 250 words as a soft cap; no check numbers, test lists or run statistics (those go in the README).
- Chapter 24 adds one sentence that the first run needs `scripts/ollama-model.sh pull` and that `MODEL=off ./verify.sh` runs without a model.

## Steps

| Step | Work | Files | Owner | Depends | Parallel | Stack | Status |
|---|---|---|---|---|---|---|---|
| 0 | Preflight: branch release/r09 exists; iteration-plan r09 in progress (done at 7f9568f); save this plan | `_plans/r09-plan.md` | orchestrator | owner OK | none | no | pending |
| 1A | Re-survey (complete the table above; decision 3 image labels; model digests); sources and bibliography rows (Garcia-Molina and Salem; Hohpe Process Manager; Richardson Saga; Camel Saga and LRA; MicroProfile LRA; Narayana coordinator; Quarkus Flow; SW DSL; quarkus-langchain4j; Ollama; model card; Drools rule units); `*-src-*` rows | `_plans/versions.md`, `_plans/sources.md`, `bibliography.md` | exec 1A | 0 | 1B, 1C | no | pending |
| 1B | Spikes S1 to S6 | scratchpad | exec 1B | 0 | 1A, 1C | lock, serial | pending |
| 1C | domain-model 2.4.0 (below); `_plans/domain-model.md` rows 22 to 24, Services table (payment reacts to OrderCancelled; command consumers in 23), topic notes, version line | `examples/domain-model/**`, `_plans/domain-model.md` | exec 1C | 0 | 1A, 1B | mvn under lock | pending |
| 1D | Infra: `examples/_infra/compose-lra.yaml`, `compose-ollama.yaml` (profile `model`, external volume), `scripts/ollama-model.sh`; `_infra/README.md` rows | `examples/_infra/*`, `scripts/ollama-model.sh` | orchestrator | S1, S4 | after 1B | lock | pending |
| G0 | Gate: spikes pass or fallbacks chosen; owner confirms decisions 1 to 3 with spike data; install domain-model 2.4.0; bump every example pom 00 to 21 and `mvn -B verify` each under the lock; "G0 decisions" appended | this plan, example poms | orchestrator | 1A to 1D | none | mvn under lock | pending |
| 2A | Ch 22: three modules, connector JSON, tests, Containerfiles, compose, README, example page, figure specs and outputs, chapter draft without numbers | `examples/22-*/**`, `_docs/22-*.md`, `_example_pages/22-*.md`, `diagrams/specs/22-*.py`, `diagrams/22-*.*` | exec 2A | G0 | with 2B, 2C | mvn under lock | pending |
| 2B | Ch 23: same set; Flow workflow and Flyway; LRA config | `examples/23-*/**`, `_docs/23-*.md`, `_example_pages/23-*.md`, `diagrams/specs/23-*.py`, `diagrams/23-*.*` | exec 2B | G0 | with 2A, 2C | mvn under lock | pending |
| 2C | Ch 24: same set; DRL, prompt template, corpus and fixture, python oracle | `examples/24-*/**`, `_docs/24-*.md`, `_example_pages/24-*.md`, `diagrams/specs/24-*.py`, `diagrams/24-*.*` | exec 2C | G0 | with 2A, 2B | mvn under lock | pending |
| 2D | Consistency: backward links in 22 to 24 to 08, 11, 12, 13, 17, 19, 20; check that 12's "Kafka only" sentence and 22's opening agree; chapter 00 setup surface gains the model pull and the coordinator image; claims rows drafted | `_plans/claims.md`, `_docs/00-*.md` if needed | orchestrator | G0 | yes | no | pending |
| 3 | Runtime windows, serial, exclusive, 150 min cap each, order 23, 22, 24. Verify N/N twice from clean; three bench runs; every experiment once; 15-run loops (22: check 10; 23: checks 9, 10; 24: checks 3 to 6 on the corpus, and the analysis fixture captured); bite proofs; numbering frozen | as 2A to 2C | orchestrator | 2A to 2C | serial | exclusive | pending |
| 4 | Orchestrator re-run of every prose experiment, logged with failing check numbers | `_plans/verification-log.md` | orchestrator | 3 | serial | yes | pending |
| 5 | Final serial run 00 to 24 (fresh containers each, one flock, no `edp-*` left); 24 runs with the model and with `MODEL=off`; static gates; claims and log; per-chapter commits with `python3 scripts/host_ids.py check` first | claims, log | orchestrator | 4 | serial | yes | pending |
| 6 | Opus validation (max 2 repair rounds): claims audit; check-bite audit; timing and model witness audit (15 runs or rate stated); bench consistency; 7-gram originality against the Camel Saga page, Quarkus Flow docs, datamesh ch 13, 14, 21, EIP Camel ch 10 and Appendices F and G, and CNDP App D; licence audit of every image and model; fresh clone; word counts; figures byte-identical | none | validator | 5 | none | yes | pending |
| 7 | Ship: PR, CI green (mvn for 22 to 24 without Ollama), squash on owner OK, tag r09.0, notes-only release, delete the branch; PRD rows 22 to 24 and Q12 (if decision 1 changes the model) rewritten; roadmap and status; iteration-plan done; CLAUDE.md decisions entry and port list | as listed | orchestrator | 6 | none | no | pending |

Executors:

- They make no git writes, and do not edit `claims.md`, `verification-log.md`, `CLAUDE.md`, `examples/_infra`, `validate.py` or `domain-model` (1C only).
- `mvn verify` runs only under `flock -w 7200 <scratchpad>/stack.lock`. Step 2 executors run no compose, no `verify.sh` and no Ollama.
- Everything else as in r08: fresh-start rule, `<repo>` and `~/` only, captured logs through `host_ids.py filter`. Model latency is stated for a CPU class ("8-core/16-thread laptop CPU"), never the part.

**domain-model 2.4.0 (1C).**

- `Topics` constants, none in `ALL`, each with Javadoc:
  - `INVENTORY_COMMANDS = "edp.inventory.commands"` (ReserveStock and ReleaseStock from an orchestrator, keyed by order id).
  - `PAYMENTS_COMMANDS = "edp.payments.commands"` (TakePayment and RefundPayment).
  - `SHIPPING_DELIVERY_EXCEPTIONS = "edp.shipping.delivery-exceptions"` (a carrier's free-text note on a Shipment).
  - `SHIPPING_EXCEPTION_ANALYSES = "edp.shipping.exception-analyses"` (the recorded reason, model and prompt version).
  - `SHIPPING_EXCEPTION_DECISIONS = "edp.shipping.exception-decisions"` (RESHIP or REFUND, with the rule).
- Test `orchestrationAndDecisionTopicsAreOutsideTheCatalog` (regex, not in `ALL`, distinct).
- `_plans/domain-model.md`:
  - version line 2.4.0;
  - a note that commands are messages to an existing service about an existing entity;
  - a note that RESHIP and REFUND are decision values and that the reason enum is a payload field;
  - a note that `DeliveryExceptionReported` is a message about `Shipment` (no new entity);
  - Services table: payment-service reacts to InventoryReserved and OrderCancelled; in chapter 23, inventory and payment also read their command topics.

**domain-model.md rows (1C):**

- **22:** "the order flow to PAID choreographed over the catalog topics by order, inventory and payment services, each publishing through the outbox; PaymentFailed and OrderCancelled compensate (release stock, cancel order) idempotently and commutatively; order-service owns the saga state and a payment deadline in PostgreSQL; payment-service refuses a payment for an order it has seen cancelled".
- **23:** "the same flow orchestrated from order-service two ways over the same participants with commands on `edp.inventory.commands` and `edp.payments.commands`: the Camel Saga EIP with a Narayana LRA coordinator (participants join and register their compensation) and a Quarkus Flow workflow with its state in PostgreSQL (compensation as workflow tasks); a payment service that does not answer; decision table against chapter 22".
- **24:** "a carrier's free-text delivery-exception note on a `Shipment`: a LangChain4j AI service on a local Ollama model extracts a closed reason, recorded on `edp.shipping.exception-analyses`; a Drools rule unit decides RESHIP or REFUND from the recorded reason and attempt, recorded on `edp.shipping.exception-decisions`; a replay re-decides from the record without the model".

## Acceptance criteria

**22:**

- verify N/N, N >= 20, from clean twice; mvn green with >= 14 tests, including the race test, the commutative release and the guard-off test asserting the bad outcome.
- Checks 6 to 8 show compensation once.
- Check 10 green in 15 of 15 runs, or the README states the rate.
- `PAYMENT_CANCEL_GUARD=off` fails exactly 13 and 17, in step 3 and step 4.
- One connector, three outbox tables; no service publishes outside its transaction.

**23:**

- verify N/N, N >= 20, from clean twice; mvn green with >= 14 tests, running on `InMemorySagaService` with no coordinator, and Flow tests with Dev Services.
- Both orchestrators reach the same terminal states for the same inputs (checks 2 to 7).
- The timeout checks 9 and 10 are green 15 of 15 or rated.
- Check 12 shows no payment for the Camel late join and check 13 shows REFUNDED for Flow.
- `PAYMENT_JOIN=off` and `FLOW_COMPENSATION=off` fail their stated checks only.
- Chapter prose <= 2,500 words without a split.

**24:**

- `mvn -B verify` green in CI with no Ollama and no network model pull (Dev Services off; asserted by a test that `quarkus.langchain4j.ollama.devservices.enabled` is false in the test profile); >= 14 tests, including the exhaustive rules table and the golden prompt.
- verify with the model N/N, N >= 16, from clean twice; `MODEL=off ./verify.sh` green.
- Check 10 shows a zero model-call delta on replay; `REPLAY_MODE=reanalyse` fails check 10.
- Every corpus note in `verify.sh` has 15 of 15 identical reasons logged; any note that does not is in the README with its rate and not in `verify.sh`.
- The model's licence is an OSI-approved licence (or the owner's recorded exception).
- The model digest is pinned in `verify.sh` and versions.md; there is no `pull` inside `verify.sh`.

**Every chapter:**

- Prose <= 2,500 words; exactly one callout in the house form; >= 2 figures (22 and 23 have 3) from specs.
- BRO within the rule; `part: processing-and-coordinating-streams`.
- No dashes, versions, test names, self-reference or banned names.
- UBI 10 Containerfiles per the convention; `/q/health/ready` healthchecks; env conventions (`CAMEL_COMPONENT_KAFKA_BROKERS`, `KAFKA_BOOTSTRAP_SERVERS` for SmallRye and Flow messaging, `QUARKUS_DATASOURCE_*`, `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT`, plus `CAMEL_LRA_*` and `QUARKUS_LANGCHAIN4J_OLLAMA_BASE_URL`).
- One Prometheus check with `service_name`; topics only from `Topics` 2.4.0; FASTLINE and AIRCO only.
- Demo switches labelled; every claim verified or cut; every experiment in the final logs.

**Iteration:**

- Static gates green (`validate.py`, voice-scan, idiomatic-scan, `host_ids.py check`, site-build).
- Every example pom 00 to 24 on domain-model 2.4.0 with `mvn -B verify` green.
- Final serial run 00 to 24 logged; versions.md records Quarkus Flow 1.3.0, Drools 10.2.0, Ollama 0.40.16, the model and digest, the coordinator image and digest, and the langchain4j platform hold with reasons.
- CI green; tag r09.0; PRD rows 22 to 24, Q12 if changed, and CLAUDE.md decisions and ports updated.

## Sources to fetch and confirm (1A)

- Camel 4.22.x Saga EIP and LRA (recorded above); Camel Quarkus LRA extension; camel-lra configuration (Quarkus property names).
- MicroProfile LRA 2.0 spec: join a closed or cancelled LRA (status code), participant callbacks, time limit.
- Narayana LRA coordinator: quay README (fetched) and image labels.
- Quarkus Flow 1.3.0: messaging, persistence (fetched); DSL cheatsheet (fetched); the listen-timeout and try/catch DSL pages; Java cookbook; configuration reference.
- Serverless Workflow DSL reference: `timeout`, `try`/`catch`, `listen`; confirm no compensation keyword in 1.0.
- quarkus-langchain4j: Ollama page (fetched), AI services, structured output / response schema, observability spans.
- Ollama API docs: `format` with JSON schema, `options.seed`, `temperature`, `top_k`, `num_predict`, keep-alive and env vars.
- Model cards and licences: Qwen2.5-3B-Instruct (qwen-research, recorded), Qwen2.5-1.5B-Instruct, Qwen3-4B-Instruct-2507, granite-3.3-2b-instruct (apache-2.0 per HF API).
- Apache KIE Drools 10.x rule units with Quarkus (current docs URL).
- Hohpe and Woolf Process Manager; Richardson Pattern: Saga; Garcia-Molina and Salem (Crossref confirmed, recorded).
- Lawrence ch 7 and Bellemare ch 10 (titles from the TOC rows; no re-fetch).
- Sibling links: EIP Camel ch 10, Appendices F and G; CNDP Appendix D; datamesh ch 13, 14, 21 (URLs from the sibling sites; the datamesh site is not a book, so it is cited only if the hub lists it).

## Risks

| Risk | Detection | Response |
|---|---|---|
| Model licence (qwen2.5:3b is Qwen Research) | found here | decision 1: Apache-2.0 model chosen by S4 |
| LRA coordinator image has only date tags, possibly snapshot builds | 1A labels | decision 3: pin by digest with the recorded LRA version, or the last release-named tag |
| camel-lra client incompatible with the 2.x coordinator | S1 | pin the coordinator line that works (7.2.2.Final-3.29.3), recorded as a hold |
| `Long-Running-Action` lost or mistyped across Kafka | S1 | header deserializer or explicit `setHeader` from a string; the decision 7 fallback (orchestrator-registered compensation) |
| Participant join after cancel is not refused | S1 | the countermeasure from 22 in the participant (a cancelled-order record), and the claim restated |
| Quarkus Flow 1.3.0 (built on Quarkus 3.39) conflicts with 3.40.1.2 or camel-bom | S2 `dependency:tree`, build | pin the newest Flow that converges; else run Flow in its own module, same compose |
| Flow `listen` has no timeout in the DSL | S2 | an emitted delayed timer event or a workflow-level timeout with catch; prose follows the observed behaviour |
| A Flow instance does not resume after a restart | S2 | check 18 restated or cut; the decision table row says so |
| drools-quarkus-ruleunits 10.2.0 fails on Quarkus 3.40 | S3 | `drools-ruleunits-engine` without the extension, else KieSession (decision 10) |
| Model output unstable at temperature 0 on CPU | S4 15 runs | keep only stable notes in `verify.sh`; rates in the README; `MODEL=off` path for rules and replay |
| Model too slow on CPU (timeouts, long runs) | S4 latency | a smaller model (qwen2.5:1.5b), `num-predict` cap, a 60 s timeout, warm-up precondition |
| Ollama Dev Services starts in tests or CI | test log grep in step 2 | `%test...devservices.enabled=false`; a test asserting it |
| quarkus-langchain4j does not send the schema as `format` | S4 request log | set `chat-model.format` explicitly to the schema, or `json` plus the schema in the prompt and the enum parse guard |
| External model volume breaks the fresh-start rule | owner | decision 2 records the exception; a digest precondition proves the input is the pinned one |
| Stack memory (22: Connect plus three services; 24: Ollama 4g) | S5, S6 `docker stats` | mem_limits recorded; one stack at a time |
| Chapter 23 over 2,500 words | draft count | the budget above; cut order stated; no split |
| Saga timeout witnesses flaky (sweeper 1 s, coordinator timer, Flow timer) | 15-run loops | wide windows; preconditions read state first; rates in the README |
| Overlap with datamesh ch 13, 14, EIP Camel Appendices F and G, CNDP App D | validator 7-gram | own scenario and code; links |
| Banned names or invented entities ("manifest", a new "Exception" entity) | `validate.py`; domain review | messages about Shipment only |
| Host identifiers in model or latency logs | `host_ids.py check` | filter; CPU class only |

## Owner decisions (recommendations)

1. **Model and licence.** `qwen2.5:3b` (PRD Q12) is under the Qwen Research License (Hugging Face `license_name: qwen-research`), which breaks "open source only". Recommendation: **replace it with an Apache-2.0 model chosen by S4 on the corpus**. Prefer `qwen3:4b-instruct-2507-q4_K_M` (2.5 GB, non-thinking) if its warm latency per note is acceptable (target <= 15 s) and its stability is 15 of 15. Otherwise use `qwen2.5:1.5b` (about 1 GB, same family), with `granite3.3:2b` as the third candidate. Update PRD Q12 and CLAUDE.md at ship. Alternative: keep qwen2.5:3b as a recorded licence exception (not recommended).
2. **Model caching versus the fresh-start rule.** Recommendation:
   - **one external Docker volume `edp-ollama-models`, filled once by `scripts/ollama-model.sh pull`, mounted read-only if S4 allows**, treated like an image (an input, not state);
   - `verify.sh` asserts the pinned digest as a precondition and never pulls; `down -v` leaves it (external);
   - `validate.py` gains an allowance for exactly this volume name.
   Alternative: pull in every run (2 to 3 GB of download per run; network-dependent evidence).
3. **LRA coordinator image.** Recommendation: **pin the newest date tag (`2026-10-26`, by digest) if 1A shows it contains Narayana LRA 2.0.1.Final** (a final release) built on a final Quarkus; record that upstream publishes date tags only since 2025-11. Otherwise pin `7.2.2.Final-3.29.3` (the last release-named tag) as a hold with the reason.
4. **Chapter 22 atomic publish.** Recommendation: **outbox in all three services, one Debezium connector over three outbox tables** (ch 08 and 14 machinery; a saga step that commits without its event strands the saga). Alternative: SmallRye publish after commit plus idempotent consumers, with the gap stated in prose.
5. **Chapter 22 scope.** Recommendation: **the saga ends at PAID (order, inventory, payment)**. Shipping is named as the retriable step after the pivot; no shipping-service.
6. **Countermeasure.** Recommendation: **payment-service reacts to OrderCancelled** and refuses a payment for an order it has seen cancelled (a semantic lock); `PAYMENT_CANCEL_GUARD=off` is the negative witness. Domain Services table updated.
7. **Chapter 23 transport.** Recommendation: **commands over Kafka** (`edp.inventory.commands`, `edp.payments.commands`), with replies on the catalog topics; LRA callbacks over HTTP between coordinator and services only (coordinator infrastructure, not domain traffic). Participants join with `propagation(SUPPORTS)`. Fallback (if S1 fails the cross-JVM join): the orchestrator registers compensations that send release and refund commands.
8. **Chapter 23 shape.** Recommendation: **one order-service hosting both orchestrators side by side over the same participants** (r08 precedent), with order-id ranges per form and both forms in every run.
9. **Chapter 23 split.** Recommendation: **no split.** Plan to 2,400 words with the section budget and the stated cut order. A split renumbers 24 to 35, the PRD and every downstream reference.
10. **Drools.** Recommendation: **`drools-quarkus-ruleunits` 10.2.0** (rule units, no KIE Server) through `drools-bom`, if S3 passes on Quarkus 3.40.1.2 (it is built on 3.27.2). Fallback: `drools-ruleunits-engine` without the extension, else KieSession as in the siblings, recorded.
11. **LangChain4j integration.** Recommendation: **quarkus-langchain4j-ollama `@RegisterAiService` returning a record, called from a Camel `.bean()`**; platform-managed 1.13.4 (no override). Not camel-quarkus-langchain4j-chat (the datamesh DEF-001 client override).
12. **Chapter 24 rules and the unknown case.** Recommendation: the six rules above. **UNKNOWN goes to REFUND** (the customer is not left waiting, and it is one visible line a business can change), keeping the decision set to RESHIP or REFUND as the PRD says. Alternative: a third value REVIEW (a PRD change).
13. **Chapter 24 with the model down.** Recommendation: **wait** (`breakOnFirstError`, redelivery, lag shown), with no fallback analysis; nothing is decided without an analysis.
14. **Chapter 24 state.** Recommendation: **no PostgreSQL**. The carrier's attempt number travels on the note, so a decision is a pure function of the recorded analysis and replay needs no state.
15. **Topics.** Recommendation: **domain-model 2.4.0** with the five constants above (none in `ALL`); every example pom bumped at G0.
16. **Ports.** Recommendation: three-service examples use **8080, 8082, 8084**; LRA coordinator host **8091**; Ollama host **11434**; CLAUDE.md port list updated at ship.
17. **Quarkus Flow version.** Recommendation: **1.3.0** through `quarkus-flow-bom`, imported after the platform BOMs, pinned in the example pom with a versions.md row (not platform-managed, so the newest final applies).
18. **Platform holds.** Recommendation: **quarkus-langchain4j 1.13.4 / langchain4j 1.19.3 as managed by the platform** (upstream 1.15.1 not adopted; same rule as kafka-clients). spring-jdbc row 9 unchanged (7.0.9; not used in r09).
19. **verify.sh modes for 24.** Recommendation: the default run uses the model; `MODEL=off` runs the rule, replay and LGTM checks on a recorded fixture. CI runs mvn only.
20. **Demo switches** (labelled demo code):
    - `PAYMENT_DECLINE_ABOVE`, `PAYMENT_CANCEL_GUARD`, `SAGA_PAYMENT_TIMEOUT` (22);
    - `PAYMENT_JOIN`, `FLOW_COMPENSATION`, `SAGA_TIMEOUT` (23);
    - `REPLAY_MODE`, `RESHIP_ATTEMPT_LIMIT`, `MODEL`, `ANALYST` and `/demo/replay` (24).
21. **Examples and slugs.** `examples/22-saga`, `examples/23-orchestrated-sagas`, `examples/24-decisions-on-events`; chapter slugs the same; packages `...saga`, `...orchestration`, `...decisions`.
22. **Citations.**
    - Garcia-Molina and Salem (new bibliography row); Hohpe and Woolf Process Manager page (new row); Richardson Pattern: Saga (new row).
    - Lawrence ch 7; Bellemare ch 10 (22, 23) and ch 9 (24).
    - Camel, Quarkus Flow, quarkus-langchain4j, Ollama and Drools docs as the API authority.
    - EIP Camel ch 10 and Appendices F and G, and CNDP App D as sibling links.
23. **Runtime window order:** 23 (most moving parts), then 22, then 24 (model loops last, so the CPU is free).
24. **At ship:** rewrite PRD rows 22 to 24 in domain wording and Q12 if decision 1 changes the model; update the roadmap and status; add the CLAUDE.md r09 decisions entry and ports.

### Critical files for implementation

- `<repo>/_plans/domain-model.md` and `<repo>/examples/domain-model/src/main/java/com/patterncatalyst/shipping/Topics.java` (2.4.0 constants, rows 22 to 24, Services table)
- `<repo>/examples/14-aggregate-root-and-event-store/connect/order-outbox.json` and `<repo>/examples/08-transactional-outbox/order-service/src/main/java/com/patterncatalyst/shipping/outbox/OrderService.java` (the outbox shape chapter 22 multiplies)
- `<repo>/examples/11-idempotent-consumer-and-inbox/inventory-service/src/main/java/com/patterncatalyst/shipping/inbox/ReservationService.java` (inbox in one transaction; the release must be idempotent and commutative)
- `<repo>/examples/20-join/verify.sh`, `compose.yaml` and `pom.xml` (latest verify helpers, BOM import order with camel-bom first, Containerfile and compose conventions)
- `<repo>/scripts/validate.py` (fresh-start rule needing the decision 2 allowance, `BANNED_DOMAIN`, word and BRO limits) and `<repo>/examples/_infra/` (new `compose-lra.yaml`, `compose-ollama.yaml`)

Sibling references (read only, not copied): `~/Dev/datamesh-reference-arch-quarkus/examples/ai-rules-service/pom.xml` (Drools 10.2.0, quarkus-flow 1.1.3, langchain4j BOM order and the DEF-001 note), `~/Dev/datamesh-reference-arch-quarkus/_docs/14-ai-rules-triage.md`, `~/Dev/enterprise-integration-patterns-with-camel/_docs/10-composed-routing.md` (Process Manager prose), `~/Dev/cloud-native-design-patterns/_docs/17-appendix-d-sagas.md`.

Sources used in this session: [Camel Saga EIP 4.22.x](https://camel.apache.org/components/4.22.x/eips/saga-eip.html), [Camel LRA 4.22.x](https://camel.apache.org/components/4.22.x/others/lra.html), [Camel Quarkus LRA](https://camel.apache.org/camel-quarkus/latest/reference/extensions/lra.html), [Quarkus Flow intro](https://docs.quarkiverse.io/quarkus-flow/dev/index.html), [messaging](https://docs.quarkiverse.io/quarkus-flow/dev/messaging.html), [persistence](https://docs.quarkiverse.io/quarkus-flow/dev/persistence.html), [DSL cheatsheet](https://docs.quarkiverse.io/quarkus-flow/dev/dsl-cheatsheet.html), [fault tolerance](https://docs.quarkiverse.io/quarkus-flow/dev/fault-tolerance.html), [Serverless Workflow DSL](https://github.com/serverlessworkflow/specification/blob/main/dsl.md), [quarkus-langchain4j Ollama](https://docs.quarkiverse.io/quarkus-langchain4j/dev/ollama-chat-model.html), [Ollama qwen2.5 tags](https://ollama.com/library/qwen2.5/tags), [qwen3 tags](https://ollama.com/library/qwen3/tags), [granite3.3 tags](https://ollama.com/library/granite3.3/tags), [Drools docs](https://docs.drools.org/latest/drools-docs/drools/KIE/index.html), [SonataFlow saga example (background only)](https://kie.incubator.apache.org/docs/10.2.x/sonataflow/serverlessworkflow/1.43.0.Final/use-cases/orchestration-based-saga-pattern.html), quay.io, Docker Hub, GitHub, Hugging Face, Crossref and Maven Central APIs as cited in the tables.
