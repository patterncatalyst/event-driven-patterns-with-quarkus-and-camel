#!/usr/bin/env bash
# Starts the stack and checks the observable effects of retry and dead letter on edp.orders.placed: an unparseable
# record and an invalid one go to the dead letter topic with their original bytes, a record that hits a locked row
# is retried and reserved, and the record behind it waits. Then the same inputs run through the Camel route.
# FAILURE_STRATEGY=fail, RETRY_MAX=0, CAMEL_DEAD_LETTER_CHANNEL=false and the other settings of the README are
# the negative witnesses. KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-13

COMPOSE=(docker compose -f compose.yaml)
APP=edp-inventory-service
TOPIC=edp.orders.placed
DLQ_TOPIC=edp.orders.placed-dlq
HOLD="${HOLD:-12}"
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
TOTAL=0
PASSED=0

# The inputs. O-7002 is cut off, O-7006 is not JSON at all, O-7003 asks for a quantity of 0.
BAD_7002='{"orderId":"O-7002",'
JUNK_7006='junk O-7006 not json'
order() { printf '{"orderId":"%s","customerId":"CU-77","itemSku":"%s","quantity":%s}' "$1" "$2" "$3"; }

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

expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
fail_loudly() { printf 'PRECONDITION FAILED: %s\n' "$1" >&2; exit 1; }

kafka() { docker exec edp-kafka "/opt/kafka/bin/$1" --bootstrap-server localhost:9092 "${@:2}"; }
sql() { "${PSQL[@]}" -c "$1"; }
ready() { curl -sf http://localhost:8080/q/health/ready >/dev/null; }
stock() { sql "SELECT quantity_on_hand FROM inventory.inventory_item WHERE sku='$1'"; }
stock_is() { [[ "$(stock "$1")" == "$2" ]]; } # stock_is <sku> <quantity>
stocks() { sql "SELECT string_agg(quantity_on_hand::text, ' ' ORDER BY sku) FROM inventory.inventory_item"; }

# A background session that holds a row lock on the SKU for $HOLD seconds.
start_lock() {
    "${PSQL[@]}" -c "DO \$\$ BEGIN PERFORM 1 FROM inventory.inventory_item WHERE sku = '$1' FOR UPDATE; PERFORM pg_sleep($HOLD); END \$\$" >/dev/null 2>&1 &
}
lock_held() {
    [[ "$(sql "SELECT count(*) FROM pg_stat_activity WHERE datname='edp' AND state='active' AND pid <> pg_backend_pid() AND query LIKE '%sku = ''$1'' FOR UPDATE%pg_sleep%'")" -ge 1 ]]
}
lock_released() { ! lock_held "$1"; }

# Every record as "Offset:N<TAB>[headers<TAB>]key<TAB>value", one record per line (a header may span lines).
normalise() {
    python3 -I -c "
import re, sys
text = sys.stdin.buffer.read().decode('utf-8', 'replace')
for rec in re.split(r'(?m)^(?=Offset:[0-9]+\t)', text):
    if rec.startswith('Offset:'):
        print(rec.rstrip('\n').replace('\n', ' '))
"
}
dump() { # dump <topic> [extra formatter properties]: the topic from the beginning
    kafka kafka-console-consumer.sh --topic "$1" --from-beginning --timeout-ms 6000 \
        --formatter-property print.key=true --formatter-property print.offset=true "${@:2}" 2>/dev/null | normalise || true
}
dlq_dump() { dump "$DLQ_TOPIC" --formatter-property print.headers=true; }
dlq_lines_for() { printf '%s\n' "$DLQ" | awk -F'\t' -v k="$1" 'index($NF, k)'; } # records whose value mentions the id
dlq_count() { printf '%s\n' "$DLQ" | grep -c '^Offset:' || true; }
copies_with_value() { # copies_with_value <id> <value>: how many dead letters for the id carry exactly this value
    dlq_lines_for "$1" | V="$2" awk -F'\t' '$NF == ENVIRON["V"]' | wc -l
}
source_offset() { printf '%s\n' "$SRC" | awk -F'\t' -v k="$1" '$2 == k {sub(/^Offset:/, "", $1); print $1}'; }

attempts() { docker logs "$APP" 2>&1 | grep -c "reserving $1:" || true; }
group_assigned() { kafka kafka-consumer-groups.sh --describe --group "$1" 2>/dev/null | awk -v t="$TOPIC" '$2 == t && $3 == 0 && $7 != "-" {f = 1} END {exit !f}'; }
group_lag_zero() {
    kafka kafka-consumer-groups.sh --describe --group "$1" 2>/dev/null | awk -v t="$TOPIC" '
        $2 == t && $3 == 0 {seen = 1; if ($4 == "-" || $6 != 0) bad = 1} END {exit !(seen && !bad)}'
}
tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }

produce_inputs() { # the six records in one producer call, so they arrive as one batch in this order
    {
        printf 'O-7001\t%s\n' "$(order O-7001 SKU-11 1)"
        printf 'O-7002\t%s\n' "$BAD_7002"
        printf 'O-7006\t%s\n' "$JUNK_7006"
        printf 'O-7003\t%s\n' "$(order O-7003 SKU-13 0)"
        printf 'O-7004\t%s\n' "$(order O-7004 SKU-14 1)"
        printf 'O-7005\t%s\n' "$(order O-7005 SKU-15 1)"
    } | docker exec -i edp-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 \
        --topic "$TOPIC" --reader-property parse.key=true >/dev/null 2>&1
}

# O-7004 has been retried while its row is still locked, and O-7005 behind it has not been read.
retried_while_locked() { lock_held SKU-14 && (( $(attempts O-7004) >= 2 )); }
blocked_behind_retry() { lock_held SKU-14 && [[ "$(stock SKU-14)/$(stock SKU-15)" == "100/100" ]]; }

position_kept() { # position_kept <id>: the dead letter names the source topic and the offset the record had in it
    local off line; off="$(source_offset "$1")"
    [[ -n "$off" ]] || return 1
    line="$(dlq_lines_for "$1" | head -1)"
    [[ "$line" == *"dead-letter-topic:$TOPIC"* && "$line" =~ dead-letter-offset:${off}[,[:space:]] ]]
}
reason_names_quantity() { [[ "$(dlq_lines_for O-7003 | head -1)" == *"dead-letter-reason:quantity must be positive: 0"* ]]; }
none_of_the_good_ones() { # none of the three valid orders is on the dead letter topic
    local id
    for id in O-7001 O-7004 O-7005; do [[ -z "$(dlq_lines_for "$id")" ]] || return 1; done
}

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 ready || fail_loudly "inventory-service is not ready"

echo "Infrastructure"
check "inventory-service is ready and inventory-smallrye owns partition 0 of $TOPIC" poll 60 group_assigned inventory-smallrye
check "both topics exist and the five SKUs hold 100 each" \
    expect "$(kafka kafka-topics.sh --list | grep -cxE "$TOPIC|$DLQ_TOPIC" || true)/$(stocks)" "2/100 100 100 100 100"

echo "SmallRye: SKU-14 is locked, six records are produced"
start_lock SKU-14
poll 15 lock_held SKU-14 || fail_loudly "the lock session on SKU-14 did not start"
produce_inputs
precondition "the row lock on SKU-14 is still held after the records were produced" lock_held SKU-14
check "O-7004 is retried while its row is locked" poll 10 retried_while_locked
check "while the lock is held, O-7005 behind it waits: SKU-14 and SKU-15 still 100" blocked_behind_retry
check "after the lock is released, O-7004 is reserved: SKU-14 = 99" poll 40 stock_is SKU-14 99
check "O-7005 follows: SKU-15 = 99, consumption went on past both bad records" poll 40 stock_is SKU-15 99
check "O-7001 is reserved: SKU-11 = 99" poll 20 stock_is SKU-11 99

# The dead letter topic and the source topic are read once; the checks below read these copies.
DLQ="$(dlq_dump)"
SRC="$(dump "$TOPIC")"

echo "SmallRye: dead letters"
check "O-7002 is on the dead letter topic with its original bytes" expect "$(copies_with_value O-7002 "$BAD_7002")" 1
check "O-7002 names its source topic and the offset it had there" position_kept O-7002
check "O-7006, not JSON at all, is on the dead letter topic with its original bytes" expect "$(copies_with_value O-7006 "$JUNK_7006")" 1
check "O-7003 is on the dead letter topic and its reason names the quantity rule" reason_names_quantity
check "O-7003 was attempted exactly once" expect "$(attempts O-7003)" 1
check "the dead letter topic holds exactly 3 records" \
    expect "$(dlq_count)" 3
check "the three valid orders are not on the dead letter topic" none_of_the_good_ones
check "the group has no lag: the offsets of the dead-lettered records were committed" poll 30 group_lag_zero inventory-smallrye

echo "Camel: the same inputs through the route"
sql "UPDATE inventory.inventory_item SET quantity_on_hand = 100" >/dev/null
# A new group starts at the end of the topic, so only the records produced below are read.
kafka kafka-consumer-groups.sh --group inventory-camel --topic "$TOPIC" --reset-offsets --to-latest --execute >/dev/null 2>&1 \
    || fail_loudly "could not set the offsets of inventory-camel"
CONSUMER_PROFILE=camel "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait inventory-service
poll 60 ready || fail_loudly "inventory-service is not ready with the camel profile"
check "the service runs with the camel profile and inventory-camel owns partition 0" \
    expect "$(docker exec "$APP" printenv QUARKUS_PROFILE)/$(poll 60 group_assigned inventory-camel && echo assigned)" "camel/assigned"
start_lock SKU-14
poll 15 lock_held SKU-14 || fail_loudly "the lock session on SKU-14 did not start"
produce_inputs
precondition "the row lock on SKU-14 is still held after the records were produced" lock_held SKU-14
check "O-7004 is redelivered while its row is locked" poll 10 retried_while_locked
check "while the lock is held, SKU-14 and SKU-15 are still 100" blocked_behind_retry
check "after the lock is released, O-7004 is reserved: SKU-14 = 99" poll 40 stock_is SKU-14 99
check "O-7005 is reserved after O-7004: SKU-15 = 99" poll 40 stock_is SKU-15 99
check "O-7001 is reserved by the route: SKU-11 = 99" stock_is SKU-11 99
DLQ="$(dlq_dump)"
echo "Camel: dead letters"
check "the dead letter topic gained O-7002 and O-7006 with their original bytes" \
    expect "$(copies_with_value O-7002 "$BAD_7002")/$(copies_with_value O-7006 "$JUNK_7006")" "2/2"
check "the dead letter topic gained O-7003, attempted once by the route" \
    expect "$(dlq_lines_for O-7003 | wc -l)/$(attempts O-7003)" "2/1"
check "the dead letter topic holds exactly 6 records" expect "$(dlq_count)" 6

echo "Observability"
check "Tempo has a trace for inventory-service" poll 90 tempo_has_trace inventory-service
check "Loki has logs for inventory-service" poll 90 loki_has_log inventory-service

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
