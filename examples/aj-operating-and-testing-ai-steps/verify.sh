#!/usr/bin/env bash
# Starts the stack and checks the observable effects of the three things this appendix demonstrates around chapter
# 24's model step: the exactly-once guard around the model call (a redelivered or duplicated note reuses the
# committed row instead of paying for the model twice), the backfill endpoint (calls the model only for an
# exception behind the target prompt version, rebuilds the snapshot every time), and the replay endpoint (reads the
# snapshot with zero model calls by default).
# Needs the model volume: scripts/ollama-model.sh pull, once. This script never pulls.
# MODEL=off seeds the analysis table from fixtures/analyses.jsonl and runs the backfill and replay checks on it,
# with no Ollama container and no Kafka route (ANALYST_ENABLED=false).
# ANALYSIS_DEDUP=off and REPLAY_MODE=reanalyse (demo code) are the negative witnesses; see the README's Experiments
# table for their measured effect, which this script does not run inline. KEEP=1 leaves the stack running.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-aj

COMPOSE=(docker compose -f compose.yaml)
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
SVC=http://localhost:8080
OLLAMA=http://localhost:11434
EXCEPTIONS=edp.shipping.delivery-exceptions
ANALYSES=edp.shipping.exception-analyses
ANALYSIS_GROUP=shipping-exception-analysis
MODEL_ID=qwen3:4b-instruct-2507-q4_K_M
MODEL_DIGEST=0edcdef34593
PROMPT_VERSION=v1
CRASH_ID=E-2403
STALE_ID=E-BACKFILL-DEMO
NOTED_AT="$(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%SZ)"
MODEL="${MODEL:-on}"
TOTAL=0
PASSED=0
N=0

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
    N=$((N + 1)); TOTAL=$((TOTAL + 1))
    if "$@" >/dev/null 2>&1; then
        PASSED=$((PASSED + 1)); printf 'PASS %d: %s\n' "$N" "$desc"
    else
        printf 'FAIL %d: %s\n' "$N" "$desc"
    fi
}

precondition() { # precondition "<description>" <command...>: counted as a check; failing it stops the run
    local desc="$1"; shift
    N=$((N + 1)); TOTAL=$((TOTAL + 1))
    if "$@" >/dev/null 2>&1; then
        PASSED=$((PASSED + 1)); printf 'PASS %d: %s\n' "$N" "$desc"
    else
        printf 'PRECONDITION FAILED %d: %s\n' "$N" "$desc" >&2; exit 1
    fi
}

skip() { # skip "<description>": keeps the numbering of the full run when the model is off
    N=$((N + 1)); printf 'SKIP %d: %s (model checks not run)\n' "$N" "$1"
}

poll() { # poll <seconds> <command...>: retry every second until the command succeeds
    local end=$((SECONDS + $1)); shift
    until "$@"; do
        (( SECONDS < end )) || return 1
        sleep 1
    done
}

fail_loudly() { printf 'PRECONDITION FAILED: %s\n' "$1" >&2; exit 1; }
logs() { docker logs "$1" 2>&1; }
kafka() { docker exec edp-kafka "/opt/kafka/bin/$1" --bootstrap-server localhost:9092 "${@:2}"; }
sql() { "${PSQL[@]}" -c "$1"; }

# --- the corpus: fixtures/corpus.jsonl, one line per note, with the reason a reader would give it
corpus_field() { # corpus_field <exceptionId> <field>
    python3 -I -c '
import json, sys
for line in open("fixtures/corpus.jsonl"):
    row = json.loads(line)
    if row["exceptionId"] == sys.argv[1]:
        print(row[sys.argv[2]])' "$1" "$2"
}
report() { # report <exceptionId...>: the line the carrier integration would publish, keyed by order id
    python3 -I -c '
import json, sys
noted_at, wanted = sys.argv[1], set(sys.argv[2:])
for line in open("fixtures/corpus.jsonl"):
    row = json.loads(line)
    if row["exceptionId"] not in wanted:
        continue
    shipment = {"id": row["shipmentId"], "orderId": row["orderId"], "carrier": row["carrier"],
                "trackingNumber": "TRK-" + row["shipmentId"], "status": "SCHEDULED"}
    event = {"exceptionId": row["exceptionId"], "shipment": shipment, "attempt": row["attempt"],
             "note": row["note"], "occurredAt": noted_at}
    print("%s|%s" % (row["orderId"], json.dumps(event, separators=(",", ":"))))' "$NOTED_AT" "$@"
}
produce() { # produce <topic>: lines "key|json" on stdin
    docker exec -i edp-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic "$1" \
        --reader-property parse.key=true --reader-property 'key.separator=|' >/dev/null 2>&1
}

# --- reading a topic: "key|json" lines, the whole one-partition topic
topic_end() { kafka kafka-get-offsets.sh --topic "$1" | awk -F: '{s += $3} END {print s + 0}'; }
dump() { # dump <topic>
    local n; n="$(topic_end "$1")"
    (( n > 0 )) || return 0
    kafka kafka-console-consumer.sh --topic "$1" --partition 0 --offset earliest --max-messages "$n" --timeout-ms 15000 \
        --formatter-property print.key=true --formatter-property 'key.separator=|' 2>/dev/null
}
analyses_of() { # analyses_of <exceptionId>: every analysis record for that exception, in topic order
    dump "$ANALYSES" | python3 -I -c '
import json, sys
for line in sys.stdin:
    value = json.loads(line.partition("|")[2])
    if value["exceptionId"] == sys.argv[1]:
        print(json.dumps(value))' "$1"
}
analysis_field() { # analysis_field <exceptionId> <field>: from the last analysis record of that exception
    analyses_of "$1" | python3 -I -c '
import json, sys
found = None
for line in sys.stdin:
    found = json.loads(line)
if found is None:
    sys.exit(1)
print(found[sys.argv[1]])' "$2"
}

# --- groups
describe_all() { kafka kafka-consumer-groups.sh --describe --all-groups 2>/dev/null; }
group_owns_its_partition() { describe_all | awk -v g="$ANALYSIS_GROUP" '$1 == g && $3 ~ /^[0-9]+$/ && $7 != "-" {f = 1} END {exit !f}'; }

# --- the model
ollama_has_the_model() {
    curl -sf "$OLLAMA/api/tags" | python3 -I -c '
import json, sys
for m in json.load(sys.stdin)["models"]:
    if m["name"] == sys.argv[1] and m["digest"].startswith(sys.argv[2]):
        sys.exit(0)
sys.exit(1)' "$MODEL_ID" "$MODEL_DIGEST"
}
warm_up() { # one generate, so the model is loaded before anything is timed or counted
    curl -sf --max-time 120 "$OLLAMA/api/generate" -d "{\"model\":\"$MODEL_ID\",\"prompt\":\"Reply with ok\",\"stream\":false,\"options\":{\"num_predict\":4}}" | grep '"done":true' >/dev/null
}
# The platform counter of Quarkus LangChain4j, as Prometheus holds it (exported every 5 s): answered model calls.
app_calls() {
    curl -sf -G "http://localhost:19090/api/v1/query" \
        --data-urlencode 'query=sum(langchain4j_aiservices_counted_total{service_name="shipping-service",result="success"})' |
        python3 -I -c 'import json, sys; r = json.load(sys.stdin)["data"]["result"]; print(int(float(r[0]["value"][1])) if r else 0)'
}

# --- reasons
reason_is() { [[ "$(analysis_field "$1" reason)" == "$2" ]]; }
analyses_are_well_formed() { # analyses_are_well_formed <exceptionId...>: reason in the enum, the pinned model and prompt
    local id
    for id in "$@"; do
        analysis_field "$id" reason | grep -E '^(DAMAGED|LOST|RECIPIENT_ABSENT|ADDRESS_PROBLEM|REFUSED|DELAYED|UNKNOWN)$' >/dev/null || return 1
        [[ "$(analysis_field "$id" modelDigest)" == "$MODEL_DIGEST" && "$(analysis_field "$id" model)" == "$MODEL_ID" \
            && "$(analysis_field "$id" promptVersion)" == "$PROMPT_VERSION" ]] || return 1
    done
}
every_reason_is_expected() {
    local id
    for id in "$@"; do reason_is "$id" "$(corpus_field "$id" expected)" || return 1; done
}

# --- the exactly-once guard
duplicate_is_reused() { # duplicate_is_reused <exceptionId>: two analysis records, the second marked derived
    local count derived
    count="$(analyses_of "$1" | wc -l)"
    [[ "$count" == 2 ]] || return 1
    derived="$(analyses_of "$1" | tail -1 | python3 -I -c 'import json, sys; print(json.load(sys.stdin).get("derivedFromPromptVersion") or "")')"
    [[ -n "$derived" ]]
}
crashed_id_is_reused() { # the record that reached the topic after the crash-forced redelivery is marked derived
    local derived
    derived="$(analysis_field "$CRASH_ID" derivedFromPromptVersion)" || return 1
    [[ -n "$derived" ]]
}

# --- backfill and replay (Postgres, not Kafka, is the source of truth for both)
seed_analysis_row() { # seed_analysis_row <exceptionId> <promptVersion> <digest> <orderId> <shipmentId> <carrier> <attempt> <reason> <evidence> <model>
    sql "insert into analysis.analysis (exception_id, prompt_version, model_digest, order_id, shipment_id, carrier, attempt, reason, evidence, model, occurred_at)
         values ('$1', '$2', '$3', '$4', '$5', '$6', $7, '$8', '$9', '${10}', now() - interval '1 day')
         on conflict do nothing;" >/dev/null
}
seed_fixture_analyses() { # loads fixtures/analyses.jsonl straight into Postgres, at the pinned prompt version
    python3 -I -c '
import json
for line in open("fixtures/analyses.jsonl"):
    row = json.loads(line.partition("|")[2])
    evidence = row["evidence"].replace("\x27", "\x27\x27")
    print(row["exceptionId"], row["promptVersion"], row["modelDigest"], row["orderId"], row["shipmentId"],
          row["carrier"], row["attempt"], row["reason"], evidence, row["model"], sep="\t")' |
    while IFS=$'\t' read -r eid version digest oid sid carrier attempt reason evidence model; do
        seed_analysis_row "$eid" "$version" "$digest" "$oid" "$sid" "$carrier" "$attempt" "$reason" "$evidence" "$model"
    done
}
reanalyse() { curl -sf -X POST "$SVC/admin/reanalyse?promptVersion=$1" -o "$2"; }
reanalysed_count() { python3 -I -c 'import json, sys; print(json.load(open(sys.argv[1]))["reanalysed"])' "$1"; }
replay_to() { curl -sf -X POST "$SVC/demo/replay" -o "$1"; }
replay_mode() { python3 -I -c 'import json, sys; print(json.load(open(sys.argv[1]))["mode"])' "$1"; }
replay_disposition() { # replay_disposition <response file> <exceptionId>
    python3 -I -c '
import json, sys
for d in json.load(open(sys.argv[1]))["dispositions"]:
    if d["exceptionId"] == sys.argv[2]:
        print(d["disposition"])' "$1" "$2"
}

# --- observability
prom_holds() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" | grep '"value"' >/dev/null; }
tempo_has() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }

IDS=(E-2401 E-2402 E-2403 E-2404 E-2405 E-2406)

echo "Starting the stack (model: $MODEL)"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run), then create every
# container and anonymous volume anew. The model volume is external and stays.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
export DEMO_CRASH_AFTER_ANALYSIS="${DEMO_CRASH_AFTER_ANALYSIS:-$CRASH_ID}"
if [[ "$MODEL" == "off" ]]; then
    export ANALYST_ENABLED=false
    "${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait topics-init postgres shipping-service
else
    "${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
fi
poll 60 curl -sf "$SVC/q/health/ready" || fail_loudly "shipping-service is not ready"

echo "Infrastructure"
if [[ "$MODEL" == "off" ]]; then
    skip "the model volume holds the pinned model and one generate succeeds"
else
    model_present_and_warm() { ollama_has_the_model && warm_up; }
    precondition "Ollama lists the pinned model with its digest, and one warm-up generate succeeds" model_present_and_warm
fi
topics_have_one_partition() {
    local t
    for t in "$EXCEPTIONS" "$ANALYSES"; do
        [[ "$(kafka kafka-topics.sh --describe --topic "$t" 2>/dev/null | grep -c 'Partition: [0-9]' || true)" == 1 ]] || return 1
    done
}
service_ready_and_topics() { curl -sf "$SVC/q/health/ready" >/dev/null && topics_have_one_partition; }
precondition "shipping-service is ready and the two topics have one partition each" service_ready_and_topics

if [[ "$MODEL" == "off" ]]; then
    echo "Recorded analyses (E-2401 to E-2407), seeded straight into Postgres from the fixture"
    seed_fixture_analyses
    skip "six analyses hold a reason from the closed set, the model digest and the prompt version"
    skip "each reason equals the one a reader gives the note"
    skip "the off-topic note is UNKNOWN"
    skip "a duplicate delivery reuses the committed row"
    skip "the crash-configured exception is marked as reused"
else
    poll 60 group_owns_its_partition || fail_loudly "the analysis group did not get its partition"
    echo "Analysis: six notes and an off-topic note, published once each"
    report "${IDS[@]}" E-2407 | produce "$EXCEPTIONS"
    all_analysed() { local id; for id in "${IDS[@]}" E-2407; do analysis_field "$id" reason >/dev/null || return 1; done; }
    poll 180 all_analysed || true
    check "six analyses hold a reason from the closed set, the model digest and the prompt version" \
        analyses_are_well_formed "${IDS[@]}"
    check "each reason equals the one a reader gives the note" every_reason_is_expected "${IDS[@]}"
    check "the off-topic note is UNKNOWN" poll 60 reason_is E-2407 UNKNOWN

    echo "The exactly-once guard"
    CALLS_BEFORE_DUP="$(app_calls)"
    report E-2401 | produce "$EXCEPTIONS"
    check "a duplicate delivery of E-2401's note reuses the committed row, marked as derived" \
        poll 30 duplicate_is_reused E-2401
    no_extra_call_for_the_duplicate() { [[ "$(app_calls)" == "$CALLS_BEFORE_DUP" ]]; }
    check "the duplicate made no extra model call" no_extra_call_for_the_duplicate
    check "the crash-configured exception's record is marked as reused, after the forced redelivery" \
        poll 30 crashed_id_is_reused
fi

echo "Backfill and replay"
# A stale exception is one a past release analysed at an older prompt version: its note is on the topic, but no row
# at the current version exists, so backfill reads the note and appends a current row. The live route would analyse
# any note on the topic at the current version and leave nothing stale, so recreate shipping-service with the route
# off for this phase. The checks that need the route (reasons, the exactly-once guard) have already run.
if [[ "$MODEL" != "off" ]]; then
    ANALYST_ENABLED=false "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait shipping-service >/dev/null 2>&1 \
        || fail_loudly "could not recreate shipping-service with the analysis route off"
    poll 60 curl -sf "$SVC/q/health/ready" >/dev/null || fail_loudly "shipping-service did not come back with the route off"
fi
if [[ "$MODEL" == "off" ]]; then
    skip "backfill calls the model once, for the exception behind the target prompt version"
else
    seed_analysis_row "$STALE_ID" v0 stale-digest O-BACKFILL-DEMO S-BACKFILL-DEMO FASTLINE 1 UNKNOWN stale stale-model
    python3 -I -c '
import json, sys
noted_at, exception_id = sys.argv[1], sys.argv[2]
shipment = {"id": "S-BACKFILL-DEMO", "orderId": "O-BACKFILL-DEMO", "carrier": "FASTLINE",
            "trackingNumber": "TRK-BACKFILL-DEMO", "status": "SCHEDULED"}
event = {"exceptionId": exception_id, "shipment": shipment, "attempt": 1,
         "note": "Box crushed at depot, contents leaking through the corner.", "occurredAt": noted_at}
print("O-BACKFILL-DEMO|%s" % json.dumps(event, separators=(",", ":")))' "$NOTED_AT" "$STALE_ID" | produce "$EXCEPTIONS"
    BACKFILL_OUT="$(mktemp)"
    trap 'rm -f "$BACKFILL_OUT"; cleanup' EXIT
    poll 30 reanalyse "$PROMPT_VERSION" "$BACKFILL_OUT"
    backfill_reanalysed_the_stale_exception() {
        # The backfill response is the authoritative record: exactly one exception was behind the target version and
        # was reanalysed, and it is the stale one. reanalyseOne calls the model once per reanalysed exception, so one
        # reanalysed exception is one model call. (The backfill writes the store and the snapshot, not the output
        # topic, so it is read from the response, not from analysis_field, which reads that topic.)
        [[ "$(python3 -I -c 'import json, sys; d = json.load(open(sys.argv[1])); print("%d|%s" % (d["reanalysed"], ",".join(d["reanalysedIds"])))' "$BACKFILL_OUT")" == "1|$STALE_ID" ]]
    }
    check "backfill calls the model once, for the exception behind the target prompt version" backfill_reanalysed_the_stale_exception
fi

SAME_VERSION_OUT="$(mktemp)"
trap 'rm -f "$SAME_VERSION_OUT" "${BACKFILL_OUT:-}"; cleanup' EXIT
CALLS_BEFORE_SAME_VERSION="$([[ "$MODEL" != "off" ]] && app_calls || echo 0)"
reanalyse "$PROMPT_VERSION" "$SAME_VERSION_OUT"
same_version_backfill_reanalyses_nothing() {
    [[ "$(reanalysed_count "$SAME_VERSION_OUT")" == 0 ]] || return 1
    [[ "$MODEL" == "off" ]] && return 0
    [[ "$(app_calls)" == "$CALLS_BEFORE_SAME_VERSION" ]]
}
check "a same-version backfill reanalyses nothing and calls the model for nothing" same_version_backfill_reanalyses_nothing

REPLAY_OUT="$(mktemp)"
trap 'rm -f "$REPLAY_OUT" "$SAME_VERSION_OUT" "${BACKFILL_OUT:-}"; cleanup' EXIT
CALLS_BEFORE_REPLAY="$([[ "$MODEL" != "off" ]] && app_calls || echo 0)"
replay_to "$REPLAY_OUT"
replay_is_recorded() { [[ "$(replay_mode "$REPLAY_OUT")" == recorded ]]; }
check "the replay runs in recorded mode" replay_is_recorded
# By this point the snapshot has been rebuilt by the same-version backfill above, in both modes, so the replay
# check below reads the same way whether E-2403 came from a live analysis or the seeded fixture.
e2403_replays_to_reship() { [[ "$(replay_disposition "$REPLAY_OUT" E-2403)" == RESHIP ]]; }
check "E-2403 (recipient absent, attempt 2, under the limit) replays to RESHIP" e2403_replays_to_reship
if [[ "$MODEL" != "off" ]]; then
    no_model_call_on_replay() { [[ "$(app_calls)" == "$CALLS_BEFORE_REPLAY" ]]; }
    check "the replay made no model call" no_model_call_on_replay
fi

echo "Observability"
check "Prometheus holds exception_analysis_lookups_total for shipping-service" \
    poll 90 prom_holds 'sum(max_over_time(exception_analysis_lookups_total{service_name="shipping-service"}[1h])) >= 1'
if [[ "$MODEL" == "off" ]]; then
    check "Tempo has a trace for shipping-service" poll 90 tempo_has "tags=service.name=shipping-service"
else
    check "Tempo has a trace of shipping-service with a langchain4j AI service span" \
        poll 90 tempo_has 'q={ resource.service.name = "shipping-service" && name =~ "langchain4j.*" }'
fi
check "Loki has logs for shipping-service" poll 90 loki_has_log shipping-service

if [[ "$PASSED" != "$TOTAL" ]]; then
    echo "shipping-service log, last lines:" >&2
    logs edp-shipping-service | tail -40 | cut -c1-260 >&2 || true
fi
SUFFIX=""
[[ "$MODEL" == "off" ]] && SUFFIX=" (model checks not run)"
echo "$PASSED/$TOTAL checks passed$SUFFIX"
[[ "$PASSED" == "$TOTAL" ]]
