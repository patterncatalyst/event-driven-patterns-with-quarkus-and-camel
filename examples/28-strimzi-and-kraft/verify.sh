#!/usr/bin/env bash
# Declares Kafka as two KRaft node pools under the Strimzi operator, then runs the services of chapters 02, 03 and 04
# one after another against it, unchanged: only their addresses are new. Checks the pools, the quorum, the pods, the
# databases, the images and the three observable effects, then replaces the broker pod.
# KAFKA_BROKERS=compose (demo code) keeps the compose Kafka address on notification-service of the chapter 02 set;
# NODE_POOLS=dual (demo code) runs one dual-role node. KEEP=1 leaves the namespaces in place.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../_k8s/lib.sh
source ../_k8s/lib.sh
trap k8s_teardown EXIT

OVERLAY=.
[[ "${NODE_POOLS:-}" == dual ]] && OVERLAY=dual
SET02=../_k8s/apps/02
DEMO_ADDRESS=""
if [[ "${KAFKA_BROKERS:-}" == compose ]]; then SET02=compose-address; DEMO_ADDRESS=1; fi
BOOTSTRAP="edp-kafka-bootstrap.$NS_KAFKA.svc:9092"
NOTIFY="http://127.0.0.1:$PORT2"
SHIP="$ORDERS"                       # chapter 04's shipping-service holds NodePort 30080
BROKER_ID=""; CTRL_ID=""

k8s_fresh "$OVERLAY"

jp() { kc -n "$1" get "$2" -o "jsonpath=$3"; } # jp <namespace> <resource> <jsonpath>
condition() { jp "$1" "$2" '{.status.conditions[?(@.type=="Ready")].status}'; }
field() { python3 -I -c "import json,sys; d=json.load(sys.stdin); print(d[0]['$1'] if d else '')"; }
place() { curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"id\":\"$1\",\"customerId\":\"CU-77\",\"itemSku\":\"SKU-42\",\"quantity\":3,\"amount\":59.90}" "$ORDERS/orders"; }

# --- the cluster and the Kafka resources
ports_on_loopback() { # every published NodePort is bound to 127.0.0.1 and nothing else
    local out; out="$(docker port edp)" || return 1
    [[ -n "$out" && "$out" != *0.0.0.0* && "$out" != *"[::]"* ]] && [[ "$out" == *"127.0.0.1:30080"* ]]
}
available() { [[ "$(jp "$1" "deploy/$2" '{.status.conditions[?(@.type=="Available")].status}')" == True ]]; }
kafka_ready() {
    [[ "$(condition "$NS_KAFKA" kafka/edp)" == True \
        && "$(jp "$NS_KAFKA" kafka/edp '{.status.observedGeneration}')" == "$(jp "$NS_KAFKA" kafka/edp '{.metadata.generation}')" ]]
}
roles_of() { jp "$NS_KAFKA" "kafkanodepool/$1" '{.spec.roles[*]}'; }
pools_have_separate_roles() { [[ "$(roles_of controller)/$(roles_of broker)" == "controller/broker" ]]; }
one_id_each() { [[ "$BROKER_ID" =~ ^[0-9]+$ && "$CTRL_ID" =~ ^[0-9]+$ && "$BROKER_ID" != "$CTRL_ID" ]]; }
owner_of_pod() { jp "$NS_KAFKA" "pod/$1" '{.metadata.ownerReferences[0].kind}/{.metadata.ownerReferences[0].name}'; }
pods_owned_by_podsets() {
    [[ "$(owner_of_pod "edp-broker-$BROKER_ID")" == "StrimziPodSet/edp-broker" \
        && "$(owner_of_pod "edp-controller-$CTRL_ID")" == "StrimziPodSet/edp-controller" ]]
}
controller_leads_alone() {
    local s; s="$(kafka_exec kafka-metadata-quorum.sh describe --status | python3 -I quorum.py status)"
    [[ "$s" == "leader=$CTRL_ID voters=$CTRL_ID observers=$BROKER_ID" ]]
}
replication_roles() { local s; s="$(kafka_exec kafka-metadata-quorum.sh describe --replication | python3 -I quorum.py replication)"
    [[ "$s" == "$(printf '%s\n' "$BROKER_ID=Observer" "$CTRL_ID=Leader" | sort -n | paste -sd' ')" ]]; }
compose_kafka_tag() { sed -n 's|.*image: docker.io/apache/kafka:\(.*\)|\1|p' ../_infra/compose-kafka.yaml; }
kafka_version_matches_compose() {
    local v; v="$(jp "$NS_KAFKA" kafka/edp '{.status.kafkaVersion}')"
    [[ -n "$v" && "$v" == "$(jp "$NS_KAFKA" kafka/edp '{.spec.kafka.version}')" && "$v" == "$(compose_kafka_tag)" ]]
}
pg_ready() { [[ "$(condition "$NS_APPS" cluster.postgresql.cnpg.io/edp-pg)" == True ]]; }
databases_exist() {
    [[ "$(psql_exec postgres "select string_agg(datname || ':' || pg_get_userbyid(datdba), ',' order by datname) from pg_database where datname in ('edp','edp03','edp04')")" == "edp:edp,edp03:edp,edp04:edp" ]]
}

# --- images and addresses
image_ids_match() { # image_ids_match <slug prefix>: the node holds what docker build produced, for each image of the set
    local slug svc ref host node
    while read -r slug svc <&3; do
        [[ "$slug" == "$1"* ]] || continue
        ref="localhost/edp/${slug%%-*}-$svc:local"
        host="$(docker image inspect --format '{{.Id}}' "$ref")" || return 1
        node="$(_node_image_id "$ref" </dev/null)" || return 1
        [[ "${host#sha256:}" == "${node#sha256:}" ]] || { echo "$ref: host $host, node $node" >&2; return 1; }
    done 3< images.txt
}
env_names_match() { # env_names_match <example dir> <compose service> <deployment>: same names as compose, no Dockerfile or code change
    python3 -I envcompare.py <(docker compose -f "../$1/compose.yaml" config --format json) \
        <(kc -n "$NS_APPS" get deploy "$3" -o json) "$2" "$3"
}
env_names_02() { env_names_match 02-event-notification order-service order-service && env_names_match 02-event-notification notification-service notification-service; }
env_names_03() { env_names_match 03-event-carried-state-transfer order-service order-service && env_names_match 03-event-carried-state-transfer notification-service notification-service; }
env_names_04() { env_names_match 04-ordering-and-partitioning order-1 order && env_names_match 04-ordering-and-partitioning shipping-service shipping-service; }
bootstrap_address_is() { # bootstrap_address_is <deployment>
    [[ "$(jp "$NS_APPS" "deploy/$1" '{.spec.template.spec.containers[0].env[?(@.name=="CAMEL_COMPONENT_KAFKA_BROKERS")].value}')" == "$BOOTSTRAP" ]] \
        && [[ -n "$(jp "$NS_KAFKA" svc/edp-kafka-bootstrap '{.spec.clusterIP}')" ]]
}

# --- the three app sets
rollout_02() { # the demo applies without waiting for the pair, so a pod that never starts is a failed check
    if [[ -n "$DEMO_ADDRESS" ]]; then
        kc apply --server-side --field-manager=edp-verify -k "$SET02"
        kc -n "$NS_APPS" rollout status deploy/order-service --timeout=240s
        kc -n "$NS_APPS" rollout status deploy/notification-service --timeout=60s
    else
        deploy_set "$SET02"
    fi
}
notifications() { curl -sf "$NOTIFY/notifications?orderId=$1"; }
has_notification() { [[ -n "$(notifications "$1" | field customerId)" ]]; }
copy_status() { curl -s "$NOTIFY/order-copies/$1" | python3 -I -c 'import json,sys; print(json.load(sys.stdin).get("status", ""))' 2>/dev/null || true; }
copy_is() { [[ "$(copy_status "$1")" == "$2" ]]; }
copy_is_complete() {
    [[ "$(curl -s "$NOTIFY/order-copies/$1")" == "{\"id\":\"$1\",\"customerId\":\"CU-77\",\"itemSku\":\"SKU-42\",\"quantity\":3,\"amount\":59.90,\"status\":\"PLACED\"}" ]]
}
no_pod() { [[ -z "$(kc -n "$NS_APPS" get pod -l "app=$1" -o name)" ]]; }
job_done() { [[ "$(jp "$NS_APPS" job/topics-init '{.status.succeeded}')" == 1 ]]; }
partitions_of() { kafka_exec kafka-topics.sh --describe --topic "$1" 2>/dev/null | grep -o 'PartitionCount: [0-9]*' | cut -d' ' -f2; }
group_clients() { # the number of distinct clients that own a partition of the shipments topic
    kafka_exec kafka-consumer-groups.sh --describe --group order-service 2>/dev/null \
        | awk '$2 == "edp.shipping.shipments" && $1 != "GROUP" {print $NF}' | sort -u | wc -l
}
group_is_split() { [[ "$(group_clients)" == 2 ]]; }
history_of() { psql_exec edp04 "SELECT coalesce(string_agg(event_type, ',' ORDER BY id), '') FROM orders.order_status_history WHERE order_id='$1'"; }
status_of() { curl -s "$NOTIFY/orders/$1/status" | python3 -I -c 'import json,sys; print(json.load(sys.stdin).get("status", ""))' 2>/dev/null || true; }
status_is() { [[ "$(status_of "$1")" == "$2" ]]; }
history_is() { [[ "$(history_of "$1")" == "ShipmentScheduled,ShipmentInTransit,ShipmentDelivered" ]]; }

# --- the broker pod
broker_uid() { jp "$NS_KAFKA" "pod/edp-broker-$BROKER_ID" '{.metadata.uid}'; }
broker_claim() { jp "$NS_KAFKA" "pod/edp-broker-$BROKER_ID" '{.spec.volumes[*].persistentVolumeClaim.claimName}'; }
broker_replaced() {
    [[ "$(broker_uid)" != "$UID_BEFORE" && "$(condition "$NS_KAFKA" "pod/edp-broker-$BROKER_ID")" == True ]]
}
placed_offsets() { kafka_exec kafka-get-offsets.sh --topic edp.orders.placed; }
offsets_unchanged() { [[ "$(placed_offsets)" == "$OFFSETS_BEFORE" ]]; }
offsets_and_record() { offsets_unchanged && topic_holds O-2801; }
topic_holds() { # topic_holds <key>: a record keyed <key> is on edp.orders.placed
    local out; out="$(kafka_exec kafka-console-consumer.sh --topic edp.orders.placed --from-beginning --timeout-ms 10000 --property print.key=true 2>/dev/null)" || true
    [[ "$out" == "$1"$'\t'* || "$out" == *$'\n'"$1"$'\t'* ]]
}

# 1 to 3: the platform is up
precondition "the NodePorts are published on 127.0.0.1 only" ports_on_loopback
precondition "the CloudNativePG operator is Available" available cnpg-system cnpg-controller-manager
precondition "the Strimzi operator is Available" available "$NS_KAFKA" strimzi-cluster-operator

# 4 to 12: Kafka as two KRaft node pools, then PostgreSQL
check "Kafka edp is Ready for its current generation" kafka_ready
BROKER_ID="$(jp "$NS_KAFKA" kafkanodepool/broker '{.status.nodeIds[*]}' 2>/dev/null || true)"
CTRL_ID="$(jp "$NS_KAFKA" kafkanodepool/controller '{.status.nodeIds[*]}' 2>/dev/null || true)"
check "the controller pool holds the controller role and the broker pool the broker role" pools_have_separate_roles
check "each pool has one node id and the two ids differ" one_id_each
check "both pods are owned by their StrimziPodSet" pods_owned_by_podsets
check "the controller is the leader and the only voter, the broker the only observer" controller_leads_alone
check "the replication view lists the controller as Leader and the broker as Observer" replication_roles
check "Kafka runs the version the spec asks for, the version of the compose broker" kafka_version_matches_compose
check "CloudNativePG Cluster edp-pg is Ready" poll 180 pg_ready
check "databases edp, edp03 and edp04 exist, owned by edp" poll 90 databases_exist

# 13 to 36: chapter 02, then 03, then 04, each alone
echo "Chapter 02 set"
check "chapter 02 images in the node are the images docker built" image_ids_match 02-
check "the chapter 02 pair rolls out" rollout_02
check "chapter 02 Deployments use the environment names of compose.yaml" env_names_02
check "notification-service points at the Strimzi bootstrap Service" bootstrap_address_is notification-service
check "order O-2801 is accepted with 201" expect "$(place O-2801)" 201
check "the notification for O-2801 appears" poll 60 has_notification O-2801
check "the notification holds customer CU-77 and status PLACED" \
    expect "$(notifications O-2801 | field customerId)/$(notifications O-2801 | field orderStatus)" CU-77/PLACED
check "one trace holds POST /orders and notification-service" tempo_trace_with 90 "POST /orders" notification-service
remove_set "$SET02"

echo "Chapter 03 set"
check "chapter 03 images in the node are the images docker built" image_ids_match 03-
check "the chapter 03 pair rolls out" deploy_set ../_k8s/apps/03
check "chapter 03 Deployments use the environment names of compose.yaml" env_names_03
check "order O-2802 is accepted with 201" expect "$(place O-2802)" 201
check "the copy of O-2802 carries every field" poll 60 copy_is_complete O-2802
kc -n "$NS_APPS" scale deploy/order-service --replicas=0 >/dev/null
check "order-service has no pod" poll 60 no_pod order-service
check "with order-service gone, the copy of O-2802 is still answered" copy_is O-2802 PLACED
remove_set ../_k8s/apps/03

echo "Chapter 04 set"
check "chapter 04 images in the node are the images docker built" image_ids_match 04-
check "the chapter 04 set rolls out with two order replicas" deploy_set ../_k8s/apps/04
check "chapter 04 Deployments use the environment names of compose.yaml" env_names_04
check "the topics Job completes" poll 120 job_done
check "edp.shipping.shipments has 3 partitions" expect "$(partitions_of edp.shipping.shipments)" 3
check "the two order replicas share the three partitions" poll 90 group_is_split
check "shipment events for O-2803 are published" curl -sf -X POST "$SHIP/shipments/O-2803"
check "O-2803 is applied in lifecycle order" poll 60 history_is O-2803
check "O-2803 ends DELIVERED" poll 60 status_is O-2803 DELIVERED

# 37 to 39: the broker pod is replaced, its volume and the records stay
UID_BEFORE="$(broker_uid)"; CLAIM_BEFORE="$(broker_claim)"; OFFSETS_BEFORE="$(placed_offsets)"
T_DEL=$SECONDS
kc -n "$NS_KAFKA" delete "pod/edp-broker-$BROKER_ID" --wait=true >/dev/null
check "the broker pod is replaced and Ready again" poll 240 broker_replaced
echo "measured: broker pod deleted to Ready again in $((SECONDS - T_DEL)) s"
check "the new pod mounts the same volume claim" expect "$(broker_claim)" "$CLAIM_BEFORE"
check "edp.orders.placed has the same end offsets and still holds O-2801" poll 120 offsets_and_record

summary
