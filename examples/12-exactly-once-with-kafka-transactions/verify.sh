#!/usr/bin/env bash
# Starts the stack and checks the observable effects of a Kafka transaction around a read-process-write loop:
# a committed batch gives one output per input and moves the input offset with it; a crash in the middle of a batch
# leaves written but uncommitted records that a read_committed reader cannot see, and the restart produces each
# output exactly once; the same crash on a Camel route that only makes its output transactional produces duplicates.
# PAYMENT_MODE=camel-transacted runs the first phases on the Camel route, so the exactly-once check must fail.
# KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-12

COMPOSE=(docker compose -f compose.yaml)
SERVICE=http://localhost:8080
IN=edp.inventory.reserved
OUT=edp.payments.processed
TXID=payment-service-tx
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

fail_loudly() { printf 'PRECONDITION FAILED: %s\n' "$1" >&2; exit 1; }

precondition() { # precondition "<description>" <command...>: a check the rest of the run depends on
    local desc="$1"; shift
    TOTAL=$((TOTAL + 1))
    if "$@" >/dev/null 2>&1; then
        PASSED=$((PASSED + 1)); printf 'PASS %d: %s\n' "$TOTAL" "$desc"
    else
        printf 'FAIL %d: %s\n' "$TOTAL" "$desc"
        fail_loudly "$desc"
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

kafka() { docker exec edp-kafka "/opt/kafka/bin/$1" --bootstrap-server localhost:9092 "${@:2}"; }
ready() { curl -sf "$SERVICE/q/health/ready" >/dev/null; }
exited_by_halt() { [[ "$(docker inspect -f '{{.State.Status}} {{.State.ExitCode}}' edp-payment-service)" == "exited 137" ]]; }

# One console-producer call, so the records reach the consumer as one batch (separate calls become batches of one).
# Arguments are <orderId>:<amount>.
reserve() {
    local line
    for line in "$@"; do
        printf '%s\t{"orderId":"%s","sku":"SKU-42","quantity":3,"amount":%s}\n' "${line%%:*}" "${line%%:*}" "${line##*:}"
    done | docker exec -i edp-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 \
        --topic "$IN" --reader-property parse.key=true >/dev/null 2>&1 || fail_loudly "the console producer failed"
}

# Every record of the output topic visible at an isolation level, as "key<TAB>value", read from the beginning.
payments() { # payments <read_committed|read_uncommitted>
    docker exec edp-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic "$OUT" \
        --from-beginning --timeout-ms 5000 --isolation-level "$1" --formatter-property print.key=true 2>/dev/null | grep -aP '\t' || true
}
keys_matching() { payments "$1" | awk -F'\t' -v re="$2" '$1 ~ re {print $1}'; } # keys_matching <isolation> <regex>, in log order
count_matching() { keys_matching "$@" | awk 'END {print NR}'; }
distinct_matching() { keys_matching "$@" | sort -u | awk 'END {print NR}'; }
# The group's committed offset on the input topic, or - when it has none.
group_offset() { kafka kafka-consumer-groups.sh --describe --group "$1" 2>/dev/null | awk -v t="$IN" '$2 == t {print $4}'; }
tx_state() { kafka kafka-transactions.sh describe --transactional-id "$1" 2>/dev/null | awk -v id="$1" '$2 == id {print $5}'; }
end_offset() { kafka kafka-get-offsets.sh --topic "$1" 2>/dev/null | awk -F: '{print $3}'; }
partition_count() { kafka kafka-topics.sh --describe --topic "$1" 2>/dev/null | grep -o 'PartitionCount: [0-9]*' | awk '{print $2}'; }

# The five committed payments: one per order, keyed by the order id, with the id, status and amount of the input.
payments_are_correct() {
    payments read_committed | python3 -I -c "
import json, sys
from decimal import Decimal
want = {'O-5001': '10.50', 'O-5002': '21.00', 'O-5003': '31.50', 'O-5004': '42.00', 'O-5005': '52.50'}
got = set()
for line in sys.stdin:
    key, value = line.rstrip('\n').split('\t', 1)
    if not key.startswith('O-500'):
        continue
    assert key not in got, 'duplicate ' + key
    p = json.loads(value, parse_float=Decimal)['payment']
    assert p['id'] == 'P-' + key and p['orderId'] == key and p['status'] == 'COMPLETED', p
    assert Decimal(str(p['amount'])) == Decimal(want[key]), p
    got.add(key)
assert got == set(want), got"
}
committed_batch_is_five() { [[ "$(count_matching read_committed '^O-500[1-5]$')" == 5 ]] && payments_are_correct; }
# The ten orders of the crashed batch, each exactly once, and no later than the transaction timeout after the crash:
# the old transaction was aborted by the restarted producer, not by the coordinator's timer.
batch_is_ten_once() {
    [[ "$(count_matching read_committed '^O-51(0[1-9]|10)$')" == 10 && "$(distinct_matching read_committed '^O-51(0[1-9]|10)$')" == 10 ]] \
        && { TEN_AT=$SECONDS; (( SECONDS - CRASH_AT < 55 )); }
}
# The ten orders are committed once each, and the K payments the aborted transaction wrote are the only extra records
# a read_uncommitted reader returns.
uncommitted_is_ten_plus_k() {
    [[ "$(count_matching read_committed '^O-51(0[1-9]|10)$')" == 10 && "$(count_matching read_uncommitted '^O-51(0[1-9]|10)$')" == $((10 + K)) ]]
}
camel_has_every_order() { [[ "$(distinct_matching read_committed '^O-60(0[1-9]|10)$')" == 10 ]]; }
camel_has_a_duplicate() { [[ -n "$(keys_matching read_committed '^O-600[1-7]$' | sort | uniq -d)" ]]; }
camel_wrote_before_the_crash() { [[ "$(count_matching read_committed '^O-600[1-7]$')" -ge 1 ]]; }
camel_offset_is_behind() { local off; off="$(group_offset payment-camel)"; [[ "$off" == "-" || ( "$off" =~ ^[0-9]+$ && "$off" -lt "$O6008" ) ]]; }
group_is() { [[ "$(group_offset "$1")" == "$2" ]]; } # group_is <group> <offset>
tx_is() { [[ "$(tx_state "$TXID")" == "$1" ]]; }

tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
prom_holds() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" | grep -q '"value"'; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 ready || fail_loudly "payment-service is not ready"

echo "Infrastructure"
check "payment-service is ready" ready
check "both topics exist with one partition" expect "$(partition_count "$IN")/$(partition_count "$OUT")" "1/1"

echo "A committed batch"
reserve O-5001:10.50 O-5002:21.00 O-5003:31.50 O-5004:42.00 O-5005:52.50
check "a read_committed reader sees exactly five payments, one per order, keyed by the order id" poll 60 committed_batch_is_five
check "the group payment-service has committed offset 5, with the output" poll 30 group_is payment-service 5
check "the transaction coordinator reports $TXID as CompleteCommit" poll 30 tx_is CompleteCommit

echo "A crash in the middle of a batch"
export CRASH_MID_BATCH_AT=O-5108
"${COMPOSE[@]}" up -d --no-deps --force-recreate --wait payment-service
reserve O-5101:99.00 O-5102:99.00 O-5103:99.00 O-5104:99.00 O-5105:99.00 O-5106:99.00 O-5107:99.00 O-5108:99.00 O-5109:99.00 O-5110:99.00
precondition "the container exits with 137 at O-5108" poll 60 exited_by_halt
CRASH_AT=$SECONDS
K="$(count_matching read_uncommitted '^O-510[1-7]$')"
precondition "a read_uncommitted reader sees the partial batch the open transaction wrote ($K of O-5101 to O-5107)" expect "$([[ "$K" -ge 1 ]] && echo yes)" yes
check "while the service is down, a read_committed reader sees none of O-5101 to O-5110" expect "$(count_matching read_committed '^O-51(0[1-9]|10)$')" 0
check "the group payment-service is still at offset 5" group_is payment-service 5

echo "Restart without the switch"
export CRASH_MID_BATCH_AT=
"${COMPOSE[@]}" up -d --no-deps --force-recreate payment-service >/dev/null
RESTART_AT=$SECONDS; TEN_AT=
check "within 30 s a read_committed reader sees all ten orders, each exactly once" poll 30 batch_is_ten_once
if [[ -n "$TEN_AT" ]]; then echo "NOTE: k=$K; ten orders once after $((TEN_AT - RESTART_AT)) s from restart, $((TEN_AT - CRASH_AT)) s from crash detection"; fi
check "a read_committed reader sees the ten orders and a read_uncommitted reader the ten plus $K aborted copies" uncommitted_is_ten_plus_k
check "the group payment-service is at offset 15" poll 30 group_is payment-service 15
check "the transaction coordinator reports $TXID as CompleteCommit" poll 30 tx_is CompleteCommit

echo "Comparison: Camel transacted route"
"${COMPOSE[@]}" stop payment-service >/dev/null
# A new Camel group starts at the end of what exists now, so only the comparison records are read.
kafka kafka-consumer-groups.sh --group payment-camel --topic "$IN" --reset-offsets --to-latest --execute >/dev/null 2>&1 \
    || fail_loudly "the payment-camel group could not be reset"
O6001="$(end_offset "$IN")"
O6008=$((O6001 + 7))
export PAYMENT_MODE=camel-transacted CRASH_MID_BATCH_AT=O-6008
"${COMPOSE[@]}" up -d --no-deps --force-recreate --wait payment-service
reserve O-6001:45.00 O-6002:45.00 O-6003:45.00 O-6004:45.00 O-6005:45.00 O-6006:45.00 O-6007:45.00 O-6008:45.00 O-6009:45.00 O-6010:45.00
check "the container exits with 137 at O-6008" poll 60 exited_by_halt
precondition "a read_committed reader already sees payments for some of O-6001 to O-6007: each exchange committed its own transaction" camel_wrote_before_the_crash
precondition "the committed offset of payment-camel is behind O-6008 (offset $O6008)" camel_offset_is_behind
export CRASH_MID_BATCH_AT=
"${COMPOSE[@]}" up -d --no-deps --force-recreate payment-service >/dev/null
check "after the restart every order O-6001 to O-6010 has a payment: nothing is lost" poll 60 camel_has_every_order
check "at least one of O-6001 to O-6007 has more than one committed payment: the input was read again" camel_has_a_duplicate

echo "NOTE: committed payments per order O-6001 to O-6010 (read_committed): $(keys_matching read_committed '^O-60(0[1-9]|10)$' | sort | uniq -c | awk '{printf "%s=%s ", $2, $1}')"
echo "Observability"
check "Tempo has a trace for payment-service" poll 90 tempo_has_trace payment-service
check "Loki has logs for payment-service" poll 90 loki_has_log payment-service
check "Prometheus holds the transaction commit time of the producer of payment-service above 0" \
    poll 90 prom_holds 'max_over_time(kafka_producer_txn_commit_time_ns_total{service_name="payment-service"}[1h]) > 0'

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
