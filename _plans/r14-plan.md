# r14 plan: Retro (Opus planner, 2026-11-16)

Status: **approved by the owner 2026-11-16 ("keep going with r14").** Branch `release/r14` from main at 02bfec9 (r13b.0). Gates green at the start: validate ok, voice and idiom 0 ban-tier, host_ids 0 hits in 1,725 files.

## Approach

Four phases in order: review waves, freeze, full live re-run, validate and ship.

- Waves run sequentially so file ownership stays disjoint: idiom first (an idiom fix that changes code also edits that code's fenced quote in its own `_docs`/`_example_pages`), then voice and QA, then one cross-book consistency pass.
- Freeze: after the last fix the orchestrator commits and records the freeze SHA `F`; every example then runs live once from fresh state. Fix now: a failure is fixed and that example re-run, plus every example that consumes the changed file.
- **Re-survey dropped (owner, 2026-11-16):** pins stay frozen. versions.md "Revisit at the r14 re-survey" notes get "Re-survey dropped (owner, 2026-11-16); hold stands".
- **Appendix H not re-run:** its code ran 7 times on 2026-11-16 and CRC is deleted. The decision flips if `git diff 02bfec9..F` touches `examples/ah-*`, `examples/08-transactional-outbox/order-service/**` (H builds that Containerfile) or the `--crc` branch of `scripts/stack-guard.sh`; executors change 08 order-service only for a substantive defect and say so. H always gets the static gates and offline `oc kustomize`.

Rejected: one agent per review type over the whole book (too large for one context); parallel voice, idiom and QA waves (collide on code quotes); a version re-survey (owner dropped it); H re-run by default (unchanged code, CRC deleted); verify-all.sh as it is (only `[0-9][0-9]-*`, no times, no resume); a CI-only full matrix (local `mvn -B verify` covers it); a history rewrite (only on a hit, with the owner's OK).

## Steps

| Step | Work | Files | Depends | Executor | Status |
|---|---|---|---|---|---|
| 0 | Preflight: save plan; iteration-plan r14 in progress, re-survey dropped; versions.md hold notes; no stack, profile or CRC; `edp-ollama-models` present | `_plans/r14-plan.md`, `iteration-plan.md`, `versions.md` | owner OK | orchestrator | done (no stack, profile or CRC; volume present) |
| 1 | Extend `scripts/verify-all.sh`: groups `compose` (00 to 27), `k8s` (28 to 35), `k8s-appendix` (ab k8s, af), `appendix` (aa, ab compose, ac, ae, ag, then ab compare); `--from <dir>`; per-example log through `host_ids.py filter`; TSV row (dir, N/N, exit, wall s, teardown); `--mvn` mode (domain-model install, then every pom); stop on first failure | `scripts/verify-all.sh` | 0 | orchestrator | done (groups listed: compose 28, k8s 8, k8s-appendix 2, appendix 6; 33 poms) |
| 2 | Idiom wave (lgtm-idiomatic-review), 6 parallel batches: I1 00 to 07; I2 08 to 13; I3 14 to 18; I4 19 to 24; I5 25 to 27 plus aa, ac, ae, ag code; I6 Part 6 overlays 28 to 35 plus ab, af, ah manifests and scripts. Each owns `examples/<dirs>/**` except README, plus the fenced code quotes in its own pages. Substantive fixes only; no behaviour, status code, field name, asserted log line or check-number change; comparison and demo code kept; `mvn -B verify` under the lock per changed module; `kubectl kustomize` per changed overlay | per batch | 0 | 6 x Sonnet | done (I1 to I6; code changed in 00 to 07, 09 to 17, 19, 20, 22, 26, 27, ac, ae, ag; I6 and ah clean; mvn green per changed module) |
| 3 | Shared substrate review (`_infra`, `_k8s`, `domain-model`, `scripts/`): report only; orchestrator applies accepted fixes; a domain-model change only for a defect, then every pom `mvn verify` | report | 2 | Sonnet; orchestrator | done (applied: prom_has capture, curl --max-time, _wait_apps poll, NS_KAFKA, cluster.sh temp cleanup, stack-guard docker-unreachable exit 2 outside --crc, verify-all leftovers, validate relative paths, money() scale-then-check with test, compose ports on 127.0.0.1; kept: startupProbe, prom.py/tempo.py excepts, ruby digest) |
| 4 | Voice and QA wave, 7 parallel batches: V1 00 to 07; V2 08 to 13; V3 14 to 18; V4 19 to 24; V5 25 to 27 and D; V6 28 to 35; V7 A, B, C, E, F, G, H. Each owns its `_docs`, `_example_pages`, `examples/*/README.md`. Voice: scan plus reading against the skill, rewrite by deletion, no taste rewording, identifiers and recorded output unchanged. QA: every prose claim has a verified claims row or is cut or escalated; README check numbers match verify.sh; Build, run, observe short; no cross-chapter or self-reference; one callout in house form; measured values agree with verification-log. Hands back a claims delta, never edits `claims.md` | per batch | 2, 3 | 7 x Sonnet | done (V1 to V7; demo-code labels V7 removed were restored by the orchestrator) |
| 5 | Claims merge (new rows, refreshed `file:line`, cuts) | `_plans/claims.md` | 4 | orchestrator | done (about 20 added, 125 refreshed, 10 cut (r14); 0 pending or UNBACKED) |
| 6 | Cross-book consistency pass: terminology (D's glossary canon); front matter (orders 0 to 43 contiguous, title, part, description, duration; marker and label on appendices); uniform `## References`; bibliography both ways (cited works present, entries cited); internal links in `_site` resolve; external URLs answer 200 or 3xx (generic User-Agent; dead ones replaced, claims updated); word limits and Build, run, observe soft caps; at least one figure per page, every SVG referenced, every spec has outputs; example pages match examples; README and index lists cover every page | all pages, READMEs, `bibliography.md`, `README.md`, `_parts`, `chapters.html` | 5 | 1 x Sonnet | done (0 broken internal links in 1,649; 462 external URLs, 2 dead fixed or removed, 7 bot-blocked 403; D event catalog fixed; 9 uncited bibliography entries kept as background reading) |
| 7 | Host scrub (lgtm-host-scrub step 1, read-only): `host_ids.py check`, `history`, `local`; PR count; grep tree and `git log -p --all` for token shapes; counts only; a history hit stops for the owner | none | 6 | orchestrator | done (check 0, history clean over all reachable blobs and messages, local: no unpushed branch, no stash; token shapes 0 in tree and history; 15 PRs) |
| 8 | Static gates and freeze: validate and `--self-test`, voice, idiom, host_ids, `build-diagrams.sh` byte-identical unless a spec changed, `site-build.sh` (ruby:4.0 container); commit; record `F` | | 7 | orchestrator | done (F = 3c1f626; gates green; site build 100 pages) |
| 9 | Full live re-run, windows W1 to W6 | `verification-log.md` | 8 | orchestrator (or delegated under `flock -w 7200 <scratchpad>/stack.lock`) | pending |
| 10 | Range reconciliation: a measured value outside a stated range widens the range in prose and README with a claims row; no re-run | docs, READMEs, claims | 9 | orchestrator | pending |
| 11 | Opus validation, at most 2 repair rounds; a code repair means a live re-run of that example and its dependents | | 10 | Opus | pending |
| 12 | Ship r14.0: PR, CI green, squash, tag, notes-only release, branch deleted; iteration-plan shipped; CLAUDE.md decision entry; ping owner | `iteration-plan.md`, CLAUDE.md | 11 | orchestrator | pending |

## Re-run windows (step 9)

| W | Content | Target |
|---|---|---|
| W1 | `--mvn`: domain-model install, then every pom on Dev Services | 1 h 10 min |
| W2 | compose 00 to 13 (`ollama-model.sh check` first) | 2 h |
| W3 | compose 14 to 27 | 2 h |
| W4 | minikube: `cluster.sh down`, new profile, 28 to 35, profile deleted | 1 h 15 min |
| W5 | fresh profile: ab k8s, af; profile deleted | 45 min |
| W6 | compose appendices aa, ab compose, ac, ae, ag, then ab compare (no stack) | 1 h |

Failure handling: fix now, re-run with `--from`. A fix to `_infra`, `_k8s/lib.sh` or `stack-guard.sh` re-runs every passed example that uses it; a fix to an image Part 6, F or H deploys re-runs those consumers. Checks with a stated failure rate (23 check 29, about 31 percent): one failure is logged against the rate and the example re-run once. Demo switches and bites are not re-run (failing sets frozen in earlier logs) unless a fix touched that guard.

Recorded in `verification-log.md` "r14 full re-run": one row per example (43 rows, H "not re-run: decision"): N/N, mvn tests, wall, teardown, log name, window wall clock; plus `F`, profile recreations, `edp-ollama-models` intact.

## Acceptance criteria

- Every review batch handed back a findings table; ban-tier 0 for both scanners; every kept watch-tier hit has a reason.
- `git diff 02bfec9..HEAD` shows no behaviour change (validator reads every code hunk); check numbering unchanged or the README reflects it; no pin changed.
- Every example changed after 02bfec9 has a passing live run at or after `F`; every non-H example passes N/N with N equal to its README count; every pom passes `mvn verify`.
- verification-log has 43 rows with N/N, wall and teardown; every k8s teardown at most 120 s; no container, volume, profile or CRC left; `edp-ollama-models` present.
- claims.md has no pending or UNBACKED row; `file:line` refs valid at HEAD (10 percent spot check per part).
- validate (and self-test), voice, idiom, host_ids, site build, diagrams byte-identical, 0 broken internal links, 0 dead external links.
- `host_ids history` and `local` 0 hits or owner decided; no token pattern in tree or history.
- CI green (content, examples, pages); r14.0 tagged and released (notes only); iteration-plan, CLAUDE.md and versions.md notes updated.

## Risks

| Risk | Detection | Response |
|---|---|---|
| Idiom fix changes behaviour | mvn tests, live run, validator diff read | revert and escalate; never weaken a check |
| Code quote in prose drifts from code | consistency pass greps fenced snippets against sources | fix the quote |
| Measured value outside a stated range | step 10 | widen range with claims row |
| Timing-sensitive checks (17 bench, 20, 21, 23 check 29) | run TSV | re-run once; defect only if it repeats |
| Dead external link | step 6 | replace source; update claims and bibliography |
| Fix touches 08 order-service | diff from 02bfec9 | H re-run (CRC window), owner told |
| Host identifier or token in history | step 7 | stop; owner decides |
| Window overrun or CNPG namespace hang | wall vs target; `_why_terminating` | split window; never strip finalizers |
| Slow first image pulls | W2 times | accept; record cold time once |
| Substrate change mid-run | step 9 rule | dependent re-runs |

## Live-window estimate

About 8 h 10 min of live time (W1 1 h 10, W2 2 h, W3 2 h, W4 1 h 15, W5 45 min, W6 1 h) plus about 30 min of fix re-runs; each window at most 2 h under the lock; H adds about 40 min only if its trigger fires.
