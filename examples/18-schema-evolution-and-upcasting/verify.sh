#!/usr/bin/env bash
# Starts the stack and checks the observable effects of schema evolution on both sides of the service: stored events
# of an earlier shape are upcast when they are read and never rewritten, the schema registry accepts the versions that
# are compatible with every earlier one and refuses the one that is not, a refused command leaves nothing behind, and a
# consumer built on the newest schema reads the records of every release, through SmallRye and through Camel.
# UPCASTERS=off and REGISTRY_RULE=BACKWARD are the negative witnesses (see the README). KEEP=1 leaves the stack running.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-18

COMPOSE=(docker compose -f compose.yaml)
ORDERS=http://localhost:8080
CONNECT=http://localhost:8083
REG=http://localhost:8081/apis/registry/v3
CONNECTOR=order-outbox
CONNECTOR_JSON="${CONNECTOR_JSON:-connect/order-outbox.json}"
PLACED=edp.orders.placed
ARTIFACT=edp.orders.placed-value
V3=order-service/src/main/resources/avro/order-placed-v3.avsc
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
TMP="$(mktemp -d)"
TOTAL=0
PASSED=0

# Demo code: the compatibility rule the registry enforces. BACKWARD checks a new schema against the latest version
# only, which accepts the broken release; BACKWARD_TRANSITIVE checks it against every version.
RULE="${REGISTRY_RULE:-BACKWARD_TRANSITIVE}"
# Demo code: the first start is the previous release of order-service, which writes shape 1.
export ORDER_PLACED_SCHEMA=1

cleanup() {
    rm -rf "$TMP"
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
ready() { curl -sf "http://localhost:$1/q/health/ready" >/dev/null; }
logs() { docker logs "$1" 2>&1; }
log_has() { logs "$1" | grep -F -- "$2" >/dev/null; } # grep without -q: under pipefail an early exit would fail the pipeline
log_lacks() { # the consumer settings the client logs at start name the deserializer, so match failures, not the word
    local hits; hits="$(logs "$1" | grep -E -- "$2" | head -3 | cut -c1-200 || true)"
    [[ -z "$hits" ]] || { printf 'unexpected log lines in %s:\n%s\n' "$1" "$hits" >&2; return 1; }
}

# --- the service
place() { # place <id> [priority]: prints the HTTP status
    local extra=""
    [[ -n "${2:-}" ]] && extra=",\"shippingPriority\":\"$2\""
    curl -s -o "$TMP/place.body" -w '%{http_code}' -H 'Content-Type: application/json' \
        -d "{\"id\":\"$1\",\"customerId\":\"CU-18\",\"itemSku\":\"SKU-18\",\"quantity\":1,\"amount\":25.00$extra}" "$ORDERS/orders"
}
view() { curl -s "$ORDERS/orders/$1" | python3 -I -c "import json,sys; d=json.load(sys.stdin); print(d['shippingPriority'], d['schemaVersion'])"; }
release() { # release <1|2|3>: recreate order-service as that release of the contract (demo code)
    ORDER_PLACED_SCHEMA="$1" "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait order-service
}

# --- the store
event_count() { sql "SELECT count(*) FROM orders.events WHERE stream_id = '$1'"; }
stored_shapes() { sql "SELECT coalesce(string_agg(stream_id || ':' || schema_version, ',' ORDER BY stream_id), '') FROM orders.events WHERE type = 'OrderPlaced'"; }
v1_rows_without_priority() { # rows written at shape 1 that carry no shippingPriority
    sql "SELECT count(*) FROM orders.events WHERE schema_version = 1 AND NOT (payload ? 'shippingPriority')"
}
payload_hash() { sql "SELECT md5(payload::text) FROM orders.events WHERE stream_id = '$1' AND version = 1"; }
update_refused() {
    local out
    out="$(sql "UPDATE orders.events SET payload = '{}'::jsonb WHERE stream_id = 'O-1801'" 2>&1)" && return 1
    [[ "$out" == *"append-only"* ]]
}
recorded() { # what notification-service recorded for the three orders
    sql "SELECT coalesce(string_agg(order_id || '=' || shipping_priority || '/' || via, ',' ORDER BY order_id), '') FROM notifications.recorded_priority WHERE order_id IN ('O-1801', 'O-1802', 'O-1803')"
}

# --- the registry
version_count() { curl -sf "$REG/groups/default/artifacts/$ARTIFACT/versions" | python3 -I -c "import json,sys; print(len(json.load(sys.stdin)['versions']))"; }
first_version_lacks_priority() { # the schema of version 1 has no shippingPriority field
    local schema
    schema="$(curl -sf "$REG/groups/default/artifacts/$ARTIFACT/versions/1/content")" || return 1
    [[ "$schema" == *'"orderId"'* && "$schema" != *shippingPriority* ]]
}
one_version_without_priority() { [[ "$(version_count)" == 1 ]] && first_version_lacks_priority; }
rule_reads_back() {
    [[ "$(curl -sf "$REG/admin/rules/COMPATIBILITY" | python3 -I -c "import json,sys; print(json.load(sys.stdin)['config'])")" == "$RULE" ]]
}
register_v3() { # POST the v3 schema as a new version: prints the HTTP status, keeps the body
    python3 -I -c 'import json,sys; print(json.dumps({"content": {"content": open(sys.argv[1]).read(), "contentType": "application/json"}}))' "$V3" > "$TMP/v3.json"
    curl -s -o "$TMP/v3.body" -w '%{http_code}' -H 'Content-Type: application/json' --data @"$TMP/v3.json" "$REG/groups/default/artifacts/$ARTIFACT/versions"
}
v3_refused_naming_the_field() {
    local code; code="$(register_v3)"
    [[ "$code" == 400 ]] && grep -F "shippingPriority" "$TMP/v3.body" >/dev/null &&
        grep -F "Incompatible artifact" "$TMP/v3.body" >/dev/null && [[ "$(version_count)" == 2 ]]
}

# --- the topic
topic_end() { kafka kafka-get-offsets.sh --topic "$PLACED" | awk -F: '{s += $3} END {print s + 0}'; }
partitions_of() { kafka kafka-topics.sh --describe --topic "$1" 2>/dev/null | grep -c 'Partition: [0-9]' || true; }
# The registry version each record on the topic was written with, in offset order ("1,1,2"). The schema id is the four
# bytes after the zero byte at the start of the value; the registry maps it to a version.
topic_versions() {
    local end versions
    end="$(topic_end)"
    [[ "$end" -gt 0 ]] || { echo ""; return 0; }
    versions="$(curl -sf "$REG/groups/default/artifacts/$ARTIFACT/versions")" || return 1
    kafka kafka-console-consumer.sh --topic "$PLACED" --from-beginning --max-messages "$end" --timeout-ms 15000 \
        --formatter-property line.separator='@@EOR@@' 2>/dev/null |
        VERSIONS="$versions" python3 -I -c "
import json, os, sys
by_id = {}
for v in json.loads(os.environ['VERSIONS'])['versions']:
    by_id[v['contentId']] = v['version']
    by_id.setdefault(v['globalId'], v['version'])
out = []
for record in sys.stdin.buffer.read().split(b'@@EOR@@'):
    if not record:
        continue
    out.append(by_id.get(int.from_bytes(record[1:5], 'big'), '?') if record[0] == 0 else '?')
print(','.join(out))"
}
versions_on_topic_are() { [[ "$(topic_versions)" == "$1" ]]; }
group_assigned() { kafka kafka-consumer-groups.sh --describe --group "$1" 2>/dev/null | awk -v t="$2" '$2 == t && $3 == 0 && $7 != "-" {f = 1} END {exit !f}'; }
group_lag_zero() { # an inactive group prints "-" for its current offset: that is not lag 0
    kafka kafka-consumer-groups.sh --describe --group "$1" 2>/dev/null | awk -v t="$2" '
        $2 == t && $3 == 0 {seen = 1; if ($4 == "-" || $6 != 0) bad = 1} END {exit !(seen && !bad)}'
}

# --- the connector
connector_running() {
    curl -sf "$CONNECT/connectors/$CONNECTOR/status" | python3 -I -c "
import json, sys
d = json.load(sys.stdin)
sys.exit(0 if d['connector']['state'] == 'RUNNING' and d['tasks'] and all(t['state'] == 'RUNNING' for t in d['tasks']) else 1)"
}
plugin_listed() { local out; out="$(curl -sf "$CONNECT/connector-plugins")" || return 1; [[ "$out" == *io.debezium.connector.postgresql.PostgresConnector* ]]; }

# --- observability
prom_holds() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" | grep '"value"' >/dev/null; }
tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }

# Predicates the checks poll.
stack_ready() {
    ready 8080 && ready 8082 && curl -sf http://localhost:19000/health/ready >/dev/null && connector_running &&
        [[ "$(partitions_of "$PLACED")" == 1 ]]
}
set_global_rule() {
    local code
    code="$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
        -d "{\"ruleType\":\"COMPATIBILITY\",\"config\":\"$RULE\"}" "$REG/admin/rules")"
    [[ "$code" == 204 || "$code" == 200 ]]
}
previous_release_stored() { expect "$(stored_shapes)/$(v1_rows_without_priority)" "O-1801:1,O-1802:1/2"; }
camel_route_ready() { log_has edp-notification-service "order-placed-reader" && group_assigned notification-camel "$PLACED"; }
camel_recorded() { group_lag_zero notification-camel "$PLACED" && [[ "$(recorded)" == "O-1801=STANDARD/camel,O-1802=STANDARD/camel,O-1803=EXPRESS/camel" ]]; }
smallrye_recorded() { group_lag_zero notification "$PLACED" && [[ "$(recorded)" == "O-1801=STANDARD/smallrye,O-1802=STANDARD/smallrye,O-1803=EXPRESS/smallrye" ]]; }
cancelled_and_unchanged() { # cancel O-1801 and compare the stored v1 payload before and after
    local before after code
    before="$(payload_hash O-1801)"
    code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$ORDERS/orders/O-1801/cancellation")"
    after="$(payload_hash O-1801)"
    [[ "$code" == 200 && "$before" == "$after" && "$(event_count O-1801)" == 2 ]]
}

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 ready 8080 || fail_loudly "order-service is not ready"
poll 60 ready 8082 || fail_loudly "notification-service is not ready"
poll 30 plugin_listed || fail_loudly "Connect does not list the PostgreSQL connector"
# The compatibility rule is set before any producer registers a schema.
set_global_rule || fail_loudly "the registry did not accept the COMPATIBILITY rule"
curl -sf -X PUT -H 'Content-Type: application/json' --data @"$CONNECTOR_JSON" "$CONNECT/connectors/$CONNECTOR/config" >/dev/null \
    || fail_loudly "the connector was not accepted by Connect"

echo "Infrastructure"
precondition "both services and the registry are ready, the connector order-outbox is RUNNING and the topic has one partition" \
    poll 60 stack_ready
precondition "the registry's global COMPATIBILITY rule reads back as $RULE" rule_reads_back

echo "The previous release"
check "POST O-1801 and O-1802 return 201; both rows are stored at schema_version 1 and carry no shippingPriority" \
    expect "$(place O-1801)/$(place O-1802)/$(stored_shapes)/$(v1_rows_without_priority)" "201/201/O-1801:1,O-1802:1/2"
check "the artifact $ARTIFACT has exactly one version, without shippingPriority" \
    poll 30 one_version_without_priority
check "the topic holds two records, both written with registry version 1" poll 60 versions_on_topic_are "1,1"

echo "The current release"
release 2
poll 60 ready 8080 || fail_loudly "order-service is not ready as the current release"
check "POST O-1803 with EXPRESS returns 201, its row is stored at schema_version 2 and the artifact has two versions" \
    expect "$(place O-1803 EXPRESS)/$(stored_shapes)/$(version_count)" "201/O-1801:1,O-1802:1,O-1803:2/2"
check "the topic holds three records, the third written with registry version 2" poll 60 versions_on_topic_are "1,1,2"

echo "The store"
check "GET /orders/O-1801 returns STANDARD from a row stored at schema_version 1" expect "$(view O-1801)" "STANDARD 1"
check "GET /orders/O-1803 returns EXPRESS from a row stored at schema_version 2" expect "$(view O-1803)" "EXPRESS 2"
check "cancelling O-1801 returns 200 and its stored version 1 payload has the same checksum afterwards" cancelled_and_unchanged
check "an UPDATE of the stored payload of O-1801 is refused by the append-only trigger" update_refused

echo "The wire"
check "notification-service recorded O-1801 and O-1802 as STANDARD and O-1803 as EXPRESS, and its group has no lag" \
    poll 60 smallrye_recorded
check "no deserialization failure is logged by notification-service" log_lacks edp-notification-service 'DeserializationException|SerializationException|AvroTypeException|Failed to deserialize'

echo "Refusing an incompatible version"
check "registering v3 as a version of $ARTIFACT is refused with 400, naming shippingPriority, and the artifact keeps two versions" \
    v3_refused_naming_the_field
release 3
poll 60 ready 8080 || fail_loudly "order-service is not ready as the broken release"
STATUS_1804="$(place O-1804 EXPRESS)"
sleep 5
check "POST O-1804 from a release that publishes v3 returns 503, stores no row, adds no record and no version" \
    expect "$STATUS_1804/$(event_count O-1804)/$(topic_end)/$(version_count)" "503/0/3/2"
check "the order-service log shows the registry refusal for O-1804" log_has edp-order-service "registry refused OrderPlaced for O-1804"

echo "Camel: the same reader as a route"
READER_PROFILE=camel-reader "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait notification-service
poll 60 ready 8082 || fail_loudly "notification-service is not ready with the camel-reader profile"
precondition "the Camel route has started and notification-camel owns partition 0" poll 60 camel_route_ready
check "the Camel reader, from the earliest offset, recorded the same three priorities and has no lag" poll 90 camel_recorded

echo "Observability"
check "Prometheus holds order_upcasts_total with from=\"1\" at 2 or more for order-service" \
    poll 90 prom_holds 'max_over_time(order_upcasts_total{from="1",service_name="order-service"}[1h]) >= 2'
check "Tempo has a trace for order-service" poll 90 tempo_has_trace order-service
check "Loki has logs for order-service" poll 90 loki_has_log order-service

if [[ "$PASSED" != "$TOTAL" ]]; then
    echo "order-service log (tail):" >&2
    logs edp-order-service | tail -30 | cut -c1-260 >&2 || true
fi
echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
