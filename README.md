# Event-Driven Patterns with Quarkus and Camel

A chaptered tutorial on event-driven patterns built with Quarkus, Apache Camel and Kafka. The running domain is shipping: consignments, handling events, tracking histories, tariffs, invoices and settlements. Every pattern chapter states the problem, shows the code that solves it, and ends with a runnable example that has its own tests and a `verify.sh`.

Site: https://patterncatalyst.github.io/event-driven-patterns-with-quarkus-and-camel/

## What it covers

| Part | Topic |
|------|-------|
| 0 | Getting started: the shipping domain, the local stack, a first event end to end |
| 1 | Events between services: event notification, event-carried state transfer, channel and message design |
| 2 | Getting events out reliably: transactional outbox, Debezium, idempotent consumers, delivery guarantees |
| 3 | Event sourcing: append-only stores, projections, CQRS, snapshots, schema evolution |
| 4 | Processing and coordinating streams: stateful processing, windows, joins, sagas |
| 5 | Observing event flows: OpenTelemetry traces, metrics and logs in Grafana LGTM |
| 6 | Running on Kubernetes with Strimzi |
| Appendices | Reference material, tooling and optional extras |

## Run targets

- **Docker Compose** for Parts 0 to 5. One shared infrastructure stack (Kafka, PostgreSQL, Apicurio Registry, Debezium Connect, Grafana LGTM) started with `scripts/setup-stack.sh up`. Requirements: Docker Engine with the `docker compose` plugin, JDK 25 and Maven. The examples need no Python; Python 3 is only for the repository tooling (`scripts/validate.py`, `scripts/host_ids.py`, `scripts/generate_diagram.py`). Run one stack at a time.
- **minikube with Strimzi** for Part 6. The same services deployed to a local Kubernetes cluster, with Kafka managed by the Strimzi operator.
- **OpenShift Local** appendix. The outbox example deployed to OpenShift Local as a demonstration of the same vendor-neutral code.

## Repository layout

```
_docs/                 chapters (Jekyll collection)
_parts/                part cards for the homepage and chapter index
_example_pages/        example pages on the site
_plans/                iteration plans (not published)
_layouts/ _includes/   site templates
assets/                site CSS
diagrams/              figures as .svg and .excalidraw
diagrams/specs/        one Python spec per figure
examples/              one runnable project per pattern chapter
examples/_infra/       shared compose stack
lgtm-presentation/     the slide deck
scripts/               stack, site, diagram and validation scripts
bibliography.md        references
```

## Run an example

```bash
cd examples/00-stack-check
./verify.sh
```

Each `verify.sh` brings its own stack up and tears it down. Running `mvn -B verify` inside an example needs the shared domain model installed first:

```bash
(cd examples/domain-model && mvn -B -q install)
```

## Build the site

The host needs no Ruby. The site builds in a container:

```bash
scripts/site-build.sh            # builds into _site/
scripts/site-build.sh --serve    # serves on http://127.0.0.1:4000/
```

## Licences

Code is licensed under Apache-2.0 ([LICENSE](LICENSE)). Prose and figures are licensed under CC BY 4.0 ([LICENSE-docs.md](LICENSE-docs.md)).
