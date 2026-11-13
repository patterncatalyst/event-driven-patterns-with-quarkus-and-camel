---
title: "Schema evolution and upcasting"
order: 18
permalink: /examples/18-schema-evolution-and-upcasting/
description: "An order event store that upcasts rows of an earlier shape when it reads them, an Avro OrderPlaced published through the outbox under a registry compatibility rule, and a notification consumer that reads every release through SmallRye and Camel."
---

[`examples/18-schema-evolution-and-upcasting/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/18-schema-evolution-and-upcasting) adds `shippingPriority` to `OrderPlaced`. `order-service` stores events with a `schema_version`, upcasts them on read and publishes Avro bytes through the outbox; `notification-service` reads the topic with the newest schema. It accompanies [Schema evolution and upcasting]({{ '/docs/18-schema-evolution-and-upcasting/' | relative_url }}).

It uses Quarkus, Hibernate ORM with Panache, Flyway, Quarkus REST, SmallRye Reactive Messaging, the Apicurio Avro serdes, the Debezium outbox extension and Event Router, Camel Kafka for the comparison reader, PostgreSQL, Apicurio Registry and OpenTelemetry to the LGTM stack.

```bash
cd examples/18-schema-evolution-and-upcasting
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="18-upcast-on-read" alt="Two rows in the append-only events table, one stored at schema_version 1 and one at 2; a load copies the payload, the upcaster chain adds shippingPriority STANDARD to the version 1 copy, and the aggregate sees only the current OrderPlaced record" caption="A stored event is never rewritten; a copy is upcast on every read" %}

## What to observe

1. **Old rows load as the current shape.** A row stored without `shippingPriority` reads as `STANDARD`, and its stored payload keeps the same checksum after a command.
2. **The registry holds the contract.** Versions 1 and 2 are accepted and version 3, which drops the default, is refused with 400.
3. **A refused command leaves nothing.** The broken release answers 503 and stores no row, no record and no version.
4. **Both readers resolve both shapes.** SmallRye and the Camel route record the same priorities from records written with versions 1 and 2.

## Infrastructure

Kafka, PostgreSQL, Apicurio Registry, Connect and the LGTM stack from `examples/_infra`, plus `order-service` and `notification-service`. The compose project is `edp-18`.
