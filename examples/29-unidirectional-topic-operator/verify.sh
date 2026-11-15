#!/usr/bin/env bash
# Declares the event catalog's topics as KafkaTopic resources on a Kafka cluster with auto-create off and checks
# what the Unidirectional Topic Operator does with them: the set equals Topics.ALL, drift in Kafka is reverted,
# a deleted topic is recreated, a partition increase made in Kafka is reported, a topic created in Kafka gets no
# resource, and deleting a KafkaTopic deletes the topic unless it was unmanaged first.
# TOPIC_AUTOCREATE=on (demo code) turns broker auto-create back on; TOPIC_SET=missing (demo code) applies the set
# one KafkaTopic short (see the README). KEEP=1 leaves the cluster state in place.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../_k8s/lib.sh
source ../_k8s/lib.sh
trap k8s_teardown EXIT

TOPIC_AUTOCREATE="${TOPIC_AUTOCREATE:-off}"
TOPIC_SET="${TOPIC_SET:-full}"
INTERVAL=15                  # reconciliationIntervalMs of kafka-patch.yaml, in seconds
BOUND=$((3 * INTERVAL))      # a change made in Kafka is seen within about three intervals
NOTIFY="http://127.0.0.1:${PORT2}"
KT_FINALIZER=strimzi.io/topic-operator
PORT_ORDER=30080

DIR=.
EXPECT_AUTOCREATE=false
if [[ "$TOPIC_AUTOCREATE" == on && "$TOPIC_SET" == missing ]]; then
    echo "TOPIC_AUTOCREATE=on and TOPIC_SET=missing are separate demos; set one" >&2; exit 2
elif [[ "$TOPIC_AUTOCREATE" == on ]]; then
    DIR=demo-autocreate; EXPECT_AUTOCREATE=true
    echo "demo code: broker auto-create is on"
elif [[ "$TOPIC_SET" == missing ]]; then
    DIR=demo-missing-topic
    export WAIT_APPS=0   # the apps may not become ready without edp.orders.placed; the checks poll for them
    echo "demo code: the applied set lacks edp.orders.placed"
fi

k8s_fresh "$DIR"

# --- reading the KafkaTopic resources and Kafka
kt() { kc -n "$NS_KAFKA" get kafkatopic -o json | python3 -I ./kafkatopic_state.py "$@"; }
kt_gone() { ! kc -n "$NS_KAFKA" get kafkatopic "$1" >/dev/null 2>&1; }
topic_list() { kafka_exec kafka-topics.sh --list; }
topic_present() { local out; out="$(topic_list)" || return 1; [[ -n "$(awk -v t="$1" '$1 == t' <<<"$out")" ]]; }
topic_absent() { local out; out="$(topic_list)" || return 1; [[ -z "$(awk -v t="$1" '$1 == t' <<<"$out")" ]]; }
describe_line() { kafka_exec kafka-topics.sh --describe --topic "$1" | awk -v t="$1" '$1 == "Topic:" && $2 == t && /PartitionCount/'; }
field_of() { # field_of "<describe line>" <label>: the value after "<label>:"
    awk -F'\t' -v l="$2:" '{for (i = 1; i <= NF; i++) { gsub(/^ +/, "", $i); if (index($i, l) == 1) { sub(l " ", "", $i); print $i; exit } }}' <<<"$1"
}
partitions_in_kafka() { field_of "$(describe_line "$1")" PartitionCount; }
topic_id() { field_of "$(describe_line "$1")" TopicId; }
retention_in_kafka() {
    kafka_exec kafka-configs.sh --describe --entity-type topics --entity-name "$1" \
        | awk '{for (i = 1; i <= NF; i++) if ($i ~ /^retention\.ms=/) { sub(/^retention\.ms=/, "", $i); print $i; exit }}'
}
expected_names() { python3 -I ./topics_all.py ../domain-model/src/main/java/com/patterncatalyst/shipping/Topics.java | LC_ALL=C sort; }

# --- the broker settings and the Topic Operator
autocreate_in_cr() { kc -n "$NS_KAFKA" get kafka edp -o json | python3 -I -c '
import json, sys
print(str(json.load(sys.stdin)["spec"]["kafka"]["config"].get("auto.create.topics.enable")).lower())'; }
operator_interval() { kc -n "$NS_KAFKA" get deployment edp-entity-operator -o json | python3 -I -c '
import json, sys
for c in json.load(sys.stdin)["spec"]["template"]["spec"]["containers"]:
    if c["name"] == "topic-operator":
        for e in c.get("env", []):
            if e["name"] == "STRIMZI_FULL_RECONCILIATION_INTERVAL_MS":
                print(e["value"])'; }

# --- the declared set against Topics.ALL, and against Kafka
set_equals_all() { [[ "$(kt names)" == "$(expected_names)" ]]; }
all_ready() { [[ -z "$(kt not-ready)" ]] && [[ -n "$(kt names)" ]]; }
kafka_matches_spec() { # every KafkaTopic's partitions and declared retention are what Kafka holds
    local t spec parts ret
    while IFS= read -r t; do
        spec="$(kt spec "$t")"; parts="${spec% *}"; ret="${spec#* }"
        [[ "$(partitions_in_kafka "$t")" == "$parts" ]] || { echo "partitions of $t" >&2; return 1; }
        if [[ "$ret" != "-" ]]; then
            [[ "$(retention_in_kafka "$t")" == "$ret" ]] || { echo "retention of $t" >&2; return 1; }
        fi
    done < <(kt names)
}

# --- one order through the chapter 02 pair
place() { # place <id>
    curl -s -m 90 -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' "$ORDERS/orders" \
        --data "{\"id\":\"$1\",\"customerId\":\"CU-29\",\"itemSku\":\"SKU-29\",\"quantity\":1,\"amount\":19.90}"
}
has_notification() { local out; out="$(curl -sf "$NOTIFY/notifications?orderId=$1")" || return 1; [[ -n "$out" && "$out" != "[]" ]]; }
order_notified() {
    poll 150 ready "$PORT_ORDER" && poll 150 ready "$PORT2" || return 1
    [[ "$(place O-2901)" == 201 ]] && poll 90 has_notification O-2901
}

# --- an undeclared topic
produce_one() { # produce_one <topic>: one record through the console producer, output on stdout
    echo probe | kc -n "$NS_KAFKA" exec -i edp-broker-0 -- /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server localhost:9092 --topic "$1" --producer-property max.block.ms=8000 2>&1
}
undeclared_refused() {
    local ok bad
    ok="$(produce_one edp.shipping.delivered)" || true
    [[ "$ok" != *"not present in metadata"* ]] || { echo "control produce failed" >&2; return 1; }
    bad="$(produce_one edp.lab.undeclared)" || true
    [[ "$bad" == *"Topic edp.lab.undeclared not present in metadata after 8000 ms"* ]] || return 1
    topic_absent edp.lab.undeclared
}

# --- scratch KafkaTopics (demo code of the verification, named edp.lab.*) for the lifecycle experiments
LAB=(drift recreated growth removed kept)
lab_apply() {
    local t
    for t in "${LAB[@]}"; do
        cat <<YAML
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata:
  name: edp.lab.$t
  namespace: $NS_KAFKA
  labels:
    strimzi.io/cluster: edp
spec:
  partitions: 3
  replicas: 1
  config:
    retention.ms: 3600000
---
YAML
    done | kc apply --server-side --field-manager=edp-verify -f -
}
lab_ready() {
    local t
    for t in "${LAB[@]}"; do [[ "$(kt ready "edp.lab.$t")" == True ]] || return 1; done
}

# --- out-of-band changes, all started together so each one is observed within its own bound
ID_RECREATED=""
T0=0
trigger_changes() {
    ID_RECREATED="$(topic_id edp.lab.recreated)"
    [[ -n "$ID_RECREATED" ]]
    kafka_exec kafka-configs.sh --alter --entity-type topics --entity-name edp.lab.drift --add-config retention.ms=60000 >/dev/null
    kafka_exec kafka-topics.sh --delete --topic edp.lab.recreated >/dev/null
    kafka_exec kafka-topics.sh --alter --topic edp.lab.growth --partitions 6 >/dev/null
    kafka_exec kafka-topics.sh --create --topic edp.lab.outside --partitions 1 --replication-factor 1 >/dev/null
    T0=$SECONDS
}
drift_reverted() { [[ "$(retention_in_kafka edp.lab.drift)" == 3600000 ]]; }
recreated() {
    local id; id="$(topic_id edp.lab.recreated)" || return 1
    [[ -n "$id" && "$id" != "$ID_RECREATED" && "$(kt ready edp.lab.recreated)" == True ]]
}
growth_reported() { [[ "$(kt reason edp.lab.growth)" == "NotSupported|Decreasing partitions not supported" ]]; }
growth_kept() { [[ "$(kt spec edp.lab.growth)" == "3 3600000" && "$(partitions_in_kafka edp.lab.growth)" == 6 ]]; }
outside_gets_no_resource() { # the topic exists, and no KafkaTopic names it until the window of three intervals has passed
    topic_present edp.lab.outside || return 1
    while (( SECONDS < T0 + BOUND )); do
        [[ -z "$(kt names-topic edp.lab.outside)" ]] || return 1
        sleep 1
    done
    [[ -z "$(kt names-topic edp.lab.outside)" ]]
}
delete_removes_topic() { kc -n "$NS_KAFKA" delete kafkatopic edp.lab.removed >/dev/null && poll "$BOUND" topic_absent edp.lab.removed; }
unmanage() { kc -n "$NS_KAFKA" annotate kafkatopic edp.lab.kept strimzi.io/managed=false --overwrite >/dev/null; }
unmanaged_status() { [[ "$(kt unmanaged edp.lab.kept)" == True ]]; }
stays_present() { # stays_present <topic> <seconds>
    local end=$((SECONDS + $2))
    while (( SECONDS < end )); do topic_present "$1" || return 1; sleep 1; done
    topic_present "$1"
}
unmanaged_delete_keeps_topic() {
    kc -n "$NS_KAFKA" delete kafkatopic edp.lab.kept >/dev/null && kt_gone edp.lab.kept && stays_present edp.lab.kept 6
}

precondition "broker auto.create.topics.enable is $EXPECT_AUTOCREATE in the Kafka resource" expect "$(autocreate_in_cr)" "$EXPECT_AUTOCREATE"
precondition "the Topic Operator in the entity operator reconciles every ${INTERVAL}000 ms" expect "$(operator_interval)" "${INTERVAL}000"
check "the KafkaTopic names equal Topics.ALL of the domain model" set_equals_all
check "every KafkaTopic is Ready" poll 90 all_ready
check "Kafka holds the partitions and the retention each KafkaTopic declares" kafka_matches_spec
check "order O-2901 is placed and its notification arrives" order_notified
check "a producer to an undeclared topic is refused and no topic is created" undeclared_refused
T_APPLY=$SECONDS
lab_apply
precondition "the five scratch KafkaTopics are Ready" poll 90 lab_ready
echo "measured: five KafkaTopics applied to Ready in $((SECONDS - T_APPLY)) s"
check "a KafkaTopic carries the finalizer $KT_FINALIZER" expect "$(kt finalizers edp.lab.removed)" "$KT_FINALIZER"
trigger_changes
check "a retention declared in the KafkaTopic and changed in Kafka is reverted within three intervals" poll "$BOUND" drift_reverted
echo "measured: retention reverted after $((SECONDS - T0)) s"
check "a topic deleted in Kafka is recreated and its KafkaTopic is Ready again" poll "$BOUND" recreated
echo "measured: topic recreated after $((SECONDS - T0)) s"
check "partitions raised in Kafka make the KafkaTopic Ready=False with reason NotSupported" poll "$BOUND" growth_reported
check "the KafkaTopic keeps 3 partitions and Kafka keeps the 6" growth_kept
check "a topic created in Kafka gets no KafkaTopic" outside_gets_no_resource
T_RM=$SECONDS
check "deleting a KafkaTopic deletes the topic" delete_removes_topic
echo "measured: KafkaTopic deleted, topic gone in $((SECONDS - T_RM)) s"
unmanage
check "managed=false marks the KafkaTopic Unmanaged" poll "$BOUND" unmanaged_status
check "deleting an unmanaged KafkaTopic leaves the topic in Kafka" unmanaged_delete_keeps_topic
summary
