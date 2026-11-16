---
layout: plan
title: r13 plan
render_with_liquid: false
---

# r13 plan: Appendices A to H (Opus planner, 2026-11-16)

Status: **approved by the owner 2026-11-16 ("keep going" on the recommended defaults, decisions 1 to 25).** Branch `release/r13`, cut from main at 169314e after r12.0. Existing pins stay frozen: Kafka 4.3.2 (HOLD), Strimzi 1.3.1, CloudNativePG 1.30.1 with operand `18.6-202611090812-standard-trixie`, PostgreSQL 18.6 (Dev Services), Apicurio 3.3.4, Debezium 3.7.1.Final, otel-lgtm 0.41.0, UBI 1.24-20, Quarkus platform 3.40.1.2, Camel 4.22.2, minikube v1.39.0, Kubernetes and kubectl v1.37.2, registry 3.1.2, kube-state-metrics v2.20.0. New components are pinned once, in step 1A, to their latest stable release: Apache Flink, the Flink Kafka SQL connector, RisingWave, Redis (the sibling books' `redis:8.10.2-alpine` is the starting point, re-surveyed), and for Appendix H the CRC, AMQ Streams and CloudNativePG OLM versions. No Helm anywhere. kubectl's built-in kustomize; every apply `--server-side`.

**Headline recommendation: split.** r13 ships Appendices A to G on compose and minikube. Appendix H (CRC) becomes iteration **r13b**, run in an owner-started CRC window after r13 merges. r14 (retro) and r15 (deck) keep their numbers. Reasons are in decision 1.

## Findings that bind the design

Read while planning (repo, skills, host, read-only). Step 1A records each external fact as a sources row.

- **No appendix page exists yet.** `_parts/07-appendices.md` exists (`part_name: appendices`, order 7, no chapters). `README.md` already promises an OpenShift Local appendix. No chapter forward-references an appendix of this book.
- **Site machinery already half supports appendices.** `_layouts/tutorial.html` shows `page.marker` in place of the number and `page.label` in place of "Chapter N"; `_layouts/part_index.html` uses `doc.marker`. `chapters.html` prints `doc.order` and needs a one-line change to print `doc.marker` when set. The pager sorts all docs by `order`, so appendices take orders 36 to 43 and H is last.
- **validate.py anticipates appendix files starting with `a`:** versions are allowed in `_docs` files whose name starts with `00-` or `a`. The podman exemption is stale: it allows `_docs` names containing `-openshift` or starting `ae-` (CRC used to be Appendix E) and its message says "Appendix G". `check_examples` only matches `examples/\d+-`, and `.github/workflows/examples.yml` only loops over `examples/[0-9][0-9]-*`, so appendix examples would escape both the freshness rule and CI. The podman scan also covers `examples/**/*`, so an H example directory needs the exemption too. The banned-word list includes `Manifest`, `UPS`, `Invoice`, `Settlement` (glossary risk in D).
- **EIP Camel names appendices `NN-appendix-<slug>.md`** with titles "Appendix Z: ..."; its `44-appendix-openshift-local.md` (2,623 words) runs AMQ Streams, binary S2I and a Helm chart. Our H differs (buildah, podman, skopeo, no Helm), so it is cited, not followed.
- **Chapter 04's resequencer is a PostgreSQL per-order buffer, not Camel's Resequencer EIP.** Appendix E is new material (Camel `resequence()` batch and stream), with chapter 04 as the comparison by back link.
- **Testing inventory (Appendix A).** Dev Services everywhere (`@QuarkusTest`); `KafkaCompanion` only in 21 (`PickupFormsTest`); Apicurio contract tests in 18 (`Registry.java`); concurrency tests in 04 (`ShipmentBufferTest`), 11, 15, 17, 22; deterministic ordering tests in 04 (`ArrivalOrderTest`, murmur2 key tests); an HTTP dependency stubbed by the test application itself in 02. **No `AdviceWith` or `CamelQuarkusTestSupport` route test exists in the repo.**
- **Chapter 06's notification-service is the right consumer for Appendix F.** Group (`NOTIFY_GROUP`), client id (from `HOSTNAME`), group protocol, assignor, `consumersCount`, max poll and a per-record work delay (`NOTIFY_WORK_MS`) all come from env. It writes one row per (group, topic, partition, offset) with an `attempts` counter, so lost records and redeliveries are both observable without code. Chapter 02's notification-service hard-codes `groupId=notification-service`, so it cannot run blue-green.
- **Chapter 08 needs Connect.** The outbox is `debezium-quarkus-outbox` plus the Debezium EventRouter on Connect. Chapter 35 already declared the same `order-outbox` connector as a `KafkaConnector` (with `topic.prefix` and CNPG `managed.roles` lessons, G0-15 and G0-16 of r12). G and H reuse that knowledge.
- **Ports.** Apicurio holds host 8081 and 19000, which is Flink's default UI port. Free choices: Flink UI 18081, RisingWave 4566 (PostgreSQL wire), Redis 6379.
- `versions.md` already has "survey at first use (r13)" rows for Flink and RisingWave. Sibling books pin `docker.io/library/redis:8.10.2-alpine` (EIP Camel, 19 uses). Redis 8 licensing (tri-licence including AGPLv3) has to be confirmed in 1A.
- **Bellemare titles fit:** ch 20 Testing (A), ch 14 Streaming SQL (C), ch 21 Deploying (F), as `_plans/sources.md` already maps.
- **Host state for CRC (read-only checks, 2026-11-16):** `crc` 2.64.0 with OpenShift 4.22.14 (crc reports 2.65.0 published). Only the 4.22.14 bundle is cached; `~/.crc/machines` is empty (no VM). `crc config` holds cpus 12, memory 32768, disk 80, which is the datamesh project's sizing. The earlier preflight log shows **user-mode networking** (vsock, `crc-vsock.socket` and `crc-http.socket` user units, `crc-daemon` user service active). The setuid `crc-admin-helper` is installed and `/etc/hosts` holds a `crc.testing` entry. No libvirt `crc` network exists. So the artifacts `crc setup` leaves behind look present and `crc start` may pass preflight without sudo, but it still edits `/etc/hosts` through the setuid helper, which is a host change. Tools on PATH: `oc` 4.22.17, podman 6.1.3, buildah 1.45.1, skopeo 1.24.1.
- **lgtm-crc facts:** AMQ Streams `amq-streams-3.2.x` ships Kafka 4.1 and 4.2, not 4.3.2. The skill builds with S2I and deploys with Helm. The `crc-admin` context is certificate-based, so `oc whoami -t` has no token for a registry login; a ServiceAccount token is needed. Teardown order: release, project, operands, Subscriptions, CSVs, InstallPlans, CRDs, operator namespaces, leftover check, `crc stop`.
- **Running containers:** only `quarkus-agent-mcp-docs-*` (tooling, ignored by the stack guard). No minikube profile exists.
- **`stack-guard.sh`:** `--minikube` refuses while CRC runs. Compose mode does not check CRC. There is no `--crc` mode.

## Approach

r13 is A to G. Each appendix uses the cheapest evidence that tests its claims:

| App. | Runnable example | Runtime | New code |
|---|---|---|---|
| A Testing | `examples/aa-testing-event-driven-code` (small; only the gaps) plus citations of existing tests | Dev Services only (no compose) | yes, small |
| B Compose to Kubernetes | `examples/ab-compose-to-kubernetes`: effective-config capture on both targets and a compare | compose half plus minikube half | scripts only |
| C Querying streams | `examples/ac-querying-streams-without-ksqldb`: one Quarkus Kafka Streams app plus Flink SQL plus RisingWave | compose | yes |
| D Pattern map | none (one figure) | none | no |
| E Resequencing | `examples/ae-resequencing-across-partitions`: Camel Resequencer stream and batch | compose | yes |
| F Deploying | `examples/af-deploying-event-driven-services`: overlays over `_k8s`, chapter 06 notification-service and chapter 02 order-service images unchanged, producer Jobs | minikube | manifests and scripts only |
| G Write-forward cache | `examples/ag-write-forward-cache`: order-service with outbox, Connect, Redis | compose | yes |
| H OpenShift Local | `examples/ah-openshift-local` (r13b) | CRC | scripts and manifests only |

1. **Step 1, in parallel:** 1A survey and sources; 1B compose G0 spike (A, B-compose, C, E, G); 1C minikube G0 spike (F, B-k8s), run after 1B under the lock; 1D orchestrator substrate (validate.py, CI, site, stack guard, `_infra` files).
2. **Step 2:** seven authoring executors on disjoint files, no stack.
3. **Step 3:** a compose window (A, B-compose, C, E, G), then a minikube window (F, B-k8s). Each runnable appendix gets **one** clean run, each demo switch once with its failing set frozen, and one bite proof (r12 decision 10).
4. **Step 5:** final runs (compose examples once each from fresh containers; F and B-k8s on a recreated profile, then the profile is deleted). This is the second clean run.
5. **Step 6:** Opus validation, at most 2 repair rounds; ship r13.0.
6. **r13b:** Appendix H, with its own preflight, owner-started CRC, G0 on CRC, authoring, live run, teardown, validation and ship (r13b.0).

Rejected: one iteration A to H (see decision 1); Helm for anything (book rule since r11); S2I for H (the PRD says buildah, podman and skopeo, and S2I needs a pom profile change, so it would not be the same image); copying lgtm-crc's chart; ksqlDB and Materialize (licences, named in C only); Valkey in place of Redis (owner decided Redis); a Redis Camel component (Camel's `spring-redis` pulls Spring Data; the Quarkus Redis client is the idiomatic choice); re-running earlier chapters live (only new content is live-run); a new domain-model version (it would force `mvn verify` of every earlier example for no lesson); external registries or `minikube docker-env`; port-forwarding of any kind, including for the CRC registry (the CRC-provided ingress and `oc exec` only); changing `crc config` (it belongs to datamesh).

## Owner decisions (recommended defaults; numbered for review)

1. **Split r13.** r13 = Appendices A to G (compose and minikube); **r13b** = Appendix H on CRC, planned below and started only after r13 merges and the owner opens a CRC window. r14 and r15 keep their numbers, so CLAUDE.md's "deck (r15)" needs no edit. Why: A to G already needs about 3 h 45 min of live windows plus about 2 h 30 min of G0 spike, against r12's 2 h window. H depends on owner presence (`crc start`), a different toolchain (podman, buildah, skopeo, oc), operator installs from catalogs that are only readable on a running cluster, and a cleanup the owner wants verified, because CRC belongs to datamesh. Coupling them would hold A to G hostage to the CRC window. *Alternatives:* (a) one iteration A to H, which means a long session with a CRC dependency on the critical path; (b) r14 = H with renumbering (retro r15, deck r16, CLAUDE.md edit); (c) a three-way split (r13 = A, B, D, E; r13b = C, F, G; r13c = H) if the owner wants each window at or below 2 h.
2. **Appendix naming and site wiring.** Files `_docs/aa-testing-event-driven-code.md`, `ab-compose-to-kubernetes.md`, `ac-querying-streams-without-ksqldb.md`, `ad-pattern-map-and-glossary.md`, `ae-resequencing-across-partitions.md`, `af-deploying-event-driven-services.md`, `ag-write-forward-cache.md`, and in r13b `ah-openshift-local.md`. Front matter `order: 36` to `43`, `marker: "A"` to `"H"`, `label: "Appendix A"`, `part: appendices`, title "Appendix A: Testing event-driven code" (EIP Camel style). Examples `examples/a<letter>-<slug>`, example pages `_example_pages/a<letter>-*.md`, figure specs `diagrams/specs/a<letter>-*.py`, captions "Figure A.1". `chapters.html` prints `marker` when set. This matches validate.py's existing `a` prefix rule. *Alternative:* EIP Camel's `36-appendix-*`, which needs validate.py rule changes and breaks the `a` version rule.
3. **Appendix spine.** E and G teach one pattern each and use the full pattern spine (`## Pattern: Resequencer across partitions`, `## Pattern: Write-forward cache`, one callout each). A, B, C and F use topical `##` sections, exactly one callout, `## Build, run, observe` (short rule), `## When not to use it` where it applies (C, F), `## References`. D has no callout and no Build, run, observe. Every appendix stays within 2,500 words and has at least one figure (F has several per section, decision 11). Versions are allowed (setup surface), but only where the version matters.
4. **A: a small new example for the gaps, plus citations.** `aa-testing-event-driven-code` is one Quarkus + Camel module (a notification route: `edp.orders.placed`, an HTTP fetch, a recorded row), tested four ways that the repo lacks: a Camel route test with `AdviceWith` (camel-quarkus-junit, per 1A) and a mocked endpoint; an HTTP dependency stubbed by a test resource; a topic assertion that **drains for the full wait** next to a first-match assertion; and a **bite harness**. `verify.sh` runs `mvn -B verify`, then for each `bites/<name>.patch` (deliberately altered copies) copies the module under `target/bites/`, applies the patch, runs the named test and expects it to fail. The first-match assertion still passes against one bite, which is the vacuous-check lesson. Dev Services, ordering, concurrency, contract and LGTM sections cite existing tests by path (04, 11, 18, 21, 25 to 27) with short excerpts. *Alternatives:* citations only (cheapest, but the AdviceWith and drain claims would be unbacked), or one big example re-implementing everything (duplicates chapters).
5. **B: a live effective-config diff in two halves.** `ab-compose-to-kubernetes/verify.sh compose` brings up only `_infra` Kafka and PostgreSQL fresh, captures broker settings (`kafka-configs.sh --describe --all` for a fixed key list: `num.partitions`, `default.replication.factor`, `min.insync.replicas`, `offsets.topic.replication.factor`, `auto.create.topics.enable`, `log.retention.hours`, the share-group enable, `transaction.state.log.*`) and PostgreSQL settings (`SHOW` for `server_version`, `wal_level`, `max_replication_slots`, `max_wal_senders`, `max_connections`), then `down -v`. `verify.sh k8s` does the same on fresh namespaces with only the `_k8s` kafka and postgres bases. `verify.sh compare` checks equality with an **allowlist of expected differences** (listeners, storage, anything CNPG fixes itself), and each allowed difference is a sentence in the appendix. The prose maps `compose-kafka.yaml` to `Kafka` plus node pools, and `compose-postgres.yaml` to the CNPG `Cluster`, quoting the existing files. *Alternative:* a static compare of the YAML files, which misses operator defaults (r12 found CNPG's `wal_level` already logical).
6. **C: three engines side by side on one compose, same input.** Pins in 1A: Apache Flink (latest stable whose Kafka SQL connector has a final release; a hold, if needed, recorded as a valid reason) and `flink-sql-connector-kafka` (sha512, fetched by an init service into a run-scoped volume, as `compose-connect-tracing.yaml` does for the agent); RisingWave (latest stable, single-node mode). New `_infra/compose-flink.yaml` (JobManager plus TaskManager, UI 18081) and `_infra/compose-risingwave.yaml` (4566). The question: **ShipmentDelivered counts per carrier per tumbling window of event time** on `edp.shipping.delivered` (domain-model C row). The answers: Kafka Streams interactive queries over a window store, served by a Quarkus REST endpoint; a Flink SQL `TUMBLE` job writing to an upsert-kafka topic; a RisingWave source plus materialized view, queried with `psql` from the existing PostgreSQL container (no host client assumed). Output topic `edp.shipping.delivery-counts-flink`, added to domain-model.md as non-catalog with no code change. Comparison table: where state lives, how a question is asked, final versus updating results, late records, footprint, licence.
7. **C rejections:** ksqlDB (Confluent Community License) and Materialize (BSL) named and rejected, each with its fetched licence page; Apache Pinot and Apache Druid get one line each (real-time OLAP stores, a different job). Bellemare ch 14 cited by title.
8. **D: no example.** Three parts: a pattern map figure (`ad-pattern-map`, parts as columns, patterns as boxes); a glossary of terms used in the book (terse, no banned names); an EIP Camel cross-reference by event and pattern (book chapter, EIP Camel chapter link, one-word relation). Links fetched for HTTP 200 in 1A. At most 2,500 words; tables count, so cells stay to links and short terms. Split into D1 and D2 only if the draft exceeds the cap.
9. **E: a new example; the per-order database buffer by back link.** One Quarkus + Camel `order-service` with two routes selected by `RESEQUENCER=stream|batch`. Several producers write shipment events to the three per-event catalog topics (`edp.shipping.scheduled`, `in-transit`, `delivered`) with keys chosen to scatter partitions. The route merges them, resequences, writes the in-order stream to `edp.shipping.shipments` (the chapter 04 funnel topic, keyed by order id) and records apply order in a PostgreSQL table for the checks. Covered: stream mode needs a **global** gap-free sequence (a `globalSequence` header from one sequencer), which per-order numbers do not give; batch mode sorts by (order id, sequence) only within a batch; gap timeout; a lost event; `rejectOld`; capacity (memory bound); a restart while holding (whether held records are lost when offsets are already committed); several producers and topics. Chapter 04's buffer is the restart-safe comparison, stated from chapter 04's own evidence, not re-run. Input records are crafted by `verify.sh` with explicit order and timestamps relative to now.
10. **F: earlier images unchanged, on the r12 substrate.** Images: chapter 06 notification-service (all consumer settings from env, one row per group and record with `attempts`) and chapter 02 order-service (HTTP rolling update). Producer: an in-cluster Job on `quay.io/strimzi/kafka:1.3.1-kafka-4.3.2` that sends uniquely numbered OrderPlaced JSON at a fixed rate (ids `O-41000` up). Strategies:
    - **Full stop** (`Recreate`): lag grows during the gap and drains after it; nothing lost.
    - **Rolling update** of a 3-replica consumer group under load: rebalances counted, records redelivered (`attempts > 1`) under `GROUP_PROTOCOL=classic` against `consumer` (KIP-848), stated as a measured rate if it varies. Plus order-service rolling with HTTP load, with and without readiness and graceful shutdown (env only).
    - **Breaking schema change:** a v2 producer that renames `orderId`. Measured: what the v1 consumer does with it (expected: a failed insert and the record skipped, so silently lost; G0 confirms). Then the **compatible v2 first** (expand) that loses nothing. The new-topic-plus-migration-consumer path is shown in figures and sourced prose plus a Try, not run, because the 06 image cannot read a new topic without a code change.
    - **Blue-green for consumers:** green is a second Deployment with `NOTIFY_GROUP=notify-green`. Cut-over: stop blue, copy blue's committed offsets to the inactive green group (`kafka-consumer-groups.sh --reset-offsets --from-file`), start green. Rollback: stop green, copy green's offsets back to blue. Witness `OFFSET_COPY=off` (demo): green re-reads from earliest (duplicates under the new group).
    
    *Alternative:* a new appendix Quarkus project with v1 and v2 consumers that can run the migration consumer live (more code, about 1 h more authoring).
11. **F figures:** several per section, one per stage: full stop (before, gap, after), rolling (before, mid-rollout rebalance, after), schema change (breaking, expand, contract, new-topic migration), blue-green (blue live, cut-over, green live, rollback). About 13 specs from `diagrams/specs/af-*.py`.
12. **G: Redis read side fed by the outbox.** Pin `docker.io/library/redis:<latest stable 8.x>-alpine` in 1A (new pin, so latest stable; 8.10.2 is the sibling value), licence recorded. New `_infra/compose-redis.yaml` (6379, no persistence, so a restart is a real cache loss). One `order-service`: `POST /orders` writes the order and the outbox row in one transaction (debezium-quarkus-outbox, as chapter 08). Debezium Connect (published image, the chapter 08 connector) routes to `edp.orders.placed` and `edp.orders.cancelled`, carrying full state as in chapter 03. A Camel route in the same service consumes them and writes the Redis view through the Quarkus Redis client with a **version guard** (an update older than the stored version is ignored). `GET /orders/{id}` reads Redis only, so staleness is visible, never hidden by a database fallback. Covered: measured staleness between commit and cache; cache loss and rebuild by replaying from earliest with a fresh group (the guard makes the replay safe); why neither a Kafka transaction (chapter 12 back link, Kafka docs) nor anything else covers PostgreSQL plus Redis in one commit. Negative witness `CACHE_WRITE=dual` (demo code): the request writes Redis before the database commit, and a demo failure after the cache write leaves Redis showing an order PostgreSQL never committed. A second witness `CACHE_GUARD=off` (demo): an out-of-order replay regresses a cancelled order to placed. *Alternative source:* CDC of the `orders` table, which exposes the internal schema and is rejected.
13. **Ids, topics, domain model.** Order ids: A `O-36xx`, B none, C shipments for orders `O-38xx`, E `O-40xx`, F `O-41000` and up, G `O-42xx`, H `O-43xx`. New non-catalog topics listed in `_plans/domain-model.md` by the orchestrator: `edp.shipping.delivery-counts-flink` (C). E reuses `edp.shipping.shipments`. No domain-model code change and no version bump; any example-local topic constant stays in the example.
14. **Run counts:** as r12 decision 10: one clean N/N run per appendix in the window, each demo switch once with its failing set frozen, one bite proof; the final fresh run is the second clean run.
15. **Sizing.** Compose: G0 measures `docker stats` peak for C (Kafka, LGTM, app, Flink JobManager and TaskManager, RisingWave) and G (Kafka, PostgreSQL, Connect, LGTM, Redis, app). Pass: peak at or below 12 GiB with no OOM (host about 41 GiB available); otherwise lower Flink and RisingWave limits. minikube: keep `--cpus=6 --memory=10g`; G0 measures F at 3 + 1 notification replicas plus green plus order-service; move to 12g only above 8 GiB.
16. **Stack guard:** compose mode also refuses while CRC runs (r13, small fix). `--crc` mode (refuses any compose project or minikube node) added in r13b.
17. **Earlier examples untouched:** `git diff --stat 169314e -- examples/0* examples/1* examples/2* examples/3*` stays empty, except a fix only if an earlier example is found broken (fix-now rule, reported).
18. **Merge on validation and CI pass;** ping the owner when merged (as r11 and r12).

r13b decisions (H), decided now so r13b can start without another planning round:

19. **Who starts CRC.** The owner runs `crc setup --check-only` and `crc start` via `!` in the session (no `crc config` change; the existing 12 CPU and 32 GiB config is used as it is). Agents work only inside the cluster through the `crc-admin` context, and run the teardown and `crc stop`. If the check-only preflight reports anything that needs sudo, r13b stops and the owner decides. *Alternatives:* (a) agents run `crc start` (it edits `/etc/hosts` through the setuid helper, a host change, so not recommended); (b) H written and validated statically, with the live run in a later owner-run window (claims unbacked until then, so it cannot ship); (c) drop H.
20. **CRC end state.** Full teardown inside the cluster (app project, operands, Subscriptions, CSVs, InstallPlans, owned CRDs, operator namespaces, registry default route reverted), a leftover inventory captured as evidence, then `crc stop`. Default addition: **`crc delete -f`** afterwards, because no VM existed before r13b and datamesh then starts from a pristine cluster. The owner may say stop only.
21. **CRC version.** Hold `crc` 2.64.0 / OpenShift 4.22.14 (cached bundle). 2.65.0 would need a new binary, a new bundle and possibly `crc setup` reinstalling the setuid helper (sudo). That is a valid reason, recorded next to the pin.
22. **Kafka on CRC: AMQ Streams** (PRD), latest CSV of its newest stable channel, `installPlanApproval: Manual`, `startingCSV`. Its Kafka version (4.2.x expected) is an H-only exception to Kafka 4.3.2, recorded in versions.md and stated once in H. The free Red Hat developer account behind the CRC pull secret is the H-only exception to "no subscription", recorded beside it. *Alternative:* the Strimzi 1.3.1 community operator, which keeps 4.3.2 but drops the vendor-distribution point the PRD wants.
23. **PostgreSQL on CRC:** CloudNativePG from the community catalog through OLM (pinned CSV, Manual approval), operand 18.6 as Part 6, `managed.roles` replication as in chapter 35. *Fallback, decided in r13b G0:* the CNPG 1.30.1 release YAML applied server-side.
24. **Images on CRC:** `buildah build` with chapter 08's own Containerfile (UBI 10, unchanged) into rootless containers-storage; `podman image inspect` records the image id; `skopeo copy containers-storage:localhost/edp/08-order-service:local docker://default-route-openshift-image-registry.apps-crc.testing/edp/order-service:08`. Credentials come from a ServiceAccount token (`oc create token builder -n edp`, read into a variable, written to a scratch authfile that is deleted afterwards, never printed). TLS is verified with the ingress CA through `--dest-cert-dir`, never `--tls-verify=false`. The local podman image is removed at teardown. The Connect image is built by AMQ Streams `KafkaConnect.spec.build` with `output.type: imagestream` (an in-cluster registry, nothing external), using the Debezium 3.7.1.Final plugin sha512 already pinned.
25. **No Helm and no LGTM on CRC.** kustomize overlays applied with `oc apply --server-side`; pods meet restricted-v2 with no fixed UID. OpenTelemetry is turned off by env (`QUARKUS_OTEL_SDK_DISABLED=true`; G0 confirms it is runtime config). The order is posted through its edge-TLS Route with `curl --cacert <ingress CA> --resolve <host>:443:127.0.0.1` if the host does not resolve app Routes (no host file change); everything else through `oc exec`.

## Shared conventions (step 1D, orchestrator)

- **validate.py:**
  - `check_examples` matches `examples/(\d+|a[a-h])-`. Appendix examples follow the compose freshness rule, or the k8s branch when `kustomization.yaml` is present (F). `ab` is allowed to hold both halves. `aa` gets a test-only branch (no compose; must run `mvn -B verify` and the bite loop). `ah` (r13b) gets a CRC branch (fresh project per run, `crc_fresh` and `crc_teardown` named).
  - The podman exemption becomes `_docs/ah-*` and `examples/ah-*`, with the message "Appendix H".
  - Self-tests for each new branch.
  - Version strings stay allowed in `a*` docs.
- **CI** `examples.yml`: the loop includes `examples/a[a-h]-*` directories that have a `pom.xml` (aa, ac, ae, ag).
- **Site:** `chapters.html` shows `doc.marker`; `_parts/07-appendices.md` blurb updated; `README.md` appendix list; examples index picks up the `_example_pages/a*` pages.
- **`_infra`:** `compose-flink.yaml` (with the connector init service and sha512), `compose-risingwave.yaml`, `compose-redis.yaml`, README rows, memory limits and healthchecks on each.
- **stack-guard.sh:** compose mode refuses while CRC runs (decision 16).
- **`_k8s`:** no new base expected. F uses `apps/02` plus a new Deployment for 06 written in the F overlay, `load_image 06-optimizing-subscribers notification-service`, and `APP_PORTS` set by the overlay. If G0 shows a shared helper is needed (offset copy), it goes into `lib.sh` as `group_offsets_copy <from> <to>` (orchestrator).
- **Executor rules (as r12):** no git writes. No edits to `claims.md`, `verification-log.md`, `CLAUDE.md`, `examples/_k8s`, `examples/_infra`, `scripts/`, `domain-model` or any earlier example. No stack in step 2 (only `mvn -B verify` with Dev Services under the lock, `kubectl kustomize`, static checks). Logs through `host_ids.py filter`. Every docker, minikube or CRC action under `flock -w 7200 <scratchpad>/stack.lock`. Executor 2X owns only `examples/aX-*`, `_docs/aX-*`, `_example_pages/aX-*` and `diagrams/specs/aX-*` (plus outputs).

## G0 spike (r13; scratchpad; under the lock)

Results go to `<scratchpad>/r13-spikes/RESULTS.md`, then "G0 decisions" are appended here.

**1B compose spike (about 90 min), serial, fresh containers per question:**

- **Q1 A route tests.** The camel-quarkus-junit artifact name and the AdviceWith form on Camel Quarkus 3.40 (`CamelQuarkusTestSupport`, `@QuarkusTest`, `adviceWith` with `replaceFromWith` and `mockEndpoints`); a test resource stub serves the HTTP dependency; KafkaCompanion drain-for-N-seconds form; time of `mvn -B verify`. Bite harness: three patches (guard removed; key changed; a duplicate allowed), each failing exactly its named test while the first-match assertion still passes on the duplicate patch.
- **Q2 B compose capture.** The exact `kafka-configs.sh` command for broker defaults on `apache/kafka:4.3.2`; the `SHOW` set; output stable across two runs.
- **Q3 C engines.**
  - (a) Flink image tag, Kafka SQL connector version and sha512, JobManager and TaskManager start, `sql-client.sh -f` job submit time; `TUMBLE` with a `WATERMARK` on `deliveredAt`; upsert-kafka sink rows on `edp.shipping.delivery-counts-flink`.
  - (b) RisingWave single-node command and memory; `CREATE SOURCE` (JSON on Kafka) and a materialized view with `TUMBLE`; `psql -h risingwave -p 4566` from the PostgreSQL container; with and without `EMIT ON WINDOW CLOSE`.
  - (c) Quarkus Kafka Streams window store by carrier with grace; IQ through `QueryableStoreTypes.windowStore()`; REST answer.
  - (d) The same input (N records, two carriers, three windows, one out-of-order and one late record, timestamps relative to now): the three answers for closed windows equal; record what each does with the late record.
  - (e) `docker stats` peak (decision 15).
  - (f) Times: first result after the last record, per engine.
- **Q4 E resequencer.**
  - (a) Stream mode with a `globalSequence` header across 3 topics and 2 producers: output order exact.
  - (b) Stream mode on per-order `sequence`: record what goes wrong (stall or wrong order).
  - (c) Batch mode (`batchSize`, `batchTimeout`, comparator on order id and sequence): within-batch order correct, a cross-batch inversion observed.
  - (d) Gap: `timeout` releases after the gap; a late arrival with `rejectOld` true and false.
  - (e) `capacity` reached: exact behaviour (block, release or error) and message.
  - (f) Restart while holding: are held records lost (offsets auto-committed) or re-read? Record the commit settings.
  - (g) Run times.
- **Q5 G cache.**
  - (a) Redis tag and licence; Quarkus Redis Dev Services image override.
  - (b) Connect EventRouter carrying full-state OrderPlaced and OrderCancelled from one outbox.
  - (c) Version guard: Lua `EVAL` or `WATCH`/`MULTI`, whichever the Quarkus Redis client expresses idiomatically; replay safety.
  - (d) Staleness distribution over 50 orders (commit to cache visible), stated as measured.
  - (e) Cache loss (`docker compose restart redis`), then a fresh-group replay rebuilds every order; time.
  - (f) `CACHE_WRITE=dual` witness: the exact state (Redis has the order, PostgreSQL does not).
  - (g) `CACHE_GUARD=off` regression shown.
  - (h) `docker stats` peak.

**1C minikube spike (about 60 min), one profile, deleted at the end unless step 3 follows within 30 min:**

- **Q6 B k8s capture** on the `_k8s` kafka and postgres bases: the same keys; the differences list for the allowlist.
- **Q7 F consumer strategies.**
  - (a) The 06 image on `_k8s` (env, NodePort, DB schema on `edp-pg`), the producer Job rate, partitions 3.
  - (b) `Recreate` rollout: gap length and lag peak.
  - (c) Rolling update of 3 replicas (`maxSurge 1`, `maxUnavailable 0`) under load: redelivered count and rows per replica, classic against consumer protocol, 3 runs each (varying rate stated as measured).
  - (d) Breaking v2 payload: the consumer's exact behaviour (error log, offset committed, row absent) and expand v2 passing.
  - (e) Blue-green: offset export and import form on an inactive group; cut-over with zero missing and zero re-read; rollback; `OFFSET_COPY=off` duplicates.
  - (f) `docker stats edp` peak.
- **Q8 F order-service rollout under HTTP load:** non-2xx count with probes and `QUARKUS_SHUTDOWN_TIMEOUT` set against unset (`GRACEFUL=off`). Does the 02 order-service publish exactly once per 201?
- **Q9 Teardown** of F's state within 120 s, three times.

Decisions taken from G0 data: 6 (Flink pin hold), 9 (stream-mode demonstration form), 10 (rolling protocol claim form), 12 (guard form), 15 (sizing).

## Per-appendix design

Shared rules: at most 2,500 words, about 2,000 target; at least one figure per appendix from specs (F about 13); no dashes, no test names in prose, no self-reference, no check numbers in Build, run, observe; demo switches are env vars labelled demo code; each runnable appendix's README carries the check list; every claim backed by a test, a G0 or window record, or a fetched source, or cut.

### A Testing event-driven code
Headings: `## Real brokers in tests`, `## A check that bites`, `## Asserting on topics`, `## Ordering tests that cannot pass by luck`, `## Concurrency tests and their limit`, `## Camel route tests`, `## Event contracts in the registry`, `## End to end: verify.sh and LGTM`, Build, run, observe, References. Figures: `aa-bite-cycle` (break, run, fail, restore), `aa-drain-vs-first-match`. verify.sh: `mvn -B verify` passes, then each bite fails its named test (about 6 checks). Sources: Quarkus testing and Dev Services guides, KafkaCompanion docs, Camel Quarkus testing docs (AdviceWith), Apicurio compatibility rules, Bellemare ch 20; back links 04, 11, 18, 21, 25 to 27.

### B Compose to Kubernetes
Headings: `## Kafka: one container, then a Kafka resource and two node pools`, `## PostgreSQL: one container, then a CloudNativePG Cluster`, `## What is the same, what differs and why`, Build, run, observe, References. Code: the compose service next to the CR (quoted from the existing files). Figure `ab-same-config-two-targets`. Checks: about 14 keys equal, allowlisted differences listed. Sources: Strimzi Kafka configuration docs, CNPG PostgreSQL configuration (fixed parameters), Kafka broker configs; back links 00, 28, 35.

### C Querying streams without ksqlDB
Headings: `## One question`, `## Kafka Streams: ask the application`, `## Flink SQL: a job that writes answers`, `## RisingWave: a database that keeps the answer current`, `## Late records`, `## Which one`, (comparison table, ksqlDB and Materialize rejected, Pinot and Druid in one line), Build, run, observe, When not to use it, References. Figures `ac-three-ways`, `ac-window-and-late-record`. About 16 checks (three answers equal for closed windows; late-record behaviour per engine as measured). Demo `LATE=on` or similar as chosen in G0. Sources: Kafka Streams interactive queries docs, Quarkus Kafka Streams guide, Flink windowing TVF and Kafka connector docs, RisingWave docs (sources, tumble, emit on window close), the licence pages, Bellemare ch 14; back link 21.

### D Pattern map, glossary, EIP Camel cross-reference
Headings: `## The map`, `## Glossary`, `## EIP Camel by pattern and event`, References. Figure `ad-pattern-map`. No example. Links checked in 1A.

### E Resequencing across partitions in depth
Pattern spine plus `## A global sequence or a per-order one`, `## Gaps, timeouts and lost events`, `## Restarts and memory bounds`, `## Several producers and topics`. Figures `ae-stream-vs-batch`, `ae-gap-timeout`, `ae-restart-loss`. About 16 checks. Witness: `RESEQUENCER=none` (demo) fails the order checks; the restart check frozen as measured. Sources: Camel Resequencer EIP docs (4.22), Hohpe and Woolf Resequencer; back link 04.

### F Deploying event-driven services on minikube
Headings: `## Full stop`, `## Rolling update of a consumer group`, `## Rolling update of a service under requests`, `## A breaking schema change`, `## Blue-green consumers: cut-over and rollback`, Build, run, observe, When not to use it, References. About 20 checks, including nothing lost per strategy, redeliveries counted, rollback with offsets copied, and non-2xx zero with graceful shutdown. Demos: `GROUP_PROTOCOL=classic`, `OFFSET_COPY=off`, `GRACEFUL=off`. Sources: Kubernetes Deployment strategy docs, KIP-848, Kafka consumer groups tool docs, Quarkus graceful shutdown, Bellemare ch 21; back links 06, 18, 28.

### G Write-forward cache with an outbox and CDC
Pattern spine plus `## How stale the cache is`, `## Losing the cache and rebuilding it`, `## The dual write it avoids`. Figures `ag-write-forward-path`, `ag-dual-write-failure`, `ag-rebuild-by-replay`. About 18 checks. Witnesses `CACHE_WRITE=dual` and `CACHE_GUARD=off`. Sources: Quarkus Redis guide, Redis licence page, Debezium outbox EventRouter, Kafka transactions docs (KIP-98), Microsoft cache-aside and materialized view patterns (contrast); back links 03, 08, 12, 16.

### H OpenShift Local (r13b)
Headings: `## What changes from minikube`, `## Starting CRC`, `## Operators at pinned versions`, `## Building with buildah, copying with skopeo`, `## The outbox on AMQ Streams and CloudNativePG`, `## Teardown`, Build, run, observe, References. Example `ah-openshift-local/`: `crc-check.sh` (refuses unless CRC runs and the context is `crc-admin`; never starts CRC), `install.sh` (pinned CSV, Manual, approve only the named InstallPlan), `build-and-copy.sh`, `deploy/` kustomize, `verify.sh` (about 14 checks: operators Succeeded at the pinned CSVs; order-service image digest in the cluster equals the local buildah image; Connect Ready with the plugin; an O-4301 outbox row reaches `edp.orders.placed` with the `eventType` header; restricted-v2 admitted; the Route answers with a verified CA), `teardown.sh` (leftover inventory captured), README. Figures `ah-build-and-copy`, `ah-crc-layout`. Last in the book (order 43).

## Steps

| Step | Work | Owner | Depends | Status |
|---|---|---|---|---|
| 0 | Preflight: owner OK, save plan, iteration-plan r13 in progress and r13b row added, no stack, profile or CRC running | orchestrator | owner OK | done |
| 1A | Pins: Flink, Kafka SQL connector (sha512), RisingWave, Redis, each with digests and licences; sources rows A to G (all Findings plus per-appendix sources, licence pages for ksqlDB, Materialize, Redis); EIP Camel links for D; bibliography rows | exec 1A (Sonnet) | 0 | done (Flink held at 2.2.1: no connector for 2.3; RisingWave v3.1.1; Redis 8.10.3-alpine) |
| 1B | Compose G0 spike Q1 to Q5 | exec 1B (Sonnet) | 0 | done (F1 to F37; peaks 2.12 and 2.06 GiB) |
| 1C | minikube G0 spike Q6 to Q9 | exec 1C (Sonnet) | 1B done (lock) | done (K1 to K12; profile deleted) |
| 1D | validate.py branches and self-tests, CI loop, `chapters.html`, part card, `_infra` Flink, RisingWave and Redis files, stack-guard compose CRC check | orchestrator | 1B in progress | done (validate.py, CI, chapters.html, part card, stack guard, _infra Flink, RisingWave, Redis; Redis Dev Services rule) |
| G0 | "G0 decisions" appended; decisions 6, 9, 10, 12, 15 from data; names, commands and durations frozen | orchestrator | 1A to 1D | done |
| 2A to 2G | Appendices A to G: example (where any), verify.sh, README, example page, appendix, figures | execs 2A to 2G (Sonnet) | G0 | done |
| 2X | Consistency: domain-model.md topic note, README appendix list, CLAUDE.md decisions draft, versions.md rows | orchestrator | G0 | pending |
| 3a | Compose window, at most 2 h 15 min: A, B-compose, C, E, G | orchestrator | 2A to 2G | done (A 7/7, C 18/18, E 23/23, G 24/24, twice each) |
| 3b | minikube window, at most 1 h 30 min: profile up, F, B-k8s, B compare, profile kept for 5 if within 30 min | orchestrator | 3a | done (B 9/9+9/9+23/23, F 30/30, twice each) |
| 4 | Fixes inside the windows, affected appendix re-run only | orchestrator | 3a, 3b | pending |
| 5 | Final runs: compose A, B-compose, C, E, G once each; recreated profile F, B-k8s, compare; profile deleted; static gates; claims and log; commits | orchestrator | 4 | pending |
| 6 | Opus validation (at most 2 repair rounds) | validator | 5 | done (2 rounds; round-2 High fixed by orchestrator) |
| 7 | Ship: PR, CI green, squash, tag r13.0, notes-only release; PRD, iteration-plan, CLAUDE.md | orchestrator | 6 | pending |

r13b steps (after r13.0):

| Step | Work | Owner | Depends | Status |
|---|---|---|---|---|
| b0 | Preflight: no compose, no minikube; owner runs `crc setup --check-only` and `crc start` via `!`; inventory `oc get sub,csv -A` and non-system namespaces | owner, orchestrator | r13.0 | pending |
| b1 | Survey on the cluster: AMQ Streams and CNPG packagemanifests (channel, CSV), AMQ Streams Kafka versions; sources rows H | exec b1 | b0 | pending |
| bG0 | CRC spike: H-Q1 to H-Q7 (below) | exec bG0 | b1 | pending |
| b2 | `ah-openshift-local` scripts, overlays, README, page, appendix, figures; validate CRC branch; stack-guard `--crc` | exec b2, orchestrator | bG0 | pending |
| b3 | Live: install, build, copy, verify, demo once; full teardown; reinstall and verify again (the second clean run; proves teardown and install repeat) | orchestrator | b2 | pending |
| b4 | Teardown evidence, `crc stop`, `crc delete -f` (decision 20) | orchestrator | b3 | pending |
| b5 | Opus validation (static and evidence; at most 2 rounds); ship r13b.0 | validator, orchestrator | b4 | pending |

r13b G0 questions:

- H-Q1: `crc config get network-mode`, and whether app Routes resolve on the host or need `--resolve`.
- H-Q2: Registry default route on or off by default; the `builder` SA token push rights; skopeo with `--dest-cert-dir` holding the ingress CA.
- H-Q3: AMQ Streams CSV and Kafka versions; `KafkaConnect` `output.type: imagestream` with the Debezium plugin.
- H-Q4: CNPG CSV in community-operators; `managed.roles`; restricted-v2 admission.
- H-Q5: Runtime `QUARKUS_OTEL_SDK_DISABLED`.
- H-Q6: Teardown leaves no CRD, CSV or namespace of ours; time taken.
- H-Q7: `docker` untouched (podman storage only); local image removed afterwards.

## Acceptance criteria

- **A:** verify N/N with N at least 5 in the window and the final run; every bite fails exactly its named test; the first-match assertion is shown to pass on the duplicate bite while the drain assertion fails; cited test paths exist and pass in CI.
- **B:** compare N/N with N at least 12 on both runs; every allowlisted difference appears in the prose with its reason; no other difference.
- **C:** N at least 14; the same input to all three engines; closed-window counts equal across engines; late-record behaviour stated only as measured; Flink, connector and RisingWave pins with licences in versions.md; ksqlDB and Materialize licences cited from fetched pages.
- **D:** no broken link (site build), at most 2,500 words, no banned names, one figure.
- **E:** N at least 14; stream and batch both shown; per-order sequence failure shown in stream mode as measured; gap, lost-event, capacity and restart behaviours each a check or a stated measurement; `RESEQUENCER=none` fails exactly its stated set.
- **F:** N at least 18 on a fresh profile; every strategy has a nothing-lost check; redelivery counts stated with their measured range; blue-green cut-over and rollback both checks; `OFFSET_COPY=off` and `GRACEFUL=off` fail exactly their stated sets; at least 10 figures; `git diff --stat 169314e -- examples/02-* examples/06-*` empty.
- **G:** N at least 16; the order and outbox row in one transaction; the cache fed only from events (no Redis call in the request path except under the demo); rebuild after loss passes; both witnesses fail exactly their stated sets; Redis pin and licence recorded.
- **H (r13b):** N at least 12, twice (before and after a full teardown and reinstall); the image in the cluster is byte-identical (digest) to the local buildah build of the unchanged chapter 08 Containerfile; no Helm, no S2I, no port-forward, no `--tls-verify=false`; teardown leaves no project operator, CSV, CRD or namespace; CRC stopped (and deleted per decision 20); no secret in any file or log.
- **Every appendix:** `part: appendices`, marker and label set, orders 36 to 43 with H last; no dashes, test names, self-reference or forward references into later appendices; versions only where they matter; at most one callout (exactly one for E and G); claims rows in `claims.md`.
- **Iteration:** validate.py (with new self-tests), voice-scan, idiomatic-scan, host_ids and site build green; CI green including appendix poms; no stack, profile or CRC left; `edp-ollama-models` intact; teardowns at most 120 s; earlier examples unchanged; window wall clocks recorded against the targets.

## Risks

| Risk | Detection | Response |
|---|---|---|
| No Flink Kafka SQL connector final release for the newest Flink minor | 1A | pin Flink to the newest minor the connector supports, hold recorded as a valid reason |
| RisingWave single-node memory or startup slow | G0 Q3e, Q3f | memory limit tuned; RisingWave last in the C run order |
| Three engines disagree on closed windows (watermark or idle partitions) | G0 Q3d | one partition for the input topic; idle-source settings; state only measured agreement |
| Camel stream resequencer behaviour at capacity or on restart differs from docs | G0 Q4e, Q4f | the measurement wins; the docs are cited only for what holds |
| 06 consumer's reaction to a breaking payload is not silent loss (for example a retry loop) | G0 Q7d | state what happens; the lesson is the same (one bad record blocks or is lost) |
| Rolling-update redelivery counts vary between runs | G0 Q7c, three runs | state the measured range (r07 rule), never a fixed number |
| Offset copy for blue-green needs the group inactive | G0 Q7e | the script waits until the group is empty before the reset |
| D or F over 2,500 words | draft count | D: terse cells, split only if needed; F: detail into figures and README |
| Redis licence (AGPLv3 option) questioned as open source | 1A licence row | AGPLv3 is OSI-approved; recorded; Valkey named as the BSD alternative in one line |
| Window over target | per-appendix timestamps in WINDOW.md | decision 14 run counts; F demos limited to three; C runs last in the compose window if image pulls are slow |
| CRC preflight needs sudo or `crc setup` (r13b) | owner's `crc setup --check-only` | r13b pauses; owner decides; nothing on the host is changed by an agent |
| AMQ Streams catalog lacks a usable CSV or Kafka version (r13b) | H-Q3 | decision 22 alternative (Strimzi community operator) |
| Secret leakage (SA token, pull secret) in H logs | secret scrub, host_ids | tokens read into variables only; scratch authfile deleted; scrub before commit |
| Host identifiers in crc, podman or oc output | `host_ids.py check`, filter on logs | scrub before commit |
| Vacuous witnesses | bites logged, failing sets frozen | as r08 to r12 |

## Live-window estimate

| Appendix | G0 spike | Window (clean run, demos, bite) | Final run | Runtime |
|---|---|---|---|---|
| A | 15 min | 15 min | 8 min | Dev Services |
| B | 10 min (compose) + 10 min (k8s) | 5 min + 10 min + 1 min compare | 15 min | compose, minikube |
| C | 40 min | 45 min (first image pulls about 15) | 12 min | compose |
| D | none | none | none | none |
| E | 25 min | 35 min | 8 min | compose |
| F | 45 min | 75 min (profile up 5, images 3, run 20, three demos 45) | 25 min (fresh profile) | minikube |
| G | 25 min | 35 min (Connect start dominates) | 10 min | compose |
| **r13 total** | **about 2 h 30 min** | **compose about 2 h 15 min; minikube about 1 h 30 min** | **about 1 h 20 min** | |
| H (r13b) | 40 min (after a 10 to 15 min `crc start`) | 60 min plus 40 min reinstall run | (the reinstall run) | CRC, plus 15 min teardown and delete |

r13 total live time is about 5 h 15 min, spread over spikes, two windows and the final runs, against r12's 2 h window plus spike. That is why H is split off.

### Critical Files for Implementation
- ~/Dev/event-driven-patterns-with-quarkus-and-camel/scripts/validate.py
- ~/Dev/event-driven-patterns-with-quarkus-and-camel/examples/_k8s/lib.sh
- ~/Dev/event-driven-patterns-with-quarkus-and-camel/examples/06-optimizing-subscribers/notification-service/src/main/java/com/patterncatalyst/shipping/tuning/NotificationWriter.java
- ~/Dev/event-driven-patterns-with-quarkus-and-camel/examples/08-transactional-outbox/compose.yaml
- ~/Dev/event-driven-patterns-with-quarkus-and-camel/chapters.html (plus `_layouts/tutorial.html`, `_parts/07-appendices.md`, `.github/workflows/examples.yml`, `scripts/stack-guard.sh`, `~/.claude/skills/lgtm-crc/SKILL.md` for r13b)

## G0 decisions (compose part, from `<scratchpad>/r13-spikes/RESULTS.md`, F1 to F37)

- **G0-1 (A):** route tests use `camel-quarkus-junit` (BOM-managed 3.40.0; JUnit 6 through `camel-test-junit6`) plus `camel-quarkus-mock`; `CamelQuarkusTestSupport` with `isUseAdviceWith()` true, `AdviceWith.adviceWith(...)` with `replaceFromWith("direct:in")` and `mockEndpoints`. The advised test class gets its own `@TestProfile`, because advice otherwise leaks into other test classes of the same Quarkus app. The HTTP dependency is a JAX-RS stub in `src/test` on the test port. KafkaCompanion drain: `consumeStrings().fromTopics(topic, Duration)` then `awaitCompletion().getRecords()`; first match: `fromTopics(topic, 1)` then `getFirstRecord()`. Three bites each fail exactly their named test; the first-match test passes on the duplicate bite. `mvn -B verify` about 1 min; about 49 s per bite.
- **G0-2 (B compose):** `kafka-configs.sh --bootstrap-server ... --entity-type brokers --entity-name 1 --describe --all`, filtered to the key list; output identical across two fresh runs. PostgreSQL `SHOW` set with `PGPASSWORD=edp`; `server_version` carries a Debian build suffix (compare the major.minor only).
- **G0-3 (C, decision 6):** Flink held at 2.2.1 with connector 5.0.0-2.2, verified end to end (`sql-client.sh -f` submit about 5 s; `TUMBLE` with a watermark on `deliveredAt`; upsert-kafka sink). RisingWave `single_node` needs `risingwave/risingwave.toml` (`compactor_memory_limit_mb = 768`) and `--total-memory-bytes` to run at `mem_limit` 4g (exit 139 otherwise); `EMIT ON WINDOW CLOSE` works and is flagged experimental by RisingWave (say so). Kafka Streams IQ needs `Materialized...withRetention` or closed windows disappear from the store. On the same input the three engines agree on closed windows (FASTLINE 4/2/1, AIRCO 2/3/1) and all drop the late record; an out-of-order record is counted only if it is inside each engine's allowed lateness (Kafka Streams grace vs the Flink and RisingWave watermark delay): set the same bound in all three and state the rule. First closed-window answer after the last record: Kafka Streams about 0.05 s, Flink about 3 s, RisingWave about 3 s (about 4 s with EMIT ON WINDOW CLOSE), poll resolution 1.5 to 3 s.
- **G0-4 (E, decision 9):** stream mode only with a global `globalSequence` header from one sequencer (3 topics, 2 producers: exact 1 to 12). The first release waits for the timeout (no predecessor for the first element). Counter-example: stream mode on the per-order `sequence` delivers 3 of 12 and loses 9 with nothing logged (state as measured: neither released nor reported). Batch mode sorts within a batch only (cross-batch inversion observed). Gap: release at timeout. Late arrival: `rejectOld=true` raises `CamelExchangeException`; false applies it last, out of order. Capacity: the consumer thread blocks until the timeout releases held elements (no error). Restart while holding: with auto-commit, held records are lost (SIGTERM and SIGKILL alike); with auto-commit off nothing is committed and all are re-read.
- **G0-5 (G, decision 12):** chapter shows the typed optimistic-locking form `withTransaction(..., watchedKeys)` of the Quarkus Redis client as the version guard; Lua `EVAL` named as the alternative. Staleness over 50 orders: min 347 ms, p50 439 ms, max 448 ms (dominated by Debezium's poll). Cache loss (`restart redis`) then a fresh-group replay rebuilt 55 orders in 2.7 s. `CACHE_WRITE=dual`: the request returns 500 and Redis holds an order PostgreSQL does not. `CACHE_GUARD=off` needs a deterministic two-phase replay (cancelled topic first, then placed) to show the regression; a single group over both topics ended correct by luck, so the witness must not rely on it. Redis Dev Services image set to the pin (validate.py rule).
- **G0-6 (decision 15):** compose peaks 2.12 GiB (C, possibly understated; first run 2.26) and 2.06 GiB (G), no OOM; limits as drafted, RisingWave 4g.
- **G0-7 (`_infra`):** `compose-flink.yaml`, `compose-risingwave.yaml` (+ `risingwave/risingwave.toml`), `compose-redis.yaml` added from the spike, ports on 127.0.0.1.

## G0 decisions (minikube part, from `<scratchpad>/r13-spikes-k8s/RESULTS.md`, K1 to K12)

- **G0-8 (B k8s):** the same `kafka-configs.sh` command with `--entity-name 0` (Strimzi broker id; compose is 1). A wrong entity name prints nothing with exit 0, so every capture check fails on an empty capture. Two fresh captures identical. Allowlist of expected differences, each a sentence in the appendix: `server_version` build suffix; `group.coordinator.rebalance.protocols` without `share` on Strimzi; `share.coordinator.state.topic.min.isr` 2 vs 1 and `.replication.factor` 3 vs 1; CNPG `max_replication_slots` 32 vs 10. The other 10 broker keys and 3 PostgreSQL keys equal.
- **G0-9 (F, decision 10):** the chapter 06 image runs unchanged (`QUARKUS_OTEL_SDK_DISABLED=true`, `GROUP_PROTOCOL=consumer` both accepted). Recreate under 100 records/s: about 7 s with no rows, backlog peak about 680, nothing missing, redeliveries present. Rolling (3 replicas, maxSurge 1, maxUnavailable 0, 9,000 records, three runs per protocol): nothing missing in all six; redelivered classic 104 to 342, consumer 0 to 180. No claim that KIP-848 avoids or reduces redelivery: checks assert nothing missing and a ceiling (600); the README states the measured ranges.
- **G0-10 (F, schema change):** a breaking v2 payload (renamed `orderId`) gives 0 of 20 rows, committed offsets, lag 0, a not-null violation and a Camel "Failed delivery" ERROR in the log: silent loss. The expand v2 loads 20 of 20.
- **G0-11 (F, blue-green):** `group_offsets_copy` in `_k8s/lib.sh` (orchestrator, from the spike): `--reset-offsets --to-current --all-topics --dry-run --export` gives `topic,partition,offset`, `--from-file --execute` works on a never-existing group and on an existing inactive group; group Empty 2 to 3 s after scale-down; cut-over about 12 s. Nothing missing holds; zero re-read does not: committed offsets trail by about the commit interval, so about 220 to 250 records were processed by both groups. The appendix states the re-read as bounded by the commit interval and points to the idempotent consumer (chapter 11); checks assert nothing missing and an overlap ceiling, not zero. `OFFSET_COPY=off`: green re-reads the whole topic (wait for a stable row count, about 70 s).
- **G0-12 (F, HTTP rollout, plan amendment):** under 80 requests/s through the NodePort a rolling update of the chapter 02 order-service lost 5 to 17 requests (0.2 to 0.7 percent) as connection resets or timeouts, never 5xx, with or without `QUARKUS_SHUTDOWN_TIMEOUT`, with `QUARKUS_SHUTDOWN_DELAY`, and with a `preStop` sleep; the no-roll control had 0 errors. Graceful shutdown did not reach zero with env only, so the `GRACEFUL=off` demo is dropped. The appendix states that requests in flight during a roll can fail at the connection level and the client retries; checks assert no 5xx, a failure ceiling, and exactly one event per 201 (0 missing, 0 duplicate, nothing for a failed request).
- **G0-13 (F):** the chapter 02 event has no `eventType`, so 06 cannot consume 02's records; both use `edp.orders.placed`. Group-level checks filter by the run's id range, because a new group replays the whole topic.
- **G0-14 (decision 15, teardown):** `docker stats edp` peak 4.66 GiB of 10 GiB: keep `--cpus=6 --memory=10g`. `k8s_teardown` 21 to 36 s with the fullest state; fresh platform 37 to 41 s (cold 180 s).
