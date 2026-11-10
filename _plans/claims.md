---
layout: plan
title: "Claims"
render_with_liquid: false
---

# Claims

Every factual claim in a chapter is listed here with its evidence. Evidence is never written into the prose.

Statuses:

- pending: written, evidence not yet run or fetched
- verified: the test, recorded run (see `verification-log.md`) or fetched source confirms it
- cut: removed from the chapter because it could not be verified

Evidence is a test, a run or a source. Ref is the test class, log entry or bibliography entry.

| id | chapter | claim | evidence (test, run or source) | ref | status |
|---|---|---|---|---|---|
| 00-01 | 00 | One local stack: Kafka, PostgreSQL, registry, Connect with Debezium, LGTM | verify.sh 00 checks 1 to 6 | _infra/compose.yaml; log 2026-11-10 | verified |
| 00-02 | 00 | SDKMAN installs under `$HOME/.sdkman` by default | https://sdkman.io/install | fetched 2026-11-10 | verified |
| 00-03 | 00 | SDKMAN lists bash, zip, unzip and curl as its basic toolset | https://sdkman.io/install | fetched 2026-11-10 | verified |
| 00-04 | 00 | Versions are exact, newest stable that works, no licence | _plans/versions.md | survey 2026-11-10 | verified |
| 00-05 | 00 | Temurin 25.0.5 LTS | api.adoptium.net | versions.md | verified |
| 00-06 | 00 | Maven 3.10 released; Quarkus has open issues against it | quarkusio/quarkus #57216 | versions.md | verified |
| 00-07 | 00 | Quarkus 3.40.1.2 LTS with Camel Quarkus 3.40.0 and Camel 4.22.1 | code.quarkus.io streams; quarkus-camel-bom pom | versions.md | verified |
| 00-08 | 00 | Kafka 4.3.2 is the newest the latest Strimzi supports | Strimzi 1.3.1 kafka-versions.yaml | versions.md | verified |
| 00-09 | 00 | PostgreSQL 18.6: CNPG operand on compose, same minor for Dev Services | compose-postgres.yaml; mvn log postgres:18.6 | log 2026-11-10 | verified |
| 00-10 | 00 | Debezium Connect 3.7.1.Final has PostgresConnector and EventRouter | verify.sh 00 checks 11, 12 | log 2026-11-10 | verified |
| 00-11 | 00 | otel-lgtm: Loki, Grafana, Tempo, Prometheus behind one OTLP endpoint | verify.sh 00 checks 13 to 15 | log 2026-11-10 | verified |
| 00-12 | 00 | UBI 10 OpenJDK 1.24-19 for build and runtime | Containerfiles | versions.md | verified |
| 00-13 | 00 | Docker 29.9.3, Compose 5.7.0 on the reference host | docker version | versions.md | verified |
| 00-14 | 00 | One compose file per service, included on network `edp` | _infra/*.yaml; example compose files | repo | verified |
| 00-15 | 00 | Kafka host 9092, containers kafka:9094 | compose-kafka.yaml | repo | verified |
| 00-16 | 00 | PostgreSQL wal_level=logical on 5432 | verify.sh 00 check 9 | log 2026-11-10 | verified |
| 00-17 | 00 | Apicurio 8081 in memory, health 19000 | compose-apicurio.yaml; verify.sh 00 check 10 | log 2026-11-10 | verified |
| 00-18 | 00 | LGTM ports 3000, 4317, 4318, 19090, 3200, 3100 | compose-lgtm.yaml; infra run | log 2026-11-10 | verified |
| 00-19 | 00 | Cockpit, often enabled on Fedora and RHEL, listens on 9090 | Cockpit project default port (reworded to "often enabled") | general; no fetch | verified (reworded) |
| 00-20 | 00 | Memory limits and health checks; about 7 GB total | _infra mem_limit values + app | repo | verified |
| 00-21 | 00 | A second stack fails to bind the fixed ports | compose port mappings | repo | verified |
| 00-22 | 00 | stack-guard refuses another compose project or minikube node, stops nothing | scripts/stack-guard.sh; executor test with foreign container | step 5 | verified |
| 00-23 | 00 | service.name stack-check, OTLP http/protobuf to lgtm:4318 | application.properties; compose.yaml | repo | verified |
| 00-24 | 00 | Grafana needs no login | compose-lgtm.yaml GF_AUTH_ANONYMOUS_* | repo | verified |
| 00-25 | 00 | Prometheus `target_info{service_name="stack-check"}` | verify.sh 00 check 14 | log 2026-11-10 | verified |
| 00-26 | 00 | Loki `{service_name="stack-check"}`; consumer logs each record | verify.sh 00 check 15 | log 2026-11-10 | verified |
| 00-27 | 00 | UBI 10 multi-stage build, context examples/ | Containerfile | repo | verified |
| 00-28 | 00 | domain-model installed first for mvn, built in-image for verify.sh | run blocks; Containerfile | repo | verified |
| 00-29 | 00 | `mvn -B verify` needs no compose stack (Dev Services) | mvn log apache/kafka:4.3.2 | log 2026-11-10 | verified |
| 00-30 | 00 | verify.sh prints PASS/FAIL, N/N, non-zero on failure; KEEP=1 | verify.sh | repo | verified |
| 00-31 | 00 | Each verify.sh has a negative witness | 00 checks 16, 17; 01 checks 6 to 9 | log 2026-11-10 | verified |
| 00-32 | 00 | POST /consignments: invalid 400, valid published keyed by id, consumer logs | ConsignmentIntakeTest; verify.sh 00 checks 7, 8, 15, 16 | log 2026-11-10 | verified |
| 00-33 | 00 | Negative weight or blank origin fails in the constructor | ConsignmentIntakeTest (weight -5; blankOriginIsRejectedWith400) | repair round 1 | verified |
| 00-34 | 00 | onException turns it into 400 before Kafka | ConsignmentIntakeTest; verify.sh 00 check 17 | log 2026-11-10 | verified |
| 00-35 | 00 | camel-quarkus-opentelemetry2 creates a span per route and processor | Tempo trace of POST /consignments: SERVER span per route consumer, INTERNAL per processor, Kafka PRODUCER parent of consumer span | validator run 2026-11-10 | verified |
| 00-36 | 00 | camel-quarkus-opentelemetry is deprecated | camel-quarkus-opentelemetry 3.40.0 quarkus-extension.yaml status: deprecated | validator | verified |
| 00-37 | 00 | quarkus-micrometer-opentelemetry exports meters over OTLP; no scrape | pom.xml; verify.sh 00 check 14 | repo | verified |
| 00-38 | 00 | quarkus.otel.logs.enabled sends logs; SmallRye Health on /q/health | application.properties; health test | repo | verified |
| 00-39 | 00 | Tests pass on Dev Services Kafka with the pinned image | mvn log apache/kafka:4.3.2 | log 2026-11-10 | verified |
| 00-40 | 00 | verify.sh 17/17 | verify.sh 00 | log 2026-11-10 | verified |
| 00-41 | 00 | Removing onException: invalid gets 500, check 16 fails, 17 passes | experiment run (16/17; mvn "expected 400 but was 500") | repair round 1 | verified |
| 00-42 | 00 | Renaming the producer topic fails checks 8 and 15 | experiment run (15/17) | repair round 1 | verified |
| 00-43 | 00 | Part 6 runs on minikube with Strimzi, CloudNativePG, same LGTM | roadmap statement; verified at r11 | iteration-plan r11 | pending (r11) |
| 00-44 | 00 | Camel Kafka brokers via `camel.component.kafka.brokers` in compose | container failed to start without it; verify.sh 17/17 with CAMEL_COMPONENT_KAFKA_BROKERS | repair round 1 | verified |
| 01-01 | 01 | Event named in past tense; sender neither knows nor cares who reads it | book's own definition | n/a | verified |
| 01-02 | 01 | Hohpe and Woolf: Event Message for reliable, asynchronous event notification | enterpriseintegrationpatterns.com EventMessage | fetched 2026-11-10 | verified |
| 01-03 | 01 | Fowler separates four patterns: notification, ECST, event sourcing, CQRS | martinfowler.com/articles/201701-event-driven.html | fetched 2026-11-10 | verified |
| 01-04 | 01 | Chapters 02 and 03 cover the first two | iteration-plan / PRD §5 (02 notification, 03 ECST) | plan | verified |
| 01-05 | 01 | consignment_event columns stream_id, version, type, jsonb payload, occurred_at | V1 migration; ConsignmentEvent | repo | verified |
| 01-06 | 01 | Hibernate never writes changes to a loaded event back to the table | loadedEventChangesAreNeverWrittenBack; fails without @Immutable | repair round 1 | verified |
| 01-07 | 01 | (cut) Camel carries the same events over Kafka from chapter 02 | n/a | n/a | cut |
| 01-08 | 01 | Command Message invokes a procedure in another application through messaging | enterpriseintegrationpatterns.com CommandMessage | fetched 2026-11-10 | verified |
| 01-09 | 01 | ChangeConsignmentStatus: id, target status, optional expected version | ChangeConsignmentStatus.java | repo | verified |
| 01-10 | 01 | One CDI bean handles the command; lifecycle one step forward | ConsignmentService; Lifecycle.allows | repo | verified |
| 01-11 | 01 | Refused command: 409, no event | illegalTransitionIsRefusedAndAppendsNothing; verify.sh 01 checks 6, 7 | log 2026-11-10 | verified |
| 01-12 | 01 | (cut) In a Camel route the record is the body sent to one endpoint | n/a | n/a | cut |
| 01-13 | 01 | Document Message transfers a data structure; receiver decides what, if anything, to do | enterpriseintegrationpatterns.com DocumentMessage | fetched 2026-11-10 | verified |
| 01-14 | 01 | Event payload is a record per event type; replay reads typed fields | ConsignmentEventPayload; replayEqualsRow | repair round 1 | verified |
| 01-15 | 01 | ConsignmentDocument from GET /consignments/{id} | ConsignmentResource | repo | verified |
| 01-16 | 01 | Rebuilt-from-events document is identical JSON | replayEqualsRow; verify.sh 01 check 3 | log 2026-11-10 | verified |
| 01-17 | 01 | Row model overwrites; previous status gone | verify.sh 01 check 4 | log 2026-11-10 | verified |
| 01-18 | 01 | Handler loads, replays, checks, inserts next version | ConsignmentService.appendEvent | repo | verified |
| 01-19 | 01 | Racing writers collide on UNIQUE(stream_id, version) | stale-expected-version test (simulated race) | repo | verified |
| 01-20 | 01 | Trigger raises on UPDATE or DELETE | appendOnlyTriggerRefusesUpdateAndDelete; verify.sh 01 checks 8, 9; trigger witness | log 2026-11-10 | verified |
| 01-21 | 01 | Replay is a left fold giving the row's document | ConsignmentDocument.replay; replayEqualsRow | repo | verified |
| 01-22 | 01 | History endpoint returns StatusHistory: id, each status with version and timestamp | priorStatusesComeFromEventsOnly; verify.sh 01 check 5 | log 2026-11-10 | verified |
| 01-23 | 01 | Only the stream can produce the history | ConsignmentService.history | repo | verified |
| 01-24 | 01 | Temporal query and new views by replay | Fowler, Event Sourcing | bibliography | verified |
| 01-25 | 01 | Row writers use an optimistic lock | ConsignmentRow @Version | repo | verified |
| 01-26 | 01 | Seven tests against Dev Services PostgreSQL; Flyway creates both tables | mvn log Tests run: 7 | repair round 1 | verified |
| 01-27 | 01 | verify.sh 10/10 | verify.sh 01 | log 2026-11-10 | verified |
| 01-28 | 01 | Lifecycle.allows true: 204 and event count grows | lifecycle witness ("expected 409 but was 204") | repair round 1 | verified |
| 01-29 | 01 | Deleting the trigger lets the UPDATE succeed | trigger witness | repair round 1 | verified |
| 01-30 | 01 | Two commands with expectedVersion 1: second 409 | stale-expected-version test | repo | verified |
| 01-31 | 01 | Remove the appendEvent call: row advances, stream stops | append witness (5 of 6 tests fail) | step 10 | verified |
| 01-32 | 01 | Long streams need snapshots; payloads are permanent contracts; corrections are new events | Fowler 2005, 2017 | bibliography | verified |
| 01-33 | 01 | Unique constraint does not make another system's change atomic | by construction | n/a | verified |
| 01-34 | 01 | Prefer a document when the receiver needs whole current state | design judgement (book's own guidance) | n/a | verified (own guidance) |
| 01-35 | 01 | Lawrence ch 2 and ch 6 titles | InformIT table of contents | sources.md | verified |
