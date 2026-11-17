#!/usr/bin/env bash
# Checks the installed platform and the deployed order-service on OpenShift Local: the operators at their pinned
# CSVs, the image in the cluster against the local buildah build, Connect and its connector through the Connect REST
# API (reads only), the admission of every pod by restricted-v2, the Route with a verified certificate, and the
# outbox record on Kafka. It changes nothing except one order, O-4301, and a short-lived Deployment that the SCC
# witness asks for and deletes. Run it between deploy.sh and teardown.sh: the local image must still exist.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh --crc
source ./crc-check.sh
source ./lib.sh

CA_DIR="$(mktemp -d)"
trap 'rm -rf "$CA_DIR"' EXIT
fetch_ca "$CA_DIR/ca.crt" || die "cannot read the ingress CA from the ConfigMap default-ingress-cert"
HOST="$(oc get route order-service -n "$NS" -o 'jsonpath={.spec.host}')"

# --- helpers
act() { "$@" >/dev/null || echo "WARN: $*" >&2; }   # an action between checks; its effect is judged by the next check
connect_pod() { oc get pod -n "$NS" -l strimzi.io/kind=KafkaConnect -o 'jsonpath={.items[0].metadata.name}'; }
connect_get() { oc exec -n "$NS" "$(connect_pod)" -- curl -sf "localhost:8083$1"; }
sql() { # psql as the application role over TCP; the pod's user is a random UID, so peer authentication does not apply
    # the password is read from the secret and goes to psql through stdin, never on a command line or into a file
    oc get secret edp-pg-app -n "$NS" -o 'jsonpath={.data.password}' | base64 -d \
        | oc exec -i -n "$NS" edp-pg-1 -c postgres -- sh -c 'IFS= read -r PGPASSWORD; export PGPASSWORD; exec psql -h 127.0.0.1 -U edp -tA -d edp -c "$1"' sh "$1"
}
consume() { # the whole topic with keys and headers; a record line holds tabs
    oc exec -n "$NS" edp-broker-0 -c kafka -- /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
        --topic "$1" --from-beginning --timeout-ms 8000 --formatter-property print.key=true --formatter-property print.headers=true 2>/dev/null || true
}
condition() { oc get "$@" -o 'jsonpath={.status.conditions[?(@.type=="Ready")].status}'; } # condition <kind> <name> -n <ns>
post() { curl -s -o /dev/null -w '%{http_code}' --cacert "$CA_DIR/ca.crt" -X POST -H 'Content-Type: application/json' \
    "https://$HOST/orders" --data "{\"id\":\"$1\",\"customerId\":\"CU-77\",\"itemSku\":\"SKU-42\",\"quantity\":3,\"amount\":59.90}"; }
stays() { # stays <seconds> <command...>: the command succeeds now and keeps succeeding for the whole period
    local end=$((SECONDS + $1)); shift
    while (( SECONDS < end )); do "$@" || return 1; sleep 1; done
    "$@"
}

# --- predicates
crc_and_context() { ./crc-check.sh; }
operator_installed() { # operator_installed <subscription> <csv>
    csv_succeeded "$2" && [[ "$(oc get subscription "$1" -n "$NS_OP" -o 'jsonpath={.status.installedCSV}')" == "$2" ]]
}
manual_and_named_only() {
    local want subs plans
    want="$(printf '%s\n' "$STRIMZI_CSV" "$CNPG_CSV" | sort)"
    subs="$(oc get subscription -n "$NS_OP" -o json | jq -r '.items[] | select(.spec.installPlanApproval == "Manual") | .spec.startingCSV' | sort)"
    [[ "$subs" == "$want" ]] || return 1
    plans="$(oc get installplan -n "$NS_OP" -o json | jq -r '.items[] | select(.spec.approved == true) | .spec.clusterServiceVersionNames[]' | sort -u)"
    [[ "$plans" == "$want" ]]
}
kafka_at_version() { [[ "$(condition kafka edp -n "$NS")" == True && "$(oc get kafka edp -n "$NS" -o 'jsonpath={.status.kafkaVersion}')" == 4.3.2 ]]; }
postgres_replication() {
    [[ "$(condition cluster.postgresql.cnpg.io edp-pg -n "$NS")" == True ]] &&
    [[ "$(sql "SELECT rolreplication FROM pg_roles WHERE rolname='edp'")" == t ]] &&
    [[ "$(sql "SHOW wal_level")" == logical ]]
}
connect_with_plugin() {
    local image plugins
    [[ "$(condition kafkaconnect edp-connect -n "$NS")" == True ]] || return 1
    image="$(oc get pod -n "$NS" -l strimzi.io/kind=KafkaConnect -o 'jsonpath={.items[0].spec.containers[0].image}')"
    plugins="$(connect_get /connector-plugins)" || return 1
    [[ "$image" == image-registry.openshift-image-registry.svc:5000/"$NS"/edp-connect@sha256:* && "$plugins" == *io.debezium.connector.postgresql.PostgresConnector* ]]
}
connector_running() { # the REST API, not the resource: the resource's task list stays empty while records flow
    local out; out="$(connect_get /connectors/order-outbox/status)" || return 1
    [[ "$(python3 -I -c '
import json, sys
d = json.load(sys.stdin)
print(d["connector"]["state"], ",".join(t["state"] for t in d["tasks"]) or "-")' <<<"$out")" == "RUNNING RUNNING" ]]
}
image_digests_equal() { local l r; l="$(local_digest)"; r="$(istag_digest)"; [[ "$l" == sha256:* && "$l" == "$r" ]]; }
deployment_by_digest() {
    local d; d="$(istag_digest)"
    [[ "$(oc get deployment order-service -n "$NS" -o 'jsonpath={.spec.template.spec.containers[0].image}')" == "$APP_INTERNAL_REPO@$d" ]] &&
    [[ "$(oc get pod -n "$NS" -l app=order-service -o 'jsonpath={.items[0].status.containerStatuses[0].imageID}')" == *"@$d" ]]
}
pods_by_scc() { # pods_by_scc <ns>: at least one running pod, every one admitted by restricted-v2; build pods are the platform's and are skipped
    oc get pod -n "$1" -o json | jq -e '[.items[] | select(.status.phase == "Running" and (.metadata.labels["openshift.io/build.name"] == null))]
        | length > 0 and all(.metadata.annotations["openshift.io/scc"] == "restricted-v2")' >/dev/null
}
all_pods_restricted() { pods_by_scc "$NS" && pods_by_scc "$NS_OP"; }
uid_in_project_range() {
    local range first size uid
    range="$(oc get namespace "$NS" -o 'jsonpath={.metadata.annotations.openshift\.io/sa\.scc\.uid-range}')"
    first="${range%/*}"; size="${range#*/}"
    uid="$(oc exec -n "$NS" deploy/order-service -- id -u)"
    (( uid >= first && uid < first + size && uid != 185 ))
}
privileged_refused() {
    act oc apply --server-side --field-manager=edp-ah -n "$NS" -f - <<YAML
apiVersion: apps/v1
kind: Deployment
metadata: {name: scc-witness}
spec:
  replicas: 1
  selector: {matchLabels: {app: scc-witness}}
  template:
    metadata: {labels: {app: scc-witness}}
    spec:
      containers:
        - name: app
          image: $APP_INTERNAL_REPO:$APP_TAG
          securityContext: {privileged: true}
YAML
    # Only an event on the witness's own ReplicaSet, raised after the witness was created, counts; and no replica
    # of the witness may ever become available.
    local ok=1 since
    since="$(oc get deployment scc-witness -n "$NS" -o 'jsonpath={.metadata.creationTimestamp}')"
    refused() {
        oc get event -n "$NS" -o json | jq -e --arg since "$since" '[.items[]
            | select(.reason == "FailedCreate" and (.involvedObject.name | startswith("scc-witness-"))
                     and ((.lastTimestamp // .eventTime // .metadata.creationTimestamp)[0:19] >= $since[0:19])
                     and (.message | test("unable to validate against any security context constraint")))] | length > 0' >/dev/null
    }
    poll 60 refused || ok=0
    [[ "$(oc get deployment scc-witness -n "$NS" -o 'jsonpath={.status.availableReplicas}')" =~ ^0?$ ]] || ok=0
    act oc delete deployment scc-witness -n "$NS" --wait=true
    (( ok ))
}
route_ready() { [[ "$(curl -s -o /dev/null -w '%{http_code}' --cacert "$CA_DIR/ca.crt" "https://$HOST/q/health/ready")" == 200 ]]; }
http_redirects() {
    local out; out="$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' "http://$HOST/q/health/ready")"
    [[ "$out" =~ ^30[1278]\ https:// ]]
}
without_ca_refused() { # the cluster's CA is not in the host's trust store: without --cacert curl stops at the certificate (exit 60)
    local rc=0
    curl -s -o /dev/null "https://$HOST/q/health/ready" || rc=$?
    [[ "$rc" == 60 ]]
}
one_record() { # exactly one record, keyed O-4301, with the eventType header OrderPlaced
    local out; out="$(consume "$TOPIC")"
    [[ "$(awk -F'\t' -v k="$ORDER" 'index("," $1 ",", ",eventType:OrderPlaced,") && $2 == k' <<<"$out" | wc -l)" == 1 ]]
}
row_and_outbox_removed() { [[ "$(sql "SELECT (SELECT count(*) FROM orders.orders WHERE id='$ORDER') || '/' || (SELECT count(*) FROM orders.outboxevent)")" == 1/0 ]]; }
second_refused() { [[ "$(post "$ORDER")" == 409 ]] && poll 10 one_record && stays 8 one_record; }

# --- checks (numbering is frozen: the README and the appendix quote the pass count only)
check "OpenShift Local is running and the context is crc-admin" crc_and_context
check "Strimzi $STRIMZI_CSV is Succeeded and installed by its Subscription" operator_installed "$STRIMZI_PACKAGE" "$STRIMZI_CSV"
check "CloudNativePG $CNPG_CSV is Succeeded and installed by its Subscription" operator_installed "$CNPG_PACKAGE" "$CNPG_CSV"
check "both Subscriptions are Manual and the only approved InstallPlans install the two pinned CSVs" manual_and_named_only
check "Kafka edp is Ready on 4.3.2" kafka_at_version
check "the PostgreSQL Cluster is Ready, wal_level is logical and the role edp has REPLICATION" postgres_replication
check "KafkaConnect is Ready, runs the image built into the ImageStream and lists the PostgreSQL connector plugin" connect_with_plugin
check "the connector and its task are RUNNING in the Connect REST API" poll 60 connector_running
check "the digest in the ImageStream equals the digest of the local buildah build" image_digests_equal
check "order-service runs by that digest" deployment_by_digest
check "every running pod in edp and edp-operators is admitted by restricted-v2" all_pods_restricted
check "order-service runs as a user inside the project's UID range, not the image's user 185" uid_in_project_range
check "a privileged Deployment in edp is refused by security context constraint admission" privileged_refused
check "the Route answers /q/health/ready with 200 over TLS verified against the ingress CA" poll 60 route_ready
check "plain HTTP to the Route is redirected to HTTPS" http_redirects
check "without the CA, curl refuses the Route's certificate" without_ca_refused
check "$ORDER is accepted through the Route with 201" expect "$(post "$ORDER")" 201
check "one record on $TOPIC, keyed $ORDER, with the eventType header OrderPlaced" poll 90 one_record
check "the order row exists and the outbox row is already removed (the application deletes it in the same transaction)" poll 20 row_and_outbox_removed
check "a second $ORDER is refused with 409 and no second record appears" second_refused

summary
