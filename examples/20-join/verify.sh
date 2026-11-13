#!/usr/bin/env bash
# Starts the stack and checks the observable effects of joining InventoryReserved and PaymentProcessed by order id two
# ways at once: a Kafka Streams left join in event time and a Camel aggregator in arrival time. Two crafted pairs make
# the forms disagree in opposite directions, an unpaid reservation shows what each does when one side never arrives,
# and a recreate of the service shows that both keep their open state. AGGREGATION_REPOSITORY=memory and
# READINESS_TIMEOUT_MS=30000 are the experiments (demo code, see the README). KEEP=1 leaves the stack running.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-20

COMPOSE=(docker compose -f compose.yaml)
APP=edp-shipping-service
RESERVED=edp.inventory.reserved
PAID=edp.payments.processed
STREAMS_OUT=edp.shipping.order-readiness
CAMEL_OUT=edp.shipping.order-readiness-camel
# The Streams group is the application id; the changelog of the reservation side of the join store is named after it.
CHANGELOG=shipping-readiness-readiness-this-join-store-changelog
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
# Event times are in the recent past: Kafka Streams forwards event time as the record timestamp, the broker refuses a
# timestamp more than an hour ahead of its clock. The scenario therefore runs from a base 20 hours before now, on a
# 15-minute boundary; its label 09:00 is that base and the last label (14:01) is 15 hours before now.
BASE_AGE_HOURS=${BASE_AGE_HOURS:-20} # demo setting: 44 hours old event times, all 24 checks pass (see the README)
BASE_EPOCH=$(( ($(date +%s) - BASE_AGE_HOURS * 3600) / 900 * 900 ))
TOTAL=0
PASSED=0
WORK="$(mktemp -d)"
TAILERS=()

cleanup() {
    for pid in "${TAILERS[@]}"; do kill "$pid" 2>/dev/null || true; done
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

stays_false() { # stays_false <seconds> <command...>: succeeds when the command fails on every second of the span
    local end=$((SECONDS + $1)); shift
    while (( SECONDS < end )); do
        if "$@"; then return 1; fi
        sleep 1
    done
}

expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
fail_loudly() { printf 'PRECONDITION FAILED: %s\n' "$1" >&2; exit 1; }

kafka() { docker exec edp-kafka "/opt/kafka/bin/$1" --bootstrap-server localhost:9092 "${@:2}"; }
sql() { "${PSQL[@]}" -c "$1"; }
alive() { curl -sf http://localhost:8080/q/health/live >/dev/null; }
ready() { curl -sf http://localhost:8080/q/health/ready >/dev/null; }
logs() { docker logs "$APP" 2>&1; }
# grep without -q: under pipefail an early-exiting grep would fail the pipeline with SIGPIPE.
log_has() { local out; out="$(logs)"; grep -E -- "$1" <<<"$out" >/dev/null; }
now_ms() { date +%s%3N; }

# --- the topics and the groups
topics_init_done() { [[ "$(docker inspect -f '{{.State.Status}}/{{.State.ExitCode}}' edp-topics-init)" == "exited/0" ]]; }
partitions_of() { kafka kafka-topics.sh --describe --topic "$1" 2>/dev/null | grep -c 'Partition: [0-9]' || true; }
topics_have_one_partition() { [[ "$(for t in $RESERVED $PAID $STREAMS_OUT $CAMEL_OUT; do partitions_of "$t"; done | tr '\n' ' ')" == "1 1 1 1 " ]]; }
group_owns_both() { # group_owns_both <group>: a member holds the partition of each input topic
    local out; out="$(kafka kafka-consumer-groups.sh --describe --all-groups 2>/dev/null)"
    [[ "$(awk -v g="$1" -v a="$RESERVED" -v b="$PAID" '$1 == g && ($2 == a || $2 == b) && $3 ~ /^[0-9]+$/ && $7 != "-"' <<<"$out" | wc -l)" == 2 ]]
}
end_offset() { kafka kafka-get-offsets.sh --topic "$1" 2>/dev/null | awk -F: '{s += $3} END {print s + 0}'; }

# --- records. Keys are order ids; event times come from the payload.
produce() { # produce <topic>: lines "<key>|<json>" on stdin, one producer call
    docker exec -i edp-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic "$1" \
        --reader-property parse.key=true --reader-property 'key.separator=|' >/dev/null 2>&1
}
at() { date -u -d "@$((BASE_EPOCH + (10#${1%%:*} * 60 + 10#${1##*:} - 540) * 60))" +%Y-%m-%dT%H:%M:00Z; } # at <hh:mm>: the label's instant
reserve() { printf '%s|{"orderId":"%s","sku":"SKU-20","quantity":1,"occurredAt":"%s"}\n' "$1" "$1" "$(at "$2")" | produce $RESERVED; } # reserve <order> <hh:mm>
pay() { printf '%s|{"payment":{"id":"P-%s","orderId":"%s","amount":25.00,"status":"COMPLETED"},"occurredAt":"%s"}\n' "$1" "$1" "$1" "$(at "$2")" | produce $PAID; }
bench_records() { # bench_records <reserved|paid> <hh:mm>: orders O-2100 to O-2299
    python3 -I -c '
import sys
kind, at = sys.argv[1], sys.argv[2]
for i in range(2100, 2300):
    o = "O-%d" % i
    if kind == "reserved":
        print("%s|{\"orderId\":\"%s\",\"sku\":\"SKU-20\",\"quantity\":1,\"occurredAt\":\"%s\"}" % (o, o, at))
    else:
        print("%s|{\"payment\":{\"id\":\"P-%s\",\"orderId\":\"%s\",\"amount\":25.00,\"status\":\"COMPLETED\"},\"occurredAt\":\"%s\"}" % (o, o, o, at))
' "$1" "$(at "$2")"
}

# --- the outputs: one background reader per output topic, so every check reads a file
tail_topic() { # tail_topic <name> <topic>
    docker exec edp-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic "$2" --from-beginning \
        --timeout-ms 900000 --formatter-property print.key=true > "$WORK/$1.out" 2>/dev/null &
    TAILERS+=("$!")
}
emitted() { # emitted <streams|camel> <type> <order>: a record of that type for that order
    awk -v t="\"type\":\"$2\"" -v o="\"orderId\":\"$3\"" 'index($0, t) && index($0, o) {f = 1} END {exit !f}' "$WORK/$1.out"
}
emitted_count() { # emitted_count <streams|camel> <type> <regex for the orderId field>
    awk -v t="\"type\":\"$2\"" -v o="$3" 'index($0, t) && $0 ~ o {n++} END {print n + 0}' "$WORK/$1.out"
}
both_emit() { emitted streams "$1" "$2" && emitted camel "$1" "$2"; } # both_emit <type> <order>
open_rows() { sql "SELECT count(*) FROM shipping.readiness WHERE id = '$1'"; }
open_row_is() { [[ "$(open_rows "$1")" == "$2" ]]; }
bench_done() { # every bench order is ReadyToShip on both outputs
    [[ "$(emitted_count streams ReadyToShip '"orderId":"O-2[12][0-9][0-9]"')" == 200 && "$(emitted_count camel ReadyToShip '"orderId":"O-2[12][0-9][0-9]"')" == 200 ]]
}
bench_overdue() { [[ "$(emitted_count streams PaymentOverdue '"orderId":"O-2[12][0-9][0-9]"')" != 0 || "$(emitted_count camel PaymentOverdue '"orderId":"O-2[12][0-9][0-9]"')" != 0 ]]; }

groups_own_both() { group_owns_both shipping-readiness && group_owns_both shipping-readiness-camel; }
no_bench_overdue() { ! bench_overdue; }
late_payment_silent() { stays_false 13 emitted camel ReadyToShip O-2003 && open_row_is O-2003 0; }
streams_never_ready_2004() { ! emitted streams ReadyToShip O-2004; }
camel_overdue_after_timeout() { stays_false 8 emitted camel PaymentOverdue O-2005 && poll 7 emitted camel PaymentOverdue O-2005; }
streams_silent_2005() { stays_false 15 emitted streams PaymentOverdue O-2005; }
changelog_grew() { (( $(end_offset "$CHANGELOG") > CHANGELOG_BEFORE )); }
restore_logged() { log_has "Finished restoring changelog $CHANGELOG" && log_has 'Restored [1-9][0-9]* CompletionTimeout'; }

recreate_service() { # recreate_service: a new container with an empty state directory, then wait for the Streams state
    READINESS_TIMEOUT_MS=60000 "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait shipping-service >/dev/null
    poll 120 alive
}

# --- observability
prom_holds() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" | grep '"value"' >/dev/null; }
tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 topics_init_done || fail_loudly "topics-init did not finish"
poll 60 ready || fail_loudly "shipping-service is not ready"

echo "Infrastructure"
precondition "the Kafka Streams instance is RUNNING (liveness is UP)" poll 120 alive
precondition "the Streams group and the Camel group each own the partition of both input topics" \
    poll 90 groups_own_both
precondition "the four topics exist with one partition each" topics_have_one_partition
precondition "the aggregation tables exist and are empty" \
    expect "$(sql "SELECT (SELECT count(*) FROM shipping.readiness) || '/' || (SELECT count(*) FROM shipping.readiness_completed)")" "0/0"
tail_topic streams $STREAMS_OUT
tail_topic camel $CAMEL_OUT
sleep 2

echo "Happy path: both facts close together in event time and in arrival"
reserve O-2001 09:00; pay O-2001 09:01
check "O-2001, reservation then payment: both outputs hold ReadyToShip within 15 s" poll 15 both_emit ReadyToShip O-2001
pay O-2002 09:02; reserve O-2002 09:03
check "O-2002, payment first: both outputs hold ReadyToShip within 15 s" poll 15 both_emit ReadyToShip O-2002

echo "Bench: 200 orders, reservations then payments in two producer calls, event times inside the window"
BEFORE_STREAMS="$(end_offset $STREAMS_OUT)"; BEFORE_CAMEL="$(end_offset $CAMEL_OUT)"
BENCH_START="$(now_ms)"
bench_records reserved 09:04 | produce $RESERVED
bench_records paid 09:05 | produce $PAID
PEAK=0; STREAMS_MS=""; CAMEL_MS=""
until bench_done || (( $(now_ms) - BENCH_START > 60000 )); do
    OPEN="$(sql 'SELECT count(*) FROM shipping.readiness' || echo 0)"; (( OPEN > PEAK )) && PEAK="$OPEN"
    if [[ -z "$STREAMS_MS" && "$(emitted_count streams ReadyToShip '"orderId":"O-2[12][0-9][0-9]"')" == 200 ]]; then STREAMS_MS=$(( $(now_ms) - BENCH_START )); fi
    if [[ -z "$CAMEL_MS" && "$(emitted_count camel ReadyToShip '"orderId":"O-2[12][0-9][0-9]"')" == 200 ]]; then CAMEL_MS=$(( $(now_ms) - BENCH_START )); fi
    sleep 0.5
done
if [[ -z "$STREAMS_MS" && "$(emitted_count streams ReadyToShip '"orderId":"O-2[12][0-9][0-9]"')" == 200 ]]; then STREAMS_MS=$(( $(now_ms) - BENCH_START )); fi
if [[ -z "$CAMEL_MS" && "$(emitted_count camel ReadyToShip '"orderId":"O-2[12][0-9][0-9]"')" == 200 ]]; then CAMEL_MS=$(( $(now_ms) - BENCH_START )); fi
check "all 200 bench orders are ReadyToShip on both outputs within 60 s" bench_done
check "no bench order is PaymentOverdue on either output" no_bench_overdue
echo "NOTE: bench, time to the last ReadyToShip on both outputs: $(( $(now_ms) - BENCH_START )) ms (both together, polled every 0.5 s)"
echo "NOTE: bench, time to 200 ReadyToShip per output (polled): streams ${STREAMS_MS:-none} ms, camel ${CAMEL_MS:-none} ms"
echo "NOTE: bench, peak open groups in shipping.readiness: $PEAK"
echo "NOTE: bench, records added to the outputs: streams $(( $(end_offset $STREAMS_OUT) - BEFORE_STREAMS )), camel $(( $(end_offset $CAMEL_OUT) - BEFORE_CAMEL ))"

echo "Divergence 1: O-2003 is close in event time and far apart in arrival"
reserve O-2003 09:10
check "O-2003: the Camel output holds PaymentOverdue within 20 s, before the payment exists" poll 20 emitted camel PaymentOverdue O-2003
pay O-2003 09:12
check "O-2003: the Streams output holds ReadyToShip" poll 15 emitted streams ReadyToShip O-2003
check "O-2003: the late payment on the Camel path emits nothing and leaves no open group" \
    late_payment_silent

echo "Divergence 2: O-2004 is far apart in event time and together in arrival"
reserve O-2004 09:20; pay O-2004 10:50
check "O-2004: the Camel output holds ReadyToShip at once" poll 15 emitted camel ReadyToShip O-2004
check "O-2004: the Streams output holds PaymentOverdue, because the payment itself moves stream time past the window" poll 30 emitted streams PaymentOverdue O-2004
check "O-2004: the Streams output holds no ReadyToShip" streams_never_ready_2004

echo "A reservation that is never paid (O-2005)"
reserve O-2005 12:00
SENT="$(now_ms)"
check "O-2005: the Camel output holds no PaymentOverdue for 8 s and holds one within 15 s of arrival" \
    camel_overdue_after_timeout
echo "NOTE: O-2005 Camel PaymentOverdue seen $(( $(now_ms) - SENT )) ms after the reservation was produced (polled every second)"
check "O-2005: the Streams output stays silent for 15 s, because no record moves stream time" \
    streams_silent_2005
reserve O-2098 13:00
check "O-2005: after a later record moves stream time the Streams output holds PaymentOverdue" poll 30 emitted streams PaymentOverdue O-2005

echo "State survives a recreate (O-2006)"
recreate_service || fail_loudly "the service did not come back after the first recreate"
CHANGELOG_BEFORE="$(end_offset $CHANGELOG)"
reserve O-2006 14:00
check "O-2006: the Camel aggregator holds the open group as a row in shipping.readiness" poll 10 open_row_is O-2006 1
check "O-2006: the Streams changelog of the join store grew" poll 20 changelog_grew
recreate_service || fail_loudly "the service did not come back after the second recreate"
pay O-2006 14:01
check "O-2006: after the recreate both outputs hold ReadyToShip" poll 60 both_emit ReadyToShip O-2006
check "the new container's log shows the join store restored and the Camel timeouts restored" \
    restore_logged

echo "Observability"
check "Prometheus holds order_readiness_total for the Camel path with outcome overdue, at least 2" \
    poll 90 prom_holds 'sum(max_over_time(order_readiness_total{outcome="overdue",path="camel",service_name="shipping-service"}[1h])) >= 2'
check "Tempo has a trace for shipping-service" poll 90 tempo_has_trace shipping-service
check "Loki has logs for shipping-service" poll 90 loki_has_log shipping-service

if [[ "$PASSED" != "$TOTAL" ]]; then
    echo "shipping-service log, last lines:" >&2
    logs | tail -40 | cut -c1-260 >&2 || true
fi
echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
