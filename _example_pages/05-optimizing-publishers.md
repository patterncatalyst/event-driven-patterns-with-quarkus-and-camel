---
title: "Optimizing publishers"
order: 5
permalink: /examples/05-optimizing-publishers/
description: "Three Camel publish routes with different producer settings, a flash-sale load driver and Kafka client metrics in Prometheus."
---

[`examples/05-optimizing-publishers/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/05-optimizing-publishers) publishes a burst of `OrderPlaced` records through three Camel routes and reads how many records each Kafka request carried. It accompanies [Optimizing publishers]({{ '/docs/05-optimizing-publishers/' | relative_url }}).

It uses Quarkus, Camel Kafka, Micrometer and OpenTelemetry to the LGTM stack. There is no database.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/05-optimizing-publishers
mvn -B verify
./verify.sh
./bench.sh
```

{% include excalidraw.html file="05-sequential-vs-concurrent" alt="One caller waits for each acknowledgement and sends one record per request; many callers share batches that fill before linger.ms ends" caption="linger.ms only helps when sends overlap" %}

## What to observe

1. **Unbatched is one record per batch.** With `lingerMs=0` and `producerBatchSize=0` the route sends at most three records per request on three partitions.
2. **Kafka's defaults already batch.** The default route sends several times the unbatched records per request with no tuning options.
3. **Tuning goes further.** The tuned route sends fewer requests and fewer bytes per record, and the broker's log holds `lz4` batches.
4. **One caller never fills a batch.** The tuned route with one caller in flight sends about one record per request; with 100 callers it does not.
5. **acks=1 without idempotence drops the producer id.** After a recreate the new batches carry no producer id.
6. **Throughput belongs to bench.sh.** `results/` holds the medians and ranges of the repeated rounds.

## Infrastructure

Kafka and the LGTM stack from `examples/_infra`, a one-shot container that creates the topic, and `order-service` on port 8080. The compose project is `edp-05`.
