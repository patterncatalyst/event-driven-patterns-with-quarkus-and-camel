# Appendix B: Compose to Kubernetes

The same Kafka and PostgreSQL, described twice: as the compose services of `examples/_infra` and as the `Kafka`, `KafkaNodePool` and CloudNativePG `Cluster` resources of `examples/_k8s`. Each target starts fresh and is read through its own running system, `kafka-configs.sh --describe --all` for the broker and `current_setting()` for PostgreSQL. A third step compares the two captures against a list of expected differences.

There is no Java and no image. The files of `_infra` and `_k8s` are used as they are.

## Layout

| Path | Purpose |
|---|---|
| `compose.yaml` | Includes `compose-kafka.yaml` and `compose-postgres.yaml` from `_infra`, nothing else |
| `drift.yaml` | Demo code: starts the compose broker with `num.partitions=6` (`DRIFT=partitions`) |
| `kustomization.yaml` | Namespaces, Strimzi, Kafka with two node pools and the CloudNativePG Cluster, from `_k8s`; no LGTM |
| `configs.py` | The key lists, the Kafka defaults, the allowlist and every capture or comparison step (`python3 -I`) |
| `verify.sh` | `compose`, `k8s` and `compare` |
| `target/captures/` | The captures of the last runs (gitignored) |

## Run

```bash
./verify.sh compose      # fresh containers, then read, then down -v
./verify.sh k8s          # fresh namespaces on the edp profile (created if missing), then read, then delete the namespaces
./verify.sh compare      # reads target/captures, starts nothing
DRIFT=partitions ./verify.sh compose && ./verify.sh compare
KEEP=1 ./verify.sh compose
```

The compose half refuses to run while another stack is up (`scripts/stack-guard.sh`), and the Kubernetes half refuses while a compose project is up. Run the halves one after the other. `compare` fails at its first two checks if a capture is missing or incomplete.

## What verify.sh checks

Compose and Kubernetes captures read 13 broker settings (`num.partitions`, `default.replication.factor`, `min.insync.replicas`, `offsets.topic.replication.factor`, `auto.create.topics.enable`, `log.retention.hours`, `group.coordinator.rebalance.protocols`, the three `share.coordinator.state.topic.*` and the three `transaction.state.log.*` settings listed in `configs.py`) and 5 PostgreSQL settings (`server_version`, `wal_level`, `max_replication_slots`, `max_wal_senders`, `max_connections`). Broker ids are read, never assumed: the compose file says 1, the `KafkaNodePool` says 0. The Kafka defaults are those of the Kafka 4.3 broker configuration page; the PostgreSQL defaults are CloudNativePG's global defaults (`wal_level`, `max_replication_slots`) and PostgreSQL's (`max_wal_senders`, `max_connections`, from the PostgreSQL 18 documentation pages for connections and replication).

`verify.sh compose` (9 checks):

1. Precondition: Kafka answers and its capture holds all 13 settings.
2. Precondition: PostgreSQL answers and its capture holds all 5.
3. A node id that is not a broker gives an empty capture, and the completeness check rejects it (negative witness).
4. The running broker's image is the tag in `compose-kafka.yaml`.
5. Every setting `compose-kafka.yaml` sets is what the broker reports (`KAFKA_X_Y` is `x.y`).
6. Every setting it leaves unset is the Kafka default.
7. Every parameter the `postgres` command line of `start.sh` sets is what PostgreSQL reports.
8. The parameter it leaves unset is the PostgreSQL default.
9. PostgreSQL's major.minor is the image tag's.

`verify.sh k8s` (9 checks): 1 and 2 as above (the broker pod and node id come from `status.nodeIds`); 3 the same witness with the controller's node id; 4 the Kafka resource reports the version it was asked for; 5 and 6 as above against `spec.kafka.config`; 7 the Cluster manifest (`kubectl kustomize ../_k8s/postgres`, rendered as written, because the operator adds `wal_level` and `max_replication_slots` to the live resource) sets none of the four PostgreSQL parameters compared; 8 each reports the CloudNativePG or PostgreSQL default; 9 major.minor equals the Cluster image tag's.

`verify.sh compare` (23 checks): 1 and 2 both captures exist and are complete (an empty capture stops the run); 3 to 17 each of 15 settings is equal on both targets (ten broker keys, `max_wal_senders`, `max_connections`, `wal_level`, `server_version` on major.minor, the Kafka version); 18 to 21 each of the four allowed differences has exactly its expected pair of values; 22 none of the 18 compared keys differs beyond the four allowed (it follows from 3 to 21 and guards the key lists); 23 `_docs/ab-compose-to-kubernetes.md` names every allowed key. The checks print `9/9`, `9/9` and `23/23 checks passed`.

## Allowed differences

| Setting | Compose | Kubernetes | Reason |
|---|---|---|---|
| `group.coordinator.rebalance.protocols` | `classic,consumer,streams,share` | `classic,consumer,streams` | The compose file lists `share`; the Kafka resource leaves Kafka's default |
| `share.coordinator.state.topic.min.isr` | `1` | `2` | Overridden in compose, Kafka default on Kubernetes |
| `share.coordinator.state.topic.replication.factor` | `1` | `3` | The same |
| `max_replication_slots` | `10` | `32` | The start script passes 10; CloudNativePG's default is 32 |

## Tests and checks in detail

`configs.py` filters `kafka-configs.sh` output to the key list (`kafka`), prints the PostgreSQL query (`sql`), and answers the checks: `complete` (exactly the expected keys, none empty), `explicit` (what a source sets or leaves unset against the capture), `version`, `equal`, `differs`, `others` and `documented`. `server_version` is compared on major.minor because the build suffix belongs to the distribution. All steps read files, so they can be tried on a hand-made capture without a stack.

## Experiments

Each run starts from fresh containers or namespaces. `DRIFT=partitions` is demo code.

| Switch | Effect | Failing checks (predicted) | Measured result |
|---|---|---|---|
| `DRIFT=partitions ./verify.sh compose`, then `./verify.sh compare` | The compose broker reports `num.partitions=6` | compose: 5 (8/9). compare: 7 (`num.partitions` equal) and 22 (no other difference) (21/23) | compose 8/9 (check 5 fails: the broker reports 6 against 3). compare 21/23 (checks 7 and 22 fail), as predicted. Without the switch every run was 9/9 and 23/23 |

## Timings

Compose half: 70 to 71 s from `down -v` to `down -v`, images already present. Kubernetes half: 2 min to 2 min 15 s on a warm profile, of which the captures take about a minute and the deletion of the namespaces 18 to 19 s. `cluster.sh up` on a fresh profile took 41 to 44 s with the images and manifests cached.
