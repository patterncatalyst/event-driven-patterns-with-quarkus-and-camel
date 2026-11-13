---
title: "Enrich"
order: 19
permalink: /examples/19-enrich/
description: "ShipmentScheduled enriched with the carrier's mode and tracking link twice over: a Camel Content Enricher that calls shipping-service per record, and a Kafka Streams left join against a local copy of a compacted topic."
---

[`examples/19-enrich/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/19-enrich) has two services. `shipping-service` owns the carrier reference data and serves it over HTTP. `notification-service` reads `ShipmentScheduled` and writes it enriched, through a Camel route and through a Kafka Streams topology, each to its own topic. It accompanies [Enrich]({{ '/docs/19-enrich/' | relative_url }}).

It uses Quarkus, Kafka Streams, Hibernate ORM with Panache, Flyway, Quarkus REST, Camel Kafka and HTTP, PostgreSQL, Kafka and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/19-enrich
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="19-reference-change-and-outage" alt="Timeline: the template changes at the owner, then on the topic; O-1903 between the two gets the new link from the lookup and the old link from the join; O-1904 after the topic update gets the new link from both; the owner then stops, the lookup waits with lag 1 while the join carries on, and the lookup catches up when the owner returns" caption="The lookup is current and depends on the owner; the join is as fresh as the topic and does not" %}

## What to observe

1. **Both forms produce the same record.** The same shipment has the same mode and link on both output topics.
2. **A reference change reaches the two forms at different times.** A shipment scheduled before the new template is published gets the new link from the lookup and the old link from the join.
3. **An output is not revised.** The earlier shipment keeps the link it was enriched with.
4. **A stopped owner stalls the lookup and not the join.** The Camel group has lag 1 until the owner returns, then the record is enriched and nothing is lost.
5. **A deleted reference keeps the record.** The join forwards the shipment with an empty mode and link.
6. **A recreated enricher rebuilds its copy from the topic.**

## Infrastructure

Kafka, PostgreSQL and the LGTM stack from `examples/_infra`, a one-shot container that creates the four topics with one partition each (the carrier topic compacted), `shipping-service` and `notification-service`. The compose project is `edp-19`.
