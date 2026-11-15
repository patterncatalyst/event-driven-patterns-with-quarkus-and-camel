# 28 Strimzi and KRaft

Kafka on Kubernetes as a `Kafka` resource and two `KafkaNodePool` resources, one for the controller and one for the broker, run by the Strimzi operator in KRaft mode. The services of examples 02, 03 and 04 run against it one set after another, with no change to their code, pom or Containerfile.

There is no Java here and no pom: the example is manifests, `verify.sh` and two small Python helpers. Images come from the Containerfiles of examples 02, 03 and 04 and are loaded into the minikube node.

## Layout

| Path | Purpose |
|---|---|
| `kustomization.yaml` | Namespaces, Strimzi operator, Kafka with two node pools, PostgreSQL (CloudNativePG) and LGTM, all from `examples/_k8s` |
| `images.txt` | The six images to build and load: `<example> <service>` per line |
| `dual/` | Demo code: one dual-role node instead of two pools (`NODE_POOLS=dual`) |
| `compose-address/` | Demo code: the chapter 02 set with the compose Kafka address on notification-service (`KAFKA_BROKERS=compose`) |
| `quorum.py` | Reads `kafka-metadata-quorum.sh` output: leader, voters, observers, per-node status |
| `envcompare.py` | Compares a compose service's environment names with a Deployment's |
| `verify.sh` | Applies the platform, runs the three app sets, replaces the broker pod |

The CloudNativePG operator is installed by `examples/_k8s/cluster.sh up` and stays in the profile between runs. The application sets are the `examples/_k8s/apps/02`, `03` and `04` directories; they share Service names and NodePorts, so `verify.sh` deploys one, checks it, deletes it and deploys the next. Each set has its own database in the same PostgreSQL cluster: `edp`, `edp03` and `edp04`.

## Run

```bash
examples/_k8s/cluster.sh up      # once per profile: minikube, ports, CloudNativePG
cd examples/28-strimzi-and-kraft
./verify.sh
KEEP=1 ./verify.sh               # leave the namespaces in place
kubectl --context edp delete ns edp edp-kafka edp-lgtm
examples/_k8s/cluster.sh down    # delete the profile
```

`verify.sh` deletes and recreates the three namespaces first, so a run never reuses an earlier cluster. It ends with `TBD checks passed`. The first run builds six images, which takes a few minutes.

## What verify.sh checks

1. Preconditions: the NodePorts are bound to loopback only, the CloudNativePG operator and the Strimzi operator are Available.
2. Kafka `edp` is Ready for its current generation. The controller pool holds the controller role and the broker pool the broker role, each with one node id and the ids differ. Both pods are owned by a `StrimziPodSet`.
3. The quorum from `kafka-metadata-quorum.sh`: the controller is `LeaderId` and the only voter, the broker is the only observer; the replication view says Leader and Observer. The Kafka version in `status` equals the spec and the compose broker's image tag.
4. The CloudNativePG cluster is Ready and the three databases exist, owned by `edp`.
5. For each of the three sets: the image in the node has the ID `docker build` produced; the Deployments have the same environment variable names as `compose.yaml`; the set rolls out. Then the effect of the set:
   - 02: notification-service points at the bootstrap Service; order O-2801 is accepted, its notification appears with customer CU-77 and status PLACED, and one trace in Tempo holds `POST /orders` and notification-service.
   - 03: order O-2802 is accepted and its copy carries every field; with order-service scaled to zero (no pod), the copy is still answered.
   - 04: the topics Job completes, `edp.shipping.shipments` has 3 partitions, the two order replicas share them, and the events of O-2803 are applied in lifecycle order and end DELIVERED.
6. The broker pod is deleted: the new pod has a new UID and is Ready, mounts the same volume claim, and `edp.orders.placed` has the same end offsets and still holds O-2801.

Order ids are `O-2801` to `O-2803`. Node ids are read from `status.nodeIds` in the same run and never assumed.

## Tests and checks in detail

The example has no unit tests. `verify.sh` reads effects: Kubernetes resource status, Kafka's own tool inside the broker pod, the databases from `pg_database`, the image IDs through `crictl` in the node, and the services through the published NodePorts. The environment comparison uses `docker compose config` for the compose side, so it reads the files of examples 02, 03 and 04 as they are.

## Experiments

Each run starts from fresh namespaces. Both switches are demo code; the failing sets are predicted and the live windows record the measured result.

| Switch | Effect | Failing checks (predicted) | Measured result |
|---|---|---|---|
| `KAFKA_BROKERS=compose ./verify.sh` | notification-service of the chapter 02 set keeps `kafka:9094`: it cannot resolve the name, Camel fails to start, the pod never becomes ready | the set does not roll out, the address check, the notification appears, the notification content, the trace | TBD |
| `NODE_POOLS=dual ./verify.sh` | One node holds both roles | the role check, one id each, the pod owners, the quorum and the replication view | TBD |

With `KAFKA_BROKERS=compose`, the other checks pass: order-service has the right address, so the order is accepted and `edp.orders.placed` holds it, and the sets for chapters 03 and 04 run with the right addresses.

## Timings

Bring-up on a fresh profile, measured in the spike: profile creation, CloudNativePG operator, Strimzi operator, Kafka Ready and CloudNativePG cluster Ready are recorded in `examples/_k8s/README.md` and the iteration log. A replaced broker pod is Ready again in `TBD(spike: 23 s)`.
