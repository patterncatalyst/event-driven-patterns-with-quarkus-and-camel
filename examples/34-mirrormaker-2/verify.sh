#!/usr/bin/env bash
# Runs a second Kafka cluster, edp-dr, beside edp and a MirrorMaker 2 that copies edp.orders.placed and the group
# offsets of notification-service from edp to edp-dr. notification-service is stopped, five more orders are placed,
# and the service is started again with only its bootstrap address pointing at edp-dr. The checks read the topics,
# the committed offsets of the group on both clusters and the connector status.
# OFFSET_SYNC=off (demo code) switches off sync.group.offsets.enabled, so edp-dr never gets the group. KEEP=1 leaves
# the namespaces in place.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../_k8s/lib.sh
source ../_k8s/lib.sh
trap k8s_teardown EXIT

OFFSET_SYNC="${OFFSET_SYNC:-on}"
DIR=.
EXPECT_SYNC=true
case "$OFFSET_SYNC" in
    on) ;;
    off) DIR=demo-offset-sync-off; EXPECT_SYNC=false; echo "demo code: the checkpoint connector does not sync group offsets" ;;
    *) echo "OFFSET_SYNC is on or off" >&2; exit 2 ;;
esac

k8s_fresh "$DIR"

SRC=edp-broker-0
DR=edp-dr-dual-0
TOPIC=edp.orders.placed
GROUP=notification-service
DR_BOOTSTRAP=edp-dr-kafka-bootstrap.edp-kafka.svc:9092
BOUND=60                     # checkpoints and group syncs run every 5 s; a change is seen within a few of them
NOTIFY="http://127.0.0.1:${PORT2}"
SOURCE_CONNECTOR='edp->edp-dr.MirrorSourceConnector'
CHECKPOINT_CONNECTOR='edp->edp-dr.MirrorCheckpointConnector'

# --- orders and notifications (chapter 02 pair)
place() { # place <id>
    curl -s -m 90 -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' "$ORDERS/orders" \
        --data "{\"id\":\"$1\",\"customerId\":\"CU-34\",\"itemSku\":\"SKU-34\",\"quantity\":1,\"amount\":19.90}"
}
place_all() { local id; for id in "$@"; do [[ "$(place "$id")" == 201 ]] || return 1; done; }
has_notification() { local out; out="$(curl -sf -m 5 "$NOTIFY/notifications?orderId=$1")" || return 1; [[ -n "$out" && "$out" != "[]" ]]; }
all_notified() { local id; for id in "$@"; do has_notification "$id" || return 1; done; }
none_notified() { local id; for id in "$@"; do ! has_notification "$id" || return 1; done; }

# --- offsets, read with the Kafka tools in the broker pods
end_offset_sum() { # end_offset_sum <pod>: records ever written to the topic, over its partitions
    local out; out="$(kafka_exec_on "$1" kafka-get-offsets.sh --topic "$TOPIC")" || return 1
    awk -F: -v t="$TOPIC" '$1 == t {s += $3; n++} END {if (n == 0) exit 1; print s + 0}' <<<"$out"
}
group_exists() { # group_exists <pod>
    local out; out="$(kafka_exec_on "$1" kafka-consumer-groups.sh --list)" || return 1
    [[ -n "$(awk -v g="$GROUP" '$1 == g' <<<"$out")" ]]
}
committed_sum() { # committed_sum <pod>: the group's committed offsets summed over the partitions; 0 without a group
    local out
    group_exists "$1" || { echo 0; return 0; }
    out="$(kafka_exec_on "$1" kafka-consumer-groups.sh --describe --group "$GROUP")" || return 1
    awk -v g="$GROUP" -v t="$TOPIC" '$1 == g && $2 == t && $4 ~ /^[0-9]+$/ {s += $4} END {print s + 0}' <<<"$out"
}
topic_present() { local out; out="$(kafka_exec_on "$1" kafka-topics.sh --list)" || return 1; [[ -n "$(awk -v t="$2" '$1 == t' <<<"$out")" ]]; }
ends_equal() { local a b; a="$(end_offset_sum "$SRC")" && b="$(end_offset_sum "$DR")" && [[ "$a" == "$b" && "$a" == "$1" ]]; }

# --- the resources
sync_in_cr() {
    kc -n "$NS_KAFKA" get kafkamirrormaker2 edp-mm2 -o 'jsonpath={.spec.mirrors[0].checkpointConnector.config.sync\.group\.offsets\.enabled}'
}
both_clusters_ready() { kafka_ready edp && kafka_ready edp-dr; }
mm2_ready() { [[ "$(kc -n "$NS_KAFKA" get kafkamirrormaker2 edp-mm2 -o 'jsonpath={.status.conditions[?(@.type=="Ready")].status}')" == True ]]; }
connector_state() { # connector_state <name>: the connector's state, then the state of each of its tasks
    kc -n "$NS_KAFKA" get kafkamirrormaker2 edp-mm2 -o json | python3 -I ./mm2_state.py "$1"
}
connectors_running() { [[ "$(connector_state "$SOURCE_CONNECTOR")" == "RUNNING RUNNING" && "$(connector_state "$CHECKPOINT_CONNECTOR")" == "RUNNING RUNNING" ]]; }
internal_topics_placed() { # offset syncs on the source (the default), checkpoints on the target
    topic_present "$SRC" mm2-offset-syncs.edp-dr.internal && topic_present "$DR" edp.checkpoints.internal
}
mm2_workers_reported() { # the reporter on the MirrorMaker 2 worker, through the monitoring of chapter 32
    local v; v="$(prom_query 'kafka_connect_connect_worker_metrics_connector_count{strimzi_kind="KafkaMirrorMaker2"}')" || return 1
    [[ "$v" == 2 || "$v" == 2.0 ]]
}

# --- the group on both clusters
group_on_target_matches_source() { # the target group exists and holds the source group's committed offsets
    local s d; s="$(committed_sum "$SRC")"; d="$(committed_sum "$DR")"
    group_exists "$DR" && [[ "$s" == "$1" && "$d" == "$1" ]]
}
consumer_stopped() { kc -n "$NS_APPS" scale deploy/notification-service --replicas=0 >/dev/null && _wait_gone "$NS_APPS" app=notification-service 120; }
fail_over() { # the only change: the consumer's bootstrap address
    kc -n "$NS_APPS" set env deploy/notification-service CAMEL_COMPONENT_KAFKA_BROKERS="$DR_BOOTSTRAP" >/dev/null \
        && kc -n "$NS_APPS" scale deploy/notification-service --replicas=1 >/dev/null \
        && kc -n "$NS_APPS" rollout status deploy/notification-service --timeout=180s >/dev/null \
        && poll 90 ready "$PORT2" \
        && [[ "$(kc -n "$NS_APPS" get deploy notification-service -o 'jsonpath={.spec.template.spec.containers[0].env[?(@.name=="CAMEL_COMPONENT_KAFKA_BROKERS")].value}')" == "$DR_BOOTSTRAP" ]]
}
committed_reaches() { [[ "$(committed_sum "$DR")" == "$1" ]]; }

precondition "both Kafka clusters, edp and edp-dr, are Ready" both_clusters_ready
precondition "MirrorMaker 2 edp-mm2 is Ready" mm2_ready
precondition "sync.group.offsets.enabled in the checkpoint connector is $EXPECT_SYNC" expect "$(sync_in_cr)" "$EXPECT_SYNC"
check "the source and the checkpoint connector and their tasks are RUNNING" poll "$BOUND" connectors_running

first_orders() { place_all O-3401 O-3402 O-3403 O-3404 O-3405 && poll 120 all_notified O-3401 O-3402 O-3403 O-3404 O-3405; }
second_orders_unseen() { place_all O-3406 O-3407 O-3408 O-3409 O-3410 && none_notified O-3406 O-3407 O-3408 O-3409 O-3410; }
second_orders_notified() { poll 90 all_notified O-3406 O-3407 O-3408 O-3409 O-3410; }
target_group_not_ahead() { local d e; d="$(committed_sum "$DR")" && e="$(end_offset_sum "$DR")" && [[ "$d" == 5 && "$d" -le "$e" ]]; }
resumed_from_synced_offset() { # the group reaches the end, having read exactly the records placed after the stop
    poll "$BOUND" committed_reaches 10 && [[ "$(( $(committed_sum "$DR") - BEFORE ))" == 5 ]]
}

check "orders O-3401 to O-3405 are placed and their notifications arrive on edp" first_orders
check "$TOPIC exists on edp-dr under the same name" poll "$BOUND" topic_present "$DR" "$TOPIC"
check "both clusters hold the same 5 records of $TOPIC" poll "$BOUND" ends_equal 5
check "the offset syncs are on edp and the checkpoints on edp-dr" internal_topics_placed
check "group $GROUP exists on edp-dr with the committed offsets it has on edp" poll "$BOUND" group_on_target_matches_source 5
precondition "notification-service is stopped" consumer_stopped
check "orders O-3406 to O-3410 are placed and nothing is notified" second_orders_unseen
check "both clusters hold the same 10 records of $TOPIC" poll "$BOUND" ends_equal 10
check "the group on edp-dr stays at 5 and is not ahead of the records" poll 30 target_group_not_ahead
BEFORE="$(committed_sum "$DR" || echo 0)"
check "notification-service runs again with only its bootstrap address changed to edp-dr" fail_over
T_FAILOVER=$SECONDS
check "orders O-3406 to O-3410 are notified" second_orders_notified
echo "measured: failed over and notified in $((SECONDS - T_FAILOVER)) s after the consumer was Ready"
check "the group on edp-dr resumes from the synced offset and reads exactly the 5 later records" resumed_from_synced_offset
check "the MirrorMaker 2 worker reports its 2 connectors through the monitoring base" poll "$BOUND" mm2_workers_reported
check "Grafana serves the dashboard edp-mirrormaker2" poll "$BOUND" grafana_dashboard edp-mirrormaker2
summary
