---
title: "Prerequisites and stacks"
order: 0
part: getting-started
permalink: /docs/00-prerequisites-and-stacks/
description: "The tools, versions and local stack every example runs on, and a first Consignment sent through Quarkus, Camel, Kafka and the LGTM observability stack."
duration: "30 minutes"
---

Every example in the book runs on one local stack: a Kafka broker, a PostgreSQL database, a schema registry, Kafka Connect with Debezium, and a Grafana LGTM container that receives traces, metrics and logs. An example is a small Quarkus and Camel service plus a script that starts the stack, exercises the service and checks what happened.

{% include excalidraw.html file="00-compose-stack" alt="The compose stack: curl posts to the stack-check service, which publishes to and consumes from Kafka; Connect links Kafka and PostgreSQL; the service sends telemetry to LGTM" caption="Figure 0.1: The stack every example runs on" %}

## What you need

A Fedora or RHEL host with Docker Engine and the `docker compose` plugin, JDK 25, Maven, git and curl. Parts 1 to 5 never need more than that. Install Docker Engine from [Docker's repository for your distribution](https://docs.docker.com/engine/install/) and add your user to the `docker` group so no command needs `sudo`.

Install JDK and Maven with [SDKMAN](https://sdkman.io/), which installs under `$HOME/.sdkman` by default:

```bash
for t in git curl zip unzip docker; do command -v "$t" >/dev/null || echo "missing: $t"; done
docker compose version

curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 25.0.5-tem
sdk install maven 3.9.16

java -version && mvn -version
```

If the first line prints `missing:`, install that tool with your package manager before continuing. SDKMAN lists bash, zip, unzip and curl as its basic toolset.

## Versions

Every version in the book is an exact pin, chosen as the newest stable release that works with the rest. Nothing needs a licence or a subscription.

| Component | Version | Why this one |
|-----------|---------|--------------|
| JDK | Temurin 25.0.5 | Long-term-support line, the minimum for every example |
| Maven | 3.9.16 | Maven 3.10 is released, but Quarkus has open issues against it |
| Quarkus platform | 3.40.1.2 | Long-term-support stream; includes Camel Quarkus 3.40.0 and Camel 4.22.1 |
| Apache Kafka | 4.3.2 | Newest version the latest Strimzi supports, so compose and Kubernetes match |
| PostgreSQL | 18.6 | CloudNativePG operand image on compose, same minor for Dev Services |
| Apicurio Registry | 3.3.4 | Schema registry |
| Debezium Connect | 3.7.1.Final | Published image, includes the PostgreSQL connector and the outbox `EventRouter` |
| Grafana LGTM | 0.40.0 | Loki, Grafana, Tempo and Prometheus behind one OTLP endpoint |
| UBI 10 OpenJDK | 1.24-19 | Build and runtime images for every service we package |
| Docker Engine, Compose plugin | 29.9.3, 5.7.0 | Recorded from the reference host |

Each service image is pinned by tag in `examples/_infra`.

## The stack

`examples/_infra` holds one compose file per service. An example includes the files it needs and adds its own services on the shared `edp` network.

| Service | Host port | Role |
|---------|-----------|------|
| Kafka (KRaft, one node) | 9092 | The log every pattern publishes to and reads from. Containers use `kafka:9094` |
| PostgreSQL, `wal_level=logical` | 5432 | State, outbox tables and the event store. Logical decoding lets Debezium read changes |
| Apicurio Registry | 8081, health 19000 | Schemas for the schema-evolution chapters. In memory, empty after a restart |
| Kafka Connect with Debezium | 8083 | Log tailing and the outbox router |
| Grafana LGTM | 3000, 4317, 4318, 19090, 3200, 3100 | Grafana, OTLP gRPC and HTTP, Prometheus, Tempo, Loki |

Cockpit, often enabled on Fedora and RHEL, listens on 9090, so Prometheus is published on 19090. Every service has a memory limit and a health check; the limits add up to about 7 GB if every container fills its allowance.

### One stack at a time

Run one stack. The services publish fixed host ports, so a second copy fails to bind them, and the Kubernetes parts need the memory the compose stack uses. Every `verify.sh` begins with `scripts/stack-guard.sh`, which refuses to start while containers from another compose project or a minikube node are running. It never stops anything for you. Stop an example's stack with `docker compose down -v` in its directory, or let `verify.sh` do it.

## Where the signals land

The stack-check service sets `service.name` to `stack-check` and exports all three signals over OTLP with http/protobuf to `lgtm:4318`.

{% include excalidraw.html file="00-telemetry-path" alt="The service sends traces, metrics and logs through the OTLP receiver in the LGTM container, which routes them to Tempo, Prometheus and Loki" caption="Figure 0.2: One endpoint, three backends" %}

Open Grafana at [http://localhost:3000](http://localhost:3000). It needs no login. In Explore, pick a data source:

| Signal | Data source | First query |
|--------|-------------|-------------|
| Traces | Tempo | Search with `service.name = stack-check` |
| Metrics | Prometheus | `target_info{service_name="stack-check"}`, then any metric with that `service_name` label |
| Logs | Loki | `{service_name="stack-check"}`; the consumer route logs each record it reads |

## How every example is laid out

```
examples/NN-slug/
  pom.xml  src/         Quarkus and Camel project
  compose.yaml          includes ../_infra files and adds the service
  Containerfile         UBI 10 multi-stage build, context is examples/
  verify.sh             starts the stack, checks the observable effect, tears down
  README.md
```

`examples/domain-model` holds the shared shipping records (`Consignment`, `HandlingEvent` and the rest), installed into the local Maven repository or built inside the Containerfile. Every example runs two ways:

- `mvn -B verify` runs the tests. Quarkus Dev Services starts the containers a test needs, so no compose stack is required.
- `./verify.sh` builds the image, starts the compose stack and checks the behaviour end to end. It prints one `PASS` or `FAIL` line per check, ends with `N/N checks passed` and exits non-zero on any failure. `KEEP=1 ./verify.sh` leaves the stack running afterwards so you can inspect it.

Each `verify.sh` includes a negative witness: a check that fails when the pattern's guard is removed. A script that only proved the happy path would pass for the wrong reasons.

## Build, run, observe: the stack check

The first example is the smallest service that touches every piece. `POST /consignments` takes a `Consignment`, rejects an invalid one with 400 and publishes a valid one to the `consignments` topic keyed by its id. A second route consumes the topic and logs each record.

```java
from("platform-http:/consignments?httpMethodRestrict=POST")
        .routeId("consignment-intake")
        .unmarshal().json(Consignment.class)
        .setHeader(KafkaConstants.KEY, simple("${body.id}"))
        .marshal().json()
        .to("kafka:consignments")
        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(202))
        .setBody(constant(""));
```

The `Consignment` record validates itself in its constructor, so a negative weight or a blank origin fails during unmarshalling. An `onException` clause turns that failure into a 400 before the route reaches Kafka.

Telemetry comes from the framework. `camel-quarkus-opentelemetry2` creates a span per route and processor; the older `camel-quarkus-opentelemetry` extension is deprecated. `quarkus-micrometer-opentelemetry` exports the Micrometer meters over OTLP, so metrics take the same path as traces and no Prometheus scrape is configured. `quarkus.otel.logs.enabled=true` sends the log records. SmallRye Health serves `/q/health`.

Run it:

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/00-stack-check
mvn -B verify
./verify.sh
```

The tests pass on a Dev Services Kafka running the pinned broker image. `verify.sh` then prints:

```
PASS 1: edp-kafka is healthy
...
PASS 7: valid Consignment accepted with 2xx
PASS 8: the record keyed C-1794344257 arrives on the consignments topic
PASS 9: PostgreSQL wal_level is logical
...
PASS 13: Tempo has a trace for service stack-check
PASS 14: Prometheus has a metric from stack-check
PASS 15: Loki has a log line from stack-check
PASS 16: negative witness: invalid Consignment gets 400
PASS 17: negative witness: record count unchanged (1)
17/17 checks passed
```

Try each of these and rerun `./verify.sh`:

- Remove the `onException` clause. An invalid Consignment gets 500 instead of 400, so check 16 fails; check 17 still passes because nothing reached the topic.
- Change `kafka:consignments` in the intake route to `kafka:consignments-v2`. Check 8 fails because no record arrives on `consignments`, and check 15 fails because the consumer route has nothing to log.

## Part 6 runs on Kubernetes

Parts 1 to 5 use this compose stack. Part 6 moves the same patterns to minikube with Strimzi for Kafka, CloudNativePG for PostgreSQL and the same LGTM image. It has its own setup at the start of Part 6, with its tool versions and commands; nothing on this page installs a cluster. Stop the compose stack before you start one.

## References

- Kafka, Apicurio, Debezium, Grafana LGTM, Quarkus and Camel Quarkus documentation, linked from the service files in `examples/_infra` and the example's `pom.xml`.
- [Camel Quarkus OpenTelemetry2 extension](https://camel.apache.org/camel-quarkus/latest/reference/extensions/opentelemetry2.html)
- [Quarkus OpenTelemetry guides](https://quarkus.io/guides/opentelemetry)
