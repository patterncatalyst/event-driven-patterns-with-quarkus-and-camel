---
layout: plan
title: r11 plan
render_with_liquid: false
---

# r11 plan: chapters 28 to 31, Part 6 begins on minikube (Opus planner, 2026-11-15)

Status: **approved by the owner 2026-11-15** (all ten recommended decisions; decision 1 amended below; versions in a setup section of chapter 28; merge once validation and CI pass). Branch `release/r11`, cut from main at ed94f19 after r10. Existing pins stay frozen (Strimzi 1.3.1, CloudNativePG v1.30.1, operand `18.6-202611090812-standard-trixie`, Kafka 4.3.2 HOLD, otel-lgtm 0.41.0, UBI 1.24-20). New pins are surveyed once, in step 1A: minikube, Kubernetes, kubectl, and the digests of Strimzi's `quay.io/strimzi/operator:1.3.1` and `quay.io/strimzi/kafka:1.3.1-kafka-4.3.2` and CNPG's `ghcr.io/cloudnative-pg/cloudnative-pg:1.30.1`. No Helm and no standalone kustomize: kubectl's built-in kustomize is used.

## Owner decisions (2026-11-15)

- Decisions 1 to 10 approved as recommended, with decision 1 amended:
  - minikube v1.39.0 (2026-09-02) is the newest release; its `NewestKubernetesVersion` is v1.37.0. Kubernetes stable is v1.37.2 (dl.k8s.io/release/stable.txt). Pin Kubernetes **v1.37.2**; G0 Q1 confirms minikube 1.39.0 runs it, fallback v1.37.0 recorded on a real failure.
  - kubectl is a user-local binary in `~/.local/bin` (not a dnf package). On owner request the orchestrator upgraded it to **v1.37.2** (sha256 checked against dl.k8s.io; previous binary kept as `kubectl-1.36.5.bak`). Scripts use kubectl and minikube from PATH and **check** their versions against `versions.env`, failing with a clear message on mismatch. No project-local download and no `.tools/`.
- Decision 3: versions in a `## Setting up the cluster` section of chapter 28; `validate.py` allows version strings only there.
- Merge on validation and CI pass; ping the owner when merged.

## Findings that bind the design

- **Tools on PATH**, user-local in `~/.local/bin`: minikube v1.39.0, kubectl v1.37.2 (after upgrade), helm v4.3.1 (unused). No minikube profile exists.
- **Host budget:** 16 threads, 62 GiB RAM (about 41 GiB available), 684 GiB free disk. inotify limits already meet the skill's preflight; the check stays read-only. Host ports 32768 and 32769 are taken, so no NodePort uses them.
- **Strimzi changelog facts that change the design:**
  - KRaft and node pools are the default since 0.48. `v1beta2` is gone in 1.0: every CR is `kafka.strimzi.io/v1`.
  - **`ServerSideApplyPhase1` is GA in 1.2.0 and cannot be disabled.** The operator owns its fields on PVCs, ServiceAccounts, Services, Ingresses and ConfigMaps under the field manager `strimzi-kafka-operator`. A foreign annotation on the bootstrap Service survives a reconcile, a forced change to `strimzi.io/discovery` is put back, and a manager's removal deletes only its own field. Chapter 30 cannot use the gate as its off switch.
  - 1.2.0 made the operator YAML's container security context restricted by default. Whether operand pods still come from the `baseline` Pod Security Provider by default is G0 Q5.
  - 1.3.0 needs Kubernetes 1.32 or newer.
- Chapter 00 already promises Part 6 "the same LGTM image" and "its own setup at the start of Part 6, with its tool versions and commands".
- `validate.py` hard-codes compose freshness for every `examples/NN-*/verify.sh`; examples 28 to 31 need a k8s branch. `check_no_forwarding` already bans forwarding.
- The apps of chapters 02 to 04 take all configuration from env and redeploy with no source or pom change. Chapter 04 needs `edp.shipping.shipments` and `edp.shipping.probe` with 3 partitions.
- Skill conventions kept: profile per project, `--keep-context`, `-p`/`--context` on every call, never `minikube config set`, loopback NodePorts via `minikube start --ports=127.0.0.1:N:N`, `docker build` then `minikube image load` with `imagePullPolicy: Never`, tiered readiness gates.
- Skill conventions dropped: Helm LGTM charts (six new pins, about 4 GiB; the single otel-lgtm pod keeps the HTTP APIs the Part 5 helpers query); Istio and KEDA (no chapter needs them); Helm for operators (pinned release manifests applied with kubectl are the subject of chapter 30); metrics-server addon.

## Approach

One shared minikube substrate in `examples/_k8s/` (profile, platform tier, Kafka, PostgreSQL, LGTM and app bases as kustomize directories). Examples 28 to 31 are manifest-only overlays with a `verify.sh` and a README. No Java in r11: apps are the chapter 02 to 04 images, built from their own Containerfiles and loaded into the node.

1. Step 1: survey, sources and G0 spike in parallel; the substrate is built during G0.
2. Step 2: four authoring executors on disjoint files, no cluster.
3. Step 3: one live session on one profile; windows 28, 29, 30, 31 serially, fresh namespaces per `verify.sh` run.
4. Step 5: final run on a recreated profile.
5. Step 6: Opus validation, then ship.

Wall-clock target: about 2 h 15 min from approval to validation.

- **28** declares Kafka as `Kafka` plus two `KafkaNodePool`s (controller, broker) in KRaft, runs the chapter 02, 03 and 04 apps unchanged against Strimzi, CNPG and otel-lgtm, and keeps the compose way of making topics (a Job running the same `kafka-topics.sh` commands, auto-create on).
- **29** turns auto-create off and declares a `KafkaTopic` for every catalog topic (`Topics.ALL`) plus chapter 02's needs; drift, outside creation, deletion through the finalizer, `strimzi.io/managed: "false"`. Apps: the chapter 02 pair.
- **30** server-side apply on three surfaces: install (CRDs too large for client-side apply), two field managers on one Deployment field (conflict, `--force-conflicts`, field removed by its only owner), Strimzi-managed objects (foreign annotation kept, owned field restored). Apps: the chapter 02 pair.
- **31** labels `edp`, `edp-kafka` and `cnpg-system` `restricted`; the app without a security context is refused with the fields named; with the four fields it runs as UID 185; Strimzi pods pass with the provider G0 picks; CNPG pods pass as published; otel-lgtm stays in its own namespace, with a server dry-run label showing why.

Rejected: Quarkus Kubernetes extension (changes the 02 to 04 poms, hides the YAML); Helm for Strimzi and CNPG; Helm LGTM; `minikube image build`, registry addon, `minikube docker-env`; fresh profile per `verify.sh` run (4 to 6 min each; final run only); Strimzi external NodePort listener (`kubectl exec` replaces `docker exec`); single dual-role node (Try bullet); Strimzi in a persistent namespace; 15-run timing witnesses (no chapter claims a timing).

## Decisions (approved)

1. Tool binaries: see owner decisions above.
2. Kubernetes latest stable (v1.37.2); hold only on an actual CNPG failure in G0 Q1.
3. Versions in chapter 28's setup section.
4. `grafana/otel-lgtm:0.41.0` as one Deployment in `edp-lgtm`.
5. Two pools, `controller` (1) and `broker` (1), each 1 GiB limit, heap via `jvmOptions`, `persistent-claim` 2 Gi with `deleteClaim: true`. Dual-role is a Try bullet.
6. 28 runs all three app sets; 29 to 31 run only the chapter 02 pair.
7. Profile `edp`, `--cpus=6 --memory=12g --disk-size=30g`; G0 Q1 measures the peak; 10g if it stays under 8 GiB.
8. Fresh state per `verify.sh` run: delete and recreate namespaces `edp`, `edp-kafka`, `edp-lgtm` (KafkaTopics first). Kept per profile: CRDs, cluster-scoped RBAC, CNPG operator in `cnpg-system`, node image cache. Profile recreated at the start of the live session and for the final run, deleted at the end of each.
9. Part 6 examples are manifest overlays over `examples/_k8s`, deploy earlier chapters' Quarkus projects unchanged, no pom; CI's per-example `mvn verify` skips directories without `pom.xml`.
10. Slugs `28-strimzi-and-kraft`, `29-unidirectional-topic-operator`, `30-server-side-apply`, `31-security-context-and-pod-security`; order ids `O-28xx` to `O-31xx`; `part: running-on-kubernetes-with-strimzi`; no domain-model change.

## Shared conventions (`examples/_k8s`, built in step 1C under G0)

```
examples/_k8s/
  README.md            versions table, cluster.sh usage, NodePort map, fresh-state rule
  versions.env         MINIKUBE_VERSION, K8S_VERSION, KUBECTL_VERSION, STRIMZI_VERSION=1.3.1,
                       CNPG_VERSION=1.30.1, URLs and sha256 of every downloaded manifest
  tools.sh             checks minikube/kubectl on PATH against versions.env; fails clearly on mismatch
  cluster.sh           up | down | status: profile create (ports, sizing) or verify; platform tier
  lib.sh               sourced by verify.sh: kc(), check(), precondition(), poll(), expect(),
                       k8s_fresh, k8s_teardown, load_image, kafka_exec, psql_exec, tempo/prom helpers
  platform/cnpg/       kustomization over vendor/cnpg-1.30.1.yaml (applied --server-side)
  strimzi/             kustomization over vendor/strimzi-cluster-operator-1.3.1.yaml, namespace edp-kafka,
                       STRIMZI_FULL_RECONCILIATION_INTERVAL_MS per G0 Q3
  kafka/               Kafka edp (v1, KRaft, 4.3.2, listener plain 9092, RF 1, auto-create on,
                       num.partitions 3), KafkaNodePool controller + broker
  postgres/            CNPG Cluster edp-pg in edp: 1 instance, pinned operand, initdb db edp owner edp, 1 Gi
  lgtm/                otel-lgtm Deployment + Service (NodePorts) in edp-lgtm, emptyDir
  apps/02 apps/03 apps/04   Deployments + Services of the chapter 02 to 04 apps
  vendor/              gitignored: downloaded release manifests, verified by sha256
```

- Namespaces: `cnpg-system` (platform), `edp-kafka` (Strimzi operator, Kafka, KafkaTopics), `edp` (CNPG `Cluster`, apps), `edp-lgtm`.
- Addresses: `CAMEL_COMPONENT_KAFKA_BROKERS=edp-kafka-bootstrap.edp-kafka.svc:9092`; `QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://edp-pg-rw.edp.svc:5432/edp` with user and password from `secretKeyRef` on `edp-pg-app`; `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm.edp-lgtm.svc:4318`; service URLs to Service names in `edp`. Probes on `/q/health/ready` and `/q/health/live`. Memory limit 384Mi.
- NodePorts on 127.0.0.1: 30080, 30082, 30084 apps; 30300 Grafana, 30320 Tempo, 30100 Loki, 30909 Prometheus. Not 9090, 30081, 32768, 32769. `cluster.sh up` checks with `ss -ltnH` and `docker port edp`.
- Images: `load_image <NN> <service>` builds `localhost/edp/<NN>-<service>:local` from the compose build context and file, then `minikube -p edp image load` only when the node's image ID differs (G0 Q1 confirms the command). `imagePullPolicy: Never`. Upstream images pinned by tag, digests recorded.
- `k8s_fresh`: stack guard; `cluster.sh up` (idempotent); if `edp-kafka` exists delete `kafkatopic --all`, then `kafka` and `kafkanodepool`; delete ns `edp edp-kafka edp-lgtm --wait --timeout=240s`; assert nothing Terminating and no bound PV left; `kubectl apply --server-side --field-manager=edp-verify -k <example>`; tiered waits. `k8s_teardown` in the EXIT trap unless `KEEP=1`. Stuck finalizers fail loudly; never stripped.
- Every script applies with `--server-side`.
- Stack guard: `scripts/stack-guard.sh --minikube edp` refuses when a compose-project container runs, another minikube profile runs, or CRC is Running.
- `verify.sh`: helpers and PASS/FAIL lines as 25 and 26; starts with `k8s_fresh`, ends with the pass count; demo switches are env vars labelled demo code; no fixed sleeps; Kafka via `kubectl exec` into the broker pod, PostgreSQL via `kubectl exec edp-pg-1 -c postgres -- psql`. No forwarding.
- `validate.py` (orchestrator): k8s branch of the freshness rule for examples with `kustomization.yaml`; images fully qualified and tagged, never `latest`; `imagePullPolicy: Never` only on `localhost/edp/`; version strings allowed in chapter 28's setup section; self-tests.
- Lock: every cluster or docker-build action under `flock -w 7200 <scratchpad>/stack.lock`. Authoring executors run only `kubectl kustomize` and static checks.
- `.gitignore`: `examples/_k8s/vendor/`.

## G0 spike (step 1B, scratchpad, under the lock, one profile, about 60 min)

Results to `<scratchpad>/r11-spikes/RESULTS.md`, then "G0 decisions" appended here.

- **Q1 Bring-up.** `minikube start -p edp --keep-context --driver=docker --container-runtime=containerd --kubernetes-version=v1.37.2 --cpus=6 --memory=12g --ports=<list>` succeeds; every port on 127.0.0.1; timings of profile create, CNPG platform, Strimzi operator, Kafka Ready, CNPG `Cluster` Ready, otel-lgtm Ready; `docker stats edp` peak with six apps; `minikube image load` time and host vs node image ID comparison. Pass: all Ready; peak at most 10 GiB; load at most 30 s an image. Fallback: v1.37.0.
- **Q2 KRaft node pools.** Two pools Ready; `status.nodeIds`; `kafka-metadata-quorum.sh describe --status` shows the controller as only voter and leader, the broker as observer; `.status.kafkaVersion` 4.3.2; pods owned by `StrimziPodSet`; broker pod deleted returns with same node id and PVC, records present; topics Job on `quay.io/strimzi/kafka:1.3.1-kafka-4.3.2` works. Record exact commands.
- **Q3 UTO.** With the topic operator (short reconcile interval) and auto-create off: (a) KafkaTopic becomes topic, `Ready=True`; (b) spec'd key altered in Kafka is reverted; unspec'd key stays or resets; (c) partitions raised in Kafka: KafkaTopic status, reason, message; (d) topic created by `kafka-topics.sh` gets no KafkaTopic; (e) topic deleted in Kafka recreated or not; (f) KafkaTopic deleted: topic gone, finalizer time; (g) `strimzi.io/managed: "false"` then delete: topic remains; (h) producing to an undeclared topic: exact error; (i) namespace teardown with KafkaTopics deleted first: no stuck finalizer.
- **Q4 SSA.** (a) client-side `kubectl apply --dry-run=server` of the Strimzi install fails with "Too long" (name the CRD), `--server-side` succeeds (fallback: CNPG's documented requirement); (b) `edp-apps` then `edp-platform` on the same `resources.limits.memory`: conflict naming `edp-apps`; `--force-conflicts` moves ownership in `managedFields`; (c) client-side second writer overwrites silently; (d) a label removed from the `edp-apps` manifest disappears; (e) Strimzi: foreign annotation on `edp-kafka-bootstrap` survives a reconcile, forced change to an operator-owned field is restored; (f) `kubectl diff --server-side` output.
- **Q5 Pod Security Admission.** `enforce=restricted` (+ `enforce-version`) on `edp-kafka`, `edp`, `cnpg-system`: (a) Strimzi pods with the default provider admitted or refused; `STRIMZI_POD_SECURITY_PROVIDER_CLASS=restricted` effect; (b) CNPG instance pod and initdb Job admitted, UID 26; (c) app without security context: ReplicaSet `FailedCreate` naming the four fields; with them, `id -u` 185; (d) `readOnlyRootFilesystem: true` plus emptyDir `/tmp` starts or not; (e) dry-run label on `edp-lgtm` warnings; (f) `RuntimeDefault` seccomp works. If Strimzi complies by default, chapter 31's Strimzi witness is a violating `template.pod.securityContext` override chosen in G0.
- **Q6 Apps unchanged.** 02 pair ready with the env mapping; order produces a notification; Tempo holds one trace with `POST /orders` and a notification-service span; `CAMEL_COMPONENT_KAFKA_BROKERS=kafka:9094` (compose address): readiness and notification behaviour recorded (chapter 28 witness).

Profile deleted at the end of G0 unless step 3 follows within 30 minutes.

## Per-chapter design

Shared: no Java changes; each example holds `kustomization.yaml`, `verify.sh`, `README.md` and an example page; at least two figures per chapter from `diagrams/specs/<NN>-*.py`; spine per CLAUDE.md; short Build, run, observe; one callout; no forward references (28 does not name KafkaTopic, SSA field managers or PSA; 29 does not name PSA).

### 28 Strimzi and KRaft

Headings: `## Setting up the cluster` (versions table, `cluster.sh up`, NodePort map), `## Pattern: Operator-managed Kafka`, `## Controllers, brokers and node pools`, `## The same services on new addresses`, Build, run, observe, When not to use it, References.

Problem: on Kubernetes a broker needs a stable node id, its own volume, resolvable listeners and a controller quorum; a hand-written StatefulSet gets restart, identity and configuration rules wrong.

Code shown: the `Kafka` CR, two `KafkaNodePool`s, one Deployment `env` next to the compose service it came from, the topics Job. Figures: `28-operator-reconcile`, `28-kraft-roles`, optional `28-compose-to-minikube`.

verify.sh (about 20 checks): preconditions (guard, ports, CNPG platform, Strimzi operator); Kafka Ready; pool roles and `status.nodeIds`; broker pod owned by `StrimziPodSet`; quorum (controller only voter and leader, broker observer); `.status.kafkaVersion`; topics Job completed, `edp.shipping.shipments` 3 partitions; CNPG `Cluster` Ready; six node images match `docker build` IDs from unchanged Containerfiles; Deployment env names map to compose (python `-I` compare); 02: O-2801 notification; 03: order-service scaled to 0, O-2802 copy still answered; 04: O-2803 shipment events to DELIVERED with both replicas; broker pod deleted returns with same node id and `edp.orders.placed` still holds O-2801; one Tempo trace.

Negative witness: `KAFKA_BROKERS=compose` (demo) fails the notification and trace checks (set frozen in the window). Try: `KAFKA_BROKERS=compose`; `NODE_POOLS=dual`; delete the broker pod with `KEEP=1`.

Sources: Strimzi docs (deploying, node pools, KRaft, listeners); Strimzi blog posts on node pools, KRaft, storage; Kafka 4.3 KRaft operations; minikube docs (`--ports`, `image load`); CNPG quickstart and `Cluster` bootstrap; back links 00, 02, 03, 04.

### 29 Unidirectional Topic Operator

Headings: `## Pattern: Topics as declared resources` (problem, solution, how Strimzi models it), `## One direction only`, `## Drift`, `## Deletion and unmanaged topics`, Build, run, observe, When not to use it, References.

Code shown: one `KafkaTopic` (`edp.orders.placed`), the Kafka overlay (topic operator, auto-create off), the managed annotation. Figures: `29-one-direction`, `29-topic-lifecycle`; decision table from G0 Q3 only.

verify.sh (about 18 checks): preconditions; KafkaTopic names equal `Topics.ALL` parsed from `Topics.java`; all Ready; partitions match; O-2901 notified; undeclared topic refused and absent; spec'd retention drift reverted; partitions raised: status as measured; outside-created topic gets no KafkaTopic; Kafka-deleted topic behaviour as measured; KafkaTopic deletion removes topic; unmanaged then deleted keeps topic.

Negative witness: `TOPIC_AUTOCREATE=on` (demo) fails the undeclared-topic check; an altered kustomization missing one KafkaTopic fails the set check and the notification check (bite). Try: `TOPIC_AUTOCREATE=on`; raise partitions; delete with and without unmanaged.

Sources: Strimzi docs (Topic Operator), Strimzi UTO blog post and proposal, Kafka `auto.create.topics.enable`; back link 28.

### 30 Server-side apply

Headings: `## Pattern: Field ownership` (problem, solution, how Kubernetes and Strimzi model it), `## Installing the operators`, `## Two writers on one Deployment`, `## Strimzi's own fields`, Build, run, observe, When not to use it, References.

Code shown: `kubectl apply --server-side --field-manager=edp-apps -k .` and the `edp-platform` patch; the conflict message; a `managedFields` excerpt; `kubectl diff --server-side`. Figures: `30-field-managers`, `30-conflict-and-force`, optional `30-strimzi-ssa`.

verify.sh (about 16 checks): preconditions; client-side dry run of Strimzi install fails "Too long", server-side succeeds; `managedFields` lists `edp-apps`; `edp-platform` conflict names `edp-apps`, object unchanged; force moves ownership; re-apply by `edp-apps` conflicts again; label dropped by its only owner removed; O-3001 notified; foreign annotation on `edp-kafka-bootstrap` survives two reconciles; forced operator-owned field restored; `strimzi-kafka-operator` is a manager on the Service.

Negative witness: `APPLY=client` (demo) makes the conflict checks fail. Try: `APPLY=client`; change an operator-owned field; `kubectl diff --server-side`.

Sources: Kubernetes "Server-Side Apply" docs and kubectl apply reference; KEP-555; Strimzi "Server-Side Apply in Strimzi" blog (2026-01-26); Strimzi CHANGELOG; CNPG installation docs; back link 28.

### 31 Security context and pod security

Headings: `## Pattern: Restricted pods by default` (problem, solution, how Kubernetes, Strimzi and CloudNativePG model it), `## The app's security context`, `## Strimzi pods`, `## CloudNativePG pods`, `## What stays outside`, Build, run, observe, When not to use it, References.

Code shown: namespace labels; the app `securityContext` (plus `readOnlyRootFilesystem` with `/tmp` emptyDir if G0 Q5d passes); Strimzi provider env or override per G0 Q5a; the refusal message. Figures: `31-admission`, `31-who-sets-what`.

verify.sh (about 18 checks): preconditions; three namespaces labelled restricted; dry-run labels report no warnings; apps Ready, `id -u` 185, security context fields present; O-3101 notified; Kafka pods Running and restricted-compliant; CNPG instance pod Running UID 26; CNPG operator namespace passes dry-run label; `edp-lgtm` dry-run label returns the G0 warnings.

Negative witnesses: `APP_SECURITY=none` (demo) gives `FailedCreate` naming the four fields; Strimzi witness per G0 Q5a. Try: `APP_SECURITY=none`; the Strimzi witness; dry-run label on `edp-lgtm`.

Sources: Kubernetes Pod Security Standards and Admission docs; Strimzi "Pod Security Providers" docs and blog posts; CNPG security docs; UBI OpenJDK image docs (user 185); back links 28, 30.

## Build, run, observe rule (unchanged)

One command block (`examples/_k8s/cluster.sh up` once in 28's setup, then `cd examples/NN-slug && ./verify.sh`), one or two sentences ending with the pass count, at most three one-sentence Try bullets, 250 words soft cap, no check numbers or run statistics. 28 adds one sentence: the first run builds and loads six images.

## Steps

| Step | Work | Owner | Depends | Status |
|---|---|---|---|---|
| 0 | Preflight: owner OK, save plan, iteration-plan r11 in progress, `.gitignore` | orchestrator | owner OK | done (kubectl upgraded to v1.37.2) |
| 1A | Survey new pins and digests, manifest sha256s; sources rows 28 to 31; bibliography rows | exec 1A (Sonnet) | 0 | done |
| 1B | G0 spike Q1 to Q6 on profile `edp` | exec 1B (Sonnet) | 0 | done |
| 1C | `examples/_k8s/**` substrate; stack-guard `--minikube`; validate.py k8s branch, version exception, self-tests; CI skip for pom-less examples | orchestrator | 1B | done |
| G0 | "G0 decisions" appended; Strimzi PSA witness chosen; decisions 2 and 7 from data | orchestrator | 1A to 1C | pending |
| 2A to 2D | Chapters 28, 29, 30, 31: overlay, verify.sh, README, example page, chapter, figures | execs 2A to 2D | G0 | done |
| 2E | Consistency: chapter 00 Part 6 paragraph, part card, CLAUDE.md decisions draft | orchestrator | G0 | done |
| 3 | One live session: windows 28 to 31 serially; 2 clean N/N runs each, each experiment once with failing set frozen, one bite proof | orchestrator | 2A to 2D | done (S/r11-window/WINDOW.md) |
| 4 | Fixes inside the window, re-run affected chapter only | orchestrator | 3 | done |
| 5 | Final run on a fresh profile, 28 to 31 once each, profile deleted; static gates; claims and log; commits | orchestrator | 4 | done; ch30 teardown timeout once, fixed in lib.sh (CNPG Databases and Cluster deleted before namespaces), re-verified in step 6 |
| 6 | Opus validation (at most 2 repair rounds) | validator | 5 | done (1 round; teardown cause fixed) |
| 7 | Ship: PR, CI green, squash, tag r11.0, notes-only release; PRD, iteration-plan, CLAUDE.md | orchestrator | 6 | pending |

Executor rules as in r10: no git writes; no edits to `claims.md`, `verification-log.md`, `CLAUDE.md`, `examples/_k8s`, `examples/_infra`, `scripts/`, `domain-model`; no cluster in step 2; logs through `host_ids.py filter`.

## Acceptance criteria

- **28:** verify N/N with N >= 18, twice from fresh namespaces and once on a fresh profile; two node pools with separate roles and the quorum shown by a Kafka tool; six images from unchanged Containerfiles and an empty `git diff --stat` under `examples/02-*`, `03-*`, `04-*`; `KAFKA_BROKERS=compose` fails exactly its stated set; setup section lists every new pin.
- **29:** N >= 16, same runs; KafkaTopic names equal `Topics.ALL`; drift, partition-raise, outside-creation, deletion and unmanaged each a check, stated only as measured; `TOPIC_AUTOCREATE=on` fails exactly its stated set.
- **30:** N >= 14, same runs; a real conflict message naming `edp-apps` in the log; `managedFields` ownership moves on force; Strimzi foreign-annotation and restored-field checks pass; `APPLY=client` fails exactly its stated set.
- **31:** N >= 15, same runs; three namespaces enforce restricted with all Strimzi, CNPG and app pods Running; `APP_SECURITY=none` and the Strimzi witness fail exactly their stated sets; otel-lgtm exception stated with its dry-run warning.
- **Every chapter:** at most 2,500 words; one callout; at least 2 figures from specs; `part: running-on-kubernetes-with-strimzi`; no dashes, test names, self-reference or forward references; versions only in 28's setup section; demo switches labelled; every claim backed or cut.
- **Iteration:** no forwarding anywhere; no profile, namespace or `edp-*` container left after step 5; validate.py, voice-scan, idiomatic-scan, host_ids and site build green; versions.md rows for minikube, Kubernetes, kubectl and digests; no domain-model change; CI green; tag r11.0; wall clock recorded.

## Risks

| Risk | Detection | Response |
|---|---|---|
| CNPG 1.30 or minikube 1.39.0 on Kubernetes 1.37.2 | G0 Q1; `Cluster` Ready precondition | fall back to v1.37.0, recorded |
| KafkaTopic finalizers block namespace deletion | `k8s_fresh` timeout and Terminating assertion | delete topics first; fail loudly |
| Strimzi operand pods already restricted by default | G0 Q5a | violating `template` override as witness |
| Unspec'd config drift not reverted | G0 Q3b | state spec'd keys only |
| Reconcile interval longer than poll bound | logged durations | interval env in the base |
| Image load slow or ID compare wrong | node ID equals host ID check | load only on mismatch |
| Memory pressure with six apps | `docker stats edp`, restart count check | 12g, or stagger app sets |
| Port collisions | `ss -ltnH`, `docker port` | renumber before G0 closes |
| `--ports` binds 0.0.0.0 | `docker port` shows 127.0.0.1 only | loopback form only |
| PATH tool version drift | `tools.sh` version check | fail with the expected version |
| Compose stack or CRC running alongside | `stack-guard.sh --minikube` | refuse; never stop anything |
| Vacuous negative witnesses | bites logged, failing sets frozen | as r08 to r10 |
| Chapter 28 over 2,500 words | draft count | setup detail to `_k8s/README.md` |
| Host identifiers in kubectl output | `host_ids.py check`, filter on logs | scrub before commit |
| Downloaded manifests change behind the URL | sha256 in `versions.env` | refuse on mismatch |

## G0 decisions (2026-11-15, from `<scratchpad>/r11-spikes/RESULTS.md`, findings F1 to F9)

- **G0-1 Kubernetes v1.37.2** runs on minikube v1.39.0 with every component Ready. CNPG v1.30.1 lists 1.37 as "Tested, but not supported" (`supported_releases.md` at v1.30.1); the pin stays (decision 2: no actual failure) and chapter 28's setup section says it in one sentence.
- **G0-2 Profile `--cpus=6 --memory=10g --disk-size=30g`.** Peak `docker stats edp` 4.92 GiB, steady 4.3 to 4.6 GiB with six app pods. Bring-up: profile 30 s, CNPG operator 12 s, Strimzi operator 37 s, Kafka Ready +56 s, CNPG Cluster 39 s, otel-lgtm 29 s. `docker build` 44 to 55 s and `minikube image load` 16 to 19 s per image; host and node image IDs identical.
- **G0-3 Vendor manifests live inside their kustomization directories** (`examples/_k8s/platform/cnpg/cnpg-1.30.1.yaml`, `examples/_k8s/strimzi/strimzi-cluster-operator-1.3.1.yaml`), gitignored, downloaded by `cluster.sh` and checked against the sha256 in `versions.env`. This keeps plain `kubectl apply --server-side -k` working (F1: kustomize refuses `../vendor` without `--load-restrictor`). The Strimzi YAML's RoleBinding subjects name `myproject`; `strimzi/kustomization.yaml` patches them (F2).
- **G0-4 One database per app set** (F3: the 02, 03 and 04 Flyway histories collide in one database). CNPG `Cluster edp-pg` bootstraps `edp` (chapter 02 set); declarative CNPG `Database` resources add `edp03` and `edp04`, owner `edp`. Chapter 28 runs the three sets **one after another** on the same Service names and NodePorts (F4): deploy, check, delete the set's Deployments, next set.
- **G0-5 Topic operator:** `spec.entityOperator.topicOperator.reconciliationIntervalMs: 15000` in chapter 29's Kafka overlay (default 90 s). Measured: KafkaTopic Ready 1.1 s; spec'd `retention.ms` drift reverted after 8.2 s; partitions raised in Kafka gives `Ready=False`, reason `NotSupported`, message "Decreasing partitions not supported"; topic created with `kafka-topics.sh` gets no KafkaTopic; topic deleted in Kafka recreated after 27.5 s; KafkaTopic deleted removes the topic in 1.4 s; `strimzi.io/managed: "false"` then delete keeps the topic; undeclared topic: producer `TimeoutException: Topic ... not present in metadata after 8000 ms` after `UNKNOWN_TOPIC_OR_PARTITION` warnings; KafkaTopics deleted first, namespace gone in 11 s. **Unspec'd key behaviour is not proven: the chapter makes no claim about it** unless the window measures it. Finalizer name is `strimzi.io/topic-operator`.
- **G0-6 Chapter 30's "Too long" witness is CloudNativePG**, not Strimzi: client-side apply of `cnpg-1.30.1.yaml` fails "Too long: may not be more than 262144 bytes" on `clusters.postgresql.cnpg.io` and `poolers.postgresql.cnpg.io`; Strimzi 1.3.1's largest CRD (`kafkas`, about 172 KB) fits. Conflict message names `edp-apps`; `--force-conflicts` moves ownership to `edp-platform`, and `edp-apps` then conflicts naming `edp-platform`; client-side apply overwrites silently and drops `edp-apps` ownership; label dropped by its only owner is removed. Strimzi: foreign annotation on `edp-kafka-bootstrap` survives two reconciles; forced `strimzi.io/discovery` change restored in about 5 s. Field-manager names are taken from `managedFields` output, never assumed.
- **G0-7 Chapter 31:** the Strimzi operator pod is restricted-compliant as published; Kafka pods from the default provider are **refused** under `enforce=restricted` ("violates PodSecurity restricted:v1.37"). `STRIMZI_POD_SECURITY_PROVIDER_CLASS=restricted` on the operator admits them (Kafka Ready in 84 s, uid 1001). The chapter 31 Strimzi witness is the default provider (`STRIMZI_PSP=baseline`, demo), measured directly; the `seccompProfile: Unconfined` template override is also refused but is not used (recovery took about 8 minutes). Any witness that blocks Kafka is followed by namespace teardown, never repair. CNPG operator and instance (uid 26) admitted as published. App without a security context: ReplicaSet `FailedCreate` naming the four fields; with them `id -u` is 185; `readOnlyRootFilesystem: true` plus an emptyDir on `/tmp` starts, so chapter 31 includes it. otel-lgtm: the server dry-run label warns that the existing pod violates restricted, so `edp-lgtm` stays unlabelled. `RuntimeDefault` works on containerd.
- **G0-8 Chapter 28 witness:** with `CAMEL_COMPONENT_KAFKA_BROKERS=kafka:9094` notification-service crash-loops ("Couldn't resolve server kafka:9094", "No resolvable bootstrap urls"), readiness never comes and no notification appears. On a fresh namespace there is no old pod to keep serving.
- **G0-9 Tempo:** poll up to 90 s for a trace (a 15 s search missed once).
- **G0-10 KRaft:** broker pool nodeIds [0], controller [1]; pods named `edp-broker-0`, `edp-controller-1`; owned by `StrimziPodSet`; `kafka-metadata-quorum.sh describe --status` shows node 1 the only voter and leader, node 0 the only observer; `.status.kafkaVersion` 4.3.2; broker pod deleted is Ready again in 23 s with the same PVC, node id and offsets.

### `examples/_k8s/lib.sh` contract (binding for chapter executors)

`verify.sh` starts `set -euo pipefail; cd "$(dirname "${BASH_SOURCE[0]}")"; source ../_k8s/lib.sh`, then `trap k8s_teardown EXIT`, then `k8s_fresh .` (or `k8s_fresh <dir> [field-manager]`).

- `kc ...` = `kubectl --context edp ...`; `mk ...` = `minikube -p edp ...`.
- `check "<desc>" <cmd...>`, `precondition "<desc>" <cmd...>` (counted; failure prints diagnostics and stops), `poll <seconds> <cmd...>`, `expect <actual> <expected>`, `summary` (prints `N/N checks passed`, exit 0 only when all passed). Same PASS/FAIL line format as chapter 26.
- `k8s_fresh <kustomize-dir> [manager=edp-verify]`: stack guard (`--minikube edp`), `cluster.sh up` (idempotent: profile, CNPG platform), deletes KafkaTopics, Kafka, node pools, then namespaces `edp edp-kafka edp-lgtm`, asserts none left Terminating, `kc apply --server-side --field-manager=<manager> -k <dir>`, then tiered waits: Strimzi operator Available, Kafka `Ready`, CNPG Cluster `Ready`, lgtm rollout, every Deployment in `edp` rolled out (skip with `WAIT_APPS=0`).
- `k8s_teardown`: deletes the three namespaces (KafkaTopics first) unless `KEEP=1`; never deletes the profile.
- `load_image <NN-slug> <service>`: `docker build -f examples/<NN-slug>/<service>/Containerfile -t localhost/edp/<NN>-<service>:local examples`, `mk image load` only when the node's image ID differs. Called inside `k8s_fresh` for each image listed in the example's `images.txt` (`<NN-slug> <service>` per line).
- `kafka_exec <tool> <args...>`: `kc -n edp-kafka exec edp-broker-0 -- /opt/kafka/bin/<tool> --bootstrap-server localhost:9092 <args...>` (tools that take no `--bootstrap-server` via `kafka_raw`).
- `psql_exec <db> <sql>`: `kc -n edp exec edp-pg-1 -c postgres -- psql -tA -d <db> -c <sql>`.
- `http_get <url>`, `ready <nodeport>` (`/q/health/ready` on 127.0.0.1).
- `tempo_trace_with <seconds> <span-name>...`: polls Tempo at 127.0.0.1:30320 (python3 -I helper in `examples/_k8s/tools/tempo.py`, trace ids zero-padded to 32) for one trace holding every named span.
- `deploy_set <kustomize-dir>` / `remove_set <kustomize-dir>`: apply / delete an app set and wait for rollout (chapter 28's sequential sets).
- Constants: `NS_APPS=edp NS_KAFKA=edp-kafka NS_LGTM=edp-lgtm`, `ORDERS=http://127.0.0.1:30080`, `PORT2=30082`, `PORT3=30084`.

Base kustomizations a chapter overlay may list in `resources:` (paths from `examples/NN-slug/`): `../_k8s/namespaces`, `../_k8s/strimzi`, `../_k8s/strimzi-restricted` (component-style alternative), `../_k8s/kafka`, `../_k8s/postgres`, `../_k8s/lgtm`, `../_k8s/apps/02`, `../_k8s/apps/03`, `../_k8s/apps/04`. The CNPG platform is applied by `cluster.sh up`, not by overlays.
