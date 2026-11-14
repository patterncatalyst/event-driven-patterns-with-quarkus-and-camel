---
title: "Decisions on events"
order: 24
permalink: /examples/24-decisions-on-events/
description: "One shipping-service that reads a carrier's free-text delivery-exception note, has a local model return a reason from a closed set, decides RESHIP or REFUND with a Drools rule unit, records both results, and replays the decisions from the record with no model call."
---

[`examples/24-decisions-on-events/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/24-decisions-on-events) is one service, `shipping-service`. Two Camel routes carry the events: one calls an AI service on every note and records the analysis, one runs a rule unit on every analysis and records the decision. It accompanies [Decisions on events]({{ '/docs/24-decisions-on-events/' | relative_url }}).

It uses Quarkus, Quarkus LangChain4j with Ollama, the Drools rule unit engine, Camel Kafka, Quarkus REST, SmallRye Health, Kafka and OpenTelemetry to the LGTM stack. The model is a local Ollama container with a model volume filled once by `scripts/ollama-model.sh pull`.

```bash
(cd examples/domain-model && mvn -B -q install)
scripts/ollama-model.sh pull
cd examples/24-decisions-on-events
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="24-rules-table" alt="A grid of the seven reasons against attempts 1 to 3, each cell RESHIP or REFUND with the rule that makes it" caption="The decision table the rule unit implements, one rule per cell" %}

## What to observe

1. **A closed reason.** Each analysis on `edp.shipping.exception-analyses` holds a reason from the enum, the model digest and the prompt version.
2. **A decision with its rule.** Every decision on `edp.shipping.exception-decisions` names the rule that made it, and equals the rule table applied to the recorded reason and attempt.
3. **The same damage, a different attempt.** The first attempt is reshipped and the second is refunded, for the same order.
4. **A replay with no model.** `POST /demo/replay` returns the recorded decisions and the model-call count does not move.
5. **A waiting note.** With Ollama stopped the note has no analysis and the consumer group has lag 1, until the model returns.

## Infrastructure

Kafka, the LGTM stack and Ollama from `examples/_infra`, plus `shipping-service`. The compose project is `edp-24`.
