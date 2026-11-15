#!/usr/bin/env bash
# Builds a Connect image inside the cluster, declares the chapter 08 outbox connector and the chapter 10 inventory
# connector as KafkaConnector resources, and checks what each delivers: an outbox row on edp.orders.placed, a table
# change on edp.inventory.item-changes and as a document on edp.inventory.items. Then it lists, refuses to alter,
# stops, alters and resumes the inventory connector's offsets through the resource. Nothing is written through the
# Connect REST API: the checks only read it.
# PLUGIN_SHA=wrong (demo code) applies a checksum that does not match the plugin archive, so the build fails and
# every check that needs Connect fails (see the README). KEEP=1 leaves the namespaces in place afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../_k8s/lib.sh
source ../_k8s/lib.sh
trap k8s_teardown EXIT

PLUGIN_SHA="${PLUGIN_SHA:-pinned}"
CONNECT_DIR=connect
DEMO=0
if [[ "$PLUGIN_SHA" == wrong ]]; then
    CONNECT_DIR=demo/plugin-sha-wrong; DEMO=1
    echo "demo code: the plugin checksum does not match the archive"
elif [[ "$PLUGIN_SHA" != pinned ]]; then
    echo "PLUGIN_SHA is pinned or wrong" >&2; exit 2
fi
# A demo run waits only briefly for effects that cannot happen.
bound() { if (( DEMO )); then echo 3; else echo "$1"; fi; }

REGISTRY=10.96.100.50:5000
ORDER=O-3501
CHANGES=edp.inventory.item-changes
DOCS=edp.inventory.items
SERVER=edp-inventory            # the source partition of inventory-cdc: its topic.prefix
OFFSETS_CM=inventory-cdc-offsets
ALTER_CM=inventory-cdc-offsets-alter

k8s_fresh .

# --- helpers
py() { python3 -I ./tools/read.py "$@"; }
act() { "$@" >/dev/null || echo "WARN: $*" >&2; }   # an action between checks; its effect is judged by the next check
sql() { psql_exec edp "$1"; }
sql_file() { psql_exec edp - < "$1" >/dev/null; }
consume() { # the whole topic with keys and headers; a record line holds tabs
    kafka_exec kafka-console-consumer.sh --topic "$1" --from-beginning --timeout-ms 6000 \
        --property print.key=true --property print.headers=true 2>/dev/null || true
}
post() { curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' "$ORDERS/orders" \
    --data "{\"id\":\"$1\",\"customerId\":\"CU-77\",\"itemSku\":\"SKU-42\",\"quantity\":3,\"amount\":59.90}"; }
registry_get() { kc -n "$NS_KAFKA" exec edp-broker-0 -- curl -sf "http://$REGISTRY$1"; }
wal_position() { sql "SELECT pg_wal_lsn_diff(pg_current_wal_lsn(),'0/0')::bigint"; }
update_sku() { sql "UPDATE inventory.inventory_item SET qty_on_hand=$2, last_changed=now() WHERE item_sku='$1'" >/dev/null; }

# --- reading the resources
ckc() { kc -n "$NS_KAFKA" "$@"; }
connect_cr() { ckc get kafkaconnect edp-connect -o "jsonpath=$1"; }
connect_ready() { [[ "$(connect_cr '{.status.conditions[?(@.type=="Ready")].status}')" == True ]]; }
build_failed() { [[ "$(ckc get pod edp-connect-connect-build -o 'jsonpath={.status.phase}' 2>/dev/null || true)" == Failed ]]; }
connector_ready() { [[ "$(ckc get kafkaconnector "$1" -o 'jsonpath={.status.conditions[?(@.type=="Ready")].status}')" == True ]]; }
status_is() { # status_is <connector> <connector state> <task state, or - for none>
    local out; out="$(connect_get "/connectors/$1/status")" || return 1
    [[ "$(python3 -I -c '
import json, sys
d = json.load(sys.stdin)
print(d["connector"]["state"], ",".join(t["state"] for t in d["tasks"]) or "-")' <<<"$out")" == "$2 $3" ]]
}
connector_state() { status_is "$1" RUNNING RUNNING; }
no_literal_password() { # the CRs name the secret and hold none of its values
    local pw crs; pw="$(kc -n "$NS_APPS" get secret edp-pg-app -o 'jsonpath={.data.password}' | base64 -d)"
    crs="$(ckc get kafkaconnector -o json)"
    [[ "$crs" == *'${secrets:edp/edp-pg-app:password}'* && "$crs" != *"$pw"* ]]
}
connectors_running() { connector_state order-outbox && connector_state inventory-cdc && no_literal_password; }
slots_and_publications() {
    [[ "$(sql "SELECT string_agg(slot_name || ':' || active, ',' ORDER BY slot_name) FROM pg_replication_slots")" == "edp_inventory:t,edp_order_outbox:t" ]] &&
    [[ "$(sql "SELECT string_agg(pubname || '=' || tablename, ',' ORDER BY pubname, tablename) FROM pg_publication_tables WHERE pubname LIKE 'edp\_%'")" == \
        "edp_inventory=dbz_signal,edp_inventory=inventory_item,edp_order_outbox=outboxevent" ]]
}
image_in_registry() { [[ "$(registry_get /v2/edp/connect/tags/list)" == *'"35"'* ]]; }
plugin_in_built_image() {
    local image plugins
    image="$(ckc get pod -l strimzi.io/kind=KafkaConnect -o 'jsonpath={.items[0].spec.containers[0].image}')"
    plugins="$(connect_get /connector-plugins)" || return 1
    [[ "$image" == "$REGISTRY/edp/connect"* && "$plugins" == *io.debezium.connector.postgresql.PostgresConnector* ]]
}

stays() { # stays <seconds> <command...>: the command succeeds now and keeps succeeding for the whole period
    local end=$((SECONDS + $1)); shift
    while (( SECONDS < end )); do "$@" || return 1; sleep 1; done
    "$@"
}
both_ready() { connector_ready order-outbox && connector_ready inventory-cdc; }

# --- the order outbox
one_order_record() { # exactly one record, keyed O-3501, with the eventType header
    local out; out="$(consume edp.orders.placed)"
    [[ "$(py keys <<<"$out" | grep -cx "$ORDER" || true)" == 1 && "$(py header eventType <<<"$out" | grep -cx OrderPlaced || true)" == 1 ]]
}
no_second_record() { [[ "$(post "$ORDER")" == 409 ]] && poll "$(bound 10)" one_order_record && stays "$(bound 8)" one_order_record; }
order_row_outbox_empty() { [[ "$(sql "SELECT (SELECT count(*) FROM orders.orders WHERE id='$ORDER') || '/' || (SELECT count(*) FROM orders.outboxevent)")" == 1/0 ]]; }

# --- inventory changes and documents
snapshot_rows() { [[ "$(consume "$CHANGES" | py ops r)" == 5 ]]; }
update_row() { [[ "$(consume "$CHANGES" | py ops u "$1")" -ge 1 ]]; }
document_is() { [[ "$(consume "$DOCS" | py doc "$1")" == "$2" ]]; }

# --- offsets through the resource
annotation_gone() { [[ -z "$(ckc get kafkaconnector inventory-cdc -o 'jsonpath={.metadata.annotations.strimzi\.io/connector-offsets}')" ]]; }
listed_lsn() { ckc get configmap "$OFFSETS_CM" -o 'jsonpath={.data.offsets\.json}' 2>/dev/null | py lsn; }
live_lsn() { connect_get "/connectors/inventory-cdc/offsets" | py lsn; }
list_offsets() { # ask for the list and wait for the operator to answer; idempotent
    act ckc annotate kafkaconnector inventory-cdc strimzi.io/connector-offsets=list --overwrite
    poll "$(bound 60)" annotation_gone && [[ -n "$(listed_lsn)" ]]
}
put_alter_cm() { # put_alter_cm <lsn>: the new offset, in the shape the REST API takes
    ckc create configmap "$ALTER_CM" --dry-run=client -o yaml \
        --from-literal="offsets.json={\"offsets\":[{\"partition\":{\"server\":\"$SERVER\"},\"offset\":{\"lsn\":$1,\"lsn_proc\":$1,\"lsn_commit\":$1}}]}" \
        | kc apply --server-side --force-conflicts --field-manager=edp-verify -f - >/dev/null
}
alter_refused() { [[ "$(ckc get events --field-selector involvedObject.name=inventory-cdc -o 'jsonpath={.items[*].message}')" == *"not in STOPPED state"* ]]; }
set_state() { act ckc patch kafkaconnector inventory-cdc --type=merge -p "{\"spec\":{\"state\":\"$1\"}}"; }
stopped() { status_is inventory-cdc STOPPED -; }
altered_to() { [[ "$(live_lsn)" == "$1" ]]; }
prom_is() { [[ "$(prom_query "$1")" == "$2" ]]; }
images_unchanged() { # the node runs the images built from the chapter 08 and 09 Containerfiles
    local ref
    for ref in localhost/edp/08-order-service:local localhost/edp/09-inventory-service:local; do
        [[ -n "$(_host_image_id "$ref")" && "$(_host_image_id "$ref")" == "$(_node_image_id "$ref")" ]] || return 1
    done
}

# --- preconditions
precondition "Kafka edp is Ready" kafka_ready edp
precondition "the application role edp has the REPLICATION attribute that the Cluster declares" \
    expect "$(sql "SELECT rolreplication FROM pg_roles WHERE rolname='edp'")" t
precondition "the registry in the cluster holds no repository yet" \
    expect "$(registry_get /v2/_catalog | tr -d ' \n')" '{"repositories":[]}'

# --- the build
T_APPLY=$SECONDS
act kc apply --server-side --field-manager=edp-verify -k "$CONNECT_DIR"
if (( DEMO )); then
    poll 240 build_failed || true
    echo "demo: $(connect_cr '{range .status.conditions[*]}{.type}={.status} {.reason}: {.message}{"\n"}{end}' 2>/dev/null | cut -c1-400 | head -3)"
    echo "demo: build pod phase $(ckc get pod edp-connect-connect-build -o 'jsonpath={.status.phase}' 2>/dev/null || echo none)"
else
    poll 420 connect_ready || { echo "k8s: KafkaConnect edp-connect not Ready" >&2; _diagnostics; }
    echo "measured: KafkaConnect Ready $((SECONDS - T_APPLY)) s after the apply"
fi
check "the image edp/connect:35 was built and pushed to the registry in the cluster" image_in_registry
check "KafkaConnect edp-connect is Ready" connect_ready
check "the Connect pod runs the built image and lists the PostgreSQL connector plugin" plugin_in_built_image

# --- the tables, then the connectors
sql_file tables/inventory.sql
act kc apply --server-side --field-manager=edp-verify -k connectors
check "both KafkaConnectors are Ready" poll "$(bound 120)" both_ready
check "both connectors and their tasks are RUNNING, with credentials by reference and no literal password in a resource" \
    poll "$(bound 60)" connectors_running
check "each connector has an active replication slot and a publication of only its own tables" poll "$(bound 30)" slots_and_publications

# --- the outbox connector
check "$ORDER is accepted with 201" expect "$(post "$ORDER")" 201
check "one record on edp.orders.placed, keyed $ORDER, with the eventType header OrderPlaced" poll "$(bound 60)" one_order_record
check "the order row exists and the outbox table is empty" poll "$(bound 20)" order_row_outbox_empty
check "a second $ORDER is refused with 409 and no second record appears" no_second_record

# --- the inventory connector
check "the snapshot delivered the five SKUs as r records on $CHANGES" poll "$(bound 60)" snapshot_rows
update_sku SKU-3 79
check "an update of SKU-3 arrives as a u record with quantity 79" poll "$(bound 60)" update_row 79
check "the inventory service publishes the document SKU-3 with quantityOnHand 79 on $DOCS" poll "$(bound 60)" document_is SKU-3 79

# --- offsets through the resource
check "listing offsets writes them to the ConfigMap, with an lsn, and the operator removes the annotation" list_offsets
LSN_BEFORE="$(listed_lsn || true)"
act put_alter_cm "$(wal_position)"
act ckc annotate kafkaconnector inventory-cdc strimzi.io/connector-offsets=alter --overwrite
refused_and_unchanged() { alter_refused && altered_to "$LSN_BEFORE"; }
check "an alter while the connector runs is refused with a warning and the offsets do not change" poll "$(bound 60)" refused_and_unchanged
act ckc annotate kafkaconnector inventory-cdc strimzi.io/connector-offsets-
set_state stopped
check "state stopped stops the connector and its task" poll "$(bound 60)" stopped
update_sku SKU-4 401
TARGET="$(wal_position)"
act put_alter_cm "$TARGET"
act ckc annotate kafkaconnector inventory-cdc strimzi.io/connector-offsets=alter --overwrite
check "an alter while stopped moves the offset to the end of the WAL" poll "$(bound 60)" altered_to "$TARGET"
set_state running
check "state running resumes the connector and its task" poll "$(bound 60)" status_is inventory-cdc RUNNING RUNNING
update_sku SKU-5 502
check "the next change after the resume arrives" poll "$(bound 60)" update_row 502
skipped_while_stopped() { update_row 502 && [[ "$(consume "$CHANGES" | py ops u 401)" == 0 ]]; }
check "the change made while the connector was stopped never arrives" skipped_while_stopped

# --- metrics and images
check "Prometheus holds the connector status metric of inventory-cdc, running" \
    poll "$(bound 90)" prom_is 'kafka_connect_connector_metrics_status_info{connector="inventory-cdc",status="running"}' 1
check "the node runs the images built from the chapter 08 and 09 Containerfiles" images_unchanged

summary
