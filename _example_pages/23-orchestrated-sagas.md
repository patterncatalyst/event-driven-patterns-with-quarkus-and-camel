---
title: "Orchestrated sagas: Camel Saga and Quarkus Flow"
order: 23
permalink: /examples/23-orchestrated-sagas/
description: "One order-service that runs the same order saga under a Camel Saga with an LRA coordinator and under a Quarkus Flow workflow, over the same inventory and payment participants, with a payment service that stops answering."
---

[`examples/23-orchestrated-sagas/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/23-orchestrated-sagas) runs one order flow, order then stock then payment, under two orchestrators in `order-service`. `inventory-service` and `payment-service` take commands over Kafka and join the Camel saga when the command carries its header. It accompanies [Orchestrated sagas: Camel Saga and Quarkus Flow]({{ '/docs/23-orchestrated-sagas/' | relative_url }}).

It uses Quarkus with Quarkus REST, Camel Kafka, the Camel Saga EIP with the LRA binding, Quarkus Flow, Flyway, the Debezium outbox and Connect, SmallRye Health, PostgreSQL, Kafka, a Narayana LRA coordinator and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/23-orchestrated-sagas
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="23-three-ways" alt="Three columns, choreography, Camel Saga with an LRA coordinator and Quarkus Flow, each with where the sequence lives, where the state lives, what triggers the compensation and what owns the time limit" caption="Three places to look when an order is stuck" %}

## What to observe

1. **Same inputs, same terminal states.** Both orchestrators end PAID for a payment that is accepted and CANCELLED, with the stock released and one OrderCancelled, for one that is declined.
2. **Two kinds of saga state.** The Camel saga is an entry at the coordinator and a header on each record. The workflow is a row in PostgreSQL that disappears when the instance completes.
3. **A payment service that does not answer.** The coordinator cancels the Camel saga and the late payment cannot join, so none is taken. The workflow times out and the late payment is taken and then refunded.
4. **A recreate with sagas in flight.** The Camel saga completes through its header. The waiting workflow instance does not resume.

## Infrastructure

Kafka, PostgreSQL, Connect, the LGTM stack and the LRA coordinator from `examples/_infra`, plus three services. The compose project is `edp-23`.
