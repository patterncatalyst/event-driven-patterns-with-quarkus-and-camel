---
layout: plan
title: r01 plan
render_with_liquid: false
---

# r01 plan: event-driven-patterns-with-quarkus-and-camel (Opus planner, 2026-11-10)

Status: approved by owner 2026-11-10 with all recommendations, except decision 1: use the published quay.io/debezium/connect image first; build our own UBI 10 Connect image only if a real need appears. More than one figure per chapter wherever a figure explains better than text. Orchestrator correction applied: Connect-image JDK fallback is ubi9/openjdk-25 (same runtime), never openjdk-21.

## Approach
Copy EIP Camel's site machinery (_layouts, _includes, assets/css, _parts, _plans output:false, _example_pages, bibliography, LICENSE pair, summary-job CI). Take newer sibling patterns where EIP Camel lags: diagrams/specs/<name>.py -> diagrams/<name>.{svg,excalidraw}; CNDP per-service compose include files; CNDP content.yml; per-example verify.sh; scripts/validate.py merging CNDP validate_site.py + EIP Camel scheme check + this book's rules. Every built image UBI 10; each example has compose include, Containerfile, verify.sh. Quarkus tests use Dev Services and run before compose (one stack at a time).

Calls: ch 00 compose half only at r01 (minikube/Strimzi half at r11). LRA coordinator + Ollama deferred to r09. Debezium Connect in _infra at r01, health + plugin check in ch 00 verify.sh.

Rejected: lgtm-jekyll template as base; single compose.yaml; separate Grafana/Loki/Tempo/Mimir; java -jar on host; full verify.sh in CI (CI = mvn verify per changed example; live verify.sh local, logged); per-example copies of domain records.

## Version survey (step 2, _plans/versions.md)
Temurin 25.0.x; Maven 3.9.x; Quarkus platform + quarkus-camel-bom; Camel line + camel-launcher; Kafka (newest the latest Strimzi supports; PRD 4.3.2); apache/kafka image; Strimzi + CNPG operator (record); CNPG operand ghcr.io/cloudnative-pg/postgresql:18.n; library/postgres same minor (Dev Services only); Apicurio 3.x; Debezium .Final (plugin + debezium-quarkus-outbox); Kafka tarball + sha512; grafana/otel-lgtm; ubi10/openjdk-25(+runtime); ruby:4.0.n + Jekyll 4.4.1; JUnit/surefire/compiler; Python 3.14 + PyYAML; GH Actions exact tags; Docker Engine/compose (record host). Deferred: LRA, Ollama/qwen2.5:3b, Quarkus Flow, Drools, langchain4j (r09); Flink, RisingWave (r13); minikube/K8s (r11).

## Steps
0. Preflight (orchestrator, read-only): gh auth, docker compose, java, mvn, skopeo; no stack/cluster running.
1. Bootstrap (orchestrator): repo dir, main initial commit (README stub, LICENSE Apache-2.0, LICENSE-docs.md CC BY 4.0, .gitignore, .gitattributes, scripts/host_ids.py + .host-scrub.toml); host_ids self-test+check; gh-new-repo.sh public in patterncatalyst; Pages build_type=workflow; homepage = Pages URL; push release/r01.
2. Version survey (exec A) -> _plans/versions.md. Parallel 3-6.
3. Sources (exec B) -> bibliography.md, _plans/sources.md: Lawrence/Bellemare TOCs (O'Reilly MCP, informit), 21 Strimzi posts fetched+confirmed, Fowler x2, Microsoft ES pattern, Hohpe/Woolf. Unconfirmed = "owner to verify". No Amazon links.
4. Site scaffold (exec C): _config.yml, Gemfile(.lock in ruby container), _layouts, _includes (excalidraw path /diagrams/), css/js, index/chapters/diagrams pages, README, 8 _parts cards (Part 0-6 + Appendices).
5. Tooling + CI (exec D): validate.py (front matter, Liquid, diagram triples + no orphan specs, no em/en dashes, <=2,500 words, one callout per pattern chapter, no test names, versions only ch00/appendices, examples have verify.sh+README, pinned FQ images, Camel schemes, no podman outside _plans); voice-scan.sh, idiomatic-scan.sh vendored; diagram generator + build scripts; site-build.sh (ruby container, host-owned bundle); stack-guard.sh; setup-stack.sh; verify-all.sh; workflows pages.yml (site), content.yml (content-ok), examples.yml (examples-ok).
6. CLAUDE.md + _plans (exec E): iteration-plan r01-r15, domain-model mirror, claims.md, verification-log.md. Checkpoint after 2-6: validate + site build + commit.
7. Compose _infra (exec F): per-service compose files + aggregate; postgres start script (wal_level=logical); Debezium Connect = published quay.io/debezium/connect (pinned exact tag; record the Kafka version it bundles); ports Kafka 9092, PG 5432, Apicurio 8081, Connect 8083, Grafana 3000, OTLP 4317/4318, Prometheus 19090, Tempo 3200, Loki 3100; mem_limit, healthchecks, edp- prefix. Live up --wait / down -v.
8. examples/domain-model (exec G): records for the 15 entities, compact-constructor validation, JUnit with negative witnesses; package com.patterncatalyst.shipping. Checkpoint.
9. Ch 00 + examples/00-stack-check (exec H): Quarkus+Camel, platform-http -> Kafka -> consumer route; OTel to LGTM; verify.sh checks health, topic, wal_level, Apicurio, Connect plugins, trace/metric/log in LGTM, negative witness (invalid Consignment rejected). Figure 00-compose-stack.
10. Ch 01 + examples/01-crud-vs-events (exec I): Quarkus + Panache + Postgres + LGTM, no Camel/Kafka; row update vs append to consignment_event (unique stream_id,version; jsonb); replay == row; history only from events; illegal transition 409; append-only trigger; negative witnesses. Figure 01-row-update-vs-event-append. One callout, no versions.
11. Live verification (orchestrator, serial, clean docker): verify 00 then 01; log; claims verified; stop all.
12. Validation (Opus): validate, voice, idiomatic, host_ids, site build + curl pages, claims audit.
13. Ship: PR, CI green, ruleset, squash-merge on owner OK, tag r01.0, notes-only release, delete branch; hub entry if owner says yes.

## Acceptance criteria
(see planner report; per step 1-10 + iteration: 3 CI checks green, voice ban 0, idiomatic no high, host_ids clean, all ch 00/01 claims verified, verification-log has both runs, Pages serves ch 00/01, tag r01.0 + release.)

## Risks
CNPG operand image lacks docker-library entrypoint (UID 26; custom start script); Dev Services can't use CNPG image (pin library/postgres same minor); Kafka Dev Services image must be set to pinned apache/kafka; Connect on JDK 25 may fail (fallback ubi9/openjdk-25); Kafka split compose vs Strimzi; Prometheus 9090 vs Cockpit -> 19090; unconfirmed source chapters in prose; host identifiers in logs/Gemfile.lock; two stacks at once (stack-guard); Liquid {{; Pages deploy guard/ruby mismatch; images without curl for healthchecks.

## Owner decisions
1 DECIDED: quay.io/debezium/connect as published first (owner, 2026-11-10).
2 Ch 01 without Camel/Kafka (rec).
3 Dev Services tests on library/postgres same minor; compose/verify on CNPG image (rec).
4 Lawrence print ISBN, O'Reilly listing in sources.md (rec).
5 Hub listing + books.yml at r01 (default yes).
6 Main ruleset at r01 (rec yes).
7 Package com.patterncatalyst.shipping (rec).
