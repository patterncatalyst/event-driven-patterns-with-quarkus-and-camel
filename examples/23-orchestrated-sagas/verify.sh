#!/usr/bin/env bash
# Starts the stack and checks the same saga run by two orchestrators over the same participants: a Camel saga with an
# LRA coordinator, and a Quarkus Flow workflow with its state in PostgreSQL. Both reach PAID, both cancel on a declined
# payment, and both compensate when payment-service does not answer within the time limit. An order placed while
# payment-service is down and order-service is recreated shows what survives. PAYMENT_JOIN=off and
# FLOW_COMPENSATION=off are the negative witnesses (see the README). REPEAT=15 repeats the payment-down scenario.
# KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-23

COMPOSE=(docker compose -f compose.yaml)
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
TIMEOUT_S=20   # the time limit of a saga in both orchestrators (SAGA_TIMEOUT, compose default PT20S)
REPEAT=${REPEAT:-0}
TOTAL=0
PASSED=0
REPEAT_FAILED=0
WORK=$(mktemp -d)

cleanup() {
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

holds() { # holds <seconds> <command...>: the command succeeds every second for that long
    local end=$((SECONDS + $1)); shift
    while (( SECONDS < end )); do
        "$@" || return 1
        sleep 1
    done
}

expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
fail_loudly() { printf 'PRECONDITION FAILED: %s\n' "$1" >&2; exit 1; }
now_ms() { date +%s%3N; }

kafka() { docker exec edp-kafka "/opt/kafka/bin/$1" --bootstrap-server localhost:9092 "${@:2}"; }
sql() { "${PSQL[@]}" -c "$1"; }
ready() { curl -sf "http://localhost:$1/q/health/ready" >/dev/null; }
live() { curl -sf "http://localhost:$1/q/health/live" >/dev/null; }
coordinator_ready() { curl -sf http://localhost:8091/q/health/ready >/dev/null; }
logs() { docker logs "edp-$1" 2>&1; }

# --- the stack
all_ready() { ready 8080 && ready 8082 && ready 8084 && coordinator_ready; }
topics_init_done() { [[ "$(docker inspect -f '{{.State.Status}}/{{.State.ExitCode}}' edp-topics-init)" == "exited/0" ]]; }
partitions_of() { kafka kafka-topics.sh --describe --topic "$1" 2>/dev/null | grep -c 'Partition: [0-9]' || true; }
topics_have_one_partition() {
    local t out=""
    for t in edp.inventory.commands edp.payments.commands edp.inventory.reserved edp.inventory.insufficient \
             edp.payments.processed edp.payments.failed edp.orders.cancelled; do out+="$(partitions_of "$t") "; done
    [[ "$out" == "1 1 1 1 1 1 1 " ]]
}
flow_tables() { sql "SELECT string_agg(table_name, ',' ORDER BY table_name) FROM information_schema.tables WHERE table_schema = 'orders' AND table_name IN ('workflow_instance_entity', 'task_info_entity')"; }

# --- orders
place() { # place <camel|flow> <id> <amount> [sku]
    curl -sf -o /dev/null -X POST "http://localhost:8080/orders/$1" -H 'Content-Type: application/json' \
        -d "{\"id\":\"$2\",\"customerId\":\"CU-23\",\"itemSku\":\"${4:-SKU-23}\",\"quantity\":1,\"amount\":$3}"
}
order_status() { curl -sf "http://localhost:8080/orders/$1" | sed -n 's/.*"status":"\([A-Z]*\)".*/\1/p'; }
status_is() { [[ "$(order_status "$1")" == "$2" ]]; }
stock() { sql "SELECT quantity_on_hand FROM inventory.inventory_item WHERE sku = 'SKU-23'"; }
stock_is() { [[ "$(stock)" == "$1" ]]; }
reservation() { sql "SELECT status FROM inventory.reservation WHERE order_id = '$1'"; }
reservation_is() { [[ "$(reservation "$1")" == "$2" ]]; }
payment() { sql "SELECT status FROM payments.payment WHERE order_id = '$1'"; }
payment_is() { [[ "$(payment "$1")" == "$2" ]]; }
no_payment() { [[ -z "$(payment "$1")" ]]; }
instance_rows() { sql "SELECT count(*) FROM orders.workflow_instance_entity"; }
no_instance_rows() { [[ "$(instance_rows)" == 0 ]]; }
instance_of() { sql "SELECT flow_instance_id FROM orders.orders WHERE id = '$1'"; }
instance_waiting() { [[ "$(sql "SELECT count(*) FROM orders.workflow_instance_entity WHERE instance_id = '$(instance_of "$1")'")" == 1 ]]; }
active_lras() { curl -sf http://localhost:8091/lra-coordinator | python3 -I -c 'import json, sys; print(sum(1 for l in json.load(sys.stdin) if l.get("status") == "Active"))'; }
no_active_lras() { [[ "$(active_lras)" == 0 ]]; }
one_active_lra() { [[ "$(active_lras)" == 1 ]]; }

# --- topics, read with their headers: "<headers><TAB><key><TAB><value>"
topic_dump() {
    kafka kafka-console-consumer.sh --topic "$1" --from-beginning --timeout-ms 4000 \
        --formatter-property print.key=true --formatter-property print.headers=true 2>/dev/null || true
}
records_of() { topic_dump "$1" | awk -F'\t' -v k="$2" '$2 == k'; } # records_of <topic> <order id>
count_of() { records_of "$1" "$2" | grep -c -- "${3:-.}" || true; } # count_of <topic> <id> [pattern]
all_have() { # all_have <pattern> <topic> <id>: the order has records there and each carries the pattern
    local out; out="$(records_of "$2" "$3")"
    [[ -n "$out" ]] && [[ -z "$(printf '%s\n' "$out" | grep -v -- "$1" || true)" ]]
}
has_record() { local out; out="$(records_of "$1" "$2")"; [[ "$out" == *"$3"* ]]; } # has_record <topic> <id> <pattern>
one_cancelled_record() { [[ "$(count_of edp.orders.cancelled "$1")" == 1 ]]; }
group_caught_up() { # no lag on the commands topic for the payment-service group
    kafka kafka-consumer-groups.sh --describe --group payment-service 2>/dev/null \
        | awk '$2 == "edp.payments.commands" {n++; if ($6 != 0) bad = 1} END {exit !(n > 0 && !bad)}'
}
coordinator_requests() { # http requests the coordinator has served
    curl -sf http://localhost:8091/q/metrics 2>/dev/null | awk '/^http_server_requests_seconds_count/ {n += $2} END {print n + 0}'
}

# --- observability
prom_holds() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" | grep '"value"' >/dev/null; }
tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }

recreate() { "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait "$1"; }

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 topics_init_done || fail_loudly "topics-init did not finish"

echo "Infrastructure"
precondition "the three services and the LRA coordinator are ready" poll 90 all_ready
precondition "the seven topics exist with one partition each" topics_have_one_partition
tables_and_stock() { [[ "$(flow_tables)" == 'task_info_entity,workflow_instance_entity' && "$(stock)" == 20 ]]; }
precondition "Flow's tables exist and SKU-23 holds 20" tables_and_stock

echo "Both orchestrators, payment accepted"
place camel O-2301 100.00
place flow O-2311 100.00
check "Camel saga: O-2301 is PAID" poll 40 status_is O-2301 PAID
check "the coordinator holds no active saga: the Camel saga was completed" poll 20 no_active_lras
check "Flow workflow: O-2311 is PAID" poll 40 status_is O-2311 PAID
check "the completed workflow instance is gone from PostgreSQL" poll 20 no_instance_rows
camel_records_carry_the_saga() {
    all_have Long-Running-Action edp.inventory.commands O-2301 && all_have Long-Running-Action edp.payments.commands O-2301 \
        && all_have Long-Running-Action edp.inventory.reserved O-2301 && all_have Long-Running-Action edp.payments.processed O-2301
}
flow_records_carry_the_instance() {
    all_have flowinstanceid edp.inventory.commands O-2311 && all_have flowinstanceid edp.payments.commands O-2311 \
        && all_have ce_flowinstanceid edp.inventory.reserved O-2311 && all_have ce_flowinstanceid edp.payments.processed O-2311
}
check "O-2301: both commands and both replies carry the Long-Running-Action header" camel_records_carry_the_saga
check "O-2311: the commands carry flowinstanceid and the replies carry it as the CloudEvent attribute ce_flowinstanceid" flow_records_carry_the_instance

echo "Both orchestrators, payment declined (900.00)"
place camel O-2302 900.00
place flow O-2312 900.00
camel_declined() {
    status_is O-2302 CANCELLED && reservation_is O-2302 RELEASED && logs inventory-service | grep -F 'compensation: release stock for O-2302' >/dev/null
}
flow_declined() {
    status_is O-2312 CANCELLED && reservation_is O-2312 RELEASED && has_record edp.inventory.commands O-2312 ReleaseStock
}
each_cancelled_once() { one_cancelled_record O-2302 && one_cancelled_record O-2312; }
check "Camel saga: O-2302 is CANCELLED, its stock is RELEASED and the inventory log shows the coordinator's compensation call" poll 60 camel_declined
check "Flow workflow: O-2312 is CANCELLED, ReleaseStock is on the topic and its stock is RELEASED" poll 60 flow_declined
check "both orders are CANCELLED exactly once: one OrderCancelled each" poll 30 each_cancelled_once
check "SKU-23 holds 18: 20 less the two paid orders" poll 20 stock_is 18

echo "payment-service does not answer"
"${COMPOSE[@]}" stop payment-service >/dev/null
payment_down() { ! ready 8084; }
precondition "payment-service is stopped" payment_down
T0=$(now_ms)
place camel O-2303 100.00
place flow O-2313 100.00
cancelled_with_stock_back() { status_is "$1" CANCELLED && reservation_is "$1" RELEASED; }
check "Camel saga: the coordinator cancels O-2303 at the time limit and its stock is released" \
    poll $((TIMEOUT_S + 25)) cancelled_with_stock_back O-2303
echo "NOTE: Camel saga cancelled $(( $(now_ms) - T0 )) ms after the order, time limit ${TIMEOUT_S} s"
flow_compensated() { status_is O-2313 CANCELLED && has_record edp.payments.commands O-2313 RefundPayment && has_record edp.inventory.commands O-2313 ReleaseStock; }
check "Flow workflow: the listen task times out, RefundPayment and ReleaseStock are on the topics and O-2313 is CANCELLED" \
    poll $((TIMEOUT_S + 25)) flow_compensated
echo "NOTE: Flow workflow cancelled $(( $(now_ms) - T0 )) ms after the order, time limit ${TIMEOUT_S} s"
"${COMPOSE[@]}" up -d --no-deps --wait payment-service
payment_caught_up() { ready 8084 && group_caught_up; }
precondition "payment-service is ready again and has read its backlog (no lag on the commands topic)" poll 90 payment_caught_up
refused_join_logged() { logs payment-service | grep -F 'TakePayment for O-2303 refused'; }
join_refused() { no_payment O-2303 && refused_join_logged; }
check "O-2303: no payment was taken, because the late TakePayment could not join a cancelled saga" poll 30 join_refused
check "O-2313: the late TakePayment took the payment and the RefundPayment behind it refunded it: REFUNDED" poll 30 payment_is O-2313 REFUNDED
settled() { status_is O-2303 CANCELLED && status_is O-2313 CANCELLED && stock_is 18; }
check "neither order changed state after the late processing, and SKU-23 still holds 18" holds 5 settled

echo "order-service is recreated while payment-service is down"
# A longer limit, so both sagas are still open after the recreate (demo setting, compose passes it).
export SAGA_TIMEOUT=PT300S
recreate order-service
precondition "order-service is live with the longer limit" poll 60 live 8080
"${COMPOSE[@]}" stop payment-service >/dev/null
precondition "payment-service is stopped" payment_down
place camel O-2304 100.00
place flow O-2314 100.00
both_waiting() { reservation_is O-2304 RESERVED && reservation_is O-2314 RESERVED && instance_waiting O-2314 && one_active_lra; }
precondition "both orders are reserved and waiting: one active saga at the coordinator, one workflow instance row" poll 45 both_waiting
recreate order-service
precondition "order-service is live again" poll 60 live 8080
"${COMPOSE[@]}" up -d --no-deps --wait payment-service
precondition "payment-service is ready" poll 90 ready 8084
check "Camel saga: O-2304 is PAID: the reply joined the saga through its header and the coordinator closed it" poll 60 status_is O-2304 PAID
payment_taken() { payment_is O-2314 COMPLETED && group_caught_up; }
poll 60 payment_taken || fail_loudly "payment-service did not take the payment for O-2314"
still_waiting() { status_is O-2314 PLACED && instance_waiting O-2314; }
check "Flow workflow: O-2314 does not resume after the recreate: still PLACED with its instance row for 30 s after the payment was taken" \
    holds 30 still_waiting

echo "Bench: 100 orders per orchestrator in one burst"
burst() { # burst <camel|flow> <prefix>
    local i
    for i in $(seq -w 1 100); do place "$1" "$2$i" 100.00 SKU-23-BENCH; done
}
paid_count() { sql "SELECT count(*) FROM orders.orders WHERE id LIKE '$1%' AND status = 'PAID'"; }
all_paid() { [[ "$(paid_count "$1")" == 100 ]]; }
for form in camel flow; do
    prefix="B-${form:0:1}-"
    requests_before="$(coordinator_requests)"
    start_ms="$(now_ms)"
    burst "$form" "$prefix"
    check "$form: all 100 orders reach PAID" poll 180 all_paid "$prefix"
    echo "NOTE: bench $form: last PAID $(( $(now_ms) - start_ms )) ms after the first order; coordinator requests per order $(( ($(coordinator_requests) - requests_before) / 100 ))"
done

if (( REPEAT > 0 )); then
    echo "Repeating the payment-down scenario $REPEAT times"
    export SAGA_TIMEOUT=PT20S
    recreate order-service
    poll 60 live 8080 || fail_loudly "order-service is not live"
    OK=0
    for i in $(seq 1 "$REPEAT"); do
        "${COMPOSE[@]}" stop payment-service >/dev/null
        t0=$(now_ms)
        place camel "O-23R$i" 100.00
        place flow "O-23S$i" 100.00
        both_cancelled() { status_is "O-23R$i" CANCELLED && status_is "O-23S$i" CANCELLED; }
        if poll $((TIMEOUT_S + 25)) both_cancelled; then
            OK=$((OK + 1))
        else
            echo "NOTE: repeat $i: not both CANCELLED after $(( $(now_ms) - t0 )) ms"
        fi
        "${COMPOSE[@]}" up -d --no-deps --wait payment-service
    done
    if (( OK == REPEAT )); then echo "PASS R: both orders were cancelled within the window in $OK of $REPEAT runs"
    else echo "FAIL R: both orders were cancelled within the window in $OK of $REPEAT runs"; REPEAT_FAILED=1; fi
fi

echo "Observability"
check "Prometheus holds the compensated Flow sagas of order-service" \
    poll 90 prom_holds 'max_over_time(saga_outcomes_total{orchestrator="flow",outcome="compensated",service_name="order-service"}[1h]) >= 2'
check "Tempo has a trace for order-service" poll 90 tempo_has_trace order-service
check "Loki has logs for order-service" poll 90 loki_has_log order-service

if [[ "$PASSED" != "$TOTAL" ]]; then
    for s in order-service inventory-service payment-service; do
        echo "$s log, last lines:" >&2
        logs "$s" | tail -25 | cut -c1-260 >&2 || true
    done
fi
echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" && "$REPEAT_FAILED" == 0 ]]
