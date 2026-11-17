---
title: "Appendix H: OpenShift Local"
order: 43
marker: "H"
label: "Appendix H"
part: appendices
permalink: /docs/ah-openshift-local/
description: "The outbox order-service, Strimzi and CloudNativePG on a single-node OpenShift cluster on the workstation. The code is the code from Docker Compose and minikube; what changes is how operators arrive, how the image gets in and who the pod runs as."
duration: "45 minutes"
---

The outbox `order-service` has no Java, Containerfile or connector that is specific to a platform. The same image, the same Debezium connector and the same Kafka and PostgreSQL run on Docker Compose and on minikube. OpenShift Local (CRC) is a third place to run them: a single-node OpenShift cluster in a virtual machine on the workstation, a stand-in for an on-premises distribution. Nothing in the service depends on it.

What differs is everything around the code. Operators arrive from catalogs instead of release files, the image is copied into a registry that the cluster already runs, and the platform assigns each pod its user.

{% include excalidraw.html file="ah-crc-layout" alt="Two Subscriptions in the namespace edp-operators install Strimzi and CloudNativePG and watch the namespace edp, which holds Kafka, a PostgreSQL Cluster, Connect with an ImageStream, a connector and order-service behind a Route that curl reaches over verified HTTPS" caption="Figure H.1: Operators in one namespace, the workload in another" %}

## What changes from minikube

| | minikube | OpenShift Local |
|---|---|---|
| Operators | Release YAML applied with the server-side apply | A `Subscription` per operator, pinned and approved by hand |
| Application image | Built on the host and loaded into the node | Built on the host and copied into the cluster's registry |
| Connect image | Built by the operator into a registry started for the purpose | Built by the operator into an `ImageStream` |
| Pod user | The image's own user | A user from the project's range, assigned at admission |
| Exposure | NodePort published at `minikube start` | An edge-TLS `Route` with a host name the workstation resolves |
| Telemetry | LGTM in the cluster | None: the OpenTelemetry SDK is switched off |

The manifests for Kafka and the PostgreSQL `Cluster` are the ones minikube uses, with the namespace set by kustomize. Every command names its namespace with `-n`, and every apply is `oc apply --server-side`. There is no Helm and no port forwarding of any kind. The example needs a cluster with no other Strimzi or CloudNativePG install: the operators' CRDs are cluster-wide, so `install.sh` refuses when any exist and `teardown.sh` removes them.

## Starting CRC

The instance is started by its owner with `crc start`. Setting it up edits the host's DNS, so the scripts do not do it: `crc-check.sh` stops them unless CRC is running and the current context is `crc-admin`, and `scripts/stack-guard.sh --crc` refuses while a Compose project or a minikube node is running. One stack at a time holds here as everywhere.

```bash
crc status                    # OpenShift: Running
oc config current-context     # crc-admin
```

CRC is one node that is both control plane and worker, and the cluster is ephemeral: the documentation says it is not meant for production and has no upgrade path. Its pull secret comes from a Red Hat account that costs nothing. Network mode `user` is the default. Under it the CRC daemon adds each Route's host name to the hosts file while the Route exists and removes it afterwards, so `curl` reaches a Route by name. The scripts change nothing on the host, and the daemon's entry goes when the Route goes.

Trust is the other piece. The default ingress certificate is signed by a CA that is public, in the ConfigMap `default-ingress-cert` in `openshift-config-managed`. The scripts read that and never touch the secret `router-certs-default`, which holds the private key. Every TLS call, `curl` and `skopeo` alike, is verified against that CA. No script turns verification off.

## Operators at pinned versions

OpenShift installs operators through the Operator Lifecycle Manager. A `Subscription` names a package, a channel and a catalog; `startingCSV` fixes the first version, and `installPlanApproval: Manual` makes the manager propose an install plan and wait. With automatic approval the Subscription would install each newer version in the channel as it appears. With manual approval, the only plan the scripts approve is the one that installs the named CSV, and every later upgrade stays a pending proposal.

```yaml
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata: {name: strimzi-kafka-operator}
spec:
  channel: strimzi-1.x
  name: strimzi-kafka-operator
  source: community-operators
  sourceNamespace: openshift-marketplace
  installPlanApproval: Manual
  startingCSV: strimzi-cluster-operator.v1.3.1
```

```bash
PLAN=$(oc get installplan -n edp-operators -o json \
  | jq -r '.items[] | select(.spec.clusterServiceVersionNames | index("strimzi-cluster-operator.v1.3.1")) | .metadata.name')
oc patch installplan "$PLAN" -n edp-operators --type merge -p '{"spec":{"approved":true}}'
```

Strimzi comes from the community catalog, on the channel `strimzi-1.x`. The channel `stable` stops at an old release, so a channel is read from the catalog, not assumed. CloudNativePG comes from the certified catalog, where EDB publishes the package with the upstream images, on the channel `stable-v1`. Both are open source and need no key. Red Hat's supported downstream of Strimzi, AMQ Streams, is sold as a subscription and ships a Kafka release older than the one used here, so it is not installed.

One `OperatorGroup` in `edp-operators` targets the namespace `edp`, so both operators watch the project and nothing else. Over the runs measured, the first operator was ready 34 to 37 s after its plan was approved and the second 8 to 14 s after its.

## Building with buildah, copying with skopeo

The cluster has no node to load an image into, and the image must be the one the other platforms run. OpenShift can build from source inside the cluster, but a source build produces a different image. The example builds the unchanged Containerfile on the host and copies the result in.

```bash
buildah build -f 08-transactional-outbox/order-service/Containerfile \
  -t localhost/edp/08-order-service:crc examples/
TOKEN="$(oc create token builder -n edp --duration=15m)"
skopeo copy --preserve-digests \
  --dest-cert-dir .state/certs.d --dest-registry-token "$TOKEN" \
  containers-storage:localhost/edp/08-order-service:crc \
  docker://$REGISTRY/edp/order-service:08
```

`buildah` builds rootless, with no daemon, into containers-storage. CRC already publishes the registry through its default route, so nothing is exposed. The project's `builder` service account may push to its own project, and its token is short-lived and lives in one shell variable for one call: it is neither written to a file nor printed, though it is on skopeo's command line while the copy runs. `--dest-cert-dir` holds the ingress CA.

{% include excalidraw.html file="ah-build-and-copy" alt="A rootless buildah build of the unchanged Containerfile gives an image with digest D in containers-storage; skopeo copy with preserve-digests sends it through the registry route with verified TLS to the ImageStream order-service:08, and the Deployment references the internal registry at D" caption="Figure H.2: The digest of the local build is the digest in the cluster" %}

On a first copy into an empty registry, a plain copy changes the digest: skopeo compresses the layers that buildah stored uncompressed, so the same image id arrives under a different manifest digest. A layer the registry already holds is reused as it is. `--preserve-digests` keeps the manifest as it is, or fails. The Deployment then names the image by digest, and the script compares the digest in the `ImageStream` with the one of the local build before it deploys. A rebuild changes the digest too, because the image records when it was created, so the digest is written to a file at copy time and read at deploy time. The build took 49 to 56 s and the copy 2 to 3 s.

Connect is built in the cluster. `KafkaConnect.spec.build` with `output.type: imagestream` makes the operator run an OpenShift build that downloads the Debezium plugin, checks its sha512 and pushes to the `ImageStream`, which must exist first. OpenShift admits that build pod under the privileged constraint, a property of OpenShift builds and not of the example; the Connect pod it produces runs under `restricted-v2`.

## The outbox on Strimzi and CloudNativePG

`POST /orders` writes the order and its outbox row in one transaction. Debezium reads the write-ahead log, and the Outbox Event Router publishes the row to `edp.orders.placed` with the event type as a header. The connector is the one from the other platforms with a different host and credentials taken from the secret CloudNativePG creates, referenced by a Connect config provider so that no resource holds the password. The `Cluster` declares the application role with the `REPLICATION` attribute in `managed.roles`, and the connector is applied only after `order-service` has run, because its Flyway migration creates the table the connector captures.

The Deployment sets `QUARKUS_OTEL_SDK_DISABLED=true`, a runtime property, because no collector runs here. It sets no user. `restricted-v2` drops every capability and assigns a user from the project's range, so the image's own user 185 is replaced and the service runs unchanged. Every running pod in both namespaces except the Connect build pod carries `openshift.io/scc: restricted-v2`.

```bash
curl --cacert "$CA" -X POST -H 'Content-Type: application/json' "https://$HOST/orders" \
  --data '{"id":"O-4301","customerId":"CU-77","itemSku":"SKU-42","quantity":3,"amount":59.90}'
oc exec -n edp edp-broker-0 -c kafka -- /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic edp.orders.placed --from-beginning --timeout-ms 8000 \
  --formatter-property print.key=true --formatter-property print.headers=true
```

The consumer prints the headers, then the key `O-4301`, then the payload. The header `eventType:OrderPlaced` is among them. Connect's state is read through its REST API with `oc exec`, because in one run the `KafkaConnector` resource listed no tasks while records were flowing. PostgreSQL is reached over TCP with the application role: the pod's user is a random number with no matching role, so peer authentication does not apply.

## Teardown

A shared cluster is left as it was found, and deleting the namespaces does not do that. Deleting an operator by CLI removes the Subscription and the CSV, not the CRDs or the resources they define. What survives the namespaces is cluster-scoped: 21 CRDs, 8 ClusterRoles named `cnpg-*-editor-role`, `cnpg-*-viewer-role`, `strimzi-kafka-broker` and `strimzi-kafka-client`, the volumes with the `Retain` policy that the claims left behind, and the image objects and blobs in the registry.

`teardown.sh` removes them in dependency order: connector, application, Connect, Kafka, `Cluster`, `ImageStream`s, Subscriptions, CSVs, install plans, the `OperatorGroup`, CRDs, ClusterRoles, namespaces, volumes. `oc adm prune images` is the only cluster-wide step, so it lists what it would delete and stops unless every image is the project's. It then removes the local buildah image and prints an inventory. It never strips a finalizer: a delete that does not finish stops the script and names the resource. It fails if anything of the project remains, and it takes 43 to 45 s. It does not stop CRC; that belongs to whoever started it.

The UBI 10 base images stay in rootless storage, because buildah keeps them for the next build. The scripts delete the volume objects and do not inspect the node's disk behind them.

## Build, run, observe

```bash
cd examples/ah-openshift-local
./install.sh && ./build-and-copy.sh && ./deploy.sh
./verify.sh
./teardown.sh
```

The install waits for two operators, Kafka, the `Cluster` and a Connect build; the copy prints one digest; the deploy ends with the connector Ready. Watch the install plans that are approved by name and the digest that the copy prints. It ends with `20/20 checks passed`, and the teardown ends with an empty inventory.

Try one change and rerun:

- Point the local tag at another image after the copy and run `verify.sh`: only the digest check fails.
- Call the Route with `curl` and no `--cacert`: it stops at the certificate.

## When not to use it

CRC is one ephemeral node for learning how the code behaves on OpenShift, not a place to keep data. Its registry, volumes and operators are meant to be thrown away.

The pinned, manually approved Subscription trades upgrades for repeatability: a security fix in an operator waits for a person. Copying an image from outside the cluster suits a demonstration where the image must be the one built elsewhere; a team that builds in the cluster pays less and gets a different image than the one built elsewhere.

> **🤖 Explore with your agent**
> "In examples/ah-openshift-local, run build-and-copy.sh with and without --preserve-digests and compare skopeo inspect of containers-storage:localhost/edp/08-order-service:crc with oc get istag order-service:08 -n edp -o jsonpath='{.image.metadata.name}'. Then state which value the Deployment in deploy/ must reference and why the tag 08 is not enough."
> Check: with the flag the two digests are equal and the Deployment image ends in @sha256 and that digest; the tag names whatever was pushed last, the digest names one image.

## References

- CRC, [Introducing CRC](https://crc.dev/docs/introducing/), [Networking](https://crc.dev/docs/networking/) and [Using CRC](https://crc.dev/docs/using/) (the `crc-admin` context, stopping and deleting the instance)
- Red Hat, [Exposing the registry](https://docs.redhat.com/en/documentation/openshift_container_platform/4.22/html/registry/securing-exposing-registry) and [Accessing the registry](https://docs.redhat.com/en/documentation/openshift_container_platform/4.22/html/registry/accessing-the-registry), OpenShift Container Platform 4.22
- Red Hat, [Managing security context constraints](https://docs.redhat.com/en/documentation/openshift_container_platform/4.22/html/authentication_and_authorization/managing-pod-security-policies) and [Administrator tasks](https://docs.redhat.com/en/documentation/openshift_container_platform/4.22/html/operators/administrator-tasks) (installing from the CLI, removing an operator), OpenShift Container Platform 4.22
- Operator Lifecycle Manager, [Subscription](https://olm.operatorframework.io/docs/concepts/crds/subscription/) (`startingCSV`, `installPlanApproval`, approving an InstallPlan)
- Red Hat, [Streams for Apache Kafka API reference](https://docs.redhat.com/en/documentation/red_hat_streams_for_apache_kafka/3.2/html-single/streams_for_apache_kafka_api_reference) (`ImageStreamOutput`) and [overview](https://docs.redhat.com/en/documentation/red_hat_streams_for_apache_kafka/3.2/html-single/streams_for_apache_kafka_on_openshift_overview) (the subscription)
- CloudNativePG, [Installation and upgrades](https://cloudnative-pg.io/docs/1.30/installation_upgrade/) (OLM and the certified build), [PostgreSQL role management](https://cloudnative-pg.io/docs/1.30/declarative_role_management/) and [Security](https://cloudnative-pg.io/docs/1.30/security/) (security context constraints)
- containers, [skopeo-copy](https://raw.githubusercontent.com/containers/skopeo/main/docs/skopeo-copy.1.md) (`--preserve-digests`, `--dest-cert-dir`, `--dest-registry-token`) and [buildah-build](https://raw.githubusercontent.com/containers/buildah/main/docs/buildah-build.1.md)
- Quarkus, [Using OpenTelemetry](https://quarkus.io/version/3.40/guides/opentelemetry) (`quarkus.otel.sdk.disabled`)
- [Transactional outbox]({{ '/docs/08-transactional-outbox/' | relative_url }}): the order-service, its Containerfile and the connector
- [Debezium and Connect on Strimzi]({{ '/docs/35-debezium-and-connect-on-strimzi/' | relative_url }}): `KafkaConnect` builds and `KafkaConnector` resources
- [Deploying event-driven services on minikube]({{ '/docs/af-deploying-event-driven-services/' | relative_url }}): the same images on the minikube substrate
