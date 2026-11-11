#!/usr/bin/env bash
# Starts the stack and runs one order through a stalled partition three ways: keyed by order id,
# keyed by carrier, keyed by event type (the negative witness), and then keyed by event type again
# with the per-order buffer on. KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-04

COMPOSE=(docker compose -f compose.yaml)
SHIP=http://localhost:8080
ORDERS=http://localhost:8082
TOPIC=edp.shipping.shipments
GROUP=order-service
LIFECYCLE=ShipmentScheduled,ShipmentInTransit,ShipmentDelivered
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
TOTAL=0
PASSED=0
PAUSES=""
RUN="$(date +%s)"

cleanup() {
    "${COMPOSE[@]}" unpause order-1 order-2 >/dev/null 2>&1 || true
    if [[ "${KEEP:-0}" == "1" ]]; then
        echo "KEEP=1: stack left running; run \`docker compose down -v\` in this directory to remove it"
    else
        "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

check() { # check "<description>" <command...>
    local desc="$1"; shift
    TOTAL=$((TOTAL + 1))
    if "$@" >/dev/null 2>&1; then
        PASSED=$((PASSED + 1)); printf 'PASS %d: %s\n' "$TOTAL" "$desc"
    else
        printf 'FAIL %d: %s\n' "$TOTAL" "$desc"
    fi
}

poll() { # poll <seconds> <command...>: retry every second until the command succeeds
    local end=$((SECONDS + $1)); shift
    until "$@"; do
        (( SECONDS < end )) || return 1
        sleep 1
    done
}

expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
fail_loudly() { printf 'PRECONDITION FAILED: %s\n' "$1" >&2; exit 1; }

kafka() { docker exec edp-kafka "/opt/kafka/bin/$1" --bootstrap-server localhost:9092 "${@:2}"; }

# "partition=client" per partition of the shipment topic, from the consumer group listing.
assignment() {
    kafka kafka-consumer-groups.sh --describe --group "$GROUP" 2>/dev/null \
        | awk -v t="$TOPIC" '$2 == t && $1 != "GROUP" {print $3 "=" $NF}' | sort
}
group_ready() {
    local a
    a=$(assignment)
    [[ "$(wc -l <<<"$a")" -eq 3 ]] && [[ "$(cut -d= -f2 <<<"$a" | sort -u | paste -sd, -)" == "order-1,order-2" ]]
}
group_idle() { # every partition has committed up to its end
    local lags
    lags=$(kafka kafka-consumer-groups.sh --describe --group "$GROUP" 2>/dev/null \
        | awk -v t="$TOPIC" '$2 == t && $1 != "GROUP" {print $6}')
    [[ -n "$lags" ]] && ! grep -qv '^0$' <<<"$lags"
}
owner_of() { grep "^$1=" <<<"$ASSIGN" | cut -d= -f2; }

end_offsets() { kafka kafka-get-offsets.sh --topic "$1"; } # topic:partition:offset

# edp.shipping.probe exists only to ask the broker which partition its default partitioner gives a key.
# The partition the stock console producer, with the default partitioner, gives a key.
probe_partition() {
    local before after
    before=$(end_offsets edp.shipping.probe)
    printf '%s:x\n' "$1" | docker exec -i edp-kafka /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server localhost:9092 --topic edp.shipping.probe \
        --property parse.key=true --property key.separator=: >/dev/null 2>&1
    after=$(end_offsets edp.shipping.probe)
    awk -F: 'NR == FNR {b[$2] = $3; next} $3 != b[$2] {print $2}' <(echo "$before") <(echo "$after")
}

publish() { curl -s -X POST "$SHIP/shipments/$1${2:+?carrier=$2}"; }
partitions_of() { grep -o '"partition":[0-9]*' | cut -d: -f2; }
sequences_of() { grep -o '"sequence":[0-9]*' | cut -d: -f2 | paste -sd, -; }

history() { "${PSQL[@]}" -c "SELECT coalesce(string_agg(event_type, ',' ORDER BY id), '') FROM orders.order_status_history WHERE order_id='$1'"; }
history_count() { "${PSQL[@]}" -c "SELECT count(*) FROM orders.order_status_history WHERE order_id='$1'"; }
held_count() { "${PSQL[@]}" -c "SELECT count(*) FROM orders.pending_shipment_event WHERE order_id='$1'"; }
final_status() { "${PSQL[@]}" -c "SELECT coalesce(max(status), '') FROM orders.order_status WHERE order_id='$1'"; }
tempo_has_trace() { curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1" | grep -q '"traceID"'; }

# Stalls the replica that owns the Scheduled record's partition, publishes one order, waits until
# the other replica has done what it can, then releases the stall and waits for convergence.
#   stall <order id> <Scheduled key> <Delivered key> <direct|buffer>
stall() {
    local id="$1" skey="$2" dkey="$3" mode="$4" ps pd p
    ASSIGN=$(assignment)
    BEFORE=$ASSIGN
    ps=$(probe_partition "$skey"); pd=$(probe_partition "$dkey")
    PAUSED=$(owner_of "$ps"); OTHER_OWNER_OF_DELIVERED=$(owner_of "$pd")
    echo "  Scheduled record goes to partition $ps (owner $PAUSED); Delivered to partition $pd (owner $OTHER_OWNER_OF_DELIVERED)"
    echo "  group before the stall:" $ASSIGN
    PRE_PAUSED=$PAUSED
    PRE_DELIVERED_OWNER=$OTHER_OWNER_OF_DELIVERED
    [[ -n "$PAUSED" ]] || fail_loudly "no replica owns partition $ps"

    local paused_at=$SECONDS
    "${COMPOSE[@]}" pause "$PAUSED" >/dev/null
    RECEIPTS=$(publish "$id")
    PARTS=$(partitions_of <<<"$RECEIPTS" | paste -sd, -)
    echo "  published $id to partitions $PARTS with $PAUSED paused"
    EXPECT_OTHER=0
    for p in ${PARTS//,/ }; do
        [[ "$(owner_of "$p")" != "$PAUSED" ]] && EXPECT_OTHER=$((EXPECT_OTHER + 1))
    done
    if [[ "$mode" == "direct" ]]; then
        local end=$((SECONDS + 60))
        until [[ "$(history_count "$id")" == "$EXPECT_OTHER" ]]; do (( SECONDS < end )) || break; sleep 1; done
    else
        local end=$((SECONDS + 60))
        until [[ "$(held_count "$id")" == "$EXPECT_OTHER" ]]; do (( SECONDS < end )) || break; sleep 1; done
    fi
    DURING=$(history "$id")
    HELD=$(held_count "$id")
    echo "  applied while $PAUSED is paused: ${DURING:-nothing}; held in the buffer: $HELD"
    "${COMPOSE[@]}" unpause "$PAUSED" >/dev/null
    PAUSES+=$((SECONDS - paused_at))" "
    local end2=$((SECONDS + 90))
    until [[ "$(history_count "$id")" == 3 ]]; do (( SECONDS < end2 )) || break; sleep 1; done
    HISTORY=$(history "$id")
    STATUS=$(final_status "$id")
    HELD_AFTER=$(held_count "$id")
    echo "  applied in the end: $HISTORY (status $STATUS)"
    poll 60 group_ready || true
    AFTER=$(assignment)
    echo "  group after the stall:" $AFTER
}

"${COMPOSE[@]}" up -d --build --wait

check "shipments topic has 3 partitions" \
    expect "$(kafka kafka-topics.sh --describe --topic "$TOPIC" | grep -o 'PartitionCount: [0-9]*' | cut -d' ' -f2)" 3
check "both order replicas are in the group and own all 3 partitions" poll 90 group_ready

# 1. Order-id key: one order, one partition.
echo "Order-id key"
ID="O-$RUN-1"
stall "$ID" "$ID" "$ID" direct
check "precondition: the order's three events share the partition of its key" \
    expect "$(partitions_of <<<"$RECEIPTS" | sort -u | paste -sd, -)" "$(probe_partition "$ID")"
check "events carry per-order sequence numbers 1,2,3" expect "$(sequences_of <<<"$RECEIPTS")" 1,2,3
check "nothing is applied while the replica that owns the partition is paused" expect "$DURING" ""
check "history is in lifecycle order" expect "$HISTORY" "$LIFECYCLE"
check "final status is DELIVERED" expect "$STATUS" DELIVERED
check "no rebalance: assignment unchanged" expect "$AFTER" "$BEFORE"
check "order-1 serves the status over HTTP" \
    expect "$(curl -s "$ORDERS/orders/$ID/status" | grep -o '"status":"[A-Z]*"' | cut -d'"' -f4)" DELIVERED

# 2. Carrier key: order kept, parallelism capped.
echo "Carrier key"
SHIPPING_PARTITION_KEY=carrier "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait shipping-service
USED=""
SPLIT_OK=true
for n in 1 2 3 4 5 6; do
    carrier=FASTLINE; (( n % 2 == 0 )) && carrier=AIRCO
    r=$(publish "O-$RUN-C$n" "$carrier")
    [[ "$(partitions_of <<<"$r" | sort -u | wc -l)" -eq 1 ]] || SPLIT_OK=false
    USED+="$(partitions_of <<<"$r" | sort -u) "
done
check "carrier key: each order stays in one partition" expect "$SPLIT_OK" true
check "carrier key: six orders use at most two of three partitions" \
    expect "$(tr ' ' '\n' <<<"$USED" | grep . | sort -u | wc -l | awk '{print ($1 <= 2) ? "yes" : "no"}')" yes
all_delivered() {
    local n
    for n in 1 2 3 4 5 6; do
        [[ "$(final_status "O-$RUN-C$n")" == DELIVERED && "$(history "O-$RUN-C$n")" == "$LIFECYCLE" ]] || return 1
    done
}
check "carrier key: every order ends DELIVERED in lifecycle order" poll 60 all_delivered

# 3. Event-type key: the negative witness.
echo "Event-type key"
export SHIPPING_PARTITION_KEY=event-type
"${COMPOSE[@]}" up -d --no-deps --force-recreate --wait shipping-service
ID="O-$RUN-2"
stall "$ID" ShipmentScheduled ShipmentDelivered direct
[[ "$PRE_PAUSED" != "$PRE_DELIVERED_OWNER" ]] \
    || fail_loudly "Scheduled and Delivered are owned by the same replica ($PRE_PAUSED); the stall cannot reorder them"
check "precondition: Scheduled and Delivered partitions are owned by different replicas" \
    expect "$([[ "$PRE_PAUSED" != "$PRE_DELIVERED_OWNER" ]] && echo yes)" yes
check "the unpaused replica applied the events it owns while the other was paused" \
    expect "$DURING" "ShipmentInTransit,ShipmentDelivered"
delivered_before_scheduled() {
    [[ "$HISTORY" == *ShipmentDelivered*ShipmentScheduled* ]]
}
check "DELIVERED was applied before SCHEDULED" delivered_before_scheduled
check "final status regressed to SHIPPED" expect "$STATUS" SHIPPED
check "no rebalance: assignment unchanged" expect "$AFTER" "$BEFORE"

# 4. Event-type key with the per-order buffer on.
echo "Event-type key with the buffer"
poll 60 group_idle || true
RESEQUENCE=true "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait order-1 order-2
check "replicas rejoin the group with the buffer on" poll 90 group_ready
ID="O-$RUN-3"
stall "$ID" ShipmentScheduled ShipmentDelivered buffer
[[ "$PRE_PAUSED" != "$PRE_DELIVERED_OWNER" ]] \
    || fail_loudly "Scheduled and Delivered are owned by the same replica ($PRE_PAUSED)"
check "precondition: Scheduled and Delivered partitions are owned by different replicas" \
    expect "$([[ "$PRE_PAUSED" != "$PRE_DELIVERED_OWNER" ]] && echo yes)" yes
check "events that arrived early are held in the buffer while sequence 1 is missing" expect "$HELD" 2
check "none of the held events is applied while sequence 1 is missing" expect "$DURING" ""
check "history is in lifecycle order" expect "$HISTORY" "$LIFECYCLE"
check "final status is DELIVERED" expect "$STATUS" DELIVERED
check "the buffer is empty once sequence 1 arrives" expect "$HELD_AFTER" 0
check "no rebalance: assignment unchanged" expect "$AFTER" "$BEFORE"

# The consumer session timeout is 45 s by default; a longer pause would trigger a rebalance.
all_pauses_short() { local p; for p in $PAUSES; do (( p < 45 )) || return 1; done; }
check "every pause was shorter than the 45 s session timeout" all_pauses_short

check "Tempo has a trace for shipping-service" poll 90 tempo_has_trace shipping-service
check "Tempo has a trace for order-service" poll 90 tempo_has_trace order-service

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
