---
title: "Appendix J: Operating and testing an AI step"
order: 45
marker: "J"
label: "Appendix J"
part: appendices
permalink: /docs/aj-operating-and-testing-ai-steps/
description: "A model call is expensive and its answer is not guaranteed the same twice. Memoize each answer in a committed row keyed by the note so a redelivery reuses it, keep CI deterministic with a mocked model on Dev Services while a live harness exercises the real one, and re-derive recorded analyses when the model pin or prompt version changes without replaying the corpus through the model."
duration: "45 minutes"
---

A model call costs money and time, and asking it twice does not guarantee the same answer twice. A crash between the call and the commit means the next delivery pays for the model again. An edited prompt silently invalidates every answer recorded under the old one, and nobody notices until an old row and a new one stop matching. A build that runs on every commit cannot depend on a model that answers differently from one run to the next, so the call needs an operating layer around it: a guard that makes the call happen at most once per note, a test suite that runs without the model at all, and a way to carry recorded answers forward across a model or prompt change without paying for the corpus again.

{% include excalidraw.html file="aj-memoized-model-call" alt="A note arrives on delivery-exceptions; on first arrival the guard finds no row and calls the model, committing the analysis; on redelivery after a crash the guard finds the row and the model is never called; ANALYSIS_DEDUP=off removes the guard" caption="Figure J.1: One answer per note, committed once and reused on redelivery" %}

## Learning objectives

- Reuse a committed analysis on redelivery instead of calling the model again
- Keep CI deterministic with a mocked analyst on Dev Services
- Exercise the real model only in a tagged stability harness
- Re-derive analyses and decisions from the record after a prompt or pin change
- Replay dispositions from a snapshot with zero model calls

## Why this matters

When a crash lands between a model's answer and the record that holds it, a careless retry pays for the call again and risks a different answer the second time. A build that waits on a live model fails for reasons that have nothing to do with the code. Commit the answer before anything acts on it, keep the default build free of the model, and re-derive from the record instead of replaying through it, so a cost paid once stays paid.

## A committed answer, reused on redelivery

The model call sits behind a guard, not in front of one. `MemoizedAnalysisService` looks up the analysis table by a key made of the note's id, the prompt version and the model's digest before it calls anything, and only calls the model on a miss:

```java
@Transactional
public ExceptionAnalysis analyse(DeliveryExceptionReported note) {
    AnalysisKey key = new AnalysisKey(note.noteId(), PROMPT_VERSION, modelDigest);
    if (dedupEnabled()) {
        ExceptionAnalysis stored = AnalysisRecord.findByKey(key);
        if (stored != null) {
            return stored;
        }
    }
    ExceptionAnalysis fresh = analyst.analyse(note.text());
    AnalysisRecord.commit(key, fresh);
    return fresh;
}
```

The commit happens in the same transaction as the lookup, before the result reaches a caller or a topic. A fault injected right after that commit, `DEMO_CRASH_AFTER_ANALYSIS` set for one note, crashes the consumer before the record is acknowledged, so Kafka redelivers the note. The second pass runs the same method, the lookup finds the row this time, and the model is not called. The model-call counter is the proof: across a publish and a forced redelivery it equals the number of distinct notes, not the number of deliveries.

`ANALYSIS_DEDUP=off` is the negative witness. It skips the lookup, so the guard that makes a redelivery free disappears and the second pass calls the model again for a note it already answered. Without that switch to turn off, nothing in the test suite would tell apart a service that memoizes from one that merely looks like it does.

## Deterministic CI, a live harness

`mvn verify` never reaches Ollama. The test profile wires a mocked `DeliveryExceptionAnalyst` that answers from a fixed table instead of a model, so the guard, the crash-and-redeliver path and the admin endpoints are exercised against an analyst that is not timing- or hardware-dependent:

```java
@Alternative
@Priority(1)
@ApplicationScoped
public class MockDeliveryExceptionAnalyst implements DeliveryExceptionAnalyst {
    @Override
    public ExceptionAnalysis analyse(String note) {
        return FIXTURES.getOrDefault(note, UNKNOWN_ANALYSIS);
    }
}
```

A golden prompt-contract test checks the system prompt text and the response schema against a committed fixture, so an edit to either fails the test until `PROMPT_VERSION` is bumped to match. The contract is between the prompt and its version, not between the model and an expected answer, so the test needs no model to run.

{% include excalidraw.html file="aj-ci-vs-live" alt="CI sends a note through the mocked analyst with no Ollama and gets a deterministic answer; the live run and the stability harness send a note through the real model over an endpoint" caption="Figure J.3: One route, two analysts: the mock holds CI still, Ollama answers live" %}

A separate class, tagged `@Tag("live")` and excluded from the default `mvn verify` profile, sends the same notes through the real model several times and asserts the reason stays the same across runs. It belongs outside the default build because a live model's latency and availability are not something a commit should wait on, and a run that calls Ollama is run by hand or on a schedule, never as a merge gate.

## Re-analysing on a pin or prompt change

The analysis table is append-only, and a row is never edited in place. Bumping `PROMPT_VERSION` or pulling a new model digest does not change an existing row; it makes every row recorded under the old version stale and leaves the new rows to be written as the notes are re-analysed. `POST /admin/reanalyse` finds the stale rows by key and calls the model only for those:

```java
@POST
@Path("/admin/reanalyse")
public Response reanalyse(@QueryParam("promptVersion") String target) {
    List<AnalysisRecord> stale = AnalysisRecord.find(
            "noteId in (select noteId from AnalysisRecord) and (promptVersion != ?1 or modelDigest != ?2)",
            target, modelDigest).list();
    stale.forEach(row -> analyst.reanalyse(row.noteId(), target));
    return Response.accepted().build();
}
```

A same-version call finds nothing stale and makes zero model calls; a bumped version calls the model once per note still on the old key, never for the whole corpus.

{% include excalidraw.html file="aj-backfill-and-snapshot" alt="The append-only analysis store holds current and stale rows by version; a pin or prompt bump re-analyses only the stale rows and appends a new one; a DispositionSnapshot lets replay re-derive decisions with no model call; REPLAY_MODE=reanalyse bypasses the snapshot" caption="Figure J.2: Backfill touches what changed; replay never calls the model" %}

Decisions carry the same question forward. `DispositionSnapshot` records the verdict and the rule name that produced it alongside the analysis it was decided from, so `POST /demo/replay` rebuilds every decision by reading the snapshot and re-running the rules, not by asking the model again:

```java
public record DispositionSnapshot(String exceptionId, String reason, String verdict, String ruleName) {}
```

`REPLAY_MODE=reanalyse` is the negative witness here: it has replay skip the snapshot and go back to the notes, so the model-call count rises by one per note and a model that now answers differently would change the replayed decisions too. Reading the snapshot the normal way never moves that counter.

## Build, run, observe

```bash
cd examples/aj-operating-and-testing-ai-steps
mvn -B verify
./verify.sh
```

`mvn -B verify` runs the guard, the prompt-contract test and the admin endpoints against the mocked analyst with no Ollama; `verify.sh` runs the live model, publishes the fixture corpus with a crash injected on one note, and checks the recorded analyses, the reanalysed and replayed decisions, and both negative witnesses. It prints `NN/NN checks passed`.

Try one change and rerun:

- `ANALYSIS_DEDUP=off ./verify.sh`: a redelivered note calls the model a second time.
- `REPLAY_MODE=reanalyse ./verify.sh`: replay calls the model instead of reading the snapshot.
- `DEMO_CRASH_AFTER_ANALYSIS=<id> ./verify.sh`: that note's consumer crashes right after its analysis commits, before the result is used.

## When not to use it

A call that is cheap and answers the same way every time needs none of this: memoizing a deterministic lookup only adds a table to maintain for no saved cost. A hosted model whose terms forbid storing its output leaves nothing to commit, snapshot or replay, so that work moves outside this pattern, paid fresh on every request. And a domain that must re-derive every decision in full on demand, never trusting a stored snapshot as the record, pays to ask the model again each time it checks a past result instead of reading a row.

## Common pitfalls

- Publishing an analysis before its row commits lets a redelivery still pay for the model, because the record it should reuse was never written first.
- Editing the prompt without bumping its version records a new answer under the old key, so later rows stop matching what actually produced them.
- Backfilling the whole corpus instead of the stale rows alone re-pays for every note a pin or prompt change never touched.
- A stability test that asserts on a note whose true answer is itself unstable fails on a model behaving exactly as expected.

> **🤖 Explore with your agent**
> "In examples/aj-operating-and-testing-ai-steps, publish the notes in fixtures/corpus.jsonl to edp.shipping.delivery-exceptions with DEMO_CRASH_AFTER_ANALYSIS set for one note, read edp.shipping.exception-analyses, then POST /admin/reanalyse with a bumped prompt version and POST /demo/replay. Rerun with ANALYSIS_DEDUP=off and REPLAY_MODE=reanalyse and state what changes."
> Check: with the guard on, MemoizedAnalysisService records each answer in the analysis table once and the model-call count equals the distinct notes across the redelivery; a same-version reanalyse makes zero model calls and a bumped version calls the model only for stale rows; replay re-derives dispositions from DispositionSnapshot with zero model calls, and both off-switches make the count rise.

## References

- [Decisions on events]({{ '/docs/24-decisions-on-events/' | relative_url }}): the analyse-then-decide split operated and tested here, kept unchanged
- [Idempotent consumer and inbox]({{ '/docs/11-idempotent-consumer-and-inbox/' | relative_url }}): the recorded-key guard that makes a redelivery safe is the same shape as the analysis table's key
- [Exactly-once with Kafka transactions]({{ '/docs/12-exactly-once-with-kafka-transactions/' | relative_url }}): a stronger way to pair a commit with a publish, where the guard here instead relies on a redelivery finding the row
- Quarkus LangChain4j, [AI services](https://docs.quarkiverse.io/quarkus-langchain4j/dev/ai-services.html): the `@RegisterAiService` interface a mocked `@Alternative` bean replaces for CI
- Ollama, [API](https://github.com/ollama/ollama/blob/main/docs/api.md): the endpoint the live and stability runs call and CI never does
- Gregor Hohpe and Bobby Woolf, [Idempotent Receiver](https://www.enterpriseintegrationpatterns.com/patterns/messaging/IdempotentReceiver.html), Enterprise Integration Patterns: a receiver safe to redeliver to, the property the analysis guard gives the model call
- Gregor Hohpe and Bobby Woolf, [Guaranteed Delivery](https://www.enterpriseintegrationpatterns.com/patterns/messaging/GuaranteedMessaging.html), Enterprise Integration Patterns: the redelivery this appendix's guard must survive without a second model call
