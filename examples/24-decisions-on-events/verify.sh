#!/usr/bin/env bash
# Starts the stack and checks the observable effects of analysing a carrier's free-text note with a local model and
# deciding on the recorded analysis with rules: the analyses hold a reason from a closed set with the model digest and
# prompt version, every decision equals the rule table applied to its recorded analysis, a replay from the record
# reproduces the decisions with no model call, and a stopped model leaves a note waiting.
# Needs the model volume: scripts/ollama-model.sh pull, once. This script never pulls.
# MODEL=off runs the rule, replay and observability checks on a recorded fixture, with no Ollama container.
# REPLAY_MODE=reanalyse (demo code) is the negative witness (see the README). KEEP=1 leaves the stack running.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-24

COMPOSE=(docker compose -f compose.yaml)
SVC=http://localhost:8080
OLLAMA=http://localhost:11434
EXCEPTIONS=edp.shipping.delivery-exceptions
ANALYSES=edp.shipping.exception-analyses
DECISIONS=edp.shipping.exception-decisions
ANALYSIS_GROUP=shipping-exception-analysis
MODEL_ID=qwen3:4b-instruct-2507-q4_K_M
MODEL_DIGEST=0edcdef34593
PROMPT_VERSION=v1
RULES_VERSION=v1
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
count_of() { dump "$1" | grep -c '|{' || true; }
analysis_field() { # analysis_field <exceptionId> <field>: from the last analysis record of that exception
    dump "$ANALYSES" | python3 -I -c '
import json, sys
found = None
for line in sys.stdin:
    value = json.loads(line.partition("|")[2])
    if value["exceptionId"] == sys.argv[1]:
        found = value
if found is None:
    sys.exit(1)
print(found[sys.argv[2]])' "$1" "$2"
}
decision_field() { # decision_field <exceptionId> <field>
    dump "$DECISIONS" | python3 -I -c '
import json, sys
found = None
for line in sys.stdin:
    value = json.loads(line.partition("|")[2])
    if value["exceptionId"] == sys.argv[1]:
        found = value
if found is None:
    sys.exit(1)
print(found[sys.argv[2]])' "$1" "$2"
}

# --- the rule table, written out here apart from the rules: reason x attempt -> decision and rule name
oracle() { # oracle <reason> <attempt>: "<decision>|<rule>" for the attempt limit of 3
    python3 -I -c '
import sys
reason, attempt, limit = sys.argv[1], int(sys.argv[2]), 3
if reason in ("DAMAGED", "LOST"):
    print("RESHIP|Damaged or lost, first attempt" if attempt == 1 else "REFUND|Damaged or lost, later attempt")
elif reason in ("RECIPIENT_ABSENT", "DELAYED"):
    print("RESHIP|Recipient absent or delayed, under the attempt limit" if attempt < limit
          else "REFUND|Recipient absent or delayed, at the attempt limit")
elif reason in ("ADDRESS_PROBLEM", "REFUSED"):
    print("REFUND|Address problem or refused")
elif reason == "UNKNOWN":
    print("REFUND|Unknown reason")
else:
    sys.exit(1)' "$1" "$2"
}
decision_matches_oracle() { # decision_matches_oracle <exceptionId>: the recorded decision is the table applied to the recorded analysis
    local reason attempt want got
    reason="$(analysis_field "$1" reason)" && attempt="$(analysis_field "$1" attempt)" || return 1
    want="$(oracle "$reason" "$attempt")" || return 1
    got="$(decision_field "$1" decision)|$(decision_field "$1" rule)" || return 1
    [[ "$want" == "$got" ]]
}
all_decisions_match_oracle() {
    local id
    for id in "$@"; do decision_matches_oracle "$id" || return 1; done
}

# --- groups
describe_all() { kafka kafka-consumer-groups.sh --describe --all-groups 2>/dev/null; }
group_lag() { # log end minus committed (0 when nothing is committed) for the analysis group
    describe_all | awk -v g="$ANALYSIS_GROUP" '$1 == g && $3 ~ /^[0-9]+$/ {c = ($4 == "-") ? 0 : $4; l += $5 - c; seen = 1} END {if (seen) print l}'
}
lag_is() { [[ "$(group_lag)" == "$1" ]]; }
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
warm_up() { # one generate, so the model is loaded before a note is timed or counted
    curl -sf --max-time 120 "$OLLAMA/api/generate" -d "{\"model\":\"$MODEL_ID\",\"prompt\":\"Reply with ok\",\"stream\":false,\"options\":{\"num_predict\":4}}" | grep '"done":true' >/dev/null
}
ollama_chats() { logs edp-ollama | grep -c 'POST.*"/api/chat"' || true; }
# The platform counter of Quarkus LangChain4j, as Prometheus holds it (exported every 5 s): answered model calls.
app_calls() {
    curl -sf -G "http://localhost:19090/api/v1/query" \
        --data-urlencode 'query=sum(langchain4j_aiservices_counted_total{service_name="shipping-service",result="success"})' |
        python3 -I -c 'import json, sys; r = json.load(sys.stdin)["data"]["result"]; print(int(float(r[0]["value"][1])) if r else 0)'
}
counters_agree() { local a o; a="$(app_calls)"; o="$(ollama_chats)"; [[ "$a" == "$o" && "$a" -gt 0 ]]; }
ollama_unreachable() { ! curl -sf --max-time 3 "$OLLAMA/api/tags" >/dev/null; }

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
analysed_and_decided() { # analysed_and_decided <exceptionId>
    analysis_field "$1" reason >/dev/null && decision_field "$1" decision >/dev/null
}

# --- replay: the decisions the endpoint returns equal the recorded ones, field for field
replay_to() { curl -sf -X POST "$SVC/demo/replay$2" -o "$1"; }
replay_equals_record() { # replay_equals_record <response file>
    dump "$DECISIONS" | python3 -I -c '
import json, sys
recorded = {}
for line in sys.stdin:
    value = json.loads(line.partition("|")[2])
    recorded[value["exceptionId"]] = value
replayed = json.load(open(sys.argv[1]))["decisions"]
ok = len(replayed) == len(recorded) and all(recorded[d["exceptionId"]] == d for d in replayed)
sys.exit(0 if ok else 1)' "$1"
}
replay_decision() { # replay_decision <response file> <exceptionId>
    python3 -I -c '
import json, sys
for d in json.load(open(sys.argv[1]))["decisions"]:
    if d["exceptionId"] == sys.argv[2]:
        print(d["decision"])' "$1" "$2"
}
replay_count() { python3 -I -c 'import json, sys; print(len(json.load(open(sys.argv[1]))["decisions"]))' "$1"; }
replay_millis() { python3 -I -c 'import json, sys; print(json.load(open(sys.argv[1]))["millis"])' "$1"; }

# --- a note waits while the model is down
retries_are_exhausted() { (( $(logs edp-shipping-service | grep -c 'Exhausted after delivery attempt') > 0 )); }  # grep -c reads all of it: no SIGPIPE under pipefail
analysis_absent_for() { # analysis_absent_for <exceptionId> <seconds>: absent at every sample, and the group keeps lag 1
    local end=$((SECONDS + $2)) lag seen=0
    while (( SECONDS < end )); do
        ! analysis_field "$1" reason >/dev/null 2>&1 || return 1
        lag="$(group_lag)"  # empty while the consumer rejoins after a seek-back: no sample
        if [[ -n "$lag" ]]; then
            [[ "$lag" == 1 ]] || return 1
            seen=$((seen + 1))
        fi
        sleep 3
    done
    (( seen >= 2 ))
}

# --- observability
prom_holds() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" | grep '"value"' >/dev/null; }
tempo_has() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }

IDS6=(E-2401 E-2402 E-2403 E-2404 E-2405 E-2406)
IDS8=("${IDS6[@]}" E-2407 E-2408)

echo "Starting the stack (model: $MODEL)"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew. The model volume is external and stays.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
if [[ "$MODEL" == "off" ]]; then
    export ANALYST_ENABLED=false
    "${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait topics-init shipping-service
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
    for t in "$EXCEPTIONS" "$ANALYSES" "$DECISIONS"; do
        [[ "$(kafka kafka-topics.sh --describe --topic "$t" 2>/dev/null | grep -c 'Partition: [0-9]' || true)" == 1 ]] || return 1
    done
}
service_ready_and_topics() { curl -sf "$SVC/q/health/ready" >/dev/null && topics_have_one_partition; }
precondition "shipping-service is ready and the three topics have one partition each" service_ready_and_topics

if [[ "$MODEL" == "off" ]]; then
    echo "Recorded analyses (E-2401 to E-2408), published from the fixture"
    produce "$ANALYSES" < fixtures/analyses.jsonl
    skip "six analyses hold a reason from the closed set, the model digest and the prompt version"
    skip "each reason equals the one a reader gives the note"
    skip "the off-topic note is UNKNOWN"
else
    poll 60 group_owns_its_partition || fail_loudly "the analysis group did not get its partition"
    echo "Analysis: six notes, an off-topic note and a second exception on the first order"
    report "${IDS8[@]}" | produce "$EXCEPTIONS"
    all_six_analysed() { local id; for id in "${IDS6[@]}"; do analysis_field "$id" reason >/dev/null || return 1; done; }
    poll 180 all_six_analysed || true
    check "six analyses hold a reason from the closed set, the model digest and the prompt version" \
        analyses_are_well_formed "${IDS6[@]}"
    check "each reason equals the one a reader gives the note" every_reason_is_expected "${IDS6[@]}"
    check "the off-topic note is UNKNOWN" poll 60 reason_is E-2407 UNKNOWN
fi

echo "Decision"
all_eight_decided() { local id; for id in "${IDS8[@]}"; do decision_field "$id" decision >/dev/null || return 1; done; }
poll 90 all_eight_decided || true
check "eight decisions each equal the rule table applied to the recorded reason and attempt, with the rule name" \
    all_decisions_match_oracle "${IDS8[@]}"
first_ships_second_refunds() { [[ "$(decision_field E-2401 decision)" == RESHIP && "$(decision_field E-2408 decision)" == REFUND ]]; }
check "damage on attempt 1 is RESHIP and damage on attempt 2 for the same order is REFUND" first_ships_second_refunds

echo "Replay"
if [[ "$MODEL" == "off" ]]; then
    skip "the model-call count and the Ollama request count are read"
else
    precondition "the model-call counter in Prometheus equals the requests Ollama served" poll 30 counters_agree
    CALLS_BEFORE="$(app_calls)"
    CHATS_BEFORE="$(ollama_chats)"
fi
ANALYSES_BEFORE="$(topic_end "$ANALYSES")"
DECISIONS_BEFORE="$(topic_end "$DECISIONS")"
REPLAY_OUT="$(mktemp)"
trap 'rm -f "$REPLAY_OUT"; cleanup' EXIT
replay_to "$REPLAY_OUT" ""
check "POST /demo/replay returns the eight decisions identical to the recorded ones" replay_equals_record "$REPLAY_OUT"
nothing_new_published() { [[ "$(topic_end "$ANALYSES")" == "$ANALYSES_BEFORE" && "$(topic_end "$DECISIONS")" == "$DECISIONS_BEFORE" ]]; }
no_model_call_since() { [[ "$(app_calls)" == "$CALLS_BEFORE" && "$(ollama_chats)" == "$CHATS_BEFORE" ]]; }
if [[ "$MODEL" == "off" ]]; then
    check "the replay published no record on the analyses or decisions topic" nothing_new_published
else
    quiet_replay() { sleep 10; nothing_new_published && no_model_call_since; }  # 10 s: the counter reaches Prometheus every 5 s
    check "the replay made no model call, by both counts, and published no record" quiet_replay
fi
RULES_OUT="$(mktemp)"
trap 'rm -f "$REPLAY_OUT" "$RULES_OUT"; cleanup' EXIT
CHATS_MID="$(ollama_chats 2>/dev/null || echo 0)"
replay_to "$RULES_OUT" "?attemptLimit=2"
rule_change_moves_one_decision() { # E-2403 (an absent recipient, attempt 2) goes from RESHIP to REFUND
    [[ "$(decision_field E-2403 decision)" == RESHIP && "$(replay_decision "$RULES_OUT" E-2403)" == REFUND ]]
}
no_model_call_by_the_rule_change() { [[ "$MODEL" == "off" || "$(ollama_chats)" == "$CHATS_MID" ]]; }
rule_change_without_model() { rule_change_moves_one_decision && no_model_call_by_the_rule_change; }
check "a replay with the attempt limit 2 turns E-2403 from RESHIP into REFUND and makes no model call" rule_change_without_model
echo "NOTE: replay of $(replay_count "$REPLAY_OUT") recorded analyses took $(replay_millis "$REPLAY_OUT") ms (reading Kafka included)"

echo "The model is down (E-2409)"
if [[ "$MODEL" == "off" ]]; then
    skip "the model is stopped and unreachable, and the analysis group has no lag"
    skip "after the redeliveries are exhausted the note still has no analysis and the analysis group still has lag 1 for 20 s"
    skip "after the model returns, the note is analysed and decided within 90 s and the lag is 0"
else
    docker stop edp-ollama >/dev/null
    down_and_caught_up() { ollama_unreachable && lag_is 0; }
    precondition "Ollama is stopped and unreachable, and the analysis group has no lag" poll 60 down_and_caught_up
    report E-2409 | produce "$EXCEPTIONS"
    # Once the redeliveries have run out, only the consumer's seek-back (breakOnFirstError) keeps the record: without it
    # the offset is committed within 5 s and the lag falls to 0.
    waits_after_the_retries() { poll 150 retries_are_exhausted && analysis_absent_for E-2409 20; }
    check "after the redeliveries are exhausted the note still has no analysis and the analysis group still has lag 1 for 20 s" \
        waits_after_the_retries
    docker start edp-ollama >/dev/null
    poll 60 ollama_has_the_model || fail_loudly "Ollama did not come back"
    warm_up || fail_loudly "the model did not load again"
    back_and_decided() { analysed_and_decided E-2409 && decision_matches_oracle E-2409 && lag_is 0; }
    check "after the model returns, the note is analysed and decided within 90 s and the lag is 0" poll 90 back_and_decided
    echo "NOTE: Ollama chat request times (s), warm: $(logs edp-ollama | grep 'POST.*"/api/chat"' | python3 -I -c '
import re, statistics, sys
times = []
for line in sys.stdin:
    m = re.search(r"\|\s*([0-9.]+)(ms|s)\s*\|", line)
    if m:
        times.append(float(m.group(1)) / (1000 if m.group(2) == "ms" else 1))
print("median %.2f, max %.2f, n %d" % (statistics.median(times), max(times), len(times)) if times else "none")')"
fi

echo "Observability"
check "Prometheus holds exception_decisions_total for REFUND of shipping-service at 3 or more" \
    poll 90 prom_holds 'sum(max_over_time(exception_decisions_total{decision="REFUND",service_name="shipping-service"}[1h])) >= 3'
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
