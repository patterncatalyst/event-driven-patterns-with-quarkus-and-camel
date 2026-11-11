#!/usr/bin/env bash
# Starts the whole stack with the stack-check app and checks every service and signal.
# KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-00

COMPOSE=(docker compose -f compose.yaml)
APP=http://localhost:8080
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

healthy() { [[ "$(docker inspect -f '{{.State.Health.Status}}' "$1")" == "healthy" ]]; }
post() { curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -d "$1" "$APP/orders"; }
order() { printf '{"id":"%s","customerId":"CUST-7","itemSku":"SKU-1","quantity":%s,"amount":49.90}' "$1" "$2"; }
topic_offsets() {
    docker exec edp-kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic edp.orders.placed \
        | awk -F: '{s += $3} END {print s + 0}'
}
topic_has() {
    docker exec edp-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
        --topic edp.orders.placed --from-beginning --timeout-ms 15000 --property print.key=true 2>/dev/null | grep -q "^$1"
}
http2xx() { [[ "$(post "$1")" =~ ^2 ]]; }
http400() { [[ "$(post "$1")" == "400" ]]; }
tempo_has_trace() { curl -sf -G "http://localhost:3200/api/search" --data-urlencode 'tags=service.name=stack-check' | grep -q '"traceID"'; }
prom_has_metric() {
    curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode 'query=target_info{service_name="stack-check"}' \
        | grep -q '"result":\[{'
}
loki_has_log() {
    local now; now=$(date +%s)
    curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode 'query={service_name="stack-check"} |= "order received"' \
        --data-urlencode "start=$((now - 600))000000000" --data-urlencode "end=$((now + 60))000000000" | grep -q '"values":\[\['
}

"${COMPOSE[@]}" up -d --build --wait

for c in edp-kafka edp-postgres edp-apicurio edp-connect edp-lgtm edp-stack-check; do
    check "$c is healthy" healthy "$c"
done

ID="O-$(date +%s)"
check "valid Order accepted with 2xx" http2xx "$(order "$ID" 2)"
check "the record keyed $ID arrives on the edp.orders.placed topic" topic_has "$ID"
check "PostgreSQL wal_level is logical" bash -c \
    "[[ \"\$(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -Atc 'SHOW wal_level')\" == logical ]]"
check "Apicurio Registry reports ready" curl -sf http://localhost:19000/health/ready
check "Connect lists PostgresConnector" bash -c \
    "curl -sf http://localhost:8083/connector-plugins | grep -q io.debezium.connector.postgresql.PostgresConnector"
check "Connect lists the outbox EventRouter" bash -c \
    "curl -sf 'http://localhost:8083/connector-plugins?connectorsOnly=false' | grep -q io.debezium.transforms.outbox.EventRouter"
check "Tempo has a trace for service stack-check" poll 90 tempo_has_trace
check "Prometheus has a metric from stack-check" poll 90 prom_has_metric
check "Loki has a log line from stack-check" poll 90 loki_has_log

BEFORE=$(topic_offsets)
check "negative witness: invalid Order (quantity 0) gets 400" http400 "$(order O-INVALID 0)"
sleep 3
AFTER=$(topic_offsets)
check "negative witness: record count unchanged ($BEFORE)" test "$BEFORE" = "$AFTER"

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
