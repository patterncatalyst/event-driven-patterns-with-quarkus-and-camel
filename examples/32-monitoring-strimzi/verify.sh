#!/usr/bin/env bash
# Runs the chapter 02 pair on Kafka with the Strimzi Metrics Reporter and checks that the signals reach
# Prometheus: broker and controller samples from the reporter, the operator's own metrics, the state of the
# custom resources from kube-state-metrics, and the dashboard. A KafkaTopic made invalid shows Ready 0, and an
# allowList change takes effect without a restart of the pods.
# METRICS=off (demo code) builds the cluster without metricsConfig; see the README for the failing set.
# KEEP=1 leaves the cluster state in place.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../_k8s/lib.sh
source ../_k8s/lib.sh
trap k8s_teardown EXIT

METRICS="${METRICS:-on}"
NOTIFY="http://127.0.0.1:${PORT2}"
BOUND=120          # a Prometheus scrape every 10 s, plus the collector's batch, stays well inside this
TOPIC_BOUND=60     # the Topic Operator reconciles every 15 s (kafka-patch.yaml)
BYTES_IN=kafka_server_brokertopicmetrics_bytesinpersec_total
CONTROLLER_METRIC=kafka_controller_kafkacontroller_activecontrollercount
WIDE='["kafka_.*","jvm_.*"]'
NARROW='["kafka_network_.*","jvm_.*"]'

DIR=.
EXPECT_TYPE=strimziMetricsReporter
if [[ "$METRICS" == off ]]; then
    DIR=demo-metrics-off; EXPECT_TYPE=none
    echo "demo code: the Kafka resource has no metricsConfig"
fi

k8s_fresh "$DIR"

# --- Prometheus, through the published NodePort
q() { python3 -I ./promq.py "$PROM" "$1" "$2"; }            # q <sum|count> <promql>
q_sum() { q sum "$1"; }
q_count() { q count "$1"; }
num() { [[ "$1" != none ]] && echo "$1" || echo 0; }
gt() { awk -v a="$1" -v b="$2" 'BEGIN { exit !(a > b) }'; }  # gt <a> <b>
sum_gt() { local v; v="$(q_sum "$1")" || return 1; [[ "$v" != none ]] && gt "$v" "$2"; }
sum_is() { [[ "$(q_sum "$1")" == "$2" ]]; }
count_is() { [[ "$(q_count "$1")" == "$2" ]]; }
count_gt() { local n; n="$(q_count "$1")" || return 1; (( n > $2 )); }

# --- the Kafka resource and its pods
metrics_type() { local v; v="$(kc -n "$NS_KAFKA" get kafka edp -o jsonpath='{.spec.kafka.metricsConfig.type}')"; echo "${v:-none}"; }
allow_list() { kc -n "$NS_KAFKA" get kafka edp -o jsonpath='{.spec.kafka.metricsConfig.values.allowList}'; }
pools() { kc -n "$NS_KAFKA" get pods -l strimzi.io/kind=Kafka -o jsonpath='{range .items[*]}{.metadata.labels.strimzi\.io/pool-name}{"\n"}{end}' | LC_ALL=C sort | paste -sd' '; }
pod_state() { kc -n "$NS_KAFKA" get pods -l strimzi.io/kind=Kafka -o json | python3 -I ./kafkapods.py; }
controller_pod() { kc -n "$NS_KAFKA" get pods -l strimzi.io/pool-name=controller -o jsonpath='{.items[0].metadata.name}'; }
endpoint() { kc -n "$NS_KAFKA" exec "$1" -- curl -s -m 3 localhost:9404/metrics; }   # endpoint <pod>
endpoint_has() { local out; out="$(endpoint "$1")" || return 1; [[ "$out" == *"$2"* ]]; }
endpoint_lacks() { # the endpoint answers (it still serves jvm_ families) and the family is gone
    local out; out="$(endpoint "$1")" || return 1
    [[ "$out" == *jvm_* && "$out" != *"$2"* ]]
}
serves_everywhere() { endpoint_has edp-broker-0 jvm_ && endpoint_has "$(controller_pod)" jvm_; }

# --- the application flow: one order through the chapter 02 pair
place() { # place <id>
    curl -s -m 90 -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' "$ORDERS/orders" \
        --data "{\"id\":\"$1\",\"customerId\":\"CU-32\",\"itemSku\":\"SKU-32\",\"quantity\":1,\"amount\":19.90}"
}
has_notification() { local out; out="$(curl -sf "$NOTIFY/notifications?orderId=$1")" || return 1; [[ -n "$out" && "$out" != "[]" ]]; }
place_ten() {
    local i id
    poll 150 ready 30080 && poll 150 ready "$PORT2" || return 1
    for i in 01 02 03 04 05 06 07 08 09 10; do
        id="O-32$i"
        [[ "$(place "$id")" == 201 ]] || { echo "order $id was not accepted" >&2; return 1; }
    done
    poll 90 has_notification O-3210
}

# --- custom resource state from kube-state-metrics
declared_topics() { kc -n "$NS_KAFKA" get kafkatopic -o name | wc -l; }
topics_ready_in_ksm() { # every KafkaTopic that exists reads Ready 1
    local want; want="$(declared_topics)"
    (( want > 0 )) && [[ "$(q_count 'edp_kafkatopic_condition{type="Ready"} == 1')" == "$want" ]]
}
topic_ready() { sum_is "edp_kafkatopic_condition{name=\"$1\",type=\"Ready\"}" 1; }
topic_not_supported() { sum_is "edp_kafkatopic_condition{name=\"$1\",type=\"Ready\",reason=\"NotSupported\"}" 0; }

# --- a scratch KafkaTopic (demo code of the verification, named edp.lab.*) made invalid on purpose
LAB=edp.lab.watched
lab_apply() {
    kc apply --server-side --field-manager=edp-verify -f - <<YAML
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata:
  name: $LAB
  namespace: $NS_KAFKA
  labels:
    strimzi.io/cluster: edp
spec:
  partitions: 3
  replicas: 1
YAML
}
raise_partitions() { kafka_exec kafka-topics.sh --alter --topic "$LAB" --partitions 6; }
repair_lab() { # a partition count cannot be lowered: delete the resource (and so the topic) and declare it again
    kc -n "$NS_KAFKA" delete kafkatopic "$LAB" --wait --timeout=60s && lab_apply
}
lab_has_partitions() { # lab_has_partitions <n>
    kafka_exec kafka-topics.sh --describe --topic "$LAB" | awk -v n="$1" 'index($0, "PartitionCount: " n) { found = 1 } END { exit !found }'
}
lab_repaired() { topic_ready "$LAB" && lab_has_partitions 3; }

# --- an allowList change with no restart
BEFORE=""
snapshot() { BEFORE="$(pod_state)"; [[ -n "$BEFORE" ]]; }
patch_allow_list() { kc -n "$NS_KAFKA" patch kafka edp --type merge -p "{\"spec\":{\"kafka\":{\"metricsConfig\":{\"values\":{\"allowList\":$1}}}}}"; }
narrow() { snapshot && patch_allow_list "$NARROW"; }
family_gone() { endpoint_lacks edp-broker-0 "$BYTES_IN"; }
family_back() { patch_allow_list "$WIDE" && poll "$TOPIC_BOUND" endpoint_has edp-broker-0 "$BYTES_IN"; }
narrow_and_wait() { # the family is on the endpoint first, so its absence afterwards is the effect of the change
    endpoint_has edp-broker-0 "$BYTES_IN" && narrow && poll "$TOPIC_BOUND" family_gone
}
family_restored() { family_back && pods_unchanged; }
pods_unchanged() { [[ -n "$BEFORE" && "$(pod_state)" == "$BEFORE" ]]; }

# --- the checks
precondition "the Kafka resource has metricsConfig type $EXPECT_TYPE" expect "$(metrics_type)" "$EXPECT_TYPE"
precondition "the Kafka pods are one broker and one controller" expect "$(pools)" "broker controller"
check "the broker and the controller serve metrics on port 9404" serves_everywhere
check "both pools are scrape targets with up 1" poll "$BOUND" count_is 'count by (node_pool) (up{job="strimzi-kafka"} == 1)' 2
BASE_BYTES="$(num "$(q_sum "sum($BYTES_IN{topic=\"edp.orders.placed\"})" 2>/dev/null || echo none)")"
check "orders O-3201 to O-3210 are placed and O-3210 is notified" place_ten
check "broker bytes in for edp.orders.placed rises after the orders" poll "$BOUND" sum_gt "sum($BYTES_IN{topic=\"edp.orders.placed\"})" "$BASE_BYTES"
check "exactly one active controller is reported" poll "$BOUND" sum_is "sum($CONTROLLER_METRIC)" 1
check "the operator is a scrape target with up 1" poll "$BOUND" sum_is 'sum(up{job="strimzi-operator"})' 1
check "the operator counts reconciliations of Kafka" poll "$BOUND" sum_gt 'strimzi_reconciliations_total{kind="Kafka"}' 0
check "kube-state-metrics reports the Kafka resource Ready" poll "$BOUND" sum_is 'edp_kafka_condition{name="edp",type="Ready"}' 1
check "kube-state-metrics reports every declared KafkaTopic Ready" poll "$BOUND" topics_ready_in_ksm
check "consumer group lag of notification-service is present" poll "$BOUND" count_gt 'kafka_consumer_group_lag_ratio{group="notification-service"}' 0
check "the dashboard edp-strimzi is provisioned" grafana_dashboard edp-strimzi
lab_apply >/dev/null || echo "scratch KafkaTopic was not applied" >&2
precondition "the scratch KafkaTopic $LAB reads Ready 1 in kube-state-metrics" poll "$BOUND" topic_ready "$LAB"
raise_partitions >/dev/null || echo "partitions were not raised" >&2
T0=$SECONDS
check "partitions raised in Kafka make the topic read Ready 0 with reason NotSupported" poll "$TOPIC_BOUND" topic_not_supported "$LAB"
echo "measured: Ready 0 after $((SECONDS - T0)) s"
check "the other declared topic still reads Ready 1" topic_ready edp.orders.placed
repair_lab >/dev/null || echo "scratch KafkaTopic was not recreated" >&2
check "the topic is declared again and reads Ready 1 with 3 partitions" poll "$TOPIC_BOUND" lab_repaired
check "narrowing the allowList removes the bytes-in family from the broker endpoint" narrow_and_wait
check "the broker and controller pods keep their names, uids and restart counts" pods_unchanged
check "restoring the allowList brings the family back, again with no restart" family_restored
summary
