#!/usr/bin/env bash
# Starts the stack, publishes the same burst of orders through three routes (unbatched, default, tuned) and
# checks structural relations between them: records per request, bytes per record, batch compression, and
# the producer id. No check depends on elapsed time. KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-05

COMPOSE=(docker compose -f compose.yaml)
APP=http://localhost:8080
TOPIC=edp.orders.placed
N=${N:-20000}
CONCURRENCY=${CONCURRENCY:-200}
TOTAL=0
PASSED=0
# shellcheck source=lib.sh

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

poll() { # poll <seconds> <command...>: retry every 2 s until the command succeeds
    local end=$((SECONDS + $1)); shift
    until "$@"; do
        (( SECONDS < end )) || return 1
        sleep 2
    done
}

expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
ge() { awk -v a="$1" -v b="$2" 'BEGIN { exit !(a >= b) }'; }  # ge <a> <b>
le() { awk -v a="$1" -v b="$2" 'BEGIN { exit !(a <= b) }'; }
gt() { awk -v a="$1" -v b="$2" 'BEGIN { exit !(a > b) }'; }
source ./lib.sh
times() { awk -v a="$1" -v k="$2" 'BEGIN { printf "%.2f", a * k }'; }

# Batch headers of partition 0, from the broker's own log files.
dump() { docker exec edp-kafka bash -c "/opt/kafka/bin/kafka-dump-log.sh --files /tmp/kafka-logs/${TOPIC}-0/*.log"; }
batches_with() { dump | grep -c -- "$1" || true; }

tempo_has_trace() { curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=order-service" | grep -q '"traceID"'; }
loki_has_log() {
    local now; now=$(date +%s)
    curl -sf -G "http://localhost:3100/loki/api/v1/query_range" \
        --data-urlencode 'query={service_name="order-service"} |= "flash sale"' \
        --data-urlencode "start=$((now - 900))000000000" --data-urlencode "end=$((now + 60))000000000" | grep -q '"values":\[\['
}

# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait

check "topic has 3 partitions" \
    expect "$(kafka kafka-topics.sh --describe --topic "$TOPIC" | grep -o 'PartitionCount: [0-9]*' | cut -d' ' -f2)" 3
check "order-service is ready" curl -sf "$APP/q/health/ready"

# Warm-up bursts, not measured.
for p in unbatched default tuned; do burst "$p" 1000 50 >/dev/null; done

declare -A RPR BPR
for p in unbatched default tuned; do
    echo "Burst: $p"
    burst "$p" "$N" "$CONCURRENCY"
    check "$p: $N orders accepted, none failed" \
        expect "$(field orders)/$(field failed)" "$N/0"
    check "$p: the topic's end offsets grew by exactly $N" expect "$OFFSET_GROWTH" "$N"
    check "$p: precondition, the producer's own counters grew by $N records in more than 0 requests" \
        expect "$DELTA_SENT/$(( DELTA_REQUESTS > 0 ))" "$N/1"
    RPR[$p]=$(ratio "$DELTA_SENT" "$DELTA_REQUESTS")
    BPR[$p]=$(ratio "$DELTA_BYTES" "$DELTA_SENT")
done

echo "Relations"
check "unbatched (control): at most 3 records per request, one per batch and 3 partitions" le "${RPR[unbatched]}" 3
check "default: at least 2 times the unbatched records per request" ge "${RPR[default]}" "$(times "${RPR[unbatched]}" 2)"
check "tuned: at least 5 times the unbatched records per request" ge "${RPR[tuned]}" "$(times "${RPR[unbatched]}" 5)"
check "tuned: fewer bytes per record than default" gt "${BPR[default]}" "${BPR[tuned]}"
check "broker log holds lz4 batches (tuned) and uncompressed batches (control)" \
    expect "$([[ $(batches_with 'compresscodec: lz4') -gt 0 && $(batches_with 'compresscodec: none') -gt 0 ]] && echo yes)" yes
check "every batch carries a producer id while idempotence is on (control for the acks=1 run)" \
    expect "$(batches_with 'producerId: -1')" 0

echo "One caller at a time"
burst tuned 300 1
check "precondition: 300 sequential orders counted by the producer, in more than 0 requests" \
    expect "$DELTA_SENT/$(( DELTA_REQUESTS > 0 ))" "300/1"
check "tuned with one caller: at most 1.5 records per request" le "$(ratio "$DELTA_SENT" "$DELTA_REQUESTS")" 1.5
burst tuned 300 100
check "control, same route with 100 callers: more than 3 records per request" \
    gt "$(ratio "$DELTA_SENT" "$DELTA_REQUESTS")" 3

echo "acks=1 without idempotence"
REQUIRED_ACKS=1 ENABLE_IDEMPOTENCE=false "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait order-service
burst default 2000 50
check "precondition: 2000 orders reached the topic after the recreate" expect "$OFFSET_GROWTH" 2000
check "new batches carry no producer id" \
    expect "$([[ $(batches_with 'producerId: -1') -gt 0 ]] && echo yes)" yes

check "Tempo has a trace for order-service" poll 90 tempo_has_trace
check "Loki has a flash sale log line from order-service" poll 90 loki_has_log

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
