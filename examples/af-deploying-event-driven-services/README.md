# Appendix F: Deploying event-driven services on minikube

The `notification-service` and the `order-service` run on a minikube cluster and are changed in five ways while a producer Job sends uniquely numbered records at a fixed rate: a stop and restart, a rolling update of a consumer group, a rolling update of a service under HTTP requests, a breaking and a compatible schema change, and a blue-green cut-over with a rollback. Every scenario checks that nothing is lost and states what is repeated.

It is manifests and a `verify.sh`: no Java and no pom. Both applications are the existing service images, built from their own Containerfiles and loaded into the node unchanged. Order ids are `O-41000` and up.

## What runs

| Namespace | Holds |
|---|---|
| `edp-kafka` | Strimzi operator and Kafka `edp` (one controller, one broker) |
| `edp` | CloudNativePG `Cluster edp-pg`, `notify-blue` (3 replicas), `notify-green` (0 replicas, same image), `order-service` (2 replicas, NodePort 30080), and a producer Job per scenario |

There is no otel-lgtm: both applications run with `QUARKUS_OTEL_SDK_DISABLED=true`. The notification-service has no Service and no NodePort; its health endpoint is used by the readiness probe only. Nothing is forwarded; the load generator reaches `order-service` through the NodePort published on 127.0.0.1, and every other command is `kubectl exec`.

## Layout

| Path | Purpose |
|---|---|
| `kustomization.yaml` | The shared namespaces, Strimzi, Kafka and PostgreSQL bases and `apps/` |
| `apps/notify-blue.yaml` | The notification-service, strategy `Recreate`, group `notify-stop`; `verify.sh` changes strategy, group and protocol between scenarios |
| `apps/notify-green.yaml` | The same image under group `notify-green`, at zero replicas |
| `apps/order-service.yaml` | The order-service, 2 replicas, `RollingUpdate` with `maxSurge 1` and `maxUnavailable 0`, readiness probe, NodePort 30080 |
| `tools/producer-job.yaml` | A Job on `quay.io/strimzi/kafka:1.3.1-kafka-4.3.2` that sends numbered JSON keyed by order id at a fixed rate; payload kinds `v1`, `v2` (renamed field) and `expand` (both fields) |
| `tools/http.py` | HTTP load against the NodePort, a report of the results and the one-event-per-201 comparison (`python3 -I`) |
| `images.txt` | The two images `verify.sh` builds and loads |

## Run

```bash
examples/_k8s/cluster.sh up
cd examples/af-deploying-event-driven-services
./verify.sh
KEEP=1 ./verify.sh   # leave the namespaces in place
```

`verify.sh` starts from fresh namespaces every time and ends with `30/30 checks passed`.

| Setting | Default | Effect |
|---|---|---|
| `GROUP_PROTOCOL` | `consumer` | Demo code. `classic` runs the rolling update of the consumer group on the classic group protocol (the group is then not a consumer-protocol group) |
| `OFFSET_COPY` | `on` | Demo code. `off` skips `group_offsets_copy` at the cut-over and at the rollback, so green starts at the beginning of the topic and blue resumes from its own old offsets |

## What verify.sh does

Every group-level query filters by the scenario's id range, because a new consumer group replays the whole topic.

1. Preconditions: Kafka Ready, the three input topics with three partitions, the node images equal to the host builds (checks 1 to 3).
2. Stop and restart: 3,000 records at 100 per second (`O-41000` to `O-43999`); the Deployment is recreated while the producer runs (4 to 7).
3. Rolling update of the consumer group: `maxSurge 1`, `maxUnavailable 0`, group `notify-roll`, ids `O-44000` to `O-46999` (8 to 12).
4. Schema change: 20 records with `orderId` renamed to `orderRef`, then 20 in the old shape, then 20 with both fields (`O-50000` to `O-50059`; 13 to 17).
5. Blue-green: blue starts at the end of the topic with offsets copied from the previous group, serves `O-51000` to `O-54999`, is stopped, its offsets are copied to green, green is started; later `O-55000` to `O-57999` and the way back (18 to 25).
6. Rolling update of `order-service` under HTTP requests, after the consumers are scaled to zero because the order-service events carry no `eventType` and the notification-service cannot read them (26 to 30).

## Tests and checks in detail

There is no `mvn verify`: the example has no Java. Checks 1 to 3 are preconditions and stop the run when they fail.

| Check | Description |
|---|---|
| 1 | Kafka `edp` is Ready (precondition) |
| 2 | the three input topics have three partitions (precondition) |
| 3 | the node runs the two service images, equal to the host builds (precondition) |
| 4 | the Recreate rollout leaves a period of at least 3 s with no row written, 4.8 to 9.3 s over eight clean runs |
| 5 | the group has no lag once it has caught up |
| 6 | every one of the 3,000 records has a row |
| 7 | the records delivered again (attempts above 1) stay within the bound from the old replicas' last commit interval (see below), 0 to 460 over eight clean runs (bound up to 1,469) |
| 8 | `notify-roll` is listed as a consumer-protocol group |
| 9 | the rolling update replaced every pod |
| 10 | rows of the range were written by the old pods and by the new ones |
| 11 | every one of the 3,000 records has a row |
| 12 | the records delivered again stay within the same bound, 0 to 384 over eight clean runs on the consumer protocol, 99 in a classic run (bounds 677 to 738) |
| 13 | no record with the renamed field produced a row, 0 of 20 in each run |
| 14 | the group has no lag: it committed past the records it could not store |
| 15 | the consumer log holds `Failed delivery` |
| 16 | the next 20 records in the old shape are all stored |
| 17 | the 20 records carrying both fields are all stored, 20 of 20 in each run |
| 18 | the blue group is Empty within 60 s of the scale-down |
| 19 | the green group holds blue's committed offsets, partition by partition |
| 20 | every one of the 4,000 records has a row in blue or green |
| 21 | the ids processed by both groups stay within what blue wrote in its last commit interval, 0 to 460 over eight clean runs (bound 742 to 1,150) |
| 22 | the green group is Empty within 60 s of the scale-down |
| 23 | the blue group holds green's committed offsets, partition by partition |
| 24 | every one of the 3,000 records of the rollback range has a row in blue or green |
| 25 | the ids processed by both groups stay within what green wrote in its last commit interval, 0 to 380 over eight clean runs (bound 690 to 904) |
| 26 | without a rollout every request is answered 201, 1,576 to 1,584 requests in each of the eight clean runs |
| 27 | the rollout ran while requests were flowing |
| 28 | no request was answered with a 5xx status |
| 29 | failed requests stay below 2 percent, 0.2 to 0.5 percent over seven clean runs |
| 30 | a retry of an accepted order is answered 409; every failed request is retried, each retry is answered 201 or 409, and then each order has exactly one event |

Notes on how the checks read their evidence:

- The notification table holds one row per group, topic, partition and offset with an `attempts` counter. A missing id is a lost record; `attempts` above 1 is a record delivered again to the same group; an id with rows in two groups is a record read by both.
- Redelivery and re-read counts depend on timing, so the checks assert a bound computed from the run and print both numbers. A repeat is a record that a replica processed after its last commit and then gave up the partition. The Kafka client commits every 5 s (Camel's `autoCommitIntervalMs`, left at its default) inside `poll()`, so the bound is, per partition, the writes a replica made in the 7 s before its last write there (the interval plus 2 s for the batch in hand), summed over the replicas that gave the partition up. For the cut-over and the rollback the replicas are the source group's, and the bound is the same sum for the group that stopped. `verify.sh` installs a trigger that copies every insert and update of the notification table into `audit.write_log`, because a repeat overwrites the row's replica and time; the trigger is test instrumentation and the images stay unchanged. The checks never assert that one protocol redelivers less than the other. The bound is loose, because it also counts the writes new replicas make while draining the backlog before they hand partitions over; halving the window fails checks 21 and 25, while checks 7 and 12 still passed at a 1 s window, so no run has yet shown 7 or 12 failing. Where a replica holds a partition in more than one stretch the sum can undercount, which can only cause a false failure, never a false pass.
- Offsets are copied with `group_offsets_copy` from `examples/_k8s/lib.sh`: `kafka-consumer-groups.sh --reset-offsets --to-current --all-topics --dry-run --export` on the source group, then `--from-file ... --execute` on the target. Both groups must have no active member. The target may never have existed.
- Checks 19 and 23 export both groups' committed offsets and compare them as sorted `topic,partition,offset` lines before the target starts.
- `tools/http.py report` splits the failed requests of check 29 into `reset`, `timeout` and `refused` by exception name; no 5xx status was returned in any run.

## Experiments

Each run starts from fresh namespaces. The failing sets are frozen after the live window.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `GROUP_PROTOCOL=classic ./verify.sh` (demo code) | The rolling update runs on the classic group protocol | Check 8 (29/30) | The group is a classic-protocol group, so only check 8 fails. Window run: 0 records delivered again in the rolling update, 420 in the stop and restart, longest period with no row 8.7 s. Repair-round run: 99 and 10, 4.4 s. In both, every one of the 3,000 ids has a row |
| `OFFSET_COPY=off ./verify.sh` (demo code) | Green starts at the beginning of the topic and blue resumes from its own old offsets | Checks 19, 21, 23 and 25 (26/30) | Green and blue read from the start of the topic or from old offsets: 1,580 ids read by both groups at the cut-over and 1,050 at the rollback (window run and repair-round run alike), against bounds of 778 and 690 and at most 450 with the copy; every id still has a row |

## Measurements

Redelivery depends on timing and can be zero. In eight clean runs the rolling update of a three-pod group at 100 records per second on the consumer protocol delivered 0 to 384 records again (47, 68, 372, 19, 384, 0, 160 and 0), and the stop and restart delivered 0 to 460 again over eight runs; the longest period with no row written in the stop and restart was 4.8 to 9.3 s. The classic-protocol runs delivered 0 and 99 again in the rolling update. Over eight runs the cut-over overlap was 0 to 460 ids and the rollback overlap 0 to 380; a clean shutdown can leave nothing to re-read. No run lost a record. The cut-over took 12 to 20 s from the blue scale-down to green live (8 s without the offset copy).

Requests in flight when a pod of `order-service` is removed can fail at the connection level. In seven clean runs 5, 8, 5, 5, 8, 11 and 6 of about 2,380 to 2,391 requests failed and none was answered with a 5xx status; in the four runs that split them, the failures were 1 reset and 4 timeouts, 4 resets and 4 timeouts, 7 resets and 4 timeouts, and 2 resets and 4 timeouts. The control run without a rollout had none. A retry of an accepted order was answered 409 in each run that checked it. In one run 2 of the 11 failed requests had been accepted before the connection failed, and their events were on the topic; in the first run that retried every failed request, all 6 retries were answered 201.
