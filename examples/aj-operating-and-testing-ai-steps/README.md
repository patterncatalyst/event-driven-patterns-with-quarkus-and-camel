# Appendix J: Operating and testing AI steps

Chapter 24's model step works. This appendix is about running it in production: paying for the model exactly once
per exception even when Kafka redelivers or a producer duplicates a note, keeping the model out of continuous
integration while still proving the prompt has not drifted, and bringing older analyses forward to a new prompt
version without re-running the ones already there. `shipping-service` reuses chapter 24's AI service, gateway and
event shapes unchanged; this appendix adds the operating layer around them.

Quarkus, Quarkus LangChain4j with Ollama, Hibernate ORM with Panache and Flyway on PostgreSQL, Camel Kafka, Quarkus
REST, SmallRye Health and OpenTelemetry to the LGTM stack. There is no rules engine: the disposition this appendix's
backfill and replay demo recomputes comes from a plain Java function, not Drools. Topic names come from `Topics` in
`examples/domain-model`; no new topic is added.

## Service

| Service | Port | Purpose |
|---|---|---|
| `shipping-service` | 8080 | One Camel route, `MemoizedAnalysisService`, `BackfillResource` and `ReplayResource`. Health on `/q/health/ready` |

Topics, one partition each: `edp.shipping.delivery-exceptions` (the carrier's note, keyed by order id) and
`edp.shipping.exception-analyses` (the committed analysis). The analysis group is `shipping-exception-analysis`
with a 10 s session timeout.

PostgreSQL holds two tables in the `analysis` schema: `analysis` (append-only, primary key exception id, prompt
version, model digest) and `disposition_snapshot` (one row per exception id, rebuilt by the backfill endpoint).

| Setting | Default | Effect |
|---|---|---|
| `ANALYSIS_DEDUP` | `true` | Demo code. `false` removes the keyed lookup in `MemoizedAnalysisService`, the negative witness: a redelivered or duplicated note calls the model again |
| `DEMO_CRASH_AFTER_ANALYSIS` | unset | Demo code. The exception id named here throws once, after its row commits and before the record publishes, forcing one redelivery that must reuse the row |
| `REPLAY_MODE` | `recorded` | Demo code. `reanalyse` makes the replay endpoint read the original notes again and call the model, instead of reading the disposition snapshot |
| `ANALYST_REDELIVERY_DELAY_MS` | `5000` (compose: `1000`) | The pause between the three redeliveries of a failed or demo-crashed analysis |
| `ANALYST_ENABLED` | `true` | Demo code. `false` leaves the analysis route stopped (used by `MODEL=off`) |
| `MODEL` | `on` | Demo code for `verify.sh`. `off` starts no Ollama container and seeds `fixtures/analyses.jsonl` straight into Postgres instead of analysing |

`POST /admin/reanalyse?promptVersion=<v>` calls the model only for exceptions whose latest analysis is behind `<v>`,
appends a new row for each, and rebuilds every exception's disposition snapshot row, whether or not it needed a
model call. `POST /demo/replay` re-derives every disposition from the snapshot.

The model is `qwen3:4b-instruct-2507-q4_K_M` (Apache-2.0) on `ollama/ollama`. `scripts/ollama-model.sh pull` fills
the external volume `edp-ollama-models` once; compose mounts it read-only, `down -v` leaves it, and `verify.sh`
asserts the model digest and never pulls. `fixtures/corpus.jsonl` holds the notes used for the live run, with the
reason a reader gives each; `fixtures/analyses.jsonl` holds the same notes as committed analysis rows, for
`MODEL=off`.

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify                    # Kafka and PostgreSQL from Dev Services; the analyst is a CDI mock; no Ollama
../../scripts/ollama-model.sh pull   # once
./verify.sh                      # compose stack with the model
MODEL=off ./verify.sh
KEEP=1 ./verify.sh               # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
mvn -B verify -Plive             # exercises the real model through StabilityHarnessIT; needs Ollama already reachable
```

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it
starts, and stops this stack on exit unless `KEEP=1`.

## What mvn verify checks

`StabilityHarnessIT` is tagged `live` and excluded by default (the aggregator pom's failsafe configuration); it
only runs under `mvn -B verify -Plive`, against the packaged jar, with a real Ollama already reachable. Everything
else runs with Kafka and PostgreSQL from Dev Services, the OpenTelemetry SDK disabled, and `MockAnalyst` (a CDI
`@Mock`, not Mockito) standing in for the AI service:

1. A prompt contract test: the rendered prompt equals the golden file of its version, names exactly the enum
   values, and the schema sent to the model marks both fields required.
2. The exactly-once guard: a fresh note calls the model once and is not marked as reused; a duplicated delivery of
   the same note reuses the committed row and calls the model once in total; a crash configured for one exception id
   interrupts the route once, after the row commits, and the forced redelivery reuses the row instead of calling
   the model again. With `ANALYSIS_DEDUP=off`, a duplicated delivery calls the model twice.
3. Backfill: an exception seeded at an old prompt version is brought to the current one with exactly one model
   call, and its disposition snapshot is rebuilt from the new row; a second backfill at the same version calls the
   model for nothing.
4. Replay: with a snapshot already built, replay returns the same dispositions with zero model calls; with
   `REPLAY_MODE=reanalyse`, replay reads the original notes and calls the model again.

## What verify.sh does

1. Starts the stack and checks that the model volume holds the pinned model by digest and that one generate
   succeeds, that the service is ready and that the two topics have one partition each.
2. Publishes six notes and an off-topic one from `fixtures/corpus.jsonl` and checks each analysis: a reason from
   the enum, the model digest, the prompt version, and the reason a reader gives the note.
3. Publishes one note a second time and checks that the second analysis record is marked as derived from the first,
   with no extra model call; checks that the exception id named in `DEMO_CRASH_AFTER_ANALYSIS` is also marked as
   derived, after its forced redelivery.
4. Seeds one exception at an old prompt version directly in Postgres, calls `POST /admin/reanalyse` at the current
   version, and checks that exactly one model call was made and the reanalysed count is 1; calls it again and checks
   the reanalysed count is 0 and no further model call was made.
5. Calls `POST /demo/replay` and checks it runs in recorded mode, makes no model call, and that one exception's
   disposition matches the plain Java function applied to its recorded reason and attempt.
6. Prometheus holds `exception_analysis_lookups_total`, Tempo has a trace with a LangChain4j span, and Loki has
   logs.

Event times are one hour before the run; no window or clock is involved.

## Layout

| Path | Purpose |
|---|---|
| `shipping-service/.../DeliveryExceptionAnalyst`, `AnalystGateway`, `ExceptionAnalysis`, `Reason` | The AI service and its answer, reused unchanged from chapter 24 |
| `shipping-service/.../AnalysisRecord`, `MemoizedAnalysisService` | The exactly-once guard: a committed row is the idempotency key |
| `shipping-service/.../DemoCrash`, `ExceptionAnalysisRoute` | The one route, with the demo crash hook after the commit and before the publish |
| `shipping-service/.../DispositionSnapshot`, `Disposition`, `DispositionFunction` | Plumbing for the backfill and replay demo: a pure function, not a rules engine |
| `shipping-service/.../BackfillService`, `BackfillResource` | Re-analysis targeted at exceptions behind a prompt version, snapshot rebuild for all |
| `shipping-service/.../ReplayResource`, `RecordedTopic` | Replay from the snapshot, with the reanalyse negative witness |
| `shipping-service/.../db/migration/V1__analysis.sql` | The two tables; Flyway owns the schema, Hibernate validates only |
| `fixtures/corpus.jsonl`, `fixtures/analyses.jsonl` | The notes used for the live run, and the same notes as committed rows for `MODEL=off` |
| `compose.yaml` | Topics, PostgreSQL and the service; Ollama comes from `examples/_infra/compose-ollama.yaml` |

## Tests in detail

The Maven build runs with no Ollama (`quarkus.langchain4j.ollama.devservices.enabled=false`, asserted by a test)
and with the AI service replaced by `MockAnalyst`, a CDI `@Mock` bean that counts calls directly instead of relying
on Mockito.

- `PromptContractTest`: reused unchanged from chapter 24.
- `DedupIdempotencyTest`: a fresh analysis calls the model once and is not marked reused; a duplicate delivery of
  the same note reuses the committed row (one model call in total, the second record marked derived); a crash
  configured for one exception id forces a redelivery that reuses the row (one model call in total).
- `DedupDisabledTest`: with `ANALYSIS_DEDUP=off`, a duplicate delivery calls the model twice (the negative witness).
- `BackfillTest`: an exception seeded at an old prompt version is reanalysed once and its snapshot rebuilt; a
  same-version backfill reanalyses nothing and calls the model for nothing.
- `ReplayFromSnapshotTest`: replay in its default mode reads the snapshot and makes no model call.
- `ReplayReanalyseTest`: with `REPLAY_MODE=reanalyse`, replay calls the model again (the negative witness).
- `StabilityHarnessIT` (tagged `live`, excluded from `mvn -B verify`): publishes one corpus note to the packaged
  jar's real Kafka and checks the real model gives the expected reason.

## Experiments

| Switch | Effect | Measured result |
|---|---|---|
| `ANALYSIS_DEDUP=off ./verify.sh` (demo code) | The keyed lookup is removed | A duplicated note calls the model a second time instead of reusing the committed row |
| `REPLAY_MODE=reanalyse ./verify.sh` (demo code) | The replay reads the notes again | Every exception on the snapshot calls the model once more, instead of zero calls |
| `MODEL=off ./verify.sh` (demo code) | No Ollama; backfill and replay run on the seeded fixture | The duplicate-delivery and crash-redelivery checks are skipped; the backfill and replay checks run unchanged, because both read Postgres, not Kafka |
