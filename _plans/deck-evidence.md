# Deck evidence (r15)

Internal map from each slide number (as in `lgtm-presentation/text/`) to the claims row, verify.sh check or example file behind every number or measured behaviour on the slide or in its notes. Internal files may name chapters; the deck never does. A page reference means the book page states the value and the row or check named next to it backs it.

| slide | claim (number or behaviour) | claims id or file:line |
|---|---|---|
| 3 | Quarkus 3.40.1.2 LTS, Camel Quarkus 3.40.0, Camel 4.22.2 | 00-07 |
| 3 | Kafka 4.3.2 KRaft, newest the latest Strimzi supports; Strimzi 1.3.1 | 00-08, r04-ver-05, 28-c; examples/_k8s/versions.env:5 |
| 3 | PostgreSQL 18.6, Debezium 3.7.1.Final, LGTM 0.41.0, Apicurio 3.3.4 | 00-09, 00-10; 28-c (otel-lgtm 0.41.0); 00-04, 00-52 (Apicurio) |
| 4 | five services, no shared tables; nine events; edp.<domain>.<event> | 00-56, 00-62 |
| 5 | order and shipment states, CANCELLED only from PLACED or PAID, FASTLINE and AIRCO, failure paths | 00-73, 01-10; domain-model OrderStatus.canMoveTo |
| 6 | ports 9092, kafka:9094, 5432, 8081, 8083, 3000, 19090; Cockpit on 9090; one stack at a time | 00-15 to 00-19, 00-21 |
| 7 | one OTLP http/protobuf endpoint to Tempo, Prometheus, Loki; no login | 00-11; _docs/00 "Where the signals land" |
| 8 | row overwrites, order_event appends; three message types | 01-05 |
| 10, 12 | breakOnFirstError, two redeliveries one second apart, one request per event | 02-14, 02-40; _docs/02 breakOnFirstError paragraph |
| 11 | publish after commit, trace across both services | 02-09 |
| 13, 15 | max.request.size 1048576; 100 KB attachment, event under 1 KB, copy answers with source down | 03-22, 03-28, 03-39, 03-52, 03-16 |
| 16, 17 | murmur2 key hash; event-type key regresses DELIVERED to SHIPPED; carrier key uses two of three partitions | 04-02, 04-16, 04-17; _docs/04 figure 4.1 |
| 19, 20 | sequence number, row lock; run 3 ends SHIPPED, run 4 ends DELIVERED | 04-23, 04-24, 04-40 |
| 21 | linger 5 ms (formerly 0, KIP-1030), batch.size 16384, 20,000 orders | 05-02; _docs/05 lines 22, 77 |
| 21 | default route 4.9 of unbatched throughput (median of five rounds, range 2.1 to 6.0), tuned route 0.40 of the unbatched throughput (0.39 to 0.40), both ratios to the unbatched route of the same run; tuned is 20 ms, 128 KiB, lz4 | 05-08; _docs/05 lines 54, 81 to 87 |
| 21 | 200 callers each waiting for an acknowledgement; lz4 0.38 and zstd 0.19 bytes per record against none | 05-08; _docs/05 lines 32, 103, 105 |
| 22 | unbatched route about 3 records per request | 05-08 (records per request table); _docs/05 line 87 |
| 23 | nine partitions; drain ratios 0.33 (three threads) and 0.12 (twelve, RoundRobinAssignor), 3000 records at 5 ms | 06-04; _docs/06 line 50 |
| 23 | fetch.min.bytes 65536 cuts about 2,400 fetches to 60; up to 500 ms delay; max.poll.interval 300000 | 06-06, 06-02; _docs/06 lines 32, 38 |
| 24 | classic protocol with an eager assignor gives up all 9, consumer protocol 4 or 5, CooperativeSticky on the classic protocol 4 | 06-07 |
| 25 | lock 30 s, delivery limit 5, no ordering guarantee, accept, release, reject | 07-09 |
| 25 | eight-second job, twelve jobs behind it | _docs/07 line 10; 07-r2-01 |
| 25 | fast partition 2 to 7 s through the share group against under 1 s through the consumer group; slow partition 8 to 9.4 s against 8.8 s | 07-r2-07 |
| 26 | worker-1 acquires only the slow job; one-record batches; one-second retry cap | 07-r2-01, 07-r2-02, 07-r2-03, 07-r2-04 |
| 27 | ack accepts, nack releases or rejects; lock renewed for a job in progress | 07-r2-05, 07-09 |
| 29 | at least once; 201 in about 10 ms with Kafka stopped; commit-first 500 after 60 s | 08-10 |
| 29, 31, 32 | EventRouter keyed by aggregate id, row id header, at least once; rollback leaves no outbox row | 08-13, 08-16, 08-10; examples/08-transactional-outbox OrderService.java:26-31 |
| 33, 34 | slot, publication on three tables, heartbeat query releases WAL, one tombstone, snapshot as r events | 09-01, 09-04, 09-08, 09-09; _docs/09 figure 9.1 |
| 35 | offsets API lists at any time, alter and reset only while stopped (PATCH, DELETE); flush every 60 s default; lost slot RUNNING with no task, retry loop about 30 minutes | 10-03, 10-14, 10-15, 10-11, 10-12; _docs/10 lines 28, 68 |
| 36 | stream starts at the greater of offset and slot; alter ahead skips, behind does not rewind | 10-05, 10-06 |
| 37, 38 | ON CONFLICT DO NOTHING inbox, commit before acknowledge; session timeout 45 s default, 10 s set | 11-g; _docs/11 line 81; examples/11-idempotent-consumer-and-inbox ReservationService.java:19-34 |
| 40 | key store table: JPA repository in a transacted route, not transacted, JDBC under JTA, Kafka repository, natural idempotence | _docs/11 comparison table; verify.sh 11 |
| 41, 42, 43 | transactional.id, epoch fencing, read_committed stops at the last stable offset, aborted records stay, withTransactionAndAck, batch mode | 12-k, 12-l, 12-h, 12-m, 12-p; PaymentProcessor.java:30-41 |
| 44 to 46 | six records on one partition; SmallRye fail default and Camel default error handler; SmallRye dead letter headers, Camel none | 13-i, 13-l; _docs/13 line 125 |
| 45, 46 | lock timeout 2000 ms (two seconds); retry one second apart; without the hint the attempt waits about 10 of 12 s | 13-b, 13-h; _docs/13 lines 69, 75; DeadLetterRoute.java:27-45 |
| 48 to 50 | unique (stream_id, version) settles the race; ConcurrencyConflict; only OrderPlaced and OrderCancelled leave through the outbox | 14-a, 14-b, 14-d; EventStore.java:27-48 |
| 51, 52 | retry up to three times (SmallRye Fault Tolerance); If-Match gives 412; refusal gives 409; blind append puts CANCELLED after SHIPPED | 14-v, 14-aq, 14-ba, 14-r08-b |
| 53 | 100,000 events on SKU-1503 (100,200 in the table) | 15-d; _docs/15 lines 10, 109 |
| 53 | full load 1.6, 11.6, 127.7 ms for 1,100, 10,100, 100,200 events; snapshot load 0.50, 0.63, 0.95 ms with 99 events; medians of three runs, 21 timed loads each | 15-d, 15-y, 15-v; _docs/15 lines 101 to 109 |
| 53 | per-event full-load cost 1.4, 1.2, 1.3 us | 15-z; _docs/15 lines 107 to 109 |
| 53 | a 1,100-event stream loads in 1.6 ms without a snapshot | 15-ba |
| 54 | N=10 replays at most 9 and writes 100 snapshots per 1,000 reservations; N=1,000 replays at most 999 and writes one | 15-ac; _docs/15 line 113 |
| 56 | 250 orders (1,250 records), 95th percentile lag 318 to 334 ms; 12,881 ms with a 50 ms delay | 16-ap, 16-ag |
| 57 | guarded upsert in SQL, immutable rank function | _docs/16; verify.sh 16 |
| 59 | checkpoint under the lowest running transaction id | _docs/17; verify.sh 17 |
| 60 | replay 15 / 77 / 586 ms and projection 0.78 / 0.61 / 0.46 ms at 1,000 / 10,000 / 100,000 orders | 17-ah, 17-ai |
| 60 | 27 rows at every size; rebuild 1.8 s at 100,000; 3,600 / 36,000 / 360,000 events | 17-al, 17-ah; _docs/17 lines 89, 95 |
| 60 | about 1.6 us per event, about 1,300 times faster, gap of 14 ms at 1,000 orders | 17-ar; _docs/17 line 99 |
| 60 | 50 commands, median 994 ms at the one-second default | 17-as |
| 61 to 63 | current schema version 2, default STANDARD, BACKWARD_TRANSITIVE refuses a v3 without the default | 18-c, 18-g, 18-an, 18-be; Upcasters.java:21-28 |
| 66 | store applies a published reference 7 to 13 ms after the record is on the topic; retry three times | 19-x |
| 67 | 500 shipments: 6.4 s for the lookup form | 19-ai; _docs/19 line 95 |
| 68 | 500 shipments: 2.5 s for the join form; left join forwards an empty mode and link | 19-ai; ShipmentEnrichmentTopology.java:42-52 |
| 69, 70 | 30 minutes and 10 seconds as example windows; O-2003 and O-2004 | 20-ab, 20-ac |
| 70 | PaymentOverdue 10.05 to 11.07 s after arrival in 20 runs, 10 s timeout | 20-aj |
| 70 | matching 200 orders: about 2.6 s on Streams, about 3.1 s on Camel | 20-x |
| 71, 72 | 09:00 slot ends 09:15, list leaves past 09:20; seven shipments 09:01 to 09:07, count 8; Camel batch at 5 shipments or after 10 s | 21-h, 21-ao; _docs/21 lines 30, 73, 98 |
| 73 | 1,000 shipments, last list 1.3 s after the tick | 21-ao; PickupTopology.java:65-75 |
| 75 | sweeper cancels 0.6 to 1.7 s after the deadline | 22-aa; _docs/22 line 108 |
| 76 | compensatable, pivot, retriable steps | _docs/22 table; Richardson and Azure sources in bibliography.md |
| 78 | 20 s limit; 6 coordinator requests per order for the Camel saga (none for Flow); 100 orders, last PAID after 3.8 s with Camel and 2.0 s with Flow | 23-an, 23-ao; _docs/23 lines 141 to 162 |
| 79 | coordinator restart with one saga in flight lost it in 6 of 7 restarts | 23-au; CamelOrderSagaRoute.java:31-44 |
| 81 | ten notes, 15 runs, same reason every run, nine of ten as a reader would, injected note obeyed in all 15; 2.4 to 3.1 s median per note | 24-w, 24-z; _docs/24 lines 81, 83 |
| 82 | six rules, mutually exclusive; reanalyse replay about 13 s for eight notes against about half a second | 24-am; DecisionService.java:44-55 |
| 85 | gap between outbox write and consumer span under a second (median 250 to 770 ms over 100 orders) | 25-as, 25-a |
| 84 | traceparent in an outbox column and a Debezium header; without it a new trace | 25-o, 25-p |
| 86, 87 | backlog gauge from the replication slot, alert threshold 16 KiB, about 27 KB with twenty orders paused, about 630 bytes at rest | 26-y, 26-w, 26-cw |
| 87 | alert reached Alerting a median of 29 s after the orders were placed | 26-az |
| 88, 89 | order id and trace id as structured metadata, not labels; sweeper links to the order's trace | _docs/27; verify.sh 27 (31/31) |
| 91, 92 | 2N + 1 controllers, so at least three; controller votes, broker observes; StrimziPodSet | 28-o; _docs/28 lines 122, 188 |
| 93 | nodepool-broker.yaml lines 1 to 18 | examples/_k8s/kafka/nodepool-broker.yaml:1-18 |
| 94, 95 | undeclared name fails with a timeout; reconciled within a minute at the 15 s interval (6 to 23 s measured); NotSupported on a partition decrease | _docs/29 lines 58, 78 (verify.sh 29, 17/17) |
| 96 | nine topics, retention.ms 3600000 | examples/29-unidirectional-topic-operator/base/topics/kafkatopics.yaml:1-11; _docs/29 lines 43, 58 |
| 97 | 256 KiB annotation limit; CNPG install fails on its two largest CRDs | 30-c |
| 98 | conflict names the manager and the field; force takes the field | _docs/30 (verify.sh 30, 25/25) |
| 99, 100 | restricted label, Strimzi restricted provider, edp-lgtm in its own namespace | _docs/31 (verify.sh 31, 26/26) |
| 101 | reporter, operator counters, kube-state-metrics, allow list | _docs/32 (verify.sh 32, 20/20) |
| 102, 103 | proposal then approval; next free node id when two brokers become three; scale-down prevention | _docs/33 lines 58, 108 (verify.sh 33, 17/17) |
| 104 | identity policy, group sync; duplicates and loss of uncopied records | _docs/34 (verify.sh 34, 18/18) |
| 105, 106 | checksum failure stops the build; topic.prefix clash; Connect Ready 80 to 100 s after the apply | 35-h; _docs/35 line 74 |
| 108, 109 | three guards; one failing test per guard; 20 rounds without a failure is not proof | AA-07, AA-08, AA-16; _docs/aa line 113 |
| 110 | four settings differ and any other difference fails | AB-07, AB-16 |
| 111, 112 | lateness bound 10 s for all engines; 55 s record 7 s behind counted; grace 0 gives 3 against 4 | AC-04, AC-22; _docs/ac lines 28, 114, 118 |
| 113 | stream capacity default 1000; equal numbers dropped as duplicates; held events lost on restart | AE-16; _docs/ae line 66 |
| 114 | maxSurge 1, maxUnavailable 0; at-least-once; re-read bounded by the last commit | AF-05; _docs/af |
| 115 | four-step cutover; nothing missed, re-read bounded | _docs/af; verify.sh AF (30/30) |
| 116 | median 491 to 496 ms from answer to first read; connector poll.interval.ms 500 default | _docs/ag line 69 (ag claims in AG-*) |
| 117 | buildah and skopeo --preserve-digests; digest changes on a first plain copy | _docs/ah |
| 118 | decision table rows | _docs/ad pattern map |
| 119 | authors and works | bibliography.md |
| 120 | site and hub URLs | lgtm-presentation/png/qr-site.png, qr-hub.png (decoded by render-png.sh) |

## Resolved items

- 11.6 ms (slide 53): 15-d lists 11.6 ms for the 10,100-event full load; page line 108.
- 6 coordinator requests per order (slide 78): claims row 23-an (15 runs).
- 1.3 us per event (slide 53): per-size costs are 1.4, 1.2, 1.3 us (15-z); the notes now say between 1.2 and 1.4 microseconds.
- Batching (slide 21): 4.9 and 0.4 are throughput relative to the unbatched route in the same run (05-08). The bullet now says so and gives the range 2.1 to 6.0; the notes no longer claim the ratios hold on any machine, because the unbatched route's own rounds ranged 0.43 to 1.40.
- Share group timing (slide 25): 2 to 7 s against under 1 s is claims row 07-r2-07.
- Strimzi 1.3.1 (slide 3): 00-08, r04-ver-05, 28-c and examples/_k8s/versions.env.
- Sections 09, 11, 12, 13 rows now cite claims ids and the page lines instead of the page alone.
- Slide 106 (80 to 100 s, was "about a minute and a half") and slide 116 (491 to 496 ms, was "about 490") now state the recorded ranges.
- No number was cut.
