# Deck evidence (r15)

Internal map from each slide to the claims rows, pages and example files behind it. Internal files may name chapters; the deck itself never does.

## Sections owned by S1

| slide title | claim | claims id or file:line |
|---|---|---|
| Who this is for, and what it runs on | Quarkus 3.40.1.2 LTS, Camel Quarkus 3.40.0, Camel 4.22.2 | 00-07 |
| Who this is for, and what it runs on | Kafka 4.3.2 KRaft, newest the latest Strimzi supports; Strimzi 1.3.1 | 00-08; examples/_k8s STRIMZI_VERSION=1.3.1 |
| Who this is for, and what it runs on | PostgreSQL 18.6 CloudNativePG; Debezium 3.7.1.Final; LGTM 0.41.0; Apicurio 3.3.4 | 00-09, 00-10, 00-11; Apicurio and LGTM versions from the chapter 00 table (00-04, 00-52) |
| One shop, five services | five services, share no tables, two deliberate fetches | 00-56 |
| One shop, five services | nine events, edp.<domain>.<event> | 00-62 |
| The order and shipment lifecycles | order and shipment states, FASTLINE and AIRCO, failure paths | _docs/00 lifecycle section; domain-model (OrderStatus.canMoveTo) |
| One stack for every example | ports 9092, kafka:9094, 5432, 8081, 8083, 3000, 19090; Cockpit on 9090; one stack at a time | 00-15 to 00-19, 00-21 |
| One endpoint, three backends | OTLP http/protobuf to one endpoint; Tempo, Prometheus, Loki; no login | 00-11; _docs/00 "Where the signals land" |
| One command, two storage outcomes | Event, Command, Document Message definitions; row overwrites, order_event appends | 01-05; _docs/01 |
| Event notification (3 slides) | breakOnFirstError, two redeliveries one second apart, publish after commit, one request per event | 02-40; OrderPlacedNotificationRoute; _docs/02 (claims 02-09, 02-14 cited in 03-02) |
| State transfer and claim check (3 slides) | max.request.size 1048576; event under 1 KB for 100 KB attachment; copy answers with source down; status guard | 03-22, 03-28, 03-39, 03-52, 03-16 |
| Keys and partitions | order per partition, murmur2 key hash, event-type key regresses DELIVERED to SHIPPED, carrier uses two of three | 04-02, 04-16, 04-17; Kafka docs source row (murmur2); _docs/04 |
| Resequencer (3 slides) | sequence number, held in table under row lock, run 3 SHIPPED and run 4 DELIVERED | 04-23, 04-24, 04-40 |
| Batching publishers | linger 5 ms default (KIP-1030), batch.size 16384, 20,000 orders, ratios 4.9 and 0.4, lz4 0.38 and zstd 0.19 of none | 05-02, 05-08; _docs/05 |
| Consumer concurrency | 0.33 and 0.12 drain ratios, nine partitions, 2,400 to 60 fetches, 4 or 5 partitions given up, CooperativeSticky 4, max.poll.interval 300000 | 06-04, 06-06, 06-07; _docs/06 |
| Share groups | lock 30 s, delivery limit 5, no ordering guarantee, accept/release/reject, share acquire mode; 2 to 7 s against under 1 s | 07-02, 07-04, 07-05, 07-09; "2 to 7 s" from _docs/07 When not to use it (claims id not individually looked up) |
| Transactional outbox (4 slides) | 201 in about 10 ms with Kafka stopped, commit-first 500 after 60 s; row id header; at-least-once; rollback leaves no outbox row | 08-10; _docs/08 |
| Transaction log tailing | slot, publication, envelope ops, heartbeat releases WAL, tombstone once | _docs/09 (claims 09-xx not individually looked up) |
| Offsets and replication slots | greater of offset and slot; alter forward skips, backward does not rewind; lost slot RUNNING with no task, about 30 minutes; 60 s flush default | 10-09, 10-11, 10-12, 10-14 |
| Idempotent consumer and inbox (4 slides) | ON CONFLICT DO NOTHING inbox, commit before acknowledge, 45 s default session timeout and 10 s set, key store table | _docs/11; ReservationService.java 19-34 (claims 11-xx not individually looked up) |
| Exactly-once read-process-write | fencing by epoch, read_committed stops at LSO, aborted records stay, KafkaTransactions.withTransactionAndAck, Kafka to Kafka scope | _docs/12; PaymentProcessor.java 30-41 (claims 12-xx not individually looked up) |
| Failure classes and the dead letter topic | six records, SmallRye fail default and Camel default error handler, 2000 ms lock timeout, useOriginalBody, Camel DLQ without reason headers | _docs/13; DeadLetterRoute.java 27-45 (claims 13-xx not individually looked up) |

## Sections owned by S2

| slide title | claim | claims id or file:line |
|---|---|---|
| One command, one transaction (notes) | no order table; only OrderPlaced/OrderCancelled via outbox | 14-b, 14-d |
| The append is the concurrency check | unique (stream_id, version) settles race; ConcurrencyConflict | EventStore.java:27-48; 14-a |
| A lost race (bullets, notes) | @Retry maxRetries 3; If-Match gives 412; 409 refusal | 14-v, 14-w; _docs/14 |
| Snapshot: replay only the tail | 100,000 events; 1.6/11.6/127.7 ms full; 0.50/0.63/0.95 ms snapshot; 99-event tail; ~1.3 us/event | 15-d, 15-y, 15-z, 15-ba, 15-v |
| Snapshot plus tail (notes) | N=10 replays at most 9, 100 snapshots per 1,000; N=1,000 | _docs/15 policy arithmetic (15-be) |
| Commands in, a view out (notes) | burst 250 orders (1,250 records) p95 lag 318 to 334 ms; 12,881 ms with 50 ms delay | 16-ap, 16-ag |
| Six topics (notes) | shipment_rank guard in SQL | _docs/16 |
| What each answer costs | 15/77/586 ms vs 0.78/0.61/0.46 ms; 27 rows; rebuild 1.8 s at 100,000; ~1,300x; 994 ms median; 3,600/36,000/360,000 events; 1.6 us/event | 17-ah, 17-ai, 17-al, 17-ar, 17-as |
| Upcaster slides | CURRENT=2, STANDARD default, BACKWARD_TRANSITIVE refusal | Upcasters.java:21-28; 18 verify (18-az) |
| Lookup against stream-table join (notes) | 7 to 13 ms apply; retry 3 times | 19-x |
| Enrich by calling the owner / local copy | 500 shipments: 6.4 s lookup, 2.5 s join | 19-ai |
| Two clocks (notes) | O-2003/O-2004; 10.05 to 11.07 s in 20 runs; 200 orders 2.6 s vs 3.1 s | 20-ab, 20-ac, 20-aj, 20-x |
| When a window closes (notes) | 09:00 slot, grace to 09:20, count 8; 5 shipments or 10 s | 21-h, 21-x, 21-ao |
| Slots, grace (notes) | 1,000 shipments, 1.3 s tick to last list | 21-ao |
| Choreographed saga (notes) | sweeper cancels 0.6 to 1.7 s after deadline | 22-aa |
| Choreography, Camel Saga and Flow (notes) | 100 orders last PAID 3.8 s vs 2.0 s; 6 coordinator requests per order (table in _docs/23) | 23-ao |
| Opening a saga with Camel (notes) | coordinator restart lost saga in 6 of 7 | 23-au |
| Analyse, record, then decide (notes) | ten notes, 15 runs, nine of ten; injected note obeyed 15/15; 2.4 to 3.1 s | 24-w, 24-z |
| Rules decide from the record (notes) | reanalyse replay ~13 s for eight notes vs half a second | 24-am |
Unbacked by an id I could match: 11.6 ms row (15 table row; present in _docs/15), 6 requests per order (_docs/23 table only).

## Sections owned by S3

| slide title | claim | source |
|---|---|---|
| Trace context in the message / One order, one trace | traceparent carried by outbox row column, Debezium header; without it new trace; gap under a second; parent vs link | claims 25-a..25-e; _docs/25 (verify.sh 25 22/22) |
| Measure the flow / Where each signal lives | slot-lag backlog gauge; 16 KiB threshold; alert Alerting median 29 s; ALERT_ON=age does not fire during pause; event age vs record age on redrive | _docs/26 (verify.sh 26 37/37) |
| Correlation / log-trace round trip | order id span attribute, trace id on log lines, structured metadata not label, sweeper link | _docs/27 (verify.sh 27 31/31) |
| KRaft roles / node pool | observer vs voter, 2N+1 controllers, pod keeps node id and volume, StrimziPodSet | _docs/28 (Apache Kafka KRaft docs cited there) |
| nodepool code | file content examples/_k8s/kafka/nodepool-broker.yaml:1-18 | file |
| Topics as resources / one direction | UNKNOWN_TOPIC timeout, revert within minute at 15 s interval, NotSupported on partition raise, recreate, finalizer, unmanaged | _docs/29 (verify.sh 29 17/17) |
| topics code | examples/29-unidirectional-topic-operator/base/topics/kafkatopics.yaml:1-11; nine topics, 1 h retention (3600000) | file, _docs/29 |
| Server-side apply | 256 KiB annotation limit, CNPG CRD failure, conflict naming manager, force, diff --server-side | _docs/30 (verify.sh 30 25/25) |
| Admission | restricted label, Strimzi restricted provider, edp-lgtm exception, dry-run label | _docs/31 (verify.sh 31 26/26) |
| Metrics path | reporter, operator counters, kube-state-metrics, allowList without roll, NotSupported series | _docs/32 (verify.sh 32 20/20) |
| Scaling and moving partitions | proposal then approval, next free node id, ScaleDownPreventionCheck, remove-brokers template | _docs/33 (verify.sh 33 17/17) |
| Two clusters | identity policy, group sync, duplicates expected, loss of uncopied records | _docs/34 (verify.sh 34 18/18) |
| Connect built in cluster | build pod, checksum failure, topic.prefix clash, restricted label refuses build pod, ready ~80-100 s ("about a minute and a half") | _docs/35 |
| Testing / bite cycle | three guards, mutation testing (DeMillo, Lipton, Sayward), drain vs first match, 20 rounds | _docs/aa |
| Same config two targets | four differing settings, capture requires all keys | _docs/ab |
| Querying streams | 10 s lateness bound all engines; 7 s late record counted; grace 0 gives 3 vs 4 | _docs/ac |
| Resequencing | default capacity 1000; duplicate numbers dropped; held events lost on restart | _docs/ae (verify.sh 23/23) |
| Rolling update / blue-green | maxSurge 1, maxUnavailable 0; four-step cutover; overlap bounded | _docs/af (verify.sh 30/30) |
| Write-forward cache | median ~490 ms (491 to 496 measured), poll.interval.ms 500 default | _docs/ag |
| Private registry | buildah + skopeo --preserve-digests; digest change on first copy | _docs/ah (described neutrally) |
| Credits | authors and titles from bibliography.md | bibliography.md |

