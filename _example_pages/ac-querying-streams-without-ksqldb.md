---
title: "Appendix C: Querying streams without ksqlDB"
order: 38
permalink: /examples/ac-querying-streams-without-ksqldb/
description: "Deliveries per carrier per minute of event time from one topic, answered by Kafka Streams interactive queries, a Flink SQL job and RisingWave materialized views, with one lateness bound in all three."
---

[`examples/ac-querying-streams-without-ksqldb/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/ac-querying-streams-without-ksqldb) sends the same `ShipmentDelivered` records for orders O-38xx to three engines and compares the closed windows. It accompanies [Appendix C]({{ '/docs/ac-querying-streams-without-ksqldb/' | relative_url }}).

It uses Quarkus Kafka Streams, Apache Flink SQL with the Kafka connector, RisingWave, PostgreSQL (for its `psql` client), Kafka and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/ac-querying-streams-without-ksqldb
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="ac-three-ways" alt="One input topic edp.shipping.delivered feeds three engines: Kafka Streams in the service with a window store asked through GET /counts, a Flink SQL job whose closed windows land on an upsert-kafka topic, and RisingWave views asked with psql" caption="One topic, three engines, three places for the state and three ways to ask" %}
