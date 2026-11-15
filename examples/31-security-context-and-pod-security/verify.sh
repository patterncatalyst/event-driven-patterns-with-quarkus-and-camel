#!/usr/bin/env bash
# Labels the namespaces edp and edp-kafka for the restricted Pod Security Standard and checks that every pod still
# runs: the two applications with an explicit security context, Strimzi with its restricted pod security provider,
# CloudNativePG as published. cnpg-system and edp-lgtm are checked with server dry-run labels and never relabelled.
# APP_SECURITY=none and STRIMZI_PSP=baseline (demo code) are the negative witnesses (see the README). KEEP=1 leaves
# the namespaces in place afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../_k8s/lib.sh
source ../_k8s/lib.sh
trap k8s_teardown EXIT

APP_SECURITY="${APP_SECURITY:-restricted}"   # none (demo code): the applications carry no security context
STRIMZI_PSP="${STRIMZI_PSP:-restricted}"     # baseline (demo code): Strimzi keeps its default pod security provider
DIR=.
if [[ "$APP_SECURITY" == none && "$STRIMZI_PSP" == baseline ]]; then
    echo "choose one witness at a time: APP_SECURITY=none or STRIMZI_PSP=baseline" >&2; exit 2
elif [[ "$APP_SECURITY" == none ]]; then
    DIR=demo/app-none; export WAIT_APPS=0          # the pods are refused, so there is nothing to wait for
elif [[ "$STRIMZI_PSP" == baseline ]]; then
    DIR=demo/strimzi-baseline; export WAIT_APPS=0   # the Kafka pods are refused, so the setup cannot wait for them
fi

k8s_fresh "$DIR"
if [[ "$STRIMZI_PSP" == baseline ]]; then   # demo code: Kafka is applied only now, so the setup waits above did not need it
    kc apply --server-side --field-manager=edp-verify -k ../_k8s/kafka >/dev/null
fi

PSA=pod-security.kubernetes.io
NOTIFY="http://127.0.0.1:$PORT2"
ORDER=O-3101

label() { kc get ns "$1" -o "jsonpath={.metadata.labels.pod-security\.kubernetes\.io/$2}"; }   # label <ns> <key>
dry_label() { kc label --dry-run=server --overwrite ns "$1" "$PSA/$2" 2>&1; }                 # dry_label <ns> <key>=<value>
no_warning() { local out; out="$(dry_label "$@")" || return 1; [[ "$out" != *Warning* ]]; }
warns() { local out; out="$(dry_label "$@")" || return 1; [[ "$out" == *"violate the new PodSecurity enforce level"* ]]; }
pods_restricted() { kc -n "$1" get pods -o json | python3 -I tools/restricted.py "$2"; }     # pods_restricted <ns> <minimum pods>
pod_refused() { # the API server refuses a pod with no security context
    local out; if out="$(kc apply --server-side --dry-run=server --field-manager=edp-verify -f fixtures/unsecured-pod.yaml 2>&1)"; then return 1; fi
    [[ "$out" == *'violates PodSecurity "restricted:v1.37"'* ]]
}
deployment_warned() { # a Deployment is accepted, with a warning that its pods would be refused
    local out; out="$(kc apply --server-side --dry-run=server --field-manager=edp-verify -f fixtures/unsecured-deployment.yaml 2>&1)" || return 1
    [[ "$out" == *'would violate PodSecurity "restricted:v1.37"'* ]]
}
kafka_ready() {
    [[ "$(kc -n edp-kafka get kafka edp -o 'jsonpath={.status.conditions[?(@.type=="Ready")].status}/{.status.observedGeneration}')" == "True/$(kc -n edp-kafka get kafka edp -o 'jsonpath={.metadata.generation}')" ]]
}
operator_refused() { local out; out="$(kc -n edp-kafka logs deploy/strimzi-cluster-operator 2>&1)"; [[ "$out" == *'violates PodSecurity'* ]]; }
operator_env() { kc -n edp-kafka get deploy strimzi-cluster-operator -o 'jsonpath={.spec.template.spec.containers[0].env[?(@.name=="STRIMZI_POD_SECURITY_PROVIDER_CLASS")].value}'; }
uid_in() { kc -n "$1" exec "$2" -c "$3" -- id -u; }                                            # uid_in <ns> <pod> <container>
app_namespaces_clean() { no_warning edp enforce-version=latest && no_warning edp-kafka enforce-version=latest; }
operator_not_refused() { ! operator_refused; }
failed_create_seen() { [[ -n "$(kc -n edp get events --field-selector reason=FailedCreate -o name)" ]]; }
available() { [[ "$(kc -n edp get deploy "$1" -o 'jsonpath={.status.conditions[?(@.type=="Available")].status}')" == True ]]; }
security_fields() { # security_fields <deployment>: the four fields and the read-only root filesystem, as set in the Deployment
    kc -n edp get deploy "$1" -o 'jsonpath={.spec.template.spec.securityContext.runAsNonRoot} {.spec.template.spec.securityContext.seccompProfile.type} {.spec.template.spec.containers[0].securityContext.allowPrivilegeEscalation} {.spec.template.spec.containers[0].securityContext.capabilities.drop[0]} {.spec.template.spec.containers[0].securityContext.readOnlyRootFilesystem}'
}
replicas_created() { # the ReplicaSet created its pod: no ReplicaFailure, one replica
    [[ "$(kc -n edp get deploy "$1" -o 'jsonpath={.status.replicas}/{.status.conditions[?(@.type=="ReplicaFailure")].status}')" == "1/" ]]
}
rootfs_read_only() { # writing under /deployments fails, writing under /tmp works
    ! kc -n edp exec deploy/order-service -- touch /deployments/probe 2>/dev/null && kc -n edp exec deploy/order-service -- touch /tmp/probe
}
apps_available() { poll 120 available order-service && poll 120 available notification-service; }
apps_created() { replicas_created order-service && replicas_created notification-service; }
order_created() {
    poll 60 ready 30080 || return 1
    [[ "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' "$ORDERS/orders" \
        --data "{\"id\":\"$ORDER\",\"customerId\":\"CU-77\",\"itemSku\":\"SKU-42\",\"quantity\":3,\"amount\":59.90}")" == 201 ]]
}
notified() { local out; out="$(curl -sf "$NOTIFY/notifications?orderId=$ORDER")" || return 1; [[ "$out" == *'"customerId":"CU-77"'* ]]; }

# --- namespaces
precondition "edp and edp-kafka enforce the restricted level, pinned to v1.37" \
    expect "$(label edp enforce)/$(label edp enforce-version)/$(label edp-kafka enforce)/$(label edp-kafka enforce-version)" restricted/v1.37/restricted/v1.37
precondition "the CloudNativePG cluster edp-pg is Ready" \
    expect "$(kc -n edp get cluster edp-pg -o 'jsonpath={.status.conditions[?(@.type=="Ready")].status}')" True
check "edp and edp-kafka also warn and audit at restricted v1.37" \
    expect "$(label edp warn)/$(label edp warn-version)/$(label edp audit)/$(label edp audit-version)/$(label edp-kafka warn)/$(label edp-kafka warn-version)/$(label edp-kafka audit)/$(label edp-kafka audit-version)" \
    restricted/v1.37/restricted/v1.37/restricted/v1.37/restricted/v1.37
check "edp-lgtm carries no enforce label" expect "$(label edp-lgtm enforce)" ""
check "re-evaluating edp and edp-kafka against restricted:latest reports no violating pod" app_namespaces_clean
check "the API server refuses a pod with no security context in edp" pod_refused
check "a Deployment with no security context is accepted with a warning that its pods would be refused" deployment_warned

# --- Strimzi
if [[ "$STRIMZI_PSP" == baseline ]]; then   # demo code: show the refusal the operator logs
    poll 240 operator_refused || true
    echo "demo: $(kc -n edp-kafka logs deploy/strimzi-cluster-operator 2>&1 | grep -m1 -o 'pods "[^"]*" is forbidden: violates PodSecurity[^"]*"[^"]*"' || true)"
fi
check "Kafka edp is Ready" kafka_ready
check "the operator, broker and controller pods are Running and restricted-compliant" pods_restricted edp-kafka 3
check "the broker runs as uid 1001" expect "$(uid_in edp-kafka edp-broker-0 kafka 2>/dev/null || true)" 1001
check "the operator selects the restricted pod security provider" expect "$(operator_env)" restricted
check "the operator log holds no PodSecurity refusal" operator_not_refused

# --- CloudNativePG
check "the PostgreSQL pod is Running and restricted-compliant" pods_restricted edp 1
check "PostgreSQL runs as uid 26" expect "$(uid_in edp edp-pg-1 postgres 2>/dev/null || true)" 26
check "the CloudNativePG operator pod is Running and restricted-compliant" pods_restricted cnpg-system 1
check "re-evaluating cnpg-system against restricted reports no violating pod" no_warning cnpg-system enforce=restricted

# --- otel-lgtm
check "labelling edp-lgtm restricted warns that its pod violates the level" warns edp-lgtm enforce=restricted
check "the otel-lgtm pod runs in the unlabelled edp-lgtm" \
    expect "$(kc -n edp-lgtm get pods -o 'jsonpath={.items[*].status.phase}')" Running

# --- the applications
if [[ "$APP_SECURITY" == none ]]; then   # demo code: show the refusal the ReplicaSet reports
    poll 60 failed_create_seen || true
    echo "demo: $(kc -n edp get events --field-selector reason=FailedCreate -o 'jsonpath={.items[0].message}' | cut -c1-600)"
fi
check "order-service and notification-service are Available" apps_available
check "order-service runs as uid 185" expect "$(kc -n edp exec deploy/order-service -- id -u 2>/dev/null || true)" 185
check "both Deployments set the four fields and a read-only root filesystem" \
    expect "$(security_fields order-service)|$(security_fields notification-service)" "true RuntimeDefault false ALL true|true RuntimeDefault false ALL true"
check "the root filesystem of order-service is read-only except /tmp" rootfs_read_only
check "both ReplicaSets created their pods with no ReplicaFailure" apps_created
check "every pod in edp is Running and restricted-compliant" pods_restricted edp 3
check "$ORDER is accepted with 201" order_created
check "$ORDER produces a notification" poll 90 notified

summary
