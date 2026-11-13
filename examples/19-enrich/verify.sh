#!/usr/bin/env bash
# Starts the stack and checks the observable effects of one enrichment done two ways on the same input: a Camel Content
# Enricher that asks shipping-service for every record, and a Kafka Streams left join against a local copy of the
# compacted carrier topic. A reference change shows which form sees it first, a stopped owner shows which form carries
# on, a deleted reference shows what each keeps, and a recreate shows the local copy rebuilt from the topic.
# CAMEL_BREAK_ON_FIRST_ERROR=false is the negative witness (see the README). KEEP=1 leaves the stack running.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-19

COMPOSE=(docker compose -f compose.yaml)
NOTIFY=http://localhost:8080
OWNER=http://localhost:8082
SCHEDULED=edp.shipping.scheduled
CARRIERS=edp.shipping.carriers
ENRICHED=edp.shipping.scheduled-enriched
ENRICHED_CAMEL=edp.shipping.scheduled-enriched-camel
CAMEL_GROUP=notification-enricher-camel
FASTLINE_URL='https://track.fastline.example/{trackingNumber}'
FASTLINE_URL_V2='https://track.fastline.example/v2/{trackingNumber}'
AIRCO_URL='https://track.airco.example/{trackingNumber}'
BENCH_RECORDS=500
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
now_ms() { date +%s%3N; }
logs() { docker logs "$1" 2>&1; }

# --- readiness: readiness holds once the topics exist, liveness only once Kafka Streams is RUNNING
topics_init_done() { [[ "$(docker inspect -f '{{.State.Status}}/{{.State.ExitCode}}' edp-topics-init)" == "exited/0" ]]; }
ready() { curl -sf "http://localhost:$1/q/health/ready" >/dev/null; }
streams_running() { curl -sf "$NOTIFY/q/health/live" >/dev/null; }

# --- topics and groups
partitions_of() { kafka kafka-topics.sh --describe --topic "$1" 2>/dev/null | grep -c 'Partition: [0-9]' || true; }
topics_have_one_partition() { [[ "$(for t in $SCHEDULED $CARRIERS $ENRICHED $ENRICHED_CAMEL; do partitions_of "$t"; done | tr '\n' ' ')" == "1 1 1 1 " ]]; }
carriers_is_compacted() { kafka kafka-configs.sh --describe --entity-type topics --entity-name "$CARRIERS" | grep -F 'cleanup.policy=compact' >/dev/null; }
describe_all() { kafka kafka-consumer-groups.sh --describe --all-groups 2>/dev/null; }
camel_group_owns_its_partition() { describe_all | awk -v g="$CAMEL_GROUP" '$1 == g && $3 ~ /^[0-9]+$/ && $7 != "-" {f = 1} END {exit !f}'; }
group_lag() { # log end minus committed (0 when nothing is committed) for the Camel group
    describe_all | awk -v g="$CAMEL_GROUP" '$1 == g && $3 ~ /^[0-9]+$/ {c = ($4 == "-") ? 0 : $4; l += $5 - c; seen = 1} END {if (seen) print l}'
}
lag_is() { [[ "$(group_lag)" == "$1" ]]; }
topic_end() { kafka kafka-get-offsets.sh --topic "$1" | awk -F: '{s += $3} END {print s + 0}'; }
ends_of_outputs() { # "<streams end> <camel end>" from one call
    kafka kafka-get-offsets.sh --topic-partitions "$ENRICHED:0,$ENRICHED_CAMEL:0" | awk -F: '{e[$1] = $3} END {print e["'"$ENRICHED"'"] + 0, e["'"$ENRICHED_CAMEL"'"] + 0}'
}

# --- producing: a console producer with a key, `key|json`, one call per batch
produce() { # produce <topic> [extra reader properties]: lines "key|json" on stdin
    local topic="$1"; shift
    docker exec -i edp-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic "$topic" \
        --reader-property parse.key=true --reader-property 'key.separator=|' "$@" >/dev/null 2>&1
}
reference() { printf '%s|{"carrier":"%s","mode":"%s","trackingUrlTemplate":"%s"}\n' "$1" "$1" "$2" "$3"; } # reference <carrier> <mode> <template>
publish_reference() { reference "$@" | produce "$CARRIERS"; }
scheduled() { # scheduled <orderId> <carrier>: the line the owner publishes, keyed by order id
    printf '%s|{"shipment":{"id":"S-%s","orderId":"%s","carrier":"%s","trackingNumber":"TRK-%s","status":"SCHEDULED"}}\n' "$1" "$1" "$1" "$2" "$1"
}
send_scheduled() { scheduled "$1" "$2" | produce "$SCHEDULED"; }
bench_records() { # 500 shipments, O-19100 to O-19599, both carriers
    python3 -I -c '
import json
for i in range(19100, 19100 + 500):
    order = "O-%d" % i
    carrier = "FASTLINE" if i % 2 == 0 else "AIRCO"
    shipment = {"id": "S-" + order, "orderId": order, "carrier": carrier, "trackingNumber": "TRK-" + order, "status": "SCHEDULED"}
    print("%s|%s" % (order, json.dumps({"shipment": shipment}, separators=(",", ":"))))
'
}

# --- reading an output: "key|json" lines, the last value per key wins
dump() { # dump <topic>
    local n; n="$(topic_end "$1")"
    (( n > 0 )) || return 0
    kafka kafka-console-consumer.sh --topic "$1" --partition 0 --offset earliest --max-messages "$n" --timeout-ms 15000 \
        --formatter-property print.key=true --formatter-property 'key.separator=|' 2>/dev/null
}
# record_of <topic> <orderId> <mode|url|key>: the carrier mode or tracking URL of the last record for that order
record_of() {
    dump "$1" | python3 -I -c '
import json, sys
want, field = sys.argv[1], sys.argv[2]
found = None
for line in sys.stdin:
    key, _, value = line.rstrip("\n").partition("|")
    if key == want:
        found = json.loads(value)
if found is None:
    sys.exit(1)
print({"mode": found["carrierMode"], "url": found["trackingUrl"], "key": want}[field])' "$2" "$3"
}
url_is() { [[ "$(record_of "$1" "$2" url)" == "$3" ]]; }          # url_is <topic> <orderId> <expected URL>
mode_is() { [[ "$(record_of "$1" "$2" mode)" == "$3" ]]; }        # mode_is <topic> <orderId> <expected mode, "None" for null>
same_record_on_both() { # the two outputs hold the same record for the order, field for field
    local a b
    a="$(dump "$ENRICHED" | grep -F "$1|" | tail -1)" || return 1
    b="$(dump "$ENRICHED_CAMEL" | grep -F "$1|" | tail -1)" || return 1
    [[ -n "$a" && "$a" == "$b" ]]
}
streams_and_camel_agree_on() { # <orderId> <mode> <url>: both outputs hold the order with this mode and URL
    mode_is "$ENRICHED" "$1" "$2" && mode_is "$ENRICHED_CAMEL" "$1" "$2" && url_is "$ENRICHED" "$1" "$3" && url_is "$ENRICHED_CAMEL" "$1" "$3"
}

# --- the local copy and the owner
store_template() { curl -sf "$NOTIFY/demo/carriers/$1" | python3 -I -c 'import json, sys; print(json.load(sys.stdin)["trackingUrlTemplate"])'; }
store_shows() { [[ "$(store_template "$1")" == "$2" ]]; }                      # store_shows <carrier> <template>
store_lacks() { [[ "$(curl -s -o /dev/null -w '%{http_code}' "$NOTIFY/demo/carriers/$1")" == 404 ]]; }
owner_serves() { [[ "$(curl -sf "$OWNER/carriers/$1" | python3 -I -c 'import json, sys; d = json.load(sys.stdin); print(d["mode"])')" == "$2" ]]; }
owner_change() { # demo code: an operator changes a carrier's template
    curl -sf -X PUT -H 'Content-Type: application/json' -d "{\"trackingUrlTemplate\":\"$2\"}" "$OWNER/carriers/$1" >/dev/null
}
owner_unreachable() { ! curl -sf --max-time 3 "$OWNER/q/health/ready" >/dev/null; }
lookups() { logs edp-shipping-service | grep -c 'GET /carriers/' || true; }

# --- the Camel output stays empty of one order for a while, and the group keeps lag 1
camel_waits_for() { # camel_waits_for <orderId> <seconds>: absent from the Camel output and lag 1 at every sample
    local end=$((SECONDS + $2))
    while (( SECONDS < end )); do
        ! dump "$ENRICHED_CAMEL" | grep -F "$1|" >/dev/null || return 1
        lag_is 1 || return 1
        sleep 3
    done
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
poll 60 ready 8082 || fail_loudly "shipping-service is not ready"
poll 60 ready 8080 || fail_loudly "notification-service is not ready"

echo "Infrastructure"
ready_and_running() { streams_running && camel_group_owns_its_partition; }
precondition "both services are ready, Kafka Streams is RUNNING and the Camel group owns its partition" poll 90 ready_and_running
topics_are_right() { topics_have_one_partition && carriers_is_compacted; }
precondition "the four topics exist with one partition each, and the carrier topic is compacted" topics_are_right
publish_reference FASTLINE ROAD "$FASTLINE_URL"
publish_reference AIRCO AIR "$AIRCO_URL"
references_everywhere() {
    owner_serves FASTLINE ROAD && owner_serves AIRCO AIR && store_shows FASTLINE "$FASTLINE_URL" && store_shows AIRCO "$AIRCO_URL"
}
precondition "the owner serves both carriers and the local copy shows both references" poll 60 references_everywhere

echo "Both forms on one input (O-1901, O-1902)"
send_scheduled O-1901 FASTLINE
send_scheduled O-1902 AIRCO
streams_output_is_right() {
    mode_is "$ENRICHED" O-1901 ROAD && url_is "$ENRICHED" O-1901 https://track.fastline.example/TRK-O-1901 &&
        mode_is "$ENRICHED" O-1902 AIR && url_is "$ENRICHED" O-1902 https://track.airco.example/TRK-O-1902
}
check "Streams output: O-1901 is ROAD with the FASTLINE link and its tracking number, O-1902 is AIR with the AIRCO link" \
    poll 30 streams_output_is_right
both_hold_the_same_two() { same_record_on_both O-1901 && same_record_on_both O-1902; }
check "Camel output holds the same two records, field for field" poll 30 both_hold_the_same_two

echo "A burst of $BENCH_RECORDS shipments, before anything changes"
LOOKUPS_BEFORE="$(lookups)"
read -r STREAMS_BASE CAMEL_BASE <<< "$(ends_of_outputs)"
BENCH_START="$(now_ms)"
bench_records | produce "$SCHEDULED"
STREAMS_MS=0
CAMEL_MS=0
while (( $(now_ms) - BENCH_START < 60000 )) && (( STREAMS_MS == 0 || CAMEL_MS == 0 )); do
    read -r s c <<< "$(ends_of_outputs)"
    NOW="$(now_ms)"
    if (( STREAMS_MS == 0 && s - STREAMS_BASE >= BENCH_RECORDS )); then STREAMS_MS=$((NOW - BENCH_START)); fi
    if (( CAMEL_MS == 0 && c - CAMEL_BASE >= BENCH_RECORDS )); then CAMEL_MS=$((NOW - BENCH_START)); fi
done
bench_all_enriched() { # both outputs landed in time and every output record of the burst has a mode and a link
    (( STREAMS_MS > 0 && CAMEL_MS > 0 )) || return 1
    local topic
    for topic in "$ENRICHED" "$ENRICHED_CAMEL"; do
        dump "$topic" | python3 -I -c '
import json, sys
n = bad = 0
for line in sys.stdin:
    key, _, value = line.rstrip("\n").partition("|")
    if key.startswith("O-19") and len(key) == 7:
        n += 1
        d = json.loads(value)
        bad += d["carrierMode"] is None or d["trackingUrl"] is None
sys.exit(0 if n == int(sys.argv[1]) and bad == 0 else 1)' "$BENCH_RECORDS" || return 1
    done
}
check "both outputs hold $BENCH_RECORDS enriched records within 60 s" bench_all_enriched
echo "NOTE: burst, producer call to last output record (poll step about 1 s): Streams ${STREAMS_MS} ms, Camel ${CAMEL_MS} ms"
echo "NOTE: lookups served by shipping-service during the burst: $(( $(lookups) - LOOKUPS_BEFORE ))"

echo "A reference change: the owner changes FASTLINE's template, then publishes it (O-1903, O-1904)"
owner_change FASTLINE "$FASTLINE_URL_V2"
send_scheduled O-1903 FASTLINE
lookup_new_join_old() {
    url_is "$ENRICHED_CAMEL" O-1903 https://track.fastline.example/v2/TRK-O-1903 && url_is "$ENRICHED" O-1903 https://track.fastline.example/TRK-O-1903
}
check "before the topic is updated, O-1903 gets the new link from the lookup and the old link from the join" poll 30 lookup_new_join_old
publish_reference FASTLINE ROAD "$FASTLINE_URL_V2"
poll 30 store_shows FASTLINE "$FASTLINE_URL_V2" || fail_loudly "the local copy did not show the new template"
send_scheduled O-1904 FASTLINE
both_new() {
    url_is "$ENRICHED" O-1904 https://track.fastline.example/v2/TRK-O-1904 && url_is "$ENRICHED_CAMEL" O-1904 https://track.fastline.example/v2/TRK-O-1904
}
check "after the topic is updated and the local copy shows it, O-1904 gets the new link from both" poll 30 both_new
check "O-1903 on the Streams output still holds the old link: an output is not revised" \
    url_is "$ENRICHED" O-1903 https://track.fastline.example/TRK-O-1903

echo "The owner is down (O-1905)"
poll 60 lag_is 0 || fail_loudly "the Camel group did not commit the earlier records"
docker stop edp-shipping-service >/dev/null
owner_down_and_caught_up() { owner_unreachable && lag_is 0; }
precondition "shipping-service is stopped and unreachable, and the Camel group has no lag" owner_down_and_caught_up
send_scheduled O-1905 FASTLINE
check "Streams output holds O-1905 enriched within 10 s, from the local copy" poll 10 mode_is "$ENRICHED" O-1905 ROAD
check "for 20 s the Camel output has no O-1905 and the Camel group has lag 1" camel_waits_for O-1905 20
docker start edp-shipping-service >/dev/null
poll 60 ready 8082 || fail_loudly "shipping-service did not come back"
camel_caught_up() { mode_is "$ENRICHED_CAMEL" O-1905 ROAD && lag_is 0; }
check "after the owner returns, the Camel output holds O-1905 enriched within 30 s and the group has lag 0" poll 30 camel_caught_up

echo "A reference deleted from the topic (O-1906)"
printf 'AIRCO|NULL\n' | produce "$CARRIERS" --reader-property null.marker=NULL
poll 30 store_lacks AIRCO || fail_loudly "the local copy still shows AIRCO"
send_scheduled O-1906 AIRCO
streams_keeps_it() { mode_is "$ENRICHED" O-1906 None && url_is "$ENRICHED" O-1906 None; }
check "Streams keeps O-1906 and leaves its mode and link empty" poll 30 streams_keeps_it
check "Camel enriches O-1906, because the owner still has AIRCO" poll 30 mode_is "$ENRICHED_CAMEL" O-1906 AIR

echo "The enricher is recreated (O-1907)"
"${COMPOSE[@]}" up -d --no-deps --force-recreate --wait notification-service
poll 60 ready 8080 || fail_loudly "notification-service is not ready after the recreate"
poll 90 streams_running || fail_loudly "Kafka Streams did not reach RUNNING after the recreate"
copy_rebuilt() { store_shows FASTLINE "$FASTLINE_URL_V2" && store_lacks AIRCO; }
check "the new instance rebuilt its local copy from the topic: FASTLINE has the new template and AIRCO is gone" poll 60 copy_rebuilt
send_scheduled O-1907 FASTLINE
check "O-1907 is enriched by both forms with the new link" \
    poll 90 streams_and_camel_agree_on O-1907 ROAD https://track.fastline.example/v2/TRK-O-1907

echo "Observability"
check "Prometheus holds shipment_enrichments_total for the Camel path of notification-service at the burst size or more" \
    poll 90 prom_holds "max_over_time(shipment_enrichments_total{path=\"camel\",service_name=\"notification-service\"}[1h]) >= $BENCH_RECORDS"
check "Tempo has a trace for notification-service" poll 90 tempo_has_trace notification-service
check "Loki has logs for notification-service" poll 90 loki_has_log notification-service

if [[ "$PASSED" != "$TOTAL" ]]; then
    echo "notification-service log, last lines:" >&2
    logs edp-notification-service | tail -40 | cut -c1-260 >&2 || true
fi
echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
