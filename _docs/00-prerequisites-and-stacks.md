---
title: "Prerequisites and stacks"
order: 0
part: getting-started
permalink: /docs/00-prerequisites-and-stacks/
description: "The tools, versions and local stack every example runs on, and a first Order sent through Quarkus, Camel, Kafka and the LGTM observability stack."
duration: "30 minutes"
---

Every example in the book runs on one local stack: a Kafka broker, a PostgreSQL database, a schema registry, Kafka Connect with Debezium, and a Grafana LGTM container that receives traces, metrics and logs. An example is a small Quarkus service, usually with Camel routes, plus a script that starts the stack, exercises the service and checks what happened.

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
| Maven | 3.9.16 | Maven 3.10 is released, but Quarkus has an open issue against it |
| Quarkus platform | 3.40.1.2 | Long-term-support stream; includes Camel Quarkus 3.40.0 |
| Apache Camel | 4.22.2 | Long-term-support patch, imported through `camel-bom` ahead of the platform's 4.22.1 |
| Apache Kafka | 4.3.2 | Newest version the latest Strimzi supports, so compose and Kubernetes match |
| PostgreSQL | 18.6 | CloudNativePG operand image on compose, same minor for Dev Services |
| Apicurio Registry | 3.3.4 | Schema registry |
| Debezium Connect | 3.7.1.Final | Published image, includes the PostgreSQL connector and the outbox `EventRouter` |
| Grafana LGTM | 0.40.0 | Loki, Grafana, Tempo and Prometheus behind one OTLP endpoint |
| UBI 10 OpenJDK | 1.24-20 | Build and runtime images for every service we package |
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

## The running example

Every example in the book is a piece of one business: an online shop that sells and ships physical goods. A customer orders an item. The shop records the order, reserves stock, takes payment, books a carrier and delivers the parcel, and tells the customer after each step. Five services each own one of those steps. They share no tables and react to events on Kafka. Two patterns call the order service by design: event notification fetches the current order back, and a claim check fetches a stored attachment.

{% include excalidraw.html file="00-running-example" alt="Five services: order, inventory, payment and shipping in a row joined by topics, with notification below receiving from all of them" caption="Figure 0.2: Five services joined by topics" %}

| Service | Owns | Reacts to | Emits |
|---------|------|-----------|-------|
| order-service | `Order` and its lifecycle | InventoryInsufficient, PaymentProcessed, PaymentFailed, ShipmentScheduled, ShipmentDelivered | OrderPlaced, OrderCancelled |
| inventory-service | `InventoryItem` stock | OrderPlaced, OrderCancelled, PaymentFailed | InventoryReserved, InventoryInsufficient |
| payment-service | `Payment` | InventoryReserved | PaymentProcessed, PaymentFailed |
| shipping-service | `Shipment`, carrier choice | PaymentProcessed | ShipmentScheduled, ShipmentInTransit, ShipmentDelivered |
| notification-service | `Notification` | every event | nothing |

On the happy path the flow reads left to right in Figure 0.2. The order service publishes OrderPlaced. Inventory reserves stock and publishes InventoryReserved. Payment takes the money and publishes PaymentProcessed. Shipping books FASTLINE or AIRCO, then publishes ShipmentScheduled, ShipmentInTransit and ShipmentDelivered as the parcel moves. Order reacts to payment and shipment events to advance its status, and notification reads every topic to tell the customer. Apart from those fetches, no service calls another: each knows only the topics it reads and the topics it writes.

Each service keeps its tables in its own schema of the `edp` database: `orders`, `inventory`, `payments`, `shipping` and `notifications`. The order table is named `orders` because `order` is a reserved word.

Nine events carry the flow. A topic name is `edp.<domain>.<event>`, with dots and hyphens only.

| Event | Producer | Consumers | Topic |
|-------|----------|-----------|-------|
| OrderPlaced | order | inventory, notification | `edp.orders.placed` |
| OrderCancelled | order | inventory, notification | `edp.orders.cancelled` |
| InventoryReserved | inventory | payment, notification | `edp.inventory.reserved` |
| InventoryInsufficient | inventory | order, notification | `edp.inventory.insufficient` |
| PaymentProcessed | payment | shipping, order, notification | `edp.payments.processed` |
| PaymentFailed | payment | inventory, order, notification | `edp.payments.failed` |
| ShipmentScheduled | shipping | order, notification | `edp.shipping.scheduled` |
| ShipmentInTransit | shipping | notification | `edp.shipping.in-transit` |
| ShipmentDelivered | shipping | order, notification | `edp.shipping.delivered` |

The records live in `examples/domain-model`: `Order`, `InventoryItem`, `Payment`, `Shipment`, `Notification`, their status enums and a `Topics` class that holds the names above, so no example types a topic by hand. Each record validates itself in its constructor. The payload of an event is a separate record inside each example, because how much state an event carries is itself a design decision.

{% include excalidraw.html file="00-order-lifecycle" alt="The order moves PLACED, PAID, SHIPPED, DELIVERED, with CANCELLED reachable from PLACED and PAID; the shipment moves PENDING, SCHEDULED, IN_TRANSIT, DELIVERED" caption="Figure 0.3: The order and shipment lifecycles" %}

An order moves one step at a time: `PLACED`, `PAID`, `SHIPPED`, `DELIVERED`. It can become `CANCELLED` only from `PLACED` or `PAID`. `DELIVERED` and `CANCELLED` are final. A shipment moves `PENDING`, `SCHEDULED`, `IN_TRANSIT`, `DELIVERED`. Order ids are text such as `O-1001`, and amounts carry two decimals.

Two fictional carriers deliver the parcels: FASTLINE by road and AIRCO by air. No real carrier appears anywhere in the book.

The failure paths are part of the model:

- Out of stock: InventoryInsufficient cancels the order, and the customer is told.
- Payment declined: PaymentFailed releases the reserved stock and cancels the order, and the customer is told.
- Late delivery: no ShipmentDelivered arrives in time, so someone must be alerted.
- The same event delivered twice: a consumer must not apply it twice.
- A service down: its events wait in Kafka until it returns.

## Where the signals land

The stack-check service sets `service.name` to `stack-check` and exports all three signals over OTLP with http/protobuf to `lgtm:4318`.

{% include excalidraw.html file="00-telemetry-path" alt="The service sends traces, metrics and logs through the OTLP receiver in the LGTM container, which routes them to Tempo, Prometheus and Loki" caption="Figure 0.4: One endpoint, three backends" %}

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

`examples/domain-model` holds the shared shipping records (`Order`, `Shipment` and the rest), installed into the local Maven repository or built inside the Containerfile. Every example runs two ways:

- `mvn -B verify` runs the tests. Quarkus Dev Services starts the containers a test needs, so no compose stack is required.
- `./verify.sh` builds the image, starts the compose stack and checks the behaviour end to end. It prints one `PASS` or `FAIL` line per check, ends with `N/N checks passed` and exits non-zero on any failure. `KEEP=1 ./verify.sh` leaves the stack running afterwards so you can inspect it.

Each `verify.sh` includes a negative witness: a check that fails when the pattern's guard is removed. Without it, a script could still pass after the guard it checks has been removed.

## Build, run, observe: the stack check

The first example is the smallest service that touches every piece. `POST /orders` takes an `Order`, rejects an invalid one with 400 and publishes a valid one to the `edp.orders.placed` topic keyed by its id. A second route consumes the topic and logs each record.

```java
from("platform-http:/orders?httpMethodRestrict=POST")
        .routeId("order-intake")
        .unmarshal().json(Order.class)
        .setHeader(KafkaConstants.KEY, simple("${body.id}"))
        .marshal().json()
        .to("kafka:" + Topics.ORDERS_PLACED)
        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(202))
        .setBody(constant(""));
```

The `Order` record validates itself in its constructor, so a zero quantity or a blank customer fails during unmarshalling. An `onException` clause turns that failure into a 400 before the route reaches Kafka.

Telemetry comes from the framework. `camel-quarkus-opentelemetry2` creates a span per route and processor; the older `camel-quarkus-opentelemetry` extension is deprecated. `quarkus-micrometer-opentelemetry` exports the Micrometer meters over OTLP, so metrics take the same path as traces and no Prometheus scrape is configured. The same extension turns on OpenTelemetry tracing, metrics and logs by default, so the log records reach Loki with no extra setting. SmallRye Health serves `/q/health`.

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
PASS 7: valid Order accepted with 2xx
PASS 8: the record keyed O-1794344257 arrives on the edp.orders.placed topic
PASS 9: PostgreSQL wal_level is logical
...
PASS 13: Tempo has a trace for service stack-check
PASS 14: Prometheus has a metric from stack-check
PASS 15: Loki has a log line from stack-check
PASS 16: negative witness: invalid Order (quantity 0) gets 400
PASS 17: negative witness: record count unchanged (1)
17/17 checks passed
```

Try each of these and rerun `./verify.sh`:

- Remove the `onException` clause. An invalid Order gets 500 instead of 400, so check 16 fails; check 17 still passes because nothing reached the topic.
- Change `Topics.ORDERS_PLACED` in the intake route to `"edp.orders.placed-v2"`. Check 8 fails because no record arrives on `edp.orders.placed`, and check 15 fails because the consumer route has nothing to log.

## Part 6 runs on Kubernetes

Parts 1 to 5 use this compose stack. Part 6 moves the same patterns to minikube with Strimzi for Kafka, CloudNativePG for PostgreSQL and the same LGTM image. It has its own setup at the start of Part 6, with its tool versions and commands. No cluster is needed for Parts 1 to 5. Stop the compose stack before you start one.

## References

- [Apache Kafka documentation](https://kafka.apache.org/documentation/)
- [Apicurio Registry documentation](https://www.apicur.io/registry/docs/)
- [Debezium documentation](https://debezium.io/documentation/)
- [grafana/docker-otel-lgtm](https://github.com/grafana/docker-otel-lgtm)
- [Quarkus guides](https://quarkus.io/guides/)
- [Camel Quarkus documentation](https://camel.apache.org/camel-quarkus/latest/)
- [CloudNativePG documentation](https://cloudnative-pg.io/documentation/)
- [SDKMAN](https://sdkman.io/)
- [Camel Quarkus OpenTelemetry2 extension](https://camel.apache.org/camel-quarkus/latest/reference/extensions/opentelemetry2.html)
- [Quarkus OpenTelemetry guides](https://quarkus.io/guides/opentelemetry)
