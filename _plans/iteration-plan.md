---
layout: plan
title: "Iteration plan"
render_with_liquid: false
---

# Iteration plan

Source: PRD section 10 (Notion 41261a5f-8a3b-8148-9ffd-f5691ac1251d). Each iteration follows lgtm-relay: Opus plans, owner reviews the plan, Sonnet builds, Opus validates. One `release/rNN` branch and PR per iteration, squash-merged on the owner's OK, tagged `rNN.x`, notes-only release. Run target: C = docker compose, K = minikube + Strimzi.

| Iter | Scope | Chapters | Run | Status |
|---|---|---|---|---|
| r01 | Repo, Jekyll site, CLAUDE.md, _plans, domain-model mirror, compose _infra, domain-model module, CI, validate.py, version re-survey, source and Strimzi-post confirmation | 00 to 01 | C | done (r01.0, 2026-11-10) |
| r02 | Order-to-delivery domain rework of 00 and 01; events between services | 02 to 04 (event notification, event-carried state transfer with claim check, ordering and partitioning with resequencer) | C | done (r02.0, 2026-11-10) |
| r03 | Publishers, subscribers, queues | 05 to 07 (optimizing publishers, optimizing subscribers, queues for Kafka with share groups) | C | done (r03.0, 2026-11-11) |
| r04 | Outbox, Debezium in depth, connector offsets on compose | 08 to 10 | C | done (r04.0, PR #4, 2026-11-11) |
| r05 | Idempotency, exactly-once, retry and dead letter | 11 to 13 | C | done (r05.0, 2026-11-12) |
| r06 | Aggregate root and the event store, snapshots | 14 to 15 | C | done (r06.0, 2026-11-12) |
| r07 | CQRS, replay vs projection, schema evolution | 16 to 18 | C | done (r07.0, PR #8, 2026-11-13) |
| r08 | Enrich, join, aggregate | 19 to 21 | C | done (r08.0, 2026-11-13) |
| r09 | Saga pattern, Camel Saga and Quarkus Flow, Drools + LangChain4j (adds LRA coordinator, Ollama) | 22 to 24 | C | done (r09.0, 2026-11-14) |
| r10 | Observing event flows | 25 to 27 | C | done |
| r11 | minikube + Strimzi + CloudNativePG + LGTM setup; KRaft, UTO, SSA, security context | 28 to 31 | K | shipped (r11.0, #12) |
| r12 | Monitoring Strimzi, Cruise Control, MM2, Connect on Strimzi; chapter 10's Strimzi half | 32 to 35 | K | shipped (r12.0, #13) |
| r13 | Appendices A to G (A testing, B compose to Kubernetes, C Kafka Streams / Flink SQL / RisingWave, D pattern map and glossary, E resequencing across partitions, F deploying on minikube, G write-forward cache with outbox and Redis) | A to G | C, K | shipped r13.0 (2026-11-16) |
| r13b | Appendix H: the chapter 08 outbox on OpenShift Local (CRC started by the orchestrator, Strimzi community operator (AMQ Streams dropped: subscription), CloudNativePG via OLM, buildah + skopeo); full teardown, crc stop and delete | H | CRC | shipped r13b.0 (2026-11-16) |
| r14 | Retro: QA sweep, idiomatic review, voice review, host scrub, full re-run (re-survey dropped by the owner 2026-11-16: pins frozen; Appendix H not re-run unless its code changes) | all | C, K | shipped r14.0 (2026-11-17): every example re-run N/N from fresh state; Appendix F check 30 corrected (retry every failed request) |
| r15 | The deck: `lgtm-presentation/` built like CNDP's, with `text/`, `edits/`, `BUILD.md` and a "Take it with you" QR slide; figures reused from chapter specs; stands alone | none | none | shipped r15.0 (2026-11-17): one 120-slide deck, generator plus `text/`, `edits/`, `BUILD.md`, QR slide; deck gates in CI |
| r16 | Appendix I: batch processing with Kueue on minikube (step-wise state in CloudNativePG, fan-out over partitions, aggregate on completion, replay failed steps, admission, one live preemption case, observability); no word cap (owner); 3 deck slides | Kueue v0.20.6 | none | shipped 2026-11-18 (PR #18, tag r16.0) |

## r01 in detail

Approved 2026-11-10 (see `r01-plan.md`). Decision 1: published `quay.io/debezium/connect` image first.

| Step | Work | Owner | Status |
|---|---|---|---|
| 0 | Preflight: gh auth, docker compose, java, mvn, skopeo; no stack or cluster running | orchestrator | done |
| 1 | Bootstrap: repo, initial commit, licences, host_ids, public GitHub repo, Pages workflow, push release/r01 | orchestrator | done |
| 2 | Version survey -> `_plans/versions.md` | exec A | done |
| 3 | Sources -> `bibliography.md`, `_plans/sources.md` | exec B | done |
| 4 | Site scaffold: config, layouts, includes, css/js, index pages, README, part cards | exec C | done |
| 5 | Tooling and CI: validate.py, scans, diagram scripts, site-build.sh, stack-guard.sh, workflows | exec D | done |
| 6 | CLAUDE.md and _plans (this step) | exec E | done |
| 7 | Compose `_infra`: per-service files, aggregate, Debezium Connect, ports, healthchecks | exec F | done |
| 8 | `examples/domain-model`: records for the 15 entities, JUnit with negative witnesses | exec G | done |
| 9 | Chapter 00 + `examples/00-stack-check`; figure 00-compose-stack | exec H | done |
| 10 | Chapter 01 + `examples/01-crud-vs-events`; figure 01-row-update-vs-event-append | exec I | done |
| 11 | Live verification, serial, clean docker: 00 then 01; log; claims verified; stop all | orchestrator | done |
| 12 | Validation (Opus): validate, voice, idiomatic, host_ids, site build, claims audit | validator | done; repair rounds 1 and 2 done |
| 13 | Ship: PR, CI green, ruleset, squash-merge on owner OK, tag r01.0, notes-only release, delete branch | orchestrator | done |

## r02 in detail

Approved 2026-11-10 (see `r02-plan.md`), with the owner's Claim Check (ch 03) and Resequencer (ch 04) additions.

| Step | Work | Owner | Status |
|---|---|---|---|
| 0 | Preflight: branch release/r02, Docker clean, r01 marked done, Maven HOLD re-check | orchestrator | done |
| 1 | Group 1: docs (1A), `examples/domain-model` 2.0.0 (1B), validate.py domain check (1C), sources (1D) | execs 1A to 1D | done |
| G0 | Gate: install domain-model 2.0.0 | orchestrator | done |
| 2 | Group 2: chapters 00 and 01 reworked onto the order-to-delivery domain | execs 2E, 2F | done |
| G1 | Gate: serial verify from clean, every static gate, claims and log merged | orchestrator | done |
| 3 | Group 3: chapter 02 (3H), chapter 03 with Claim Check (3I), chapter 04 with Resequencer as a per-order buffer (3J, reworked on owner review) | execs 3H to 3J | done |
| 4 | Final serial verify from clean, claims and log, per-chapter commits | orchestrator | done |
| 5 | Validation (Opus): static gates, claims audit, fresh-clone run, originality check, repair rounds | validator | done (two rounds; round 2 text-only) |
| 6 | Ship: PR, CI green, squash-merge on owner OK, tag r02.0, notes-only release, delete branch, PRD update | orchestrator | done |

Owner, 2026-11-16: once r15 ships, update the Notion pages (PRD 41261a5f-8a3b-8148-9ffd-f5691ac1251d and related pages) to the final state of the book.
