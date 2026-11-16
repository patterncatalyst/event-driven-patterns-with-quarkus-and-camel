#!/usr/bin/env bash
# Starts the stack and checks the observable effects of resequencing shipment events across partitions and topics.
# Events of one order sit on three topics and in different partitions, so they arrive in no order. The stream form
# restores the order from a global sequence; the same events keyed by the per-order number are lost without a message;
# a gap is skipped at the timeout; a late event is applied last, or rejected; capacity blocks the consumer; the batch form
# sorts inside one batch only; and records held at a restart are lost because their offsets were already committed.
# RESEQUENCER=none is the negative witness (see the README). KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-ae

COMPOSE=(docker compose -f compose.yaml)
APP=edp-order-service
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
IN_S=edp.shipping.scheduled
IN_T=edp.shipping.in-transit
IN_D=edp.shipping.delivered
OUT=edp.shipping.shipments
GROUP=resequencer
TIMEOUT_MS=2000
TOTAL=0
PASSED=0
WORK=$(mktemp -d)
TAILERS=()

cleanup() {
    docker exec edp-kafka pkill -f ConsoleConsumer >/dev/null 2>&1 || true
    kill "${TAILERS[@]}" >/dev/null 2>&1 || true
    rm -rf "$WORK"
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

precondition() { # precondition "<description>" <command...>: counted as a check; failing it stops the run
    local desc="$1"; shift
    TOTAL=$((TOTAL + 1))
    if "$@" >/dev/null 2>&1; then
        PASSED=$((PASSED + 1)); printf 'PASS %d: %s\n' "$TOTAL" "$desc"
    else
        printf 'PRECONDITION FAILED %d: %s\n' "$TOTAL" "$desc" >&2; exit 1
    fi
}

poll() { # poll <seconds> <command...>: retry every second until the command succeeds
    local end=$((SECONDS + $1)); shift
    until "$@"; do
        (( SECONDS < end )) || return 1
        sleep 1
    done
}

stable_for() { # stable_for <seconds> <command...>: succeeds once the command's output has not changed for that long
    local need=$1 last="" cur since=$SECONDS end=$((SECONDS + 90)); shift
    while (( SECONDS < end )); do
        cur="$("$@" 2>/dev/null || true)"
        if [[ "$cur" != "$last" ]]; then last="$cur"; since=$SECONDS; fi
        if (( SECONDS - since >= need )); then STABLE="$cur"; return 0; fi
        sleep 1
    done
    return 1
}

expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
fail_loudly() { printf 'PRECONDITION FAILED: %s\n' "$1" >&2; exit 1; }

kafka() { docker exec edp-kafka "/opt/kafka/bin/$1" --bootstrap-server localhost:9092 "${@:2}"; }
sql() { "${PSQL[@]}" -c "$1"; }
live() { curl -sf http://localhost:8080/q/health/live >/dev/null; }
logs() { docker logs "$APP" 2>&1; }
now_ms() { date +%s%3N; }

# --- the topics and the group
topics_init_done() { [[ "$(docker inspect -f '{{.State.Status}}/{{.State.ExitCode}}' edp-topics-init)" == "exited/0" ]]; }
partitions_of() { kafka kafka-topics.sh --describe --topic "$1" 2>/dev/null | grep -c 'Partition: [0-9]' || true; }
topics_have_three_partitions() { [[ "$(for t in "$IN_S" "$IN_T" "$IN_D" "$OUT"; do partitions_of "$t"; done | tr '\n' ' ')" == "3 3 3 3 " ]]; }
group_rows() { kafka kafka-consumer-groups.sh --describe --group "$GROUP" 2>/dev/null || true; }
app_ip() { docker inspect -f '{{(index .NetworkSettings.Networks "edp").IPAddress}}' "$APP"; }
group_assigned() { # all nine partitions of the three input topics belong to this container's consumer
    local ip; ip="$(app_ip)"
    [[ "$(group_rows | awk -v ip="/$ip" '$3 ~ /^[0-9]+$/ && $7 != "-" && $8 == ip {n++} END {print n + 0}')" == 9 ]]
}
running() { live && group_assigned; }
committed_all() { # every partition has a committed offset equal to its end offset: the client has acknowledged all records
    group_rows | awk '$3 ~ /^[0-9]+$/ {n++; if ($4 ~ /^[0-9]+$/) c += $4; e += $5} END {exit !(n == 9 && c == e)}'
}
unconsumed() { group_rows | awk '$3 ~ /^[0-9]+$/ {c += ($4 ~ /^[0-9]+$/ ? $4 : 0); e += $5} END {print e - c}'; }

phase() { # phase <mode> <sequence-by> <timeout ms> <capacity> <reject-old> <batch size> <batch timeout ms>: recreate the service
    RESEQUENCER=$1 SEQUENCE_BY=$2 RESEQUENCER_TIMEOUT_MS=$3 RESEQUENCER_CAPACITY=$4 RESEQUENCER_REJECT_OLD=$5 \
        RESEQUENCER_BATCH_SIZE=$6 RESEQUENCER_BATCH_TIMEOUT_MS=$7 \
        "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait order-service
    poll 90 running || fail_loudly "order-service is not running with its consumer assigned after the recreate"
}

# --- input: ShipmentEvent as JSON with a key. The key is chosen per event, so the events scatter over the partitions.
ev() { # ev <key> <order> <sequence> <global sequence>: one "key|json" line
    local type
    case "$3" in 1) type=ShipmentScheduled;; 2) type=ShipmentInTransit;; *) type=ShipmentDelivered;; esac
    printf '%s|{"orderId":"%s","type":"%s","sequence":%s,"globalSequence":%s,"carrier":"FASTLINE"}\n' "$1" "$2" "$type" "$3" "$4"
}
produce() { # produce <topic>: "key|json" lines on stdin, one producer call
    docker exec -i edp-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic "$1" \
        --reader-property parse.key=true --reader-property 'key.separator=|' >/dev/null 2>&1
}

# --- what the service applied (the identity id is the order of application) and published
applied_g() { sql "SELECT coalesce(string_agg(global_sequence::text, ',' ORDER BY id), '') FROM resequencing.shipment_applied WHERE global_sequence BETWEEN $1 AND $2"; }
applied_is() { [[ "$(applied_g "$1" "$2")" == "$3" ]]; } # applied_is <from> <to> <comma separated global sequences in order>
applied_ms() { sql "SELECT (extract(epoch FROM $1(applied_at)) * 1000)::bigint FROM resequencing.shipment_applied WHERE global_sequence $2"; }
seq_list() { seq -s, "$1" "$2"; }

out_summary() { # "<order>|<sequences in output order>|<partitions used>" per order read from the shipments topic
    python3 -I -c '
import json, sys
orders = {}
for line in open(sys.argv[1], errors="replace"):
    parts = line.rstrip("\n").split("\t", 2)
    if len(parts) != 3 or not parts[0].startswith("Partition:"):
        continue
    try:
        v = json.loads(parts[2])
    except ValueError:
        continue
    seqs, parts_used = orders.setdefault(parts[1], ([], set()))
    seqs.append(str(v["sequence"]))
    parts_used.add(parts[0])
for order in sorted(orders):
    seqs, parts_used = orders[order]
    print("%s|%s|%d" % (order, ",".join(seqs), len(parts_used)))
' "$WORK/out.tsv"
}
every_order_in_sequence() { # all four orders of the first scenario hold 1,2,3 in that order on the shipments topic
    local s; s="$(out_summary)"
    for o in O-4001 O-4002 O-4003 O-4004; do [[ $'\n'"$s"$'\n' == *$'\n'"$o|1,2,3|"* ]] || return 1; done
}
every_order_in_one_partition() {
    local s; s="$(out_summary)"
    for o in O-4001 O-4002 O-4003 O-4004; do [[ $'\n'"$s"$'\n' == *$'\n'"$o|1,2,3|1"$'\n'* ]] || return 1; done
}

# --- the service log of the current container
arrived() { logs | sed -n 's/.*arrived g=\([0-9]*\) .*/\1/p'; }
arrival_scrambled() { # the twelve events of the first scenario arrived, and not in global order
    local a; a="$(arrived | awk '$1 >= 1 && $1 <= 12' | tr '\n' ' ')"
    [[ "$(wc -w <<<"$a")" == 12 && "$a" != "$(seq -s' ' 1 12) " ]]
}
logged_rejection() { logs | grep -F "rejected $1" >/dev/null; }
nothing_logged() { [[ -z "$(logs | grep -E ' ERROR |rejected O-' || true)" ]]; }

# --- observability
tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 topics_init_done || fail_loudly "topics-init did not finish"
( docker exec edp-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic "$OUT" \
    --from-beginning --timeout-ms 3600000 --formatter-property print.partition=true --formatter-property print.key=true \
    2>/dev/null >> "$WORK/out.tsv" ) &
TAILERS+=("$!")

echo "Infrastructure"
precondition "order-service is live and its consumer owns all nine partitions of the three input topics" poll 90 running
precondition "the three input topics and the shipments topic have three partitions each" topics_have_three_partitions
check "the schema holds the applied-events table and the migration history" \
    expect "$(sql "SELECT string_agg(table_name, ',' ORDER BY table_name) FROM information_schema.tables WHERE table_schema = 'resequencing'")" \
    "flyway_schema_history,shipment_applied"

echo "Stream form, global sequence: twelve events of four orders, three topics, two producers"
# Global order: 1 O-4001 scheduled, 2 O-4002 s, 3 O-4001 in-transit, 4 O-4003 s, 5 O-4002 t, 6 O-4001 delivered,
# 7 O-4004 s, 8 O-4003 t, 9 O-4002 d, 10 O-4004 t, 11 O-4003 d, 12 O-4004 d.
T1=$(now_ms)
{ ev k1 O-4001 3 6; ev k2 O-4002 3 9; ev k3 O-4003 3 11; ev k4 O-4004 3 12; } | produce "$IN_D" &
PA=$!
{ ev k5 O-4001 2 3; ev k6 O-4002 2 5; ev k7 O-4003 2 8; ev k8 O-4004 2 10; } | produce "$IN_T" &
PB=$!
wait "$PA" "$PB"
{ ev k9 O-4001 1 1; ev k10 O-4002 1 2; ev k11 O-4003 1 4; ev k12 O-4004 1 7; } | produce "$IN_S"
precondition "the twelve events reached the service out of global order" poll 60 arrival_scrambled
check "the twelve events are applied in global order 1 to 12" poll 40 applied_is 1 12 "$(seq_list 1 12)"
check "on the shipments topic every order holds its events 1, 2, 3 in that order" poll 40 every_order_in_sequence
check "every order's events sit in one partition of the shipments topic" every_order_in_one_partition
first_release_waited() { (( $(applied_ms min "BETWEEN 1 AND 12") - T1 >= TIMEOUT_MS - 200 )); }
check "the first release waits for the timeout: no event has a predecessor to confirm it" first_release_waited

echo "A gap: 16 is lost, 13, 14, 15, 17 and 18 arrive"
T2=$(now_ms)
{ ev k13 O-4007 1 15; ev k14 O-4005 1 13; ev k15 O-4009 1 18; ev k16 O-4006 1 14; ev k17 O-4008 1 17; } | produce "$IN_S"
check "the events after the gap are applied in order and 16 is absent" poll 40 applied_is 13 18 "13,14,15,17,18"
gap_waited() { (( $(applied_ms min "= 17") - $(applied_ms max "= 15") >= TIMEOUT_MS / 2 )); }
check "13 to 15 are released at once and 17 and 18 wait for the timeout" gap_waited
echo "Late: 16 arrives after 18 was applied (rejectOld is off)"
ev k18 O-4010 1 16 | produce "$IN_S"
check "the late event is applied when it arrives, after 18" poll 40 applied_is 13 18 "13,14,15,17,18,16"

echo "Stream form on the per-order number: the same twelve events with SEQUENCE_BY=order"
phase stream order "$TIMEOUT_MS" 1000 false 10 3000
{ ev m1 O-4021 3 106; ev m2 O-4022 3 109; ev m3 O-4023 3 111; ev m4 O-4024 3 112; } | produce "$IN_D" &
PA=$!
{ ev m5 O-4021 2 103; ev m6 O-4022 2 105; ev m7 O-4023 2 108; ev m8 O-4024 2 110; } | produce "$IN_T" &
PB=$!
wait "$PA" "$PB"
{ ev m9 O-4021 1 101; ev m10 O-4022 1 102; ev m11 O-4023 1 104; ev m12 O-4024 1 107; } | produce "$IN_S"
applied_count() { sql "SELECT count(*) FROM resequencing.shipment_applied WHERE global_sequence BETWEEN 101 AND 112"; }
STABLE=""
stable_for 8 applied_count || fail_loudly "the applied count did not settle"
echo "NOTE: per-order sequence: $STABLE of 12 events applied"
fewer_without_a_message() { (( STABLE >= 1 && STABLE < 12 )) && nothing_logged; }
check "some of the twelve events are never applied, and the service logs nothing about it" fewer_without_a_message

echo "Stream form with rejectOld: 204 is lost and arrives late"
phase stream global "$TIMEOUT_MS" 1000 true 10 3000
{ ev r1 O-4031 1 201; ev r2 O-4032 1 202; ev r3 O-4033 1 203; ev r4 O-4034 1 205; ev r5 O-4035 1 206; } | produce "$IN_S"
poll 40 applied_is 201 206 "201,202,203,205,206" || fail_loudly "the five events were not applied"
ev r6 O-4036 1 204 | produce "$IN_S"
check "the late event is rejected and logged, not applied" poll 40 logged_rejection O-4036
check "the five events before it were applied in order" applied_is 201 206 "201,202,203,205,206"

echo "Batch form: twelve events on one partition, batches of six"
phase batch global "$TIMEOUT_MS" 1000 false 6 3000
{ ev b O-4041 2 303; ev b O-4042 1 302; ev b O-4042 2 305; ev b O-4043 1 304; ev b O-4043 2 308; ev b O-4044 1 307
  ev b O-4041 1 301; ev b O-4041 3 306; ev b O-4042 3 309; ev b O-4043 3 311; ev b O-4044 2 310; ev b O-4044 3 312; } | produce "$IN_S"
batch_order() { sql "SELECT string_agg(order_id || ':' || sequence, ',' ORDER BY id) FROM resequencing.shipment_applied WHERE order_id LIKE 'O-404%'"; }
batches_sorted() {
    [[ "$(batch_order)" == "O-4041:2,O-4042:1,O-4042:2,O-4043:1,O-4043:2,O-4044:1,O-4041:1,O-4041:3,O-4042:3,O-4043:3,O-4044:2,O-4044:3" ]]
}
check "each batch of six is sorted by order and sequence" poll 40 batches_sorted
inverted() { [[ "$(sql "SELECT (SELECT id FROM resequencing.shipment_applied WHERE order_id = 'O-4041' AND sequence = 2) < (SELECT id FROM resequencing.shipment_applied WHERE order_id = 'O-4041' AND sequence = 1)")" == "t" ]]; }
check "an order split over two batches is applied out of order: event 2 before event 1" inverted

echo "Capacity: five events held, 401 missing, 402 to 412 sent, timeout 8 s"
CAP_TIMEOUT_MS=8000
phase stream global "$CAP_TIMEOUT_MS" 5 false 10 3000
T5=$(now_ms)
{ for g in 402 403 404 405 406 407 408 409 410 411 412; do ev "c$g" "O-$((4051 + g - 402))" 1 "$g"; done; } | produce "$IN_S"
blocked_while_held() { # for six seconds: records are unconsumed and nothing is applied
    local end=$((SECONDS + 6))
    while (( SECONDS < end )); do
        (( $(unconsumed) > 0 )) && [[ -z "$(applied_g 402 412)" ]] || return 1
    done
}
check "with the buffer full the consumer stops: records stay unconsumed and nothing is applied before the timeout" blocked_while_held
released_at_timeout() { (( $(applied_ms min "BETWEEN 402 AND 412") - T5 >= CAP_TIMEOUT_MS - 1000 )); }
check "the timeout releases all eleven in order, no error" \
    poll 40 applied_is 402 412 "$(seq_list 402 412)"
check "nothing was released before the timeout" released_at_timeout

echo "Restart while holding: 502 to 506 held, 501 missing, timeout 60 s"
phase stream global 60000 1000 false 10 3000
{ for g in 502 503 504 505 506; do ev "h$g" "O-$((4071 + g - 502))" 1 "$g"; done; } | produce "$IN_S"
held_and_committed() { committed_all && [[ -z "$(applied_g 502 506)" ]]; }
precondition "five events are held, none applied, and the consumer has committed all of them" poll 40 held_and_committed
phase stream global "$TIMEOUT_MS" 1000 false 10 3000
ev h501 O-4070 1 501 | produce "$IN_S"
poll 40 applied_is 501 501 "501" || fail_loudly "501 was not applied after the restart"
nothing_more() { applied_count_held=$(applied_g 502 506); echo "$applied_count_held"; }
stable_for 8 nothing_more || fail_loudly "the applied set did not settle"
check "after the restart only 501 is applied: the five held events are lost, their offsets were committed" expect "$STABLE" ""

echo "Observability"
check "Tempo has a trace for order-service" poll 90 tempo_has_trace order-service
check "Loki has logs for order-service" poll 90 loki_has_log order-service

if [[ "$PASSED" != "$TOTAL" ]]; then
    echo "order-service log, last lines:" >&2
    logs | tail -40 | cut -c1-260 >&2 || true
fi
echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
