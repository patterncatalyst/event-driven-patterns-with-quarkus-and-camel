---
title: "Enrich"
order: 19
part: processing-and-coordinating-streams
permalink: /docs/19-enrich/
description: "A shipment event names a carrier and nothing about it, and the customer needs the mode and the tracking link. A Camel Content Enricher asks the owner for every record; a Kafka Streams join reads a local copy of the owner's topic. They differ when the reference changes and when the owner is down."
duration: "60 minutes"
---

`ShipmentScheduled` says which carrier was booked and the tracking number. The customer wants more: does the parcel travel by road or by air, and which link tracks it. `shipping-service` owns that reference data. `notification-service` builds the message the customer reads, so it needs the data without owning it. The same enrichment is written twice in `notification-service`, once as a Camel route and once as a Kafka Streams topology, and both read the same input.

{% include excalidraw.html file="19-enricher-vs-table-join" alt="shipping-service owns the carrier table, serves it over HTTP and feeds the compacted carriers topic; the Camel route calls the endpoint for every shipment, and the Streams topology joins every shipment against a local copy of the topic; each writes its own enriched topic" caption="Figure 19.1: The same merge from two places, the owner's answer now or the owner's last published record" %}

## Pattern: Content Enricher

### The problem

The sender of a message often lacks data the receiver needs. `shipping-service` publishes the facts it decided, the carrier and the tracking number, and not the carrier's tracking page, which changes for reasons that have nothing to do with a shipment. Putting the page into every event would repeat reference data in every record and freeze an old value into each of them. Making the receiver keep the data itself would give two services one truth to maintain.

Two ways of asking exist. The receiver can ask the owner each time, and so see the current row but depend on the owner being up. Or it can keep a copy fed by the owner's own events, and so work when the owner is down but only know what the owner last published. Chapter 2 made one such call, a fetch-back over HTTP that replaces a thin event with the full order. Here the answer is merged into the message and the message is kept.

### The solution

**A Content Enricher uses data in the incoming message to fetch the missing details from an external source and attaches them to the message.**

Hohpe and Woolf define the pattern by that source being external to the message. Whether the source is a call or a local copy is a choice the pattern leaves open, and it decides how the enrichment behaves when the world changes.

### How Quarkus and Camel model it

`ShipmentEnricherRoute` is the lookup form. It reads `ShipmentScheduled`, calls `shipping-service` with the carrier from the message, and merges the answer:

{% raw %}
```java
from("kafka:" + Topics.SHIPPING_SCHEDULED + "?groupId=notification-enricher-camel&sessionTimeoutMs=10000"
        + "&breakOnFirstError={{enricher.break-on-first-error}}")
    .unmarshal().json(JsonLibrary.Jackson, ShipmentScheduled.class)
    .removeHeaders("kafka.*")
    .setHeader(Exchange.HTTP_METHOD, constant("GET"))
    .enrich().simple("{{shipping-service.url}}/carriers/${body.shipment.carrier}")
            .aggregationStrategy(carrierEnrichment)
    .setHeader(KafkaConstants.KEY, simple("${body.shipment.orderId}"))
    .marshal().json(JsonLibrary.Jackson)
    .to("kafka:" + Topics.SHIPPING_SCHEDULED_ENRICHED_CAMEL);
```
{% endraw %}

`enrich` calls the endpoint and hands the original message and the answer to an `AggregationStrategy`. `CarrierEnrichment` reads the answer as a `CarrierReference` and calls `Enrichment.merge`, the one function both forms use, which fills the carrier mode and replaces `{trackingNumber}` in the template. The key is set through a header; a `key` option written with a header expression on the producer URI would write the expression text as the key.

A failed call is retried three times, and then the exchange fails. With `breakOnFirstError` the consumer seeks back to the failed record and reads it again, so the offset stays where it was until the call succeeds. The record is neither lost nor skipped, and the records behind it wait.

## The same enrichment as a stream-table join

The second form moves the reference data to the receiver. `edp.shipping.carriers` is a compacted topic with one record per carrier, so it keeps the latest reference for each and nothing older. `shipping-service` is the owner; chapters 8 and 9 show how an owner publishes a table's changes. A Kafka Streams `GlobalKTable` reads the whole topic into a local store on every instance, and the stream of shipments joins against it:

```java
GlobalKTable<String, CarrierReference> carriers = builder.globalTable(Topics.SHIPPING_CARRIERS,
        Consumed.with(Serdes.String(), references),
        Materialized.<String, CarrierReference, KeyValueStore<Bytes, byte[]>>as("carriers"));
builder.stream(Topics.SHIPPING_SCHEDULED, Consumed.with(Serdes.String(), scheduled))
        .leftJoin(carriers,
                (orderId, event) -> event.shipment().carrier().name(),
                (event, reference) -> enrichment.merge(Enrichment.KAFKA_STREAMS, event, reference))
        .to(Topics.SHIPPING_SCHEDULED_ENRICHED, Produced.with(Serdes.String(), enriched));
```

The first lambda says where to look: the join key is a field of the value, not the record key, which is the order id. That is what a global table is for. A `KTable` joins on the record key, so joining shipments on a table keyed by carrier matches nothing, and it would need a re-keying and a repartition topic for two rows. The global table needs no co-partitioning because every instance holds every partition of the reference topic. It also costs a full copy of the topic on every instance, which suits a small table and not a large one. The local copy is the same idea as `order_copy` in chapter 3, held by Kafka Streams instead of a table.

The join is a left join. A shipment whose carrier has no reference is forwarded with an empty mode and link; an inner join would drop it, and nothing would show that a customer was never told.

The topology is a `Topology` bean and the extension runs it. Readiness waits for the two input topics, liveness follows the state of the Streams instance, and a restarted instance bootstraps the store from the compacted topic before it processes shipments.

## When the reference changes

An operator changes FASTLINE's tracking page. The owner's row changes first, and the topic changes when the owner publishes it.

{% include excalidraw.html file="19-reference-change-and-outage" alt="Timeline: the template changes at the owner, then on the topic; O-1903 between the two gets the new link from the lookup and the old link from the join; O-1904 after the topic update gets the new link from both; the owner then stops, the lookup waits with lag 1 while the join carries on, and the lookup catches up when the owner returns" caption="Figure 19.2: The lookup is current and depends on the owner; the join is as fresh as the topic and does not" %}

A shipment scheduled in between gets the new link from the lookup and the old link from the join. The lookup sees the owner's current row. The join sees the topic, so it is as fresh as the time the owner takes to publish plus the time a store takes to apply it, which was TBD(spike: 6 to 17 ms) once the record was on the topic. After that the two agree.

Neither form revises an earlier output. A message already written holds the link it was enriched with, and the next shipment holds the new one. If outputs must follow the reference, they are not enriched messages but joins that recompute, and that is a table-to-table join with its own output stream.

## When the owner is down

With `shipping-service` stopped, the lookup cannot answer. The route retries, the consumer seeks back to the failed record, the offset stays put and the group shows a lag of one. Nothing is lost, nothing behind it moves, and when the owner returns the record is enriched within seconds: TBD(spike: 1.8 to 2.7 s) after the owner was back. The join does not notice. It enriches from the local copy and goes on, with data as old as the last record it read.

`breakOnFirstError=false` removes the guard. The consumer commits past the failed record and carries on, and the record is never enriched. The failure is silent and permanent, which is why the guard is the first thing to check in an enricher that calls out.

The trade-offs, measured on the same input:

| | Camel lookup | Streams join |
|---|---|---|
| Where the reference lives | Owner only | Owner, plus a full copy in every instance |
| Freshness | The owner's current row | The topic's last record for the carrier |
| Owner down | Stalls on the failed record, catches up, loses nothing | Continues from the local copy |
| Cost per record | One HTTP call, TBD(spike: 500) lookups for a burst of TBD(spike: 500) | A local store read, no lookups |
| Burst of TBD(spike: 500) shipments | TBD ms to the last output | TBD ms to the last output |
| Memory | None for the reference | The whole table, per instance |
| Missing reference | The owner answers not found and the record waits | The record is kept with an empty mode and link |
| Coupling | Availability and latency of the owner | Format of the reference topic |

A real service picks one. The example runs both side by side so that every difference above comes from the same input.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/19-enrich
mvn -B verify
./verify.sh
```

`verify.sh` sends the same shipments to both forms, changes a reference, stops `shipping-service` and recreates the enricher. It prints `TBD/TBD checks passed`. Watch the two outputs differ for one shipment after the template change, and the lookup's lag fall back to zero when the owner returns.

Try one change and rerun:

- `CAMEL_BREAK_ON_FIRST_ERROR=false ./verify.sh` (demo code): the shipment that arrives while the owner is down is committed and never enriched.

## When not to use it

Do not enrich when the receiver can ask for the data at the moment it is needed. A consumer that renders a page can call the owner then. Enrichment is the right tool when the enriched message is stored, sent on or replayed, and so must hold what was true when it was made.

Do not call out from a consumer whose availability must not depend on another service. A lookup couples the consumer to the owner's uptime and latency, and a stall in one record holds up every record behind it in the partition. Use a local copy, and accept that it is as fresh as the owner's last published change.

Do not hold a copy of data that is large, changes constantly or is private to the owner. A global table is a full copy per instance; for a big reference set a partitioned table joined on a co-partitioned key is the form, and the join key must then be the record key. If the owner publishes no change events, there is nothing to fill the copy from.

> **🤖 Explore with your agent**
> "In examples/19-enrich, change FASTLINE's template through PUT /carriers/FASTLINE on shipping-service before publishing it to edp.shipping.carriers, schedule a shipment, and compare ShipmentEnricherRoute's output with ShipmentEnrichmentTopology's. Then stop shipping-service, schedule another, and state when each form should be chosen."
> Check: the shipment scheduled between the change and its publication has the new link on edp.shipping.scheduled-enriched-camel and the old link on edp.shipping.scheduled-enriched, and with the owner stopped only the Streams output grows while the Camel group has lag 1.

## References

- Gregor Hohpe and Bobby Woolf, [Content Enricher](https://www.enterpriseintegrationpatterns.com/patterns/messaging/DataEnricher.html), *Enterprise Integration Patterns*, Addison-Wesley, 2003
- Apache Camel, [Enrich](https://camel.apache.org/components/4.22.x/eips/enrich-eip.html) and [Kafka component](https://camel.apache.org/components/4.22.x/kafka-component.html) (`breakOnFirstError`)
- Apache Kafka, [Kafka Streams DSL](https://kafka.apache.org/42/streams/developer-guide/dsl-api/) (joins, co-partitioning, the GlobalKTable)
- Quarkus, [Using Apache Kafka Streams](https://quarkus.io/guides/kafka-streams) (topics, health checks, interactive queries)
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapter 9: Composing Data from Different Sources
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 7: Denormalization and Eventification and chapter 8: Stateful Event-Driven Microservices
- Enterprise Integration Patterns with Camel, chapter 12: Transformation Fundamentals (Content Enricher)
- Chapter 2: Event notification, for the fetch-back; chapter 3 for the local copy; chapters 8 and 9 for how an owner publishes a table's changes
