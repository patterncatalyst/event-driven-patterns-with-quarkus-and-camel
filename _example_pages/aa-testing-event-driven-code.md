---
title: "Appendix A: Testing event-driven code"
order: 36
permalink: /examples/aa-testing-event-driven-code/
description: "One notification route tested against a real Kafka from Dev Services, with its HTTP source stubbed by the test application, an AdviceWith route test, first-match and drain assertions, and a harness that proves each guard by running altered copies."
---

[`examples/aa-testing-event-driven-code/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/aa-testing-event-driven-code) holds one Camel route that reads `OrderPlaced`, fetches the order over HTTP, drops a repeated event id, skips a cancelled order and publishes a notification keyed by the order id. It accompanies [Appendix A: Testing event-driven code]({{ '/docs/aa-testing-event-driven-code/' | relative_url }}).

It uses Quarkus, Camel Quarkus (Kafka, HTTP, Jackson), `camel-quarkus-junit` with `AdviceWith`, KafkaCompanion and Kafka Dev Services. No compose stack runs.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/aa-testing-event-driven-code
./verify.sh
```

{% include excalidraw.html file="aa-bite-cycle" alt="A green suite, a patch that alters one guard on a copy, a run of the broker-backed class, and exactly one named test failing, after which the copy is dropped" caption="A check is proved by breaking the code it guards" %}

## What to observe

1. **A route test without a broker.** The advised route takes an exchange on `direct:in` and ends on a mock endpoint.
2. **A drain that sees a second record.** Each altered copy fails only the test that names its guard.
3. **A first match that stays green.** With the dedup removed, the first-match test still passes.
