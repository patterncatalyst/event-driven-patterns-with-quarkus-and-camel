// Section: Running on Kubernetes with Strimzi.
"use strict";

module.exports = function (lib) {
  const { divider, bulletSlide, figSlide, codeSlide } = lib;

  divider(
    "Running on Kubernetes with Strimzi",
    "Kafka, topics, connectors and failover as resources an operator keeps true",
    `On Docker Compose a broker is a container and its address is whatever the compose file names it. On Kubernetes a broker is a pod that can be deleted at any moment, and it has to come back with the same node id, find its own data and keep a stable name. Something has to hold those rules, and here that something is the Strimzi operator. The application services run unchanged: only the addresses move, and they were already environment variables. These patterns treat the cluster itself as declared resources: node pools, topics, connectors, rebalances and a second cluster for failover, each reconciled by an operator and each with a failure mode worth knowing.`,
  );

  // Operator-managed Kafka
  bulletSlide("OPERATOR-MANAGED KAFKA", "KRaft roles and node pools", [
    ["Definition:", " describe the cluster as resources and let an operator create and keep the pods, volumes, Services and configuration that realise it."],
    ["Problem:", " a broker needs a node id that survives restarts, a volume bound to the node, a stable address, and in KRaft a controller quorum. Deployments cannot decide restart order."],
    ["Node pools:", " a group of nodes with the same roles, resources and storage. A node is a broker, a controller or both."],
    ["Broker identity:", " a replaced pod keeps its node id and its volume claim, and the topic end offsets are unchanged."],
    ["Services:", " clients use the bootstrap Service, whose name does not change when a pod does."],
  ], `A compose stack starts one Kafka container. On Kubernetes a broker is a pod that can disappear at any moment, so four things have to be held by something other than the pod: the node id, the volume, the address and, in KRaft mode, the controller quorum. A set of Deployments can start containers but cannot choose the restart order of brokers or give a replaced pod its old identity. Strimzi does that through a Kafka resource for what the cluster shares and KafkaNodePool resources for groups of nodes. In the cluster measured, one controller pool and one broker pool are the smallest cluster that shows the two roles apart. Dedicated controllers suit production. Surviving N controller failures takes 2N + 1 controllers, so a real cluster runs at least three.`);

  figSlide("OPERATOR-MANAGED KAFKA", "Controllers, brokers and observers", "28-kraft-roles",
    "KafkaNodePool resources: a controller pool votes, a broker pool observes the metadata log",
    `The controller is the leader and the only voter of the metadata quorum. The broker is an observer: it fetches the metadata log from the controller and does not vote on it. Kafka's own metadata quorum tool, run inside the broker pod, prints exactly that. Each pod is owned by a StrimziPodSet, not a ReplicaSet or a StatefulSet, and the node ids appear in each pool's status. Deleting a broker pod shows what the operator holds: a new pod with a new UID takes the name, mounts the same volume claim and keeps its node id. Identity and data belong to the node, not to the pod. The replication factors in this cluster are 1 because it has one broker, a choice for a laptop. A cluster that survives a broker loss needs more brokers and higher factors.`);

  codeSlide("OPERATOR-MANAGED KAFKA", "A broker node pool", "YAML", "_k8s/kafka/nodepool-broker.yaml", 1, 18,
    "apiVersion: kafka.strimzi.io/v1",
    "KafkaNodePool · nodepool-broker.yaml",
    `The pool is a short resource. The label ties it to its Kafka resource, the roles list makes these nodes brokers, and the storage section asks for a persistent volume per node. The delete claim setting removes the volume when the pool goes, which suits a lab where a fresh namespace should start empty, and would be wrong for data that matters. The controller pool is the same resource with the controller role. Setting both roles on one pool gives the dual-role form that Strimzi suggests for development and testing. The operator turns these twenty lines into pods, claims, Services and listener configuration, and reports the node ids in the pool's status.`);

  // Topics
  bulletSlide("TOPICS AS RESOURCES", "Topics as declared resources", [
    ["Definition:", " declare every topic as a Kubernetes resource, turn off broker auto-creation, and let the operator reconcile Kafka toward the resources and never the other way."],
    ["Problem:", " with auto-creation a typo in a topic name creates a second topic and a healthy send. No list says which topics the system needs."],
    ["Effect:", " an undeclared name fails at the first send with a metadata timeout, and no topic is created."],
    ["Drift:", " a changed setting is reverted on the next pass. Partitions cannot be lowered, so that mismatch is reported as not ready, not repaired."],
    ["Cost:", " a topic created in Kafka with the command line tools gets no resource and stays invisible to the list."],
  ], `The list of topics a system needs is part of the system, and Kubernetes already keeps lists of things and compares them with what runs. With auto-creation on, a topic appears as a side effect of a send, with broker defaults, and a producer configured with a misspelled name gets a new topic, a healthy send and a consumer that waits for events that never come. Strimzi's Unidirectional Topic Operator reads KafkaTopic resources and changes Kafka to match them. It never creates a resource from a topic it finds. The documented rule is that configuration changes flow from the resource to the topic, and changes made outside are reverted. That makes a missing resource visible too: leave one topic out of the set and the producer cannot publish.`);

  figSlide("TOPICS AS RESOURCES", "Changes go in one direction", "29-one-direction",
    "KafkaTopic resources and Kafka: what the operator reverts and what it cannot",
    `The figure shows each action and its result. A KafkaTopic applied creates the topic. A declared setting changed in Kafka is put back on the next reconciliation pass, within a minute at the fifteen second interval used here. A topic deleted in Kafka is recreated from its resource, as a new topic with a new id. Partitions raised in Kafka cannot be lowered, so the resource turns not ready with a reason that says decreasing partitions is not supported, and the mismatch is reported, not repaired. A topic created in Kafka gets no resource. Deleting the resource deletes the topic, because of a finalizer, so a topic that must outlive its resource is made unmanaged first. Teams that administer topics with Kafka's own tools will see their edits reverted.`);

  codeSlide("TOPICS AS RESOURCES", "A topic in the repository", "YAML", "29-unidirectional-topic-operator/base/topics/kafkatopics.yaml", 1, 11,
    "apiVersion: kafka.strimzi.io/v1",
    "KafkaTopic · kafkatopics.yaml",
    `This is one of nine topic resources in a single file. The name follows the topic convention for the domain, the label binds the resource to the cluster, and partitions, replicas and the retention setting are written down where review, diff and rollback work as on any manifest. The set is checked against the list of topics the services use, so a topic added to the services without a resource is caught. The retention here is one hour, a lab value. A setting that matters belongs in the resource, because a value changed in Kafka by hand is reverted. A topic that an application creates at run time, such as one per tenant, has no resource written in advance, and generating them is more machinery than the list is worth.`);

  // Field ownership
  bulletSlide("FIELD OWNERSHIP", "Server-side apply and field managers", [
    ["Definition:", " apply server-side under a named field manager, so the API server records who owns each field and refuses a write from another manager that would change it."],
    ["Problem:", " client-side apply refuses neither of two writers. A platform patch to a memory limit is silently overwritten by the next deployment."],
    ["Size limit:", " client-side apply copies the manifest into an annotation, capped at 256 KiB. The CloudNativePG install fails on its two largest CRDs."],
    ["Conflict:", " a second manager gets an error naming the manager and the field. Forcing takes the field."],
    ["Operators:", " Strimzi applies its Services server-side too, so a foreign annotation survives but a change to its own field is put back."],
  ], `Application manifests set a memory limit, a platform team raises it with a patch, and the next deployment puts it back. Nothing reports an error, and the last writer wins. Client-side apply compares the manifest, the live object and a copy of the last manifest stored in an annotation. That copy has two consequences. The annotation counts toward a 256 KiB limit for all annotations on one object, so a large CRD cannot be applied. And the server treats every write as an ordinary update. Server-side apply sends the manifest to the API server, which records field owners in managed fields. Fields set to the same value by two managers are shared. Different values are a conflict until a manager forces. Force in a pipeline with nobody watching turns the report off, so keep it for a decision.`);

  figSlide("FIELD OWNERSHIP", "Two managers, one field", "30-field-managers",
    "Deployment managed fields: edp-apps and edp-platform, a conflict and a forced change",
    `Two managers apply to one Deployment. The first owns the container's memory limit. The second sends a partial Deployment with only that field, and the server refuses it with a message naming the manager and the field. With the force flag the value changes and the managed fields show the second manager as owner. The next apply by the first manager now conflicts the other way. A diff computed by the server shows what an apply would change before anything is written, which is the check to run before applying a shared object. Mixing client-side and server-side apply on one object is the way to lose track of owners. Server-side apply reports a conflict. It does not say which value is right, so settle ownership before the second manager exists.`);

  // Admission
  bulletSlide("ADMISSION", "Restricted pods by default", [
    ["Definition:", " label every namespace that runs workloads with the restricted Pod Security level, and give each workload the security context that level requires from the one place that owns it."],
    ["Problem:", " Kubernetes admits any well-formed pod. Operators create most pods in a cluster, and the operator decides their security context."],
    ["Applications:", " a patch adds non-root, no privilege escalation, dropped capabilities, a seccomp profile and a read-only root filesystem with a writable temporary directory."],
    ["Strimzi:", " select the restricted pod security provider, or the default provider's Kafka pods are refused."],
    ["Exceptions:", " an image that cannot comply gets a namespace of its own, because a label cannot exempt one pod."],
  ], `Kubernetes ships three Pod Security Standards: privileged, baseline and restricted. Nothing applies one until a namespace asks. Telling every team to write a good security context depends on every team remembering, and most pods in a cluster are written by operators, not by the team. A namespace label turns the rule on, and the API server then refuses a violating pod whoever created it. Labelling a namespace that already holds pods prints a warning for each pod that would fail and evicts nothing, so the refusal arrives with the next pod creation, usually a rollout. The UBI images name a numeric user, so the kubelet can check the non-root requirement. The level checks pod specs and nothing else: it does not scan images or limit network paths.`);

  figSlide("ADMISSION", "What the API server admits", "31-admission",
    "Namespace labels and pod specs: restricted namespaces, one exception namespace",
    `The figure shows which pods pass and which do not. The two namespaces that hold Kafka, PostgreSQL and the applications carry the restricted label, pinned to a policy version so an upgrade does not move the rule. The applications pass with the security context patch. The Strimzi pods pass once the operator uses the restricted provider. The CloudNativePG pods need no change, because the operator and the instance pods already run as non-root with a runtime default seccomp profile. The observability image runs as published, with no security context, so it lives in a namespace without the label. A server dry-run of the label answers whether that image could be admitted, and repeats the answer whenever the image changes.`);

  // Metrics
  figSlide("OPERATOR METRICS", "Three layers, one query language", "32-metrics-path",
    "Strimzi Metrics Reporter, operator endpoint and kube-state-metrics into one collector",
    `An operator-managed cluster has three layers that fail independently: the Kafka processes, the operator, and the status of the resources the operator manages. Consumer lag and relay backlog say that something is stuck and not why. The pattern exposes each layer through its own endpoint and scrapes all of them with the collector that already carries the application telemetry. The Strimzi Metrics Reporter serves Prometheus text on every Kafka pod. The operator exposes reconciliation counters. Kube-state-metrics turns the status of custom resources into series. A topic whose partitions were raised in Kafka reads not ready while every broker stays healthy, and only the resource series shows it. The allow list is the one setting that applies without rolling the pods, and a narrowed list hides a signal with no error.`);

  // Cruise Control
  bulletSlide("REBALANCE BY PROPOSAL", "Scaling and moving partitions", [
    ["Definition:", " ask Cruise Control for a proposal, read what it would move, approve it only when acceptable, and let the operator ask and approve when a pool grows or shrinks."],
    ["Problem:", " a new broker joins empty, because placement is decided once at topic creation. A broker that holds replicas cannot simply be deleted."],
    ["Proposal:", " a status on the KafkaRebalance resource with replicas to move, data to copy and balance before and after. Nothing moves until approval."],
    ["Auto-rebalance:", " templates for adding and removing brokers. A scale-down without one is refused while the broker holds replicas."],
    ["Cost:", " a rebalance copies data and competes with clients for disk and network."],
  ], `A cluster that runs out of room gets another broker, which in Kubernetes is one number in a node pool. The pod starts, joins, passes readiness and holds nothing, because every existing partition stays where it was created. Cruise Control keeps a model of the cluster built from partition load and proposes the smallest set of movements that brings it within its goals. Strimzi surfaces that as a KafkaRebalance resource. The proposal appears in the resource's status, and approval is an annotation, a human decision by default. A large amount of data to move is the signal to wait for a quiet hour. The operator can also do both steps for the two cases it recognises, a pool that grows and a pool that shrinks.`);

  figSlide("REBALANCE BY PROPOSAL", "Grow, propose, approve, move", "33-scale-and-move",
    "KafkaRebalance: proposal ready, rebalancing, ready, and the remove-brokers case",
    `The sequence is a scale, a proposal and a move. When the pool grows from two brokers to three, the new broker gets the next free node id, not the next number in a sequence, because ids are shared by all pools. The rebalance resource asks for a plan, the status carries the proposal, and after approval the resource goes to rebalancing and then ready, with the new broker holding replicas. Shrinking works the other way. Without auto-rebalance the operator refuses to remove a broker that still holds replicas, and the Kafka status carries a scale-down prevention warning. With the remove-brokers template it drains the broker first and then removes the pod. Auto-rebalance reacts to a changed replica count in an existing pool and to nothing else, so a skewed cluster that did not scale needs a manual full rebalance.`);

  // MirrorMaker 2
  figSlide("REPLICATED CLUSTER", "Two clusters with MirrorMaker 2", "34-two-clusters",
    "edp and edp-dr: mirrored topics, checkpoints and a consumer moved by bootstrap address",
    `A second cluster that receives a copy of the topics answers where the data is, not where a consumer should start. A consumer's position is a committed offset stored in the cluster it reads from, and Kafka does not promise a record has the same offset in both. MirrorMaker 2 records how source offsets map to target offsets and, with group sync on, writes each group's translated position into the second cluster. The identity replication policy keeps topic names, so a consumer changes only its bootstrap address and continues where it stopped. Two properties apply to every failover. The target group is written only while no consumer is active in it, and a translated offset can trail the real position, so records are read twice. The copy is made after the write, so records the mirror had not yet copied are lost.`);

  // Connect
  bulletSlide("CONNECTORS AS RESOURCES", "Connect built in the cluster", [
    ["Definition:", " declare the Connect image, every connector and every offset operation as Kubernetes resources, and let the operator reconcile the worker toward them."],
    ["Problem:", " a worker image must contain plugins, a connector is JSON sent over HTTP, and an offset change leaves no trace in the repository."],
    ["Build:", " the operator runs a build pod, checks each artifact's checksum, and pushes an image to a registry the setup names. A wrong checksum stops the build."],
    ["Credentials:", " a config provider reads the database secret in the Connect pod, so the password is never in the resource."],
    ["Offsets:", " an annotation lists or alters them through a ConfigMap, only while the connector is stopped."],
  ], `On a compose stack a connector is a JSON file that a script sends to the Connect REST API. Nothing in the repository says which connectors should exist, and nothing notices when one is missing. On Kubernetes the operator that runs Kafka can run Connect too. The image becomes a build section of the KafkaConnect resource, each connector a KafkaConnector, and an offset operation an annotation on the connector. The REST API stays for reading status and offsets. Strimzi runs no registry, so one has to be named for the build. Two Debezium connectors in one Connect cluster must not share a topic prefix, or the second never takes its snapshot. The captured table must be owned by the connector's role, or the publication cannot be created.`);

  figSlide("CONNECTORS AS RESOURCES", "From resource to running connector", "35-build-and-run",
    "KafkaConnect build pod, registry, worker and KafkaConnector resources on CloudNativePG",
    `The figure follows the build and the run. The KafkaConnect resource carries a build section. The operator starts a build pod that downloads the Debezium plugin, verifies its checksum, assembles an image from Strimzi's Kafka image and pushes it to the registry. The worker pulls that image by digest. Two connectors are declared as resources: an outbox router for the order service and a log-tailing connector for the inventory table. Both read the database host from the CloudNativePG Service and the password from its Secret. In a namespace that enforces the restricted level the build pod is refused, so the build namespaces carry no Pod Security labels. Connect becomes ready about a minute and a half after the apply, build and pull included.`);
};
