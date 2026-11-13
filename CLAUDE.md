# CLAUDE.md

## Project overview

A chaptered tutorial site on event-driven patterns with Quarkus and Apache Camel on Kafka: event notification, event-carried state transfer, the transactional outbox, log tailing with Debezium, idempotent consumers, exactly-once, event sourcing, CQRS, schema evolution, stream processing, sagas, and tuning publishers and subscribers. Part 6 runs the same patterns on Kubernetes with Strimzi. Jekyll site plus one runnable example per chapter.

It is the in-depth companion to Enterprise Integration Patterns with Camel (EIP Camel, the model for layout, register, chapter spine and example structure) and a sibling of Cloud-Native Design Patterns (CNDP). Overlap with EIP Camel is expected: this book goes deeper on one stack. Quarkus and Camel only; Kafka only; no final project, no system spanning examples. Licences: code Apache-2.0, prose CC BY 4.0.

The product requirements live in Notion (PRD 41261a5f-8a3b-8148-9ffd-f5691ac1251d); the approved iteration plan is `_plans/iteration-plan.md`.

## Publishing rule: nothing identifies the machine or its owner

The repo is public, so no tracked file may name the development machine, its owner or anything that helps locate or attack it later:

- No absolute home-directory path (under /home or /Users), no account name, no hostname. Write `<repo>/...` or `~/...`.
- No laptop model (DMI `product_name` is `"redacted"`), no CPU model or model number. The CPU is a class ("8-core/16-thread laptop CPU"); prose names its properties, never the part.
- No host kernel build number or build date: give `major.minor` and the preempt model.
- No `/etc/machine-id`, DMI serial or UUID, MAC or IP address, `root=UUID=` or BOOT_IMAGE.
- Generated output is scrubbed before it is committed: `host_ids.py scrub <dir>` on a results directory, `host_ids.py filter` on a captured log (run inside the repo, or pass `--repo`, so the repo path becomes `<repo>`).
- `host_ids.py check` runs in CI and fails the build on a hit. Run it before every commit; planners and logs are the usual leak.

## House rules

- **Versions**: latest stable upstream, exact pins, never prerelease, `latest` or floating tags. Open source only; nothing needing a licence key, subscription or fee. JDK 25 minimum.
- **Images**: every image the book builds starts from UBI 10 (`registry.access.redhat.com/ubi10/openjdk-25`, `-runtime`). Fall back to `ubi9/openjdk-25` (same runtime, never a lower JDK) only on a real incompatibility, recorded next to the pin. Upstream images are used as published (Strimzi, CloudNativePG, LGTM, Debezium Connect, Ollama, LRA coordinator).
- **Idiomatic code**: Hibernate ORM with Panache, Quarkus REST, SmallRye Reactive Messaging, SmallRye Health, Micrometer and OpenTelemetry, Dev Services, Flyway; Camel Java DSL, one route per `RouteBuilder`. Nothing hand-rolled that the framework provides.
- **No vendor platforms in code**: nothing depends on OpenShift, CRC or a cloud. Appendix H is a demo of the same neutral code.
- **Fixed domain**: EIP Camel's order-to-delivery domain (services order, inventory, payment, shipping, notification). Use only the entity, event and topic names in `_plans/domain-model.md`; never invent an entity or an area. Carriers are FASTLINE and AIRCO, never a real carrier. Event payload records live per example, not in domain-model.
- **Evidence**: every claim in prose is backed by the example's tests, a recorded run or a fetched source. Evidence is never written into the prose; `_plans/claims.md` tracks it.
- **Credit sources**: cite every source by author and work in `bibliography.md`. Never copy text, code or examples.
- **Comparison code** is allowed as the exception, tested on the same input; idiomatic code is the lesson.
- **Fix now, do not defer**: defects and follow-ups are fixed in the current iteration and re-measured. Defer only content that belongs to a later part.
- **No host changes**: no sudo, packages, sysctl, firewall or kernel changes on the shared host.
- **No worktrees**: one branch, one PR. Parallel agents own disjoint files and run no writing git; the orchestrator commits.
- **Wording**: a test-altered file is a "deliberately altered copy", never "tampered".

## Stack rules

- Docker Engine and `docker compose` only (never Docker Desktop, never podman outside Appendix H). Fedora/RHEL hosts.
- One stack or cluster at a time. Stop it when idle. `scripts/stack-guard.sh` refuses a second stack.
- **No forwarding of any kind**: no `kubectl port-forward`, `minikube tunnel`, SSH tunnels (`ssh -L/-R/-D`), `socat` relays, IP forwarding (`net.ipv4.ip_forward`), iptables/nft/firewalld rules or NAT. Publish ports instead: compose `ports:`, and on minikube NodePorts published with `minikube start --ports`. Tunnels and forwards disconnect and change the host. `scripts/validate.py` fails the build on any of these in reader-facing content, examples, scripts or workflows.
- Prometheus is on host port 19090 (Cockpit holds 9090). Host ports: Kafka 9092, PostgreSQL 5432, Apicurio 8081 (management and health 19000), Connect 8083, Grafana 3000, OTLP 4317/4318, Tempo 3200, Loki 3100.
- Parts 1 to 5 run on compose (`lgtm-docker-stack`): Kafka KRaft, PostgreSQL on the CloudNativePG operand image, Apicurio, Debezium Connect (published `quay.io/debezium/connect` image first; our own UBI 10 image only on a real need), `grafana/otel-lgtm`.
- Part 6 runs on minikube with the docker driver and containerd (`lgtm-minikube-stack`): Strimzi KRaft, CloudNativePG, LGTM.
- OpenShift Local (CRC) appears only in Appendix H, through `lgtm-crc`, running the chapter 08 outbox.
- Kafka 4.3.2 in both run targets (held to what Strimzi supports); KRaft only, no ZooKeeper.

## Structure

```
_docs/            chapters and appendices (Jekyll docs collection)
_parts/           part cards
_plans/           iteration plan, domain mirror, claims, verification log (output: false)
_layouts/ _includes/ assets/   site machinery
diagrams/         specs/<name>.py -> <name>.svg + <name>.excalidraw
examples/_infra/  per-service compose files + aggregate; LGTM
examples/_k8s/    minikube manifests (Part 6)
examples/domain-model/   shared order-to-delivery records (Order, InventoryItem, Payment, Shipment, Notification, enums, Topics), package com.patterncatalyst.shipping
examples/NN-slug/ one Quarkus + Camel project, compose include, Containerfile, verify.sh, README
scripts/          validate.py, voice-scan.sh, idiomatic-scan.sh, host_ids.py, site-build.sh,
                  setup-stack.sh, stack-guard.sh, verify-all.sh
bibliography.md  LICENSE  LICENSE-docs.md
lgtm-presentation/   the deck (r15): text/, edits/, BUILD.md, QR slide
```

## Chapter spine and writing rules

Front matter: title, order, part, description, duration. `part` must match a `_parts` file's `part_name`. Quote values that contain colons.

- Write like EIP Camel, Geewax tone: problem before mechanism, show the code, state the trade-off, stop.
- About 2,000 words is a target, never a floor; 2,500 is the maximum, excluding code. Never pad.
- Pattern chapters: `## Pattern: <Name>` with `### The problem`, `### The solution` (one bold one-sentence definition), `### How Quarkus and Camel model it` (or `### How Quarkus models it` in a chapter whose example has no Camel, such as chapter 01); then `## Build, run, observe` (the commands, the pass count, one bullet per experiment: what to change, what fails), `## When not to use it`, `## References`.
- Exactly one AI callout per pattern chapter, in this form, with no HTML wrapper and no nested quote:
  > **🤖 Explore with your agent**
  > "<runnable prompt naming the example's own types and the chapter's decision rule>"
  > Check: <one line>
- At least one figure per chapter, from a spec. More than one is welcome wherever a figure explains a flow better than text. Every figure: `diagrams/specs/<name>.py` via lgtm-diagram-generator, output `diagrams/<name>.svg` and `.excalidraw`. Never hand-edit outputs.
- No em dashes or en dashes anywhere reader-facing (chapters, READMEs, example pages, figures, deck). Use commas, colons, parentheses or "to".
- No self-reference ("this chapter", "the example shows"), no test names in prose, no line-by-line code narration. State the behaviour.
- Versions appear only on setup surfaces (chapter 00, appendices), except where the version is the feature.
- Each chapter stands alone. No per-chapter verification footer; run records live in `_plans/verification-log.md`.

## Example contract

- One Quarkus + Camel project per chapter, the smallest service set that makes the pattern visible: a few classes, one or two routes, one topic or table per idea. A reviewer reads the whole example in one sitting.
- `verify.sh` checks the observable effect, not an exit code, and includes a negative witness: a check that fails when the pattern's guard is removed.
- Traces, metrics and logs reach LGTM through OpenTelemetry from chapter 00 on.
- Runs alone from a clean stack, with no other example running.
- Every live run starts fresh (owner rule, 2026-11-11): `verify.sh` first runs `docker compose down -v --remove-orphans`, then `up -d --build --force-recreate --renew-anon-volumes --wait`, and ends with `down -v` unless `KEEP=1`. Containers and volumes are never reused between runs. `scripts/validate.py` enforces it.
- Quarkus tests use Dev Services and run before compose. Dev Services uses `docker.io/library/postgres` at the same 18.x minor as the CloudNativePG operand image used by compose; Kafka Dev Services uses the pinned `apache/kafka` image.
- Topic convention: `edp.<domain>.<event>`, dots and hyphens only (for example `edp.orders.placed`).
- Multi-service examples: aggregator `pom.xml` at `examples/NN-slug/`, one module per service, app ports 8080 and 8082, replicas unpublished.
- Shared domain records come from `examples/domain-model`; no per-example copies. `mvn -B verify` in an example requires `examples/domain-model` installed first (`mvn -B -q install` there).
- The Camel Kafka component reads `camel.component.kafka.brokers`, not the Quarkus `kafka.bootstrap.servers` (Dev Services fills it only in dev and test). In compose set env `CAMEL_COMPONENT_KAFKA_BROKERS=kafka:9094`; SmallRye Reactive Messaging and Kafka Streams apps use `KAFKA_BOOTSTRAP_SERVERS`. Datasource and OpenTelemetry settings use Quarkus env mapping (`QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT`), with no custom placeholder indirection.
- Every app service in compose has a healthcheck on `/q/health/ready`, so `up --wait` covers it.
- Containerfiles follow the Quarkus `Dockerfile.jvm` convention: the build stage runs as user 185 with `mvn -B -ntp`; the runtime stage copies the four fast-jar layers (`lib/`, `*.jar`, `app/`, `quarkus/`) with `--chown=185` and uses the image's `run-java` default entrypoint with `JAVA_APP_JAR=/deployments/quarkus-run.jar`.

## Validate before commit

```bash
python3 scripts/validate.py
scripts/voice-scan.sh
scripts/idiomatic-scan.sh
python3 scripts/host_ids.py check
scripts/site-build.sh        # Jekyll in a ruby container
```

CI runs `mvn verify` per changed example; the live `verify.sh` runs locally and is logged in `_plans/verification-log.md`.

## Git flow

- One `release/rNN` branch and one PR per iteration. Squash-merge on the owner's OK. Tag `rNN.x`; the GitHub release is notes only. Delete the branch on merge.
- Conventional Commits with scopes `§N` (chapter), `demo-NN` (example) or `rNN.x` (iteration), for example `docs(§08): outbox chapter`.
- No Co-authored-by trailers.
- Run `python3 scripts/host_ids.py check` before every commit.

## Decisions log

PRD §14, decided:

- Q1 (2026-11-08): Docker Engine + `docker compose`; minikube (docker driver, containerd) for Part 6; CRC only in Appendix E (renumbered Appendix G on 2026-11-10 after the resequencing and deployment appendices were added, and Appendix H on 2026-11-11 when the write-forward cache appendix was added).
- Q2 (2026-11-10): Debezium for CDC; Connect container on compose, `KafkaConnect`/`KafkaConnector` on Strimzi (ch 35); embedded `camel-quarkus-debezium-postgres` as a comparison section in ch 09.
- Q3 (2026-11-10): event store is PostgreSQL on CloudNativePG with Panache: append-only `events` table, unique `(stream_id, version)`, `jsonb` payload. KurrentDB rejected (licence).
- Q4: repo name `event-driven-patterns-with-quarkus-and-camel`.
- Q5: two saga chapters (22 the pattern on Kafka with compensation; 23 Camel Saga EIP and Quarkus Flow); Drools embedded, LangChain4j for analysis only (ch 24).
- Q6: chapters 19 to 21 each show Kafka Streams and the Camel EIP on the same input, with a decision table; ch 20 states the Camel form approximates a window by arrival time.
- Q7: one Red Hat-branded deck via lgtm-presentation, built like CNDP's: `lgtm-presentation/` with `text/`, `edits/`, `BUILD.md`, a "Take it with you" QR slide; built last (r15).
- Q8: Apache-2.0 code, CC BY 4.0 prose.
- Q9: Kafka 4.3.2 everywhere, re-checked against Strimzi.
- Q10: Parts 1 to 5 on compose, Part 6 on minikube; ch 10 shows both connector-offset forms.
- Q11: Narayana LRA coordinator container on compose; Camel's in-memory saga service only in tests.
- Q12: Ollama with `qwen2.5:3b`, ch 24 only.

2026-11-10 (r01 plan approval):

- Debezium Connect uses the published `quay.io/debezium/connect` image first; our own UBI 10 Connect image only on a real need (fallback `ubi9/openjdk-25`).
- Chapter 01 is Quarkus + Panache + PostgreSQL only: no Camel, no Kafka.
- Java package `com.patterncatalyst.shipping`.
- Java 25 floor.
- More than one figure per chapter wherever a figure explains better than text.

2026-11-10 (r02 plan approval, all 12 recommendations):

- Topic prefix `edp.<domain>.<event>`.
- Event payload records live per example; domain-model holds entities, enums, `Carrier` and `Topics`.
- Per-event topics as in EIP Camel; chapter 04 adds the ordered `edp.shipping.shipments` stream keyed by order id.
- ShipmentInTransit added on `edp.shipping.in-transit`.
- Order CANCELLED only from PLACED or PAID.
- Chapter 04 wrong key is the event type (reorders); the carrier key shows skew, not reordering.
- Text order ids (`O-1001`).
- domain-model version 2.0.0.
- Series domain page (Notion 40561a5f): note only that this book uses EIP Camel's domain; do not rewrite it.
- Hub books.yml left alone this iteration (Bellemare `isbn: null`); our bibliography carries the ISBN.
- Chapter 24 free-text source: a carrier delivery-exception note on `Shipment`, settled at r09.
- Maven stays on 3.9.16 unless quarkusio/quarkus #57216 is fixed on the 3.40 LTS line.
- Chapter 03 adds Claim Check as a second pattern; chapter 04 adds Resequencer as a second pattern.
- Book links (titles, ISBNs, URLs) come from the hub `_data/books.yml`.

2026-11-12 (r06 plan approval, all 15 recommendations):

- Event store stays plain PostgreSQL with Panache (per Q3); no event-store product. Expected version comes from the loaded aggregate.
- Chapter 14: `Order` is the aggregate, no read table; conflict policy is retry by deciding again (SmallRye `@Retry` around a separate `@Transactional` bean) or `If-Match` reported as 412; only OrderPlaced and OrderCancelled leave, through the chapter 08 outbox; Camel at the edge.
- Chapter 15: `InventoryItem` stock ledger (internal StockReceived, StockReserved); snapshot every N=100 in the command's transaction with a version-guarded upsert; a stale snapshot schema is discarded and rebuilt, never upcast; Camel scheduled snapshotter as the out-of-band comparison; the timing ratio is a check (R=20 on the 100k stream).
- Kleppmann and Riccomini (DDIA 2nd ed.) cited at book level until the owner pastes the TOC; Vernon cited by chapter title only.
- Demo switches (`DEMO_PAUSE_FOR=orderId:Command`, `RETRY_MODE=reappend`, `SNAPSHOT_GUARD`, `INVENTORY_FETCH`) are labelled demo code.

2026-11-12 (r07 plan approval, all 19 recommendations):

- Chapter 16: the read model is projected from the public Kafka topics with SmallRye Reactive Messaging (six channels into one transactional projector); a forward-only guarded upsert (native `ON CONFLICT ... WHERE` with an immutable rank function) handles cross-topic order; lag shown with pausable channels and a Micrometer timer; one Camel route over six topics as the comparison.
- Chapter 17: a fresh example with a minimal event store; the projection tails the store with Quarkus `@Scheduled(concurrentExecution = SKIP)` and an `xid8` + `pg_snapshot_xmin` watermark (order the batch by the table columns, never by a cast alias); one bench at 1k, 10k and 100k orders feeds the table and figure.
- Chapter 18: stored v1 events upcast on read (`JsonNode` chain keyed by `schema_version`), never rewritten; the wire uses Avro with Apicurio through the chapter 08 outbox (`bytea` payload, BinaryDataConverter); global BACKWARD_TRANSITIVE rule refuses a v3 that drops the default (HTTP 400).
- Apicurio serdes overridden to 3.3.4 to match the server; Dev Services image pinned to 3.3.4. PostgreSQL held at 18.6 (owner). Young's versioning book cited by chapter titles only.
- An experiment whose failing set varies between runs is stated with its measured rate, never as deterministic.

## Skills

lgtm-relay (Opus plans, owner reviews, Sonnet builds, Opus validates), lgtm-tutorial, lgtm-jekyll, lgtm-quarkus, lgtm-camel, lgtm-docker-stack, lgtm-minikube-stack, lgtm-crc, lgtm-professional-voice, lgtm-idiomatic-review, lgtm-diagram-generator, lgtm-presentation, lgtm-host-scrub, lgtm-github.
