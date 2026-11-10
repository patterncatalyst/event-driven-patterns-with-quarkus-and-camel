---
title: "Stack check"
order: 0
permalink: /examples/00-stack-check/
description: "A Quarkus and Camel service that publishes an Order to Kafka and sends traces, metrics and logs to LGTM, with a script that checks every service in the stack."
---

Source: [`examples/00-stack-check`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/00-stack-check)

## What it contains

| Piece | Purpose |
|-------|---------|
| `OrderIntakeRoute` | `POST /orders` validates an `Order`, answers 400 for an invalid one and publishes a valid one to the `edp.orders.placed` topic, keyed by id |
| `OrderLogRoute` | consumes `edp.orders.placed` and logs each record |
| `compose.yaml` | includes Kafka, PostgreSQL, Apicurio Registry, Kafka Connect and LGTM from `examples/_infra` and adds the service |
| `verify.sh` | starts the stack and checks every service and signal |

## Run

```bash
cd examples/00-stack-check
./verify.sh
```

`KEEP=1 ./verify.sh` leaves the stack running so you can open Grafana at [http://localhost:3000](http://localhost:3000).

## Run the tests alone

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/00-stack-check
mvn -B verify
```

Quarkus Dev Services starts a Kafka container for the tests. No compose stack is needed.

The setup and the stack are described in [Prerequisites and stacks]({{ '/docs/00-prerequisites-and-stacks/' | relative_url }}).
