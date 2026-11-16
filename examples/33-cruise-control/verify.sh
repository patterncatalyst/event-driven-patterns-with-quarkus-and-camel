#!/usr/bin/env bash
# Grows a Kafka node pool and checks what Cruise Control does about it: a new broker starts empty, a KafkaRebalance
# proposal moves nothing until it is approved, the approved rebalance puts replicas on the new broker, a scale-down
# that would lose replicas is refused, and with autoRebalance configured the operator rebalances on scale-up and
# drains the broker before it removes it on scale-down.
# AUTO_REBALANCE=off (demo code) never configures autoRebalance, so the second added broker stays empty (see the
# README). KEEP=1 leaves the cluster state in place.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../_k8s/lib.sh
source ../_k8s/lib.sh
trap k8s_teardown EXIT

AUTO_REBALANCE="${AUTO_REBALANCE:-on}"
[[ "$AUTO_REBALANCE" == on || "$AUTO_REBALANCE" == off ]] || { echo "AUTO_REBALANCE is on or off" >&2; exit 2; }
EXPECT_MODES="add-brokers remove-brokers"
if [[ "$AUTO_REBALANCE" == off ]]; then
    EXPECT_MODES=""
    echo "demo code: autoRebalance is never configured"
fi

PROPOSAL_BOUND=240   # a proposal, and an approved rebalance, each within four minutes
AUTO_BOUND=420       # pod start, then the operator's proposal, approval and rebalance
DOWN_BOUND=420
HOLD=20              # seconds a proposal is watched to move nothing
TOPICS=(edp.inventory.reserved edp.orders.placed edp.payments.processed edp.shipping.scheduled)
PARTITIONS=$(( ${#TOPICS[@]} * 6 ))

kk() { kc -n "$NS_KAFKA" "$@"; }

# --- the node pool and Cruise Control
sorted_ids() { kk get kafkanodepool broker -o jsonpath='{.status.nodeIds[*]}' | tr ' ' '\n' | sort -n | paste -sd' ' -; }
id_count() { local ids; ids="$(sorted_ids)"; echo "$(wc -w <<<"$ids")"; }
pool_replicas() { kk get kafkanodepool broker -o jsonpath='{.spec.replicas}'; }
cc_available() { kk rollout status deploy/edp-cruise-control --timeout=5s; }
autorebalance_modes() { kk get kafka edp -o jsonpath='{.spec.cruiseControl.autoRebalance[*].mode}'; }
PREV_IDS=""
NEW_ID=""
scale_pool() { # scale_pool <n>: remember the ids, then change the pool; in-run changes are merge patches
    PREV_IDS="$(sorted_ids)"
    kk patch kafkanodepool broker --type merge -p "{\"spec\":{\"replicas\":$1}}" >/dev/null
}
settled() { [[ "$(id_count)" == "$1" ]] && kafka_ready edp && cc_available; } # settled <brokers>
new_id() { # the one id in the pool that was not there before scale_pool
    local id
    for id in $(sorted_ids); do
        [[ " $PREV_IDS " == *" $id "* ]] || { echo "$id"; return 0; }
    done
    return 1
}
broker_ready() { # the pod of the added broker is Ready
    [[ "$(kk get pod "edp-broker-$1" -o 'jsonpath={.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null)" == True ]]
}
cruise_control_restarted() { # one Cruise Control pod, Ready, created after the added broker's pod: it models the new broker
    local b out
    b="$(kk get pod "edp-broker-$1" -o 'jsonpath={.metadata.creationTimestamp}')" || return 1
    out="$(kk get pod -l app.kubernetes.io/name=cruise-control -o 'jsonpath={range .items[*]}{.metadata.creationTimestamp} {.status.conditions[?(@.type=="Ready")].status}{"\n"}{end}')" || return 1
    [[ "$(wc -l <<<"$out")" == 1 && "$out" == *" True" && "${out%% *}" > "$b" ]]
}
grow_to() { # grow_to <n>: scale; wait for the pool, the new broker pod, Kafka and the restarted Cruise Control; set NEW_ID
    scale_pool "$1"
    poll 420 settled "$1" || return 1
    NEW_ID="$(new_id)" && [[ -n "$NEW_ID" ]] || return 1
    poll 420 broker_ready "$NEW_ID" && poll 420 cruise_control_restarted "$NEW_ID" && poll 120 settled "$1"
}

# --- where the replicas are
describe_all() { kafka_exec_on edp-broker-0 kafka-topics.sh --describe; }
rp() { describe_all | python3 -I ./replica_placement.py "$@"; }
placement() { rp counts; }
holds() { rp count "$1"; }
healthy() { # every partition has two replicas, all in sync
    [[ "$(rp partitions)" == "$PARTITIONS" && "$(rp bad-rf 2)" == 0 && "$(rp urp)" == 0 ]]
}

# --- KafkaRebalance
rb_state() { kk get kafkarebalance "$1" -o jsonpath='{.status.conditions[?(@.status=="True")].type}' 2>/dev/null; }
rb_moves() { kk get kafkarebalance "$1" -o jsonpath='{.status.optimizationResult.numReplicaMovements}'; }
LAST_REFRESH=0
proposal_ready() { # proposal_ready <name>: a NotReady rebalance (Cruise Control restarting) is refreshed now and then
    local s; s="$(rb_state "$1")"
    [[ "$s" == *ProposalReady* ]] && return 0
    if [[ "$s" == *NotReady* ]] && (( SECONDS - LAST_REFRESH >= 20 )); then
        kk annotate kafkarebalance "$1" strimzi.io/rebalance=refresh --overwrite >/dev/null 2>&1 || true
        LAST_REFRESH=$SECONDS
    fi
    return 1
}
rebalance_ready() { [[ "$(rb_state "$1")" == Ready ]]; }
unchanged_for() { # unchanged_for <seconds> <placement> <rebalance>: nothing moves while the proposal waits
    local end=$((SECONDS + $1))
    while (( SECONDS < end )); do
        [[ "$(placement)" == "$2" ]] || return 1
        [[ "$(rb_state "$3")" == *ProposalReady* ]] || return 1
        sleep 3
    done
}

# --- the operator's own rebalance
AUTO_SEEN=0
auto_idle() { # auto_idle <mode>: the operator created edp-auto-rebalancing-<mode> and the cluster is Idle again
    local rb="edp-auto-rebalancing-$1"
    if kk get kafkarebalance "$rb" >/dev/null 2>&1; then AUTO_SEEN=1; return 1; fi
    [[ "$AUTO_SEEN" == 1 && "$(kk get kafka edp -o jsonpath='{.status.autoRebalance.state}')" == Idle ]]
}
auto_removed() { # auto_removed <mode>: the operator's generated KafkaRebalance is gone and the cluster is Idle
    ! kk get kafkarebalance "edp-auto-rebalancing-$1" >/dev/null 2>&1 && [[ "$(kk get kafka edp -o jsonpath='{.status.autoRebalance.state}')" == Idle ]]
}
auto_grow() { # a scale-up with autoRebalance configured
    AUTO_SEEN=0
    grow_to 4 || { diag "grow_to 4"; return 1; }
    poll "$AUTO_BOUND" auto_idle add-brokers || { diag "operator rebalance not Idle"; return 1; }
}

# --- preconditions and checks
topics_ready() {
    local want got t
    want="$(for t in "${TOPICS[@]}"; do echo "$t=2:True"; done)"
    got="$(kk get kafkatopic -o jsonpath='{range .items[*]}{.metadata.name}={.spec.replicas}:{.status.conditions[?(@.type=="Ready")].status}{"\n"}{end}' | LC_ALL=C sort)"
    [[ "$got" == "$want" ]]
}
load_written() {
    kc apply --server-side --field-manager=edp-verify -k load >/dev/null || return 1
    kk wait --for=condition=complete job/perf-load --timeout=300s >/dev/null || return 1
    [[ "$(kk logs job/perf-load | grep -c '100000 records sent' || true)" == "${#TOPICS[@]}" ]]
}
new_broker_empty() { grow_to 3 && [[ "$(holds "$NEW_ID")" == 0 ]]; }
RB=""
proposal_with_moves() {
    RB="add-broker-$NEW_ID"
    sed "s/@ID@/$NEW_ID/g" rebalance/add-brokers.yaml | kc apply --server-side --field-manager=edp-verify -f - >/dev/null || return 1
    poll "$PROPOSAL_BOUND" proposal_ready "$RB" && (( $(rb_moves "$RB") > 0 ))
}
approved_to_ready() {
    kk annotate kafkarebalance "$RB" strimzi.io/rebalance=approve --overwrite >/dev/null || return 1
    poll "$PROPOSAL_BOUND" rebalance_ready "$RB"
}
BEFORE=""
spread_across_brokers() { # the new broker holds replicas and every original broker holds fewer than before
    local entry id n
    [[ "$(holds "$NEW_ID")" -gt 0 ]] || return 1
    for entry in $BEFORE; do
        id="${entry%%=*}"; n="${entry#*=}"
        [[ "$(holds "$id")" -lt "$n" ]] || return 1
    done
}
scale_down_warning() {
    [[ "$(kk get kafka edp -o jsonpath='{.status.conditions[?(@.reason=="ScaleDownPreventionCheck")].message}')" == *"Reverting scale-down"* ]]
}
REFUSED=0
refusal_seen() { scale_down_warning && REFUSED=1; [[ "$REFUSED" == 1 && "$(id_count)" == 3 ]]; }
exec 3>&2
diag() { # what the operator reports, on the run's stderr (fd 3, past check's redirects), when a step fails
    { echo "--- diag: $1"; kk get kafkanodepool broker -o jsonpath='{.spec.replicas} {.status.nodeIds}{"\n"}'
      kk get kafka edp -o jsonpath='{range .status.conditions[*]}{.type} {.status} {.reason} {.message}{"\n"}{end}{.status.autoRebalance.state}{"\n"}'
      kk get kafkarebalance; } >&3 2>&1 || true
}
scale_down_refused() { # without autoRebalance the broker with replicas stays; the spec is then set back to three
    REFUSED=0
    scale_pool 2
    poll 120 refusal_seen || { diag "no refusal seen"; return 1; }
    kk patch kafkanodepool broker --type merge -p '{"spec":{"replicas":3}}' >/dev/null || return 1
    poll 300 settled 3 || { diag "not settled at three"; return 1; }
    [[ "$(holds "$NEW_ID")" -gt 0 ]] || { diag "the broker lost its replicas"; return 1; }
}
configure_auto_rebalance() {
    if [[ "$AUTO_REBALANCE" == on ]]; then
        kc apply --server-side --field-manager=edp-verify -k autorebalance >/dev/null || return 1
        kk patch kafka edp --type merge --patch-file autorebalance/kafka-patch.yaml >/dev/null || return 1
    fi
    [[ "$(autorebalance_modes)" == "$EXPECT_MODES" ]] && poll 240 kafka_ready edp && cc_available
}
new_broker_holds_replicas() { [[ -n "$NEW_ID" && "$(holds "$NEW_ID")" -gt 0 ]] && poll 90 healthy; }
shrink_pool() { # the pool is back at three; with autoRebalance the operator has also deleted its generated KafkaRebalance
    scale_pool 3
    poll "$DOWN_BOUND" settled 3 || return 1
    [[ "$AUTO_REBALANCE" == off ]] || poll "$DOWN_BOUND" auto_removed remove-brokers
}
drained() { # every replica is on a broker that is still in the pool, none is missing, all in sync
    local ids; ids="$(sorted_ids)"
    [[ "$(wc -w <<<"$ids")" == 3 && "$(rp outside "$ids")" == 0 ]] && poll 90 healthy
}

k8s_fresh .
OLD_IDS="$(sorted_ids)"

precondition "Kafka edp has Cruise Control and its Deployment is Available" cc_available
precondition "the broker pool has two nodes and no autoRebalance is configured" \
    expect "$(id_count)|$(autorebalance_modes)" "2|"
check "the four catalog topics are KafkaTopics with two replicas and are Ready" poll 90 topics_ready
check "a load Job writes 100000 records to each of the four topics" load_written
BEFORE_ALL="$(placement)"
check "every replica sits on the two original brokers: $OLD_IDS" expect "$BEFORE_ALL" "$(for id in $OLD_IDS; do printf '%s=%s ' "$id" "$PARTITIONS"; done | sed 's/ $//')"
T0=$SECONDS
check "after the pool grows to three, the new broker pod, Kafka and Cruise Control are Ready again and the new broker holds no replica" new_broker_empty
echo "measured: pool grown to three nodes, the new broker and Cruise Control Ready in $((SECONDS - T0)) s; new broker id $NEW_ID"
BEFORE="$(placement)"
T0=$SECONDS
check "an add-brokers KafkaRebalance for the new broker reaches ProposalReady with replica movements" proposal_with_moves
echo "measured: proposal ready after $((SECONDS - T0)) s, $(rb_moves "$RB" 2>/dev/null || echo 0) replica movements"
check "the proposal moves nothing while it is unapproved" unchanged_for "$HOLD" "$BEFORE" "$RB"
T0=$SECONDS
check "after the approve annotation the rebalance reaches Ready" approved_to_ready
echo "measured: approved to Ready in $((SECONDS - T0)) s"
check "the new broker holds replicas and every original broker holds fewer" spread_across_brokers
check "every partition has two replicas, all in sync" poll 90 healthy
check "without autoRebalance, scaling down a broker that holds replicas is refused" scale_down_refused
check "the Kafka resource carries autoRebalance for '${EXPECT_MODES:-nothing}' and is Ready" configure_auto_rebalance
T0=$SECONDS
check "the operator rebalances a pool that grows to four with no KafkaRebalance written by hand" auto_grow
echo "measured: pool grown to four, operator rebalance Idle after $((SECONDS - T0)) s"
check "the added broker holds replicas and every partition is fully replicated" new_broker_holds_replicas
T0=$SECONDS
check "the pool shrinks back to three nodes and the operator removes its own KafkaRebalance when it is done" shrink_pool
echo "measured: pool back at three nodes and Ready after $((SECONDS - T0)) s"
check "after the scale-down every replica is on a remaining broker and in sync" drained
summary
