#!/usr/bin/env bash
# Starts the stack and checks the observable effects of grouping the same shipments two ways. Kafka Streams closes one
# final list per carrier per 15-minute slot of event time and drops a record that arrives after its window closed;
# the Camel Aggregator closes a batch per carrier at 5 shipments or after 10 s of quiet, in arrival order. The open
# window and the open batch both survive a recreate. PICKUP_EMIT=updates and PICKUP_CHANGELOG=off are the negative
# witnesses (see the README). REPEAT=15 repeats the Camel batch scenario. KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-21

COMPOSE=(docker compose -f compose.yaml)
APP=edp-shipping-service
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
SCHEDULED=edp.shipping.scheduled
LISTS=edp.shipping.pickup-lists
LISTS_CAMEL=edp.shipping.pickup-lists-camel
CHANGELOG=shipping-pickups-pickup-lists-changelog
# Event times are in the recent past: Kafka Streams forwards event time as the record timestamp, the broker refuses a
# timestamp more than an hour ahead of its clock, and the changelog topic of a window store keeps records for a day and
# the window size and grace after their timestamp, then deletes them. The scenario therefore runs from a base 20 hours
# before now, on a 15-minute boundary; its label 09:00 is that base and the last label (19:51) is 9 hours before now.
BASE_AGE_HOURS=${BASE_AGE_HOURS:-20} # demo setting: 44 puts the restart slot beyond the changelog retention (see the README)
BASE_EPOCH=$(( ($(date +%s) - BASE_AGE_HOURS * 3600) / 900 * 900 ))
REPEAT=${REPEAT:-0}
TOTAL=0
PASSED=0
REPEAT_FAILED=0
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

expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
fail_loudly() { printf 'PRECONDITION FAILED: %s\n' "$1" >&2; exit 1; }

kafka() { docker exec edp-kafka "/opt/kafka/bin/$1" --bootstrap-server localhost:9092 "${@:2}"; }
sql() { "${PSQL[@]}" -c "$1"; }
live() { curl -sf http://localhost:8080/q/health/live >/dev/null; }
logs() { docker logs "$APP" 2>&1; }
now_ms() { date +%s%3N; }

# --- the topics and the groups
topics_init_done() { [[ "$(docker inspect -f '{{.State.Status}}/{{.State.ExitCode}}' edp-topics-init)" == "exited/0" ]]; }
partitions_of() { kafka kafka-topics.sh --describe --topic "$1" 2>/dev/null | grep -c 'Partition: [0-9]' || true; }
topics_have_one_partition() { [[ "$(for t in "$SCHEDULED" "$LISTS" "$LISTS_CAMEL"; do partitions_of "$t"; done | tr '\n' ' ')" == "1 1 1 " ]]; }
camel_group_assigned() { # the Camel consumer owns its partition
    kafka kafka-consumer-groups.sh --describe --group shipping-pickups-camel 2>/dev/null \
        | awk '$3 ~ /^[0-9]+$/ && $7 != "-" {f = 1} END {exit !f}'
}
running() { live && camel_group_assigned; } # Kafka Streams RUNNING (liveness) and the route has its partition
changelog_end() { { kafka kafka-get-offsets.sh --topic "$CHANGELOG" 2>/dev/null || true; } | awk -F: '{n += $3} END {print n + 0}'; } # 0 when the topic does not exist (PICKUP_CHANGELOG=off)
open_batches() { sql "SELECT count(*) FROM shipping.pickups"; }
no_open_batches() { [[ "$(open_batches)" == 0 ]]; }
one_open_batch() { [[ "$(open_batches)" == 1 ]]; }
tables() { sql "SELECT string_agg(table_name, ',' ORDER BY table_name) FROM information_schema.tables WHERE table_schema = 'shipping'"; }

# --- input: ShipmentScheduled with an event time in the payload. The console producer cannot set a record
# timestamp, so event time can only come from the payload.
at() { date -u -d "@$((BASE_EPOCH + (10#${1%%:*} * 60 + 10#${1##*:} - 540) * 60))" +%Y-%m-%dT%H:%M:00Z; } # at <hh:mm>: the label's instant
sched() { # sched <id> <carrier> <hh:mm>: one "key|json" line
    printf 'O-%s|{"shipment":{"id":"%s","orderId":"O-%s","carrier":"%s","trackingNumber":"TRK-%s","status":"SCHEDULED"},"occurredAt":"%s"}\n' \
        "$1" "$1" "$1" "$2" "$1" "$(at "$3")"
}
produce() { # produce: "key|json" lines on stdin, one producer call
    docker exec -i edp-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic "$SCHEDULED" \
        --reader-property parse.key=true --reader-property 'key.separator=|' >/dev/null 2>&1
}

# --- output: both output topics are read by one long-lived consumer each, into files, every record stamped with
# the host time it arrived ("<microseconds>\t<key>\t<value>"). Checks read the files.
tail_topic() { # tail_topic <topic> <name>
    ( docker exec edp-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic "$1" \
        --from-beginning --timeout-ms 3600000 --formatter-property print.key=true 2>/dev/null \
        | while IFS= read -r line; do printf '%s\t%s\n' "${EPOCHREALTIME/./}" "$line"; done >> "$WORK/$2.tsv" ) &
    TAILERS+=("$!")
}
summary() { # summary <streams|camel>: "carrier|from|count|closedBy|ids" per list, ids sorted ("-" for no slot)
    python3 -I -c '
import json, sys
for line in open(sys.argv[1], errors="replace"):
    parts = line.rstrip("\n").split("\t", 2)
    if len(parts) != 3:
        continue
    try:
        v = json.loads(parts[2])
    except ValueError:
        continue
    print("|".join([v["carrier"], v["from"] or "-", str(v["count"]), v["closedBy"], ",".join(sorted(v["shipmentIds"]))]))
' "$WORK/$1.tsv"
}
has_line() { local s; s="$(summary "$1")"; [[ $'\n'"$s"$'\n' == *$'\n'"$2"$'\n'* ]]; } # has_line <streams|camel> <line>
no_lists() { [[ -z "$(summary "$1")" ]]; }
ids_of() { summary "$1" | cut -d'|' -f5 | tr ',' '\n' | sort; }
mentions() { ids_of "$1" | grep -Fx -- "$2" >/dev/null; } # mentions <streams|camel> <id>
slot_line() { printf '%s|%s|%s|window|%s' "$1" "$(at "$2")" "$3" "$4"; } # slot_line <carrier> <hh:mm> <count> <ids>
SLOT_0900="$(slot_line FASTLINE 09:00 8 F1,F2,F3,F4,F5,F6,F7,F9)"$'\n'"$(slot_line AIRCO 09:00 3 A1,A2,A3)"
lists_are() { [[ "$(summary streams | sort)" == "$(printf '%s\n' "$1" | sort)" ]]; } # lists_are <expected lines>

# --- observability
prom_holds() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" | grep '"value"' >/dev/null; }
tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }
no_error_logged() { [[ -z "$(logs | grep -E ' ERROR ' || true)" ]]; }

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 topics_init_done || fail_loudly "topics-init did not finish"
tail_topic "$LISTS" streams
tail_topic "$LISTS_CAMEL" camel

echo "Infrastructure"
precondition "shipping-service is live (Kafka Streams RUNNING) and the Camel route owns its partition" poll 90 running
precondition "the three topics exist with one partition each" topics_have_one_partition
check "the schema holds the two aggregation tables and the migration history" \
    expect "$(tables)" "flyway_schema_history,pickups,pickups_completed"

echo "Slot 09:00: FASTLINE F1 to F7 and AIRCO A1 to A3, one producer call"
{ for i in 1 2 3 4 5 6 7; do sched "F$i" FASTLINE "09:0$i"; done; for i in 1 2 3; do sched "A$i" AIRCO "09:0$i"; done; } | produce
camel_scenario() { # camel_scenario <prefix>: FASTLINE 5 by size and 2 by timeout, AIRCO 3 by timeout
    has_line camel "FASTLINE|-|5|size|${1}F1,${1}F2,${1}F3,${1}F4,${1}F5" && has_line camel "FASTLINE|-|2|timeout|${1}F6,${1}F7" \
        && has_line camel "AIRCO|-|3|timeout|${1}A1,${1}A2,${1}A3"
}
check "Camel: FASTLINE gives a batch of 5 closed by size and one of 2 closed by timeout; AIRCO one batch of 3 closed by timeout" \
    poll 40 camel_scenario ""
check "Kafka Streams has emitted no list: stream time is 09:07 and the window of slot 09:00 is still open" no_lists streams

echo "Out of order: F8 (09:16) then F9 (09:10), produced in that order"
{ sched F8 FASTLINE 09:16; sched F9 FASTLINE 09:10; } | produce
check "Camel puts F8 and F9 in the one batch that was open at arrival, closed by timeout" \
    poll 40 has_line camel 'FASTLINE|-|2|timeout|F8,F9'
check "stream time is 09:16 and the window is still open until 09:20: Kafka Streams still has no list" no_lists streams
echo "The tick: AIRCO T1 at 09:21"
sched T1 AIRCO 09:21 | produce
check "after the tick Kafka Streams holds exactly one list for FASTLINE 09:00 (count 8, F1 to F7 and F9) and one for AIRCO 09:00 (count 3)" \
    poll 30 lists_are "$SLOT_0900"
not_on_streams() { ! mentions streams "$1"; }
check "F8 (09:16) is on no Kafka Streams list: its slot 09:15 is still open" not_on_streams F8
same_ids() { # Kafka Streams ids equal the Camel ids except F8 and T1 (later slots), each id once per output
    local s c; s="$(ids_of streams)"; c="$(ids_of camel | grep -vx -e F8 -e T1 || true)"
    [[ -n "$s" && "$s" == "$c" && -z "$(printf '%s\n' "$s" | uniq -d)" && -z "$(printf '%s\n' "$c" | uniq -d)" ]]
}
check "the shipment ids of slot 09:00 are the same on both outputs, and each appears exactly once per output" same_ids

echo "Late: L1 (09:05) after the window closed"
sched L1 FASTLINE 09:05 | produce
check "Camel puts L1 in a batch of its own: it joins whatever is open" poll 40 has_line camel 'FASTLINE|-|1|timeout|L1'
check "Prometheus counts a dropped record for shipping-service" \
    poll 90 prom_holds 'sum(kafka_stream_task_dropped_records_total{service_name="shipping-service"}) >= 1'
check "L1 is on no Kafka Streams list and the two lists of slot 09:00 are unchanged" lists_are "$SLOT_0900"
live_without_errors() { live && no_error_logged; }
check "the dropped record is not a failure: liveness is UP and the service log holds no ERROR" live_without_errors

echo "Bench: 1,000 shipments over four slots, one producer call, then a tick"
BENCH_BASE="$(at 11:00)" python3 -I bench.py gen > "$WORK/bench.txt"
BENCH_BASE="$(at 11:00)" python3 -I bench.py oracle "$WORK/bench.txt" > "$WORK/oracle.txt"
BENCH_START_MS="$(now_ms)"
produce < "$WORK/bench.txt"
BENCH_TICK_MS="$(now_ms)"
sched BTICK AIRCO 12:10 | produce
bench_streams_matches_oracle() { # eight lists for the bench hour, each carrier and slot with the oracle's count
    local got want
    got="$(summary streams | awk -F'|' -v a="$(at 11:00)" -v b="$(at 12:00)" '$2 >= a && $2 < b' | cut -d'|' -f1-3 | sort)"
    want="$(awk '{printf "%s|%s|%s\n", $1, $2, $3}' "$WORK/oracle.txt" | sort)"
    [[ -n "$got" && "$got" == "$want" && "$(printf '%s\n' "$got" | wc -l)" == 8 ]]
}
bench_camel_covers_input() { # every bench id once on a Camel batch, and each carrier's total equals the oracle's
    python3 -I -c '
import collections, sys
want = collections.Counter()
for line in open(sys.argv[1]):
    carrier, _, n = line.split()
    want[carrier] += int(n)
ids, got = [], collections.Counter()
for line in sys.stdin:
    carrier, _, count, _, members = line.rstrip("\n").split("|")
    bench = [i for i in members.split(",") if i.startswith("B") and i[1:].isdigit()]
    ids += bench
    got[carrier] += len(bench)
sys.exit(0 if len(ids) == 1000 and len(set(ids)) == 1000 and got == want else 1)
' "$WORK/oracle.txt" < <(summary camel)
}
check "Kafka Streams emits exactly 8 lists for the bench hour, and each carrier and slot count equals the oracle" \
    poll 60 bench_streams_matches_oracle
check "Camel batches hold each of the 1,000 ids exactly once, and each carrier's total equals the oracle's" \
    poll 60 bench_camel_covers_input
bench_note() {
    python3 -I -c '
import json, sys
t0, tick = int(sys.argv[3]), int(sys.argv[4])
lists = []
for line in open(sys.argv[1], errors="replace"):
    p = line.rstrip("\n").split("\t", 2)
    if len(p) == 3 and sys.argv[5] <= json.loads(p[2])["from"] < sys.argv[6]:
        lists.append(int(p[0]) // 1000)
size = timeout = last = 0
for line in open(sys.argv[2], errors="replace"):
    p = line.rstrip("\n").split("\t", 2)
    if len(p) != 3:
        continue
    v = json.loads(p[2])
    if any(i.startswith("B") and i[1:].isdigit() for i in v["shipmentIds"]):
        size += v["closedBy"] == "size"
        timeout += v["closedBy"] == "timeout"
        last = max(last, int(p[0]) // 1000)
print("NOTE: bench: Kafka Streams %d lists, last %d ms after the tick; Camel %d batches (%d by size, %d by timeout), last %d ms after the producer call" % (
    len(lists), max(lists) - tick if lists else -1, size + timeout, size, timeout, last - t0 if last else -1))
' "$WORK/streams.tsv" "$WORK/camel.tsv" "$BENCH_START_MS" "$BENCH_TICK_MS" "$(at 11:00)" "$(at 12:00)"
}
bench_note

if (( REPEAT > 0 )); then
    echo "Repeating the 7 + 3 batch scenario $REPEAT times (event times from 13:00, 20 minutes apart)"
    OK=0
    for i in $(seq 1 "$REPEAT"); do
        t=$((13 * 60 + (i - 1) * 20)); hh=$(printf '%02d' $((t / 60))); mm=$((t % 60))
        { for j in 1 2 3 4 5 6 7; do sched "R${i}F$j" FASTLINE "$hh:$(printf '%02d' $((mm + j)))"; done
          for j in 1 2 3; do sched "R${i}A$j" AIRCO "$hh:$(printf '%02d' $((mm + j)))"; done; } | produce
        if poll 45 camel_scenario "R$i"; then
            OK=$((OK + 1))
        else
            echo "NOTE: repeat $i: the batches differ from 5 and 2 for FASTLINE and 3 for AIRCO"
        fi
    done
    if (( OK == REPEAT )); then echo "PASS R: the batches were as expected in $OK of $REPEAT runs"
    else echo "FAIL R: the batches were as expected in $OK of $REPEAT runs"; REPEAT_FAILED=1; fi
fi

echo "Restart: an open window and an open batch across a recreate (slot 19:30)"
# The batch timeout is raised to 60 s so an open batch is still open after the recreate (demo setting, compose passes it).
poll 40 no_open_batches || fail_loudly "earlier Camel batches are still open"
export PICKUP_TIMEOUT_MS=60000
"${COMPOSE[@]}" up -d --no-deps --force-recreate --wait shipping-service
poll 90 running || fail_loudly "shipping-service is not running after the recreate with the longer batch timeout"
CHANGELOG_BEFORE="$(changelog_end)"
{ for i in 1 2 3; do sched "RS$i" FASTLINE "19:3$i"; done; } | produce
changelog_grew() { (( $(changelog_end) > CHANGELOG_BEFORE )); }
check "the three FASTLINE records of slot 19:30 grew the changelog topic of the window store" poll 30 changelog_grew
precondition "one Camel batch is open: a row in shipping.pickups" poll 30 one_open_batch
"${COMPOSE[@]}" up -d --no-deps --force-recreate --wait shipping-service
poll 90 running || fail_loudly "shipping-service is not running after the recreate"
{ for i in 4 5; do sched "RS$i" FASTLINE "19:3$i"; done; sched RST AIRCO 19:51; } | produce
only_list_of_slot() { [[ "$(summary streams | grep -F "|$(at 19:30)|")" == "$1" ]]; } # the one list of FASTLINE 19:30
check "Kafka Streams: after the recreate and two more records the list for FASTLINE 19:30 has count 5, and it is the only list for the slot" \
    poll 60 only_list_of_slot "FASTLINE|$(at 19:30)|5|window|RS1,RS2,RS3,RS4,RS5"
check "Camel: the restored batch of 3 and the two new records close one batch of 5 by size" \
    poll 60 has_line camel 'FASTLINE|-|5|size|RS1,RS2,RS3,RS4,RS5'

echo "NOTE: the Kafka Streams lists of slot 19:30: $(summary streams | grep -F "|$(at 19:30)|" | tr '\n' ' ')"

echo "Observability"
check "Tempo has a trace for shipping-service" poll 90 tempo_has_trace shipping-service
check "Loki has logs for shipping-service" poll 90 loki_has_log shipping-service

if [[ "$PASSED" != "$TOTAL" ]]; then
    echo "shipping-service log, last lines:" >&2
    logs | tail -40 | cut -c1-260 >&2 || true
    echo "shipping-service log, Kafka Streams restore and state lines:" >&2
    logs | grep -E 'StoreChangelogReader|StateUpdater|task \[|State transition|Suppress|dropped|late|Skipping' | tail -60 | cut -c1-420 >&2 || true
fi
echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" && "$REPEAT_FAILED" == 0 ]]
