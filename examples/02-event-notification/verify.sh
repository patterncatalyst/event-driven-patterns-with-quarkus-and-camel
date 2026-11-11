#!/usr/bin/env bash
# Starts the stack and checks the event, the fetch-back, the trace, the source-down witness and freshness.
# KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-02

COMPOSE=(docker compose -f compose.yaml)
ORDERS=http://localhost:8080
NOTIFY=http://localhost:8082
KAFKA_BIN=/opt/kafka/bin
TOTAL=0
PASSED=0

cleanup() {
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

poll() { # poll <seconds> <command...>: retry every 3 s until the command succeeds
    local end=$((SECONDS + $1)); shift
    until "$@"; do
        (( SECONDS < end )) || return 1
        sleep 3
    done
}

expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
place() { curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"id\":\"$1\",\"customerId\":\"CU-77\",\"itemSku\":\"SKU-42\",\"quantity\":3,\"amount\":59.90}" "$ORDERS/orders"; }
notifications() { curl -sf "$NOTIFY/notifications?orderId=$1"; }
field() { python3 -c "import json,sys; d=json.load(sys.stdin); print(d[0]['$1'] if d else '')"; }
has_notification() { [[ -n "$(notifications "$1" | field customerId)" ]]; }
no_notification() { [[ "$(notifications "$1")" == "[]" ]]; }
ready() { curl -sf "$1/q/health/ready"; }
topic_records() { "${KAFKA_BIN_EXEC[@]}" "$KAFKA_BIN/kafka-console-consumer.sh" --bootstrap-server localhost:9092 \
    --topic edp.orders.placed --from-beginning --timeout-ms 8000 --property print.key=true 2>/dev/null; }
KAFKA_BIN_EXEC=(docker exec edp-kafka)
# Records not yet committed for the notification group, summed over partitions.
lag() { "${KAFKA_BIN_EXEC[@]}" "$KAFKA_BIN/kafka-consumer-groups.sh" --bootstrap-server localhost:9092 \
    --describe --group notification-service 2>/dev/null \
    | awk '$2=="edp.orders.placed" { c = ($4=="-") ? 0 : $4; s += $5 - c } END { print s + 0 }'; }
lag_is() { [[ "$(lag)" == "$1" ]]; }
lag_positive() { (( $(lag) >= 1 )); }
record_is_only_the_id() { # record_is_only_the_id <orderId>
    local line key value
    line="$(topic_records | grep -P "^$1\t")"
    line="${line%%$'\n'*}"
    key="${line%%$'\t'*}"; value="${line#*$'\t'}"
    [[ "$key" == "$1" ]] && python3 -c "
import json,sys
d=json.loads(sys.argv[1])
assert sorted(d)==['eventId','occurredAt','orderId'] and d['orderId']==sys.argv[2]" "$value" "$1"
}
fetch_failed_in_log() { local log; log="$(docker logs edp-notification-service 2>&1)"; [[ "$log" == *"Exhausted after delivery attempt"* ]]; }
# A trace that holds order-service's POST /orders server span and a notification-service span: the
# trace context crossed Kafka. The fetch alone would put notification-service and order-service in a
# trace of their own, without the POST /orders span.
trace_spans_both() {
    local id trace
    for id in $(curl -sf -G "http://localhost:3200/api/search" --data-urlencode 'tags=service.name=order-service' \
        --data-urlencode limit=20 | grep -o '"traceID":"[0-9a-f]*"' | cut -d'"' -f4); do
        trace="$(curl -sf "http://localhost:3200/api/traces/$id")" || continue
        grep -q '"stringValue":"notification-service"' <<<"$trace" && grep -q '"name":"POST /orders"' <<<"$trace" && return 0
    done
    return 1
}
metric_live() { # metric_live <service> <metric>: Prometheus holds a sample for the service
    curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$2{service_name=\"$1\"}" | grep -q '"value"'
}

# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait

# Happy path: place A, the event carries the id, the notification holds the fetched facts.
A="O-$(date +%s)-A"
check "place A returns 201" expect "$(place "$A")" 201
check "OrderPlaced for A is keyed by A and carries only eventId, occurredAt and orderId" \
    poll 30 record_is_only_the_id "$A"
check "notification for A appears" poll 60 has_notification "$A"
check "notification holds the fetched customer CU-77" expect "$(notifications "$A" | field customerId)" CU-77
check "notification holds the fetched amount 59.90" expect "$(notifications "$A" | field amount)" 59.9
check "notification holds the status PLACED" expect "$(notifications "$A" | field orderStatus)" PLACED
check "one Tempo trace holds the POST /orders span and the notification-service spans" poll 90 trace_spans_both
check "order-service request metrics reach Prometheus" poll 90 metric_live order-service http_server_requests_milliseconds_count
check "notification-service JVM metrics reach Prometheus" poll 90 metric_live notification-service jvm_memory_used_bytes

# Source down: B is placed while notification-service is stopped, then order-service goes away
# before notification-service comes back. The record must wait, uncommitted, for the source.
"${COMPOSE[@]}" stop notification-service >/dev/null
B="O-$(date +%s)-B"
check "place B returns 201" expect "$(place "$B")" 201
"${COMPOSE[@]}" stop order-service >/dev/null
"${COMPOSE[@]}" start notification-service >/dev/null
check "notification-service is ready while order-service is down" poll 90 ready "$NOTIFY"
check "the fetch for B fails in notification-service" poll 90 fetch_failed_in_log
check "no notification for B while order-service is down" no_notification "$B"
check "B's offset is not committed past while order-service is down" lag_positive
"${COMPOSE[@]}" start order-service >/dev/null
check "order-service is ready again" poll 90 ready "$ORDERS"
check "B's notification appears once order-service is back" poll 90 has_notification "$B"
check "the notification group has committed every record" poll 60 lag_is 0

# Freshness: C is cancelled before notification-service reads the event.
"${COMPOSE[@]}" stop notification-service >/dev/null
C="O-$(date +%s)-C"
check "place C returns 201" expect "$(place "$C")" 201
check "cancel C returns 204" expect "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$ORDERS/orders/$C/cancel")" 204
"${COMPOSE[@]}" start notification-service >/dev/null
check "C's notification appears" poll 90 has_notification "$C"
check "C's notification shows CANCELLED, the status at fetch time" \
    expect "$(notifications "$C" | field orderStatus)" CANCELLED

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
