#!/usr/bin/env bash
# Starts Kafka, PostgreSQL, LGTM and the two services, then checks state transfer and the claim check.
# KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-03

COMPOSE=(docker compose -f compose.yaml)
ORDERS=http://localhost:8080
COPIES=http://localhost:8082
TOTAL=0
PASSED=0
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

poll() { # poll <seconds> <command...>: retry every 3 s until the command succeeds
    local end=$((SECONDS + $1)); shift
    until "$@"; do
        (( SECONDS < end )) || return 1
        sleep 3
    done
}

expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

# 100 KB of attachment text, above the 64 KB producer limit and well under Kafka's 1 MiB default.
head -c 102400 /dev/zero | tr '\0' 'g' > "$WORK/attachment.txt"
order_json() { # order_json <id> [attachment-file]
    if [[ $# -eq 2 ]]; then
        { printf '{"id":"%s","customerId":"CU-77","itemSku":"SKU-42","quantity":3,"amount":59.90,"attachment":"' "$1"
          cat "$2"; printf '"}'; } > "$WORK/body.json"
    else
        printf '{"id":"%s","customerId":"CU-77","itemSku":"SKU-42","quantity":3,"amount":59.90}' "$1" > "$WORK/body.json"
    fi
}
place() { order_json "$@"; code -H 'Content-Type: application/json' --data-binary "@$WORK/body.json" "$ORDERS/orders"; }
cancel() { code -X POST "$ORDERS/orders/$1/cancel"; }
copy() { curl -s "$COPIES/order-copies/$1"; }
copy_status() { copy "$1" | grep -o '"status":"[A-Z]*"' | cut -d'"' -f4; }
copy_has() { poll 60 bash -c "[[ \"\$(curl -s $COPIES/order-copies/$1 | grep -o '\"status\":\"[A-Z]*\"' | cut -d'\"' -f4)\" == $2 ]]"; }
placed_offsets() {
    docker exec edp-kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic edp.orders.placed \
        | awk -F: '{s += $3} END {print s + 0}'
}
placed_record() { # the value of the record keyed $1 on edp.orders.placed
    docker exec edp-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
        --topic edp.orders.placed --from-beginning --timeout-ms 15000 --property print.key=true 2>/dev/null \
        | grep "^$1" | head -1
}
prom() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1"; }
tempo_count() { # tempo_count <traceql>: number of traces matching
    curl -sf -G "http://localhost:3200/api/search" --data-urlencode "q=$1" --data-urlencode "limit=50" \
        | grep -o '"traceID"' | wc -l
}
HTTP_CLIENT_SPANS='{ resource.service.name = "notification-service" && kind = client && span.http.request.method =~ ".+" }'

# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait

STAMP=$(date +%s)
A="O-$STAMP-A"; B="O-$STAMP-B"; C="O-$STAMP-C"; D="O-$STAMP-D"; E="O-$STAMP-E"

# Event-carried state transfer, claim-check attachments (the defaults).
check "place order A returns 201" expect "$(place "$A")" 201
check "place order B with a 100 KB attachment returns 201" expect "$(place "$B" "$WORK/attachment.txt")" 201
check "place order C returns 201" expect "$(place "$C")" 201
check "cancel order C returns 204" expect "$(cancel "$C")" 204
check "copy of A reaches PLACED" copy_has "$A" PLACED
check "copy of C reaches CANCELLED" copy_has "$C" CANCELLED
check "copy of B reaches PLACED" copy_has "$B" PLACED
check "copy of A carries every Order field" bash -c \
    "[[ \"\$(curl -s $COPIES/order-copies/$A)\" == '{\"id\":\"$A\",\"customerId\":\"CU-77\",\"itemSku\":\"SKU-42\",\"quantity\":3,\"amount\":59.90,\"status\":\"PLACED\"}' ]]"
check "claim-check event for B is under 1 KB and holds no attachment text" bash -c \
    "r=\$(docker exec edp-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic edp.orders.placed --from-beginning --timeout-ms 15000 --property print.key=true 2>/dev/null | grep '^$B'); [[ \${#r} -lt 1024 && \"\$r\" == *'\"claim\"'* && \"\$r\" != *gggg* ]]"
check "copy of B records the claim size, 102400 bytes" bash -c \
    "curl -s $COPIES/order-copies/$B | grep -q '\"size\":102400'"

# Nobody asked the source: its request metrics and the consumer's spans show no fetch.
REQUESTS='http_server_requests_milliseconds_count{service_name="order-service"'
check "order-service has recorded the POST /orders requests (the metric is live)" poll 90 bash -c \
    "curl -sf -G http://localhost:19090/api/v1/query --data-urlencode 'query=${REQUESTS},uri=\"/orders\"}' | grep -q '\"value\"'"
check "Tempo holds traces from notification-service (the query can see the service)" poll 90 bash -c \
    "curl -sf -G http://localhost:3200/api/search --data-urlencode 'q={ resource.service.name = \"notification-service\" }' | grep -q traceID"
check "order-service recorded zero GET /orders/{id} requests" bash -c \
    "curl -sf -G http://localhost:19090/api/v1/query --data-urlencode 'query=${REQUESTS},uri=\"/orders/{id}\"}' | grep -q '\"result\":\\[\\]'"
check "no HTTP client span left notification-service" bash -c \
    "[[ \$(curl -sf -G http://localhost:3200/api/search --data-urlencode 'q=$HTTP_CLIENT_SPANS' | grep -c traceID) -eq 0 ]]"

"${COMPOSE[@]}" stop order-service >/dev/null 2>&1
check "order-service is stopped" bash -c "[[ \"\$(docker inspect -f '{{.State.Running}}' edp-03-order-service)\" == false ]]"
check "with order-service stopped, the copy of A answers 200" expect "$(code "$COPIES/order-copies/$A")" 200
check "with order-service stopped, the copy of A still has the customer, item and amount" bash -c \
    "curl -s $COPIES/order-copies/$A | grep -q '\"customerId\":\"CU-77\".*\"itemSku\":\"SKU-42\".*\"amount\":59.90'"
check "with order-service stopped, the cancelled order still shows CANCELLED" expect "$(copy_status "$C")" CANCELLED
check "with order-service stopped, the attachment cannot be fetched (503)" expect "$(code "$COPIES/order-copies/$B/attachment")" 503

"${COMPOSE[@]}" up -d --wait order-service >/dev/null 2>&1
curl -s "$ORDERS/orders/$A" >/dev/null
check "positive control: a GET /orders/{id} request is counted by the same metric" poll 90 bash -c \
    "curl -sf -G http://localhost:19090/api/v1/query --data-urlencode 'query=${REQUESTS},uri=\"/orders/{id}\"}' | grep -q '\"value\"'"
FETCHED=$(curl -s "$COPIES/order-copies/$B/attachment" | wc -c)
check "with order-service back, the claim is fetched on demand: 102400 bytes" expect "$FETCHED" 102400
check "the fetched content equals the attachment" bash -c "curl -s $COPIES/order-copies/$B/attachment | cmp -s - $WORK/attachment.txt"
check "the on-demand fetch is the first HTTP client span from notification-service" poll 90 bash -c \
    "[[ \$(curl -sf -G http://localhost:3200/api/search --data-urlencode 'q=$HTTP_CLIENT_SPANS' | grep -c traceID) -ge 1 ]]"

# Negative witness for the claim check: the same 100 KB attachment inside the event.
"${COMPOSE[@]}" stop order-service >/dev/null 2>&1
ORDER_EVENTS_ATTACHMENT=inline "${COMPOSE[@]}" up -d --force-recreate --wait order-service >/dev/null 2>&1
BEFORE=$(placed_offsets)
INLINE_CODE=$(place "$D" "$WORK/attachment.txt")
check "negative witness: the full-state event with a 100 KB attachment is refused with 502" expect "$INLINE_CODE" 502
check "negative witness: the producer logged RecordTooLargeException" bash -c \
    "docker logs edp-03-order-service 2>&1 | grep -q RecordTooLargeException"
sleep 3
check "negative witness: nothing was published for it" expect "$(placed_offsets)" "$BEFORE"
check "negative witness: the copy of D does not exist" expect "$(code "$COPIES/order-copies/$D")" 404

# Experiment surface: the thin event.
"${COMPOSE[@]}" stop order-service >/dev/null 2>&1
ORDER_EVENTS_CARRY_STATE=false "${COMPOSE[@]}" up -d --force-recreate --wait order-service >/dev/null 2>&1
check "thin event: order E is accepted with 201" expect "$(place "$E")" 201
check "thin event: notification-service logs it as skipped" poll 60 bash -c \
    "docker logs edp-03-notification-service 2>&1 | grep -q 'carries no order state'"
sleep 3
check "thin event: the copy of E answers 404" expect "$(code "$COPIES/order-copies/$E")" 404

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
