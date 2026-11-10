---
title: "CRUD versus events"
order: 1
permalink: /examples/01-crud-vs-events/
description: "One ChangeConsignmentStatus command applied as a row update and as an appended event, with replay, history and an append-only guard."
---

[`examples/01-crud-vs-events/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/01-crud-vs-events) applies one command to a Consignment two ways. The `consignment` row is overwritten; `consignment_event` gains a fact. It accompanies [Events, commands and documents]({{ '/docs/01-events-commands-documents/' | relative_url }}).

It uses Quarkus REST, Hibernate ORM with Panache, Flyway, PostgreSQL and OpenTelemetry to the LGTM stack. There is no Camel and no Kafka.

```bash
cd examples/01-crud-vs-events
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="01-row-update-vs-event-append" alt="The same command applied to a row and to an event stream" caption="One command, two storage outcomes" %}

## What to observe

1. **The row keeps only the latest status.** `GET /consignments/{id}` returns the Consignment with its current `ConsignmentStatus`.
2. **The stream keeps every status.** `GET /consignments/{id}/history` lists each status with its version and timestamp.
3. **Replay equals the row.** `GET /consignments/{id}/replayed` returns the same JSON as the row.
4. **An illegal move is refused.** Sending BOOKED to a LOADED Consignment returns 409 and `consignment_event` gains no row.
5. **The history is append-only.** `UPDATE consignment_event` fails in `psql` with an append-only error.
6. **Telemetry arrives.** Grafana at `http://localhost:3000` shows traces for `crud-vs-events`.

## Infrastructure

PostgreSQL and the LGTM stack from `examples/_infra`, plus the application container built from a UBI 10 Containerfile. The compose project is `edp-01`.
