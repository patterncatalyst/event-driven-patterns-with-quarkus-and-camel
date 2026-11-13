# 19 Enrich

`ShipmentScheduled` names a carrier and a tracking number. `notification-service` needs the carrier's transport mode and tracking link before it tells the customer, and `shipping-service` owns that reference data. `notification-service` enriches each shipment twice, from the same input: a Camel Content Enricher asks `shipping-service` over HTTP for every record, and a Kafka Streams left join reads a local copy of the compacted topic `edp.shipping.carriers`. Each form writes its own topic, so every difference is visible in one run. A real service would run one of them. The two differ when a reference changes and when the owner is down.

Quarkus, Kafka Streams, Hibernate ORM with Panache, Flyway, Quarkus REST, Camel Kafka and HTTP, PostgreSQL, Kafka, SmallRye Health and OpenTelemetry to the LGTM stack. Topic names come from `Topics` in `examples/domain-model`.

## Services

| Service | Port | Purpose |
|---|---|---|
| `shipping-service` | 8082 | Owns `shipping.carrier`. `GET /carriers/{carrier}`; `PUT /carriers/{carrier}` is demo code that changes a template. Health on `/q/health/ready` |
| `notification-service` | 8080 | Both enrichment forms. `GET /demo/carriers/{carrier}` is demo code that reads the local copy. Health on `/q/health/ready` and `/q/health/live` |

| Setting | Default | Effect |
|---|---|---|
| `CAMEL_BREAK_ON_FIRST_ERROR` | `true` | Demo code. `false` commits a record whose lookup failed, so it is never enriched (the negative witness) |

Topics, one partition each: `edp.shipping.scheduled` (input), `edp.shipping.carriers` (compacted reference), `edp.shipping.scheduled-enriched` (Streams output), `edp.shipping.scheduled-enriched-camel` (Camel output). Kafka Streams state is under `/tmp`; the Streams application id is `notification-enricher`, the Camel group is `notification-enricher-camel`, and both use a 10 s session timeout so a recreated service takes its partitions back within seconds. `verify.sh` plays the owner's publishing step: it writes the reference records to the compacted topic, as an outbox or log tailing would (chapters 8 and 9).

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # both modules, against PostgreSQL and Kafka from Dev Services
./verify.sh        # compose stack
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`. It ends with `TBD/TBD checks passed`.

## What verify.sh does

1. Starts the stack and checks that both services are ready, Kafka Streams is RUNNING, the Camel group owns its partition, the four topics have one partition each and the carrier topic is compacted. It publishes the two references and waits until the owner and the local copy both show them.
2. Sends `O-1901` (FASTLINE) and `O-1902` (AIRCO). Both outputs hold the same two records, with the mode and the link built from the tracking number.
3. Burst: 500 shipments in one producer call. Both outputs hold 500 enriched records within 60 s. The script prints the time from the call to the last output record for each form (poll step about 1 s) and the number of lookups `shipping-service` served, from its access log.
4. Reference change: the owner's template for FASTLINE changes first. `O-1903` gets the new link from the lookup and the old link from the join. The new reference is published and the local copy is read until it shows it; `O-1904` gets the new link from both, and `O-1903` on the Streams output keeps the old one.
5. Owner down: `shipping-service` is stopped. `O-1905` is on the Streams output within 10 s. For 20 s it is absent from the Camel output and the Camel group has lag 1. After the owner returns it is enriched within 30 s and the lag is 0.
6. Deleted reference: a tombstone removes AIRCO from the topic. `O-1906` is forwarded by the join with no mode and no link; the lookup still enriches it, because the owner still has AIRCO.
7. Recreate: `notification-service` is recreated with an empty state directory. Its local copy is rebuilt from the topic (new FASTLINE template, no AIRCO), and `O-1907` is enriched by both forms.
8. Prometheus holds `shipment_enrichments_total` for the Camel path, a trace is in Tempo and logs are in Loki.

## Layout

| Path | Purpose |
|---|---|
| `notification-service/.../ShipmentScheduled`, `CarrierReference`, `EnrichedShipmentScheduled` | The payload records |
| `notification-service/.../Enrichment` | The merge both forms call; counts `shipment.enrichments` by path and outcome |
| `notification-service/.../ShipmentEnrichmentTopology` | The `GlobalKTable` and the left join |
| `notification-service/.../ShipmentEnricherRoute`, `CarrierEnrichment` | The Camel route with `enrich` and its `AggregationStrategy` |
| `notification-service/.../CarrierStoreResource` | Demo code: reads the local copy through an interactive query |
| `shipping-service/.../CarrierRecord`, `CarrierResource` | The owner's table and its lookup endpoint |
| `shipping-service/src/main/resources/db/migration/V1__carrier.sql` | The table and its two seeded rows |
| `compose.yaml` | Topics and the two services |

## Tests and checks in detail

The Maven build runs 13 tests across the two modules. In `shipping-service` they cover the seeded references, an unknown carrier and a changed template. In `notification-service`, five run the topology in `TopologyTestDriver` with no broker: the left join enriching both carriers, a reference change applying only to later shipments, a missing reference keeping the record, an inner join dropping it, and a table join on the record key matching nothing. Four more run against Kafka from Dev Services with both forms in one application: both forms producing the same record, the lookup seeing a change before the join does, a deleted reference, and a stopped owner leaving the Camel offset uncommitted until it returns. The last starts the application with `CAMEL_BREAK_ON_FIRST_ERROR=false` and shows the failed record committed and never enriched.

`verify.sh` starts Kafka, PostgreSQL, the LGTM stack and both services and prints `TBD/TBD checks passed`. The checks read the effects:

- Both forms write the same record for the same shipment.
- A shipment scheduled between the owner's change and its publication differs between the forms; the next one agrees; the earlier output is not revised.
- With the owner stopped, the Streams output grows, the Camel output does not and the Camel group has lag 1; after the owner returns the record is enriched and the lag is 0.
- A deleted reference leaves the join's record with an empty mode and link.
- A recreated enricher rebuilds its local copy from the compacted topic.

Event times are not used: the enrichment reads no clock, so the example has no event-time rule to follow.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `CAMEL_BREAK_ON_FIRST_ERROR=false ./verify.sh` (demo code) | The record that arrives while the owner is down is committed after the redeliveries and never enriched | TBD(expected: 12 and 13, the 20 s wait that expects lag 1 and the catch-up) | TBD(spike: the record was lost in 3 of 3 runs) |

Timing notes from the spikes, re-measured in the final run: a reference record reaches the local copy TBD(spike: 6 to 17 ms) after the produce call returns; with the owner down, a waiting record is enriched TBD(spike: 1.8 to 2.7 s) after the owner returns; Kafka Streams output arrives about TBD(spike: 128 ms) after the input with a one-second commit interval and no record cache.
