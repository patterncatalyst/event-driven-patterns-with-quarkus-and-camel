#!/usr/bin/env bash
# Starts the stack and asks three engines the same question about the same records: deliveries per carrier per
# tumbling minute of event time. Kafka Streams answers through interactive queries over a window store, Flink SQL
# writes closed windows to an upsert-kafka topic, RisingWave keeps them in materialized views queried with psql. The
# lateness is one number (LATENESS_SECONDS, default 10) in all three. KS_GRACE_SECONDS=0 is the negative witness
# (demo code): Kafka Streams alone gets a smaller lateness. KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-ac

COMPOSE=(docker compose -f compose.yaml)
APP=edp-delivery-counts
INPUT=edp.shipping.delivered
FLINK_TOPIC=edp.shipping.delivery-counts-flink
LATENESS_SECONDS=${LATENESS_SECONDS:-10}
export LATENESS_SECONDS # compose gives Kafka Streams the same number unless KS_GRACE_SECONDS says otherwise
# Event times are seconds after BASE, a minute boundary ten minutes in the past: window 1 starts at BASE, window 4 at
# BASE + 180. The broker refuses a record timestamp more than an hour ahead, so nothing is in the future.
BASE=$(( $(date +%s) / 60 * 60 - 600 ))
PY=(python3 -I counts.py)
TOTAL=0
PASSED=0
WORK=$(mktemp -d)
declare -A SEEN
LAST_US=0

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

poll_timed() { # poll_timed <name> <seconds> <command...>: also records the ms from the last produced record
    local name="$1" end=$((SECONDS + $2)); shift 2
    until "$@"; do
        (( SECONDS < end )) || return 1
        sleep 0.5
    done
    SEEN[$name]=$(( (${EPOCHREALTIME/./} - LAST_US) / 1000 ))
}

fail_loudly() { printf 'PRECONDITION FAILED: %s\n' "$1" >&2; exit 1; }
kafka() { docker exec edp-kafka "/opt/kafka/bin/$1" --bootstrap-server localhost:9092 "${@:2}"; }
rw() { docker exec -e PGPASSWORD= edp-postgres psql -h risingwave -p 4566 -U root -d dev "$@"; }
rw_script() { docker exec -i -e PGPASSWORD= edp-postgres psql -h risingwave -p 4566 -U root -d dev -v ON_ERROR_STOP=1 -f -; }
logs() { docker logs "$APP" 2>&1; }
iso() { date -u -d "@$1" +%Y-%m-%dT%H:%M:%SZ; }
produce() { docker exec -i edp-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic "$INPUT" >/dev/null 2>&1; }

# --- the three answers, each normalized to "carrier|window offset|deliveries" for the windows that start in [lo, hi)
ks_answer() { # ask the Kafka Streams instance, one query per carrier
    local c
    for c in FASTLINE AIRCO; do
        curl -sf "http://localhost:8080/counts?carrier=$c&from=$(iso $((BASE - 3600)))&to=$(iso $((BASE + 3600)))"
        echo
    done | "${PY[@]}" norm ks "$BASE" "$1" "$2"
}
flink_raw() { # the console consumer exits non-zero when its timeout ends the read
    kafka kafka-console-consumer.sh --topic "$FLINK_TOPIC" --from-beginning --timeout-ms 3000 \
        --formatter-property print.key=true 2>/dev/null || true
}
flink_answer() { flink_raw | "${PY[@]}" norm flink "$BASE" "$1" "$2"; }
flink_records() { flink_raw | wc -l | tr -d ' '; }
rw_answer() { # rw_answer <view> <lo> <hi>
    rw -At -F'|' -c "select carrier, window_start, deliveries from $1" | "${PY[@]}" norm rw "$BASE" "$2" "$3"
}
w1_fastline() { awk -F'|' '$1 == "FASTLINE" && $2 == 0 {print $3}'; } # the count of FASTLINE in window 1, from an answer on stdin

# Closed windows of the scenario: window 1 starts at offset 0, window 2 at 60, window 3 at 120.
EXPECTED=$'AIRCO|0|2\nAIRCO|60|3\nAIRCO|120|1\nFASTLINE|0|4\nFASTLINE|60|2\nFASTLINE|120|1'
EXPECTED_W4="$EXPECTED"$'\nFASTLINE|180|1'

# --- preconditions
topics_init_done() { [[ "$(docker inspect -f '{{.State.Status}}/{{.State.ExitCode}}' edp-topics-init)" == "exited/0" ]]; }
partitions_of() { kafka kafka-topics.sh --describe --topic "$1" 2>/dev/null | grep -c 'Partition: [0-9]' || true; }
topics_have_one_partition() { [[ "$(for t in "$INPUT" "$FLINK_TOPIC"; do partitions_of "$t"; done | tr '\n' ' ')" == "1 1 " ]]; }
store_answers() { curl -sf "http://localhost:8080/counts?carrier=FASTLINE&from=$(iso $((BASE - 3600)))&to=$(iso $((BASE + 3600)))" >/dev/null; }
app_ready() { curl -sf http://localhost:8080/q/health/ready >/dev/null && store_answers; }
flink_running() { local o; o="$(curl -sf http://127.0.0.1:18081/jobs/overview)" || return 1; [[ "$o" == *'"state":"RUNNING"'* ]]; }
rw_ready() { rw -Atc "select count(*) from counts_rw" >/dev/null && rw -Atc "select count(*) from counts_rw_final" >/dev/null; }
lateness_is_same() { # the one number is in the Kafka Streams grace and in both SQL files
    logs | grep -F "grace=PT${LATENESS_SECONDS}S" >/dev/null \
        && grep -F "INTERVAL '$LATENESS_SECONDS' SECOND" "$WORK/flink.sql" >/dev/null \
        && grep -F "INTERVAL '$LATENESS_SECONDS seconds'" "$WORK/risingwave.sql" >/dev/null
}

# --- the checks
ks_closed() { [[ "$(ks_answer 0 180)" == "$EXPECTED" ]]; }
flink_final() { [[ "$(flink_answer 0 99999)" == "$EXPECTED" ]]; }
rw_closed() { [[ "$(rw_answer counts_rw 0 180)" == "$EXPECTED" ]]; }
rw_final() { [[ "$(rw_answer counts_rw_final 0 99999)" == "$EXPECTED" ]]; }
three_equal() { # the closed windows are the same list in all three, and the list is not empty
    local a b c
    a="$(ks_answer 0 180)"; b="$(flink_answer 0 180)"; c="$(rw_answer counts_rw 0 180)"
    [[ -n "$a" && "$a" == "$b" && "$b" == "$c" ]]
}
out_of_order_counted() { # FASTLINE window 1 is 4 in all three: the record 7 s behind the newest event is inside it
    [[ "$(ks_answer 0 180 | w1_fastline)" == 4 && "$(flink_answer 0 180 | w1_fastline)" == 4 \
        && "$(rw_answer counts_rw 0 180 | w1_fastline)" == 4 ]]
}
updating_results() { # an open window already answers in Kafka Streams and in the default RisingWave view
    [[ "$(ks_answer 180 240)" == 'FASTLINE|180|1' && "$(rw_answer counts_rw 180 240)" == 'FASTLINE|180|1' ]]
}
ks_late_dropped() { # window 1 unchanged, window 4 present, and Kafka Streams logged the skipped record
    [[ "$(ks_answer 0 240)" == "$EXPECTED_W4" ]] && (( $(logs | grep -c 'Skipping record for expired window' || true) >= 1 ))
}
flink_late_dropped() { # window 4 closed by the tick, window 1 still 4, and no revised record for window 1
    [[ "$(flink_answer 0 99999)" == "$EXPECTED_W4" && "$(flink_records)" == 7 ]]
}
rw_late_dropped() {
    [[ "$(rw_answer counts_rw 0 240)" == "$EXPECTED_W4" && "$(rw_answer counts_rw_final 0 99999)" == "$EXPECTED_W4" ]]
}
three_equal_after_late() {
    local a b c
    a="$(ks_answer 0 240)"; b="$(flink_answer 0 240)"; c="$(rw_answer counts_rw 0 240)"
    [[ -n "$a" && "$a" == "$b" && "$b" == "$c" ]]
}

# --- observability
tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }

sed "s/@LATENESS@/$LATENESS_SECONDS/g" sql/flink.sql > "$WORK/flink.sql"
sed "s/@LATENESS@/$LATENESS_SECONDS/g" sql/risingwave.sql > "$WORK/risingwave.sql"

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 topics_init_done || fail_loudly "topics-init did not finish"

echo "Infrastructure"
precondition "the input topic and the Flink output topic exist with one partition each" topics_have_one_partition
precondition "delivery-counts-service is ready (Kafka Streams RUNNING) and its store answers" poll 90 app_ready
docker cp "$WORK/flink.sql" edp-flink-jobmanager:/tmp/flink.sql
SUBMIT_T0=$(date +%s%N)
docker exec edp-flink-jobmanager /opt/flink/bin/sql-client.sh -f /tmp/flink.sql >"$WORK/flink-submit.log" 2>&1 \
    || { cat "$WORK/flink-submit.log" >&2; fail_loudly "Flink did not accept the SQL"; }
FLINK_SUBMIT_MS=$((($(date +%s%N) - SUBMIT_T0) / 1000000)); SUBMIT_T0=$(date +%s%N)
rw_script < "$WORK/risingwave.sql" >"$WORK/rw-submit.log" 2>&1 \
    || { cat "$WORK/rw-submit.log" >&2; fail_loudly "RisingWave did not accept the SQL"; }
RW_DDL_MS=$((($(date +%s%N) - SUBMIT_T0) / 1000000))
echo "NOTE: Flink job submit $FLINK_SUBMIT_MS ms, RisingWave DDL $RW_DDL_MS ms"
precondition "the Flink job is RUNNING" poll 60 flink_running
precondition "RisingWave has the source and both materialized views" poll 30 rw_ready
check "the lateness is $LATENESS_SECONDS s in the Kafka Streams grace, the Flink watermark and the RisingWave watermark" lateness_is_same

echo "Fourteen deliveries in event-time order, one of them 7 s behind the newest, the last one opening window 4"
"${PY[@]}" input "$BASE" | produce
LAST_US=${EPOCHREALTIME/./}
check "Kafka Streams answers the three closed windows" poll_timed ks 60 ks_closed
check "Flink wrote exactly the three closed windows to its output topic, none for the open one" poll_timed flink 90 flink_final
check "RisingWave counts_rw has the same three closed windows" poll_timed rw 60 rw_closed
check "RisingWave counts_rw_final (EMIT ON WINDOW CLOSE) holds exactly the three closed windows, none for the open one" \
    poll_timed rw_final 60 rw_final
check "the closed windows are the same list in all three engines" three_equal
check "the record 7 s behind the newest event time is counted in window 1 by all three: FASTLINE 4" out_of_order_counted
check "an open window already answers in Kafka Streams and in counts_rw" updating_results
echo "NOTE: from the last record to the closed windows: Kafka Streams ${SEEN[ks]:-n/a} ms, Flink ${SEEN[flink]:-n/a} ms, RisingWave ${SEEN[rw]:-n/a} ms, RisingWave final ${SEEN[rw_final]:-n/a} ms"

echo "A late record for window 1, after window 4 opened, then a record that closes window 4"
{ "${PY[@]}" late "$BASE"; "${PY[@]}" tick "$BASE"; } | produce
check "Kafka Streams drops the late record: window 1 stays 4 and the skip is logged" poll 60 ks_late_dropped
check "Flink drops it: window 4 closed, window 1 stays 4 and its topic holds 7 records, none revised" poll 90 flink_late_dropped
check "RisingWave drops it: window 1 stays 4 in both views and the final view now holds window 4" poll 60 rw_late_dropped
check "the closed windows are still the same list in all three engines" three_equal_after_late

echo "NOTE: memory in use by all containers: $(docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' | grep '^edp-' | python3 -I -c '
import re, sys
unit = {"KiB": 1 / 1024, "MiB": 1, "GiB": 1024}
total = sum(float(m.group(1)) * unit[m.group(2)] for m in map(lambda l: re.match(r"\S+ ([0-9.]+)(KiB|MiB|GiB)", l), sys.stdin) if m)
sys.stdout.write(f"{total / 1024:.2f} GiB")') (docker stats, sampled once)"

echo "Observability"
check "Tempo has a trace for delivery-counts-service" poll 90 tempo_has_trace delivery-counts-service
check "Loki has logs for delivery-counts-service" poll 90 loki_has_log delivery-counts-service

if [[ "$PASSED" != "$TOTAL" ]]; then
    echo "Kafka Streams:" >&2; ks_answer 0 99999 >&2 || true
    echo "Flink topic:" >&2; flink_answer 0 99999 >&2 || true
    echo "RisingWave counts_rw:" >&2; rw_answer counts_rw 0 99999 >&2 || true
    echo "RisingWave counts_rw_final:" >&2; rw_answer counts_rw_final 0 99999 >&2 || true
    echo "delivery-counts-service log, last lines:" >&2; logs | tail -30 | cut -c1-260 >&2 || true
fi
echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
