# 33 Cruise Control: rebalancing

A Kafka cluster of two brokers with Cruise Control is filled with data, its node pool is grown, and the example checks what Kafka and Strimzi do about the empty broker: a new broker holds nothing, a `KafkaRebalance` proposal moves nothing until it is approved, an approved rebalance spreads the replicas, a scale-down of a broker with replicas is refused, and with `autoRebalance` the operator rebalances a pool that grows and drains a broker before it removes it.

Manifests only: a kustomize overlay on `examples/_k8s`, a load Job and a small Python helper. No Java, no pom, no application and no PostgreSQL. Namespaces carry no Pod Security labels in this chapter.

## What the overlay changes

| File | Purpose |
|---|---|
| `kustomization.yaml` | The namespaces, Strimzi and Kafka bases, `topics/`, and the two patches |
| `kafka-patch.yaml` | Cruise Control with a 60 s sampling interval and 60 s windows, `default.replication.factor: 2`, auto-creation off, the Topic Operator on |
| `broker-pool-patch.yaml` | The broker pool at two nodes, 768Mi with a 384m heap |
| `topics/` | Four catalog topics (`edp.orders.placed`, `edp.inventory.reserved`, `edp.payments.processed`, `edp.shipping.scheduled`), 6 partitions and 2 replicas each |
| `load/` | A Job that writes 100000 records of 1 KiB to each topic with `kafka-producer-perf-test.sh`; a producer tool exec'd into a 768Mi broker gets the broker killed |
| `rebalance/add-brokers.yaml` | The manual `KafkaRebalance`; `@ID@` is replaced by the id read from the pool status |
| `autorebalance/` | The two template `KafkaRebalance` resources and the merge patch that adds the `autoRebalance` block |
| `replica_placement.py` | Reads `kafka-topics.sh --describe` and prints replicas per broker, partitions, replication factor and in-sync state (`python3 -I`) |
| `images.txt` | Empty: no image is built |

The new broker's id is read from `status.nodeIds` of the pool and never written down. Node ids are shared by all pools, so the first added broker is not the third number of a sequence. In-run changes to the cluster are `kubectl patch --type merge`, not repeated server-side applies of a whole `Kafka` manifest.

## Run

```bash
../_k8s/cluster.sh up
./verify.sh
KEEP=1 ./verify.sh
```

`verify.sh` deletes and recreates the namespaces, applies the overlay with `--server-side` and ends with `N/N checks passed`. Cruise Control is reached through the operator only; nothing is forwarded.

## Demo switch

Demo code, labelled in `verify.sh` and the chapter.

| Switch | Effect | Predicted failing set |
|---|---|---|
| `AUTO_REBALANCE=off ./verify.sh` | Never adds the `autoRebalance` block or the templates, so the pool that grows to four gets no rebalance | Checks 14 and 15 (the operator's rebalance and the replicas on the added broker). The scale-down is not refused here, because the added broker is empty. To be frozen in the live window |

## Tests and checks in detail

1. Precondition: Cruise Control exists in the `Kafka` resource and its Deployment is Available.
2. Precondition: the broker pool has two nodes and no `autoRebalance` is configured.
3. The four catalog topics are `KafkaTopic` resources with two replicas and are Ready.
4. The load Job completes and reports 100000 records for each of the four topics.
5. Every replica sits on the two original brokers (the ids read from the pool).
6. After the pool grows to three, with the Kafka resource and Cruise Control ready again, the new broker holds no replica.
7. An `add-brokers` `KafkaRebalance` for the new broker reaches `ProposalReady` within 240 s with `numReplicaMovements` above 0; a `NotReady` state is refreshed.
8. The replica placement is unchanged for 20 s while the proposal waits.
9. After the approve annotation the rebalance reaches `Ready` within 240 s.
10. The new broker holds replicas and every original broker holds fewer than before.
11. Every partition has two replicas, all in sync.
12. Without `autoRebalance`, scaling the pool down is refused: the `Kafka` status shows `ScaleDownPreventionCheck`, the pool returns to three nodes and the new broker still holds replicas.
13. The `Kafka` resource carries the `autoRebalance` modes `add-brokers remove-brokers` (none under `AUTO_REBALANCE=off`) and is Ready.
14. The pool grown to four is rebalanced by the operator: `edp-auto-rebalancing-add-brokers` appears with no hand-written resource and `status.autoRebalance.state` returns to `Idle` within 420 s.
15. The added broker holds replicas and every partition is fully replicated.
16. The pool shrinks back to three nodes within 300 s.
17. Every replica is on a remaining broker, the pool has three nodes and all replicas are in sync.

## Measurements

Runtime numbers are filled from the live window; `verify.sh` prints each on a `measured:` line.

- Pool grown to three and Ready: TBD. Proposal ready: TBD(spike: 2 s after a refresh). Approved to Ready: TBD(spike: 157 s). Operator rebalance after a scale to four: TBD(spike: 3 min 24 s from creation of the rebalance's pod to Ready). Scale-down with `remove-brokers`: TBD(spike: 66 s). Whole run: TBD.
- Deleting Kafka, Cruise Control and the namespaces: TBD(spike: 80 s).
