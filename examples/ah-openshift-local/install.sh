#!/usr/bin/env bash
# Installs the platform on OpenShift Local: the project and the operator namespace, one OperatorGroup, the Strimzi
# and CloudNativePG operators at pinned CSVs (Manual approval, only the named InstallPlan approved), then Kafka,
# the PostgreSQL Cluster and a Connect image built into an ImageStream. It starts from nothing: it refuses when
# either namespace exists, and it never starts, stops or configures CRC. Run teardown.sh first for a fresh start.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh --crc
source ./crc-check.sh
source ./lib.sh
T0=$SECONDS

# The plugin URL and checksum are written in platform/kafkaconnect.yaml for the reader and pinned in
# ../_k8s/versions.env; a difference between the two stops the run before anything is created.
for pin in "$DEBEZIUM_PLUGIN_URL" "$DEBEZIUM_PLUGIN_SHA512"; do
    awk -v p="$pin" '{ sub(/^[ \t]*[a-z0-9]+:[ \t]*/, "") } $0 == p { f = 1 } END { exit !f }' platform/kafkaconnect.yaml \
        || die "platform/kafkaconnect.yaml and ../_k8s/versions.env disagree on the plugin pin"
done

for ns in "$NS" "$NS_OP"; do
    if oc get namespace "$ns" >/dev/null 2>&1; then
        die "namespace $ns exists; run ./teardown.sh first (this script never deletes anything)"
    fi
done

say "== namespaces and OperatorGroup"
oc apply --server-side -f operators/namespaces.yaml >/dev/null
oc apply --server-side -n "$NS_OP" -f operators/operatorgroup.yaml >/dev/null

say "== operators at pinned CSVs"
T_OP=$SECONDS
install_operator "$STRIMZI_PACKAGE" "$STRIMZI_CHANNEL" "$STRIMZI_CATALOG" "$STRIMZI_CSV"
install_operator "$CNPG_PACKAGE" "$CNPG_CHANNEL" "$CNPG_CATALOG" "$CNPG_CSV"
say "measured: both operators Succeeded $((SECONDS - T_OP)) s after the first Subscription"

say "== Kafka, PostgreSQL and Connect"
T_PL=$SECONDS
oc apply --server-side --field-manager=edp-ah -n "$NS" -k platform >/dev/null
kafka_ready() { [[ "$(oc get kafka edp -n "$NS" -o 'jsonpath={.status.conditions[?(@.type=="Ready")].status}')" == True ]]; }
pg_ready() { [[ "$(oc get cluster.postgresql.cnpg.io edp-pg -n "$NS" -o 'jsonpath={.status.conditions[?(@.type=="Ready")].status}')" == True ]]; }
connect_ready() { [[ "$(oc get kafkaconnect edp-connect -n "$NS" -o 'jsonpath={.status.conditions[?(@.type=="Ready")].status}')" == True ]]; }
wait_for 600 "Kafka edp Ready" kafka_ready
wait_for 600 "Cluster edp-pg Ready" pg_ready
wait_for 900 "KafkaConnect edp-connect Ready (the image is built into the ImageStream first)" connect_ready
say "measured: platform Ready $((SECONDS - T_PL)) s after the apply"
say "install: done in $((SECONDS - T0)) s"
