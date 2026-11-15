#!/usr/bin/env bash
# Deploys the chapter 02 pair with the field manager edp-apps and checks who owns what. A client-side apply of
# the CloudNativePG install fails with "Too long" and a server-side apply does not. A second writer, edp-platform,
# sets the memory limit that edp-apps owns: the apply conflicts until it is forced, and ownership moves. Strimzi
# applies its own fields server-side, so a foreign annotation on the bootstrap Service survives a reconcile and a
# field the operator owns is put back. APPLY=client (demo code) makes the two writers apply client-side, which is
# the negative witness (see the README). KEEP=1 leaves the namespaces in place.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../_k8s/lib.sh
source ../_k8s/lib.sh
trap k8s_teardown EXIT
k8s_fresh . edp-apps

APPLY="${APPLY:-server}"                 # demo code: "client" makes edp-platform and the edp-apps re-apply client-side
LIMIT_PATH=(spec template spec containers 'k:{"name":"app"}' resources limits memory)
DISCOVERY_PATH=(metadata annotations strimzi.io/discovery)
OP_LOG_SELECTOR=deploy/strimzi-cluster-operator
FOREIGN_VALUE=edp-foreign-value
CNPG_MANIFEST="$_LIB_DIR/$CNPG_FILE"
STRIMZI_MANIFEST="$_LIB_DIR/$STRIMZI_FILE"

# --- helpers
limit() { kc -n "$NS_APPS" get deploy order-service -o jsonpath='{.spec.template.spec.containers[?(@.name=="app")].resources.limits.memory}'; }
owner_list() { # owner_list <object> <namespace> <path segment>...: "<manager> <operation>" for every owner of the field
    local obj="$1" ns="$2"; shift 2
    kc -n "$ns" get "$obj" --show-managed-fields -o json | python3 -I tools/owners.py "$@"
}
owned_by() { # owned_by "<manager> <operation>" <object> <namespace> <path segment>...
    local who="$1" list; shift
    list="$(owner_list "$@")"
    [[ $'\n'"$list"$'\n' == *$'\n'"$who"$'\n'* ]]
}
not_owned_by() { ! owned_by "$@"; }
owns_only() { # owns_only "<owner>" "<not owner>" <object> <namespace> <path segment>...
    local yes="$1" no="$2"; shift 2
    owned_by "$yes" "$@" && not_owned_by "$no" "$@"
}
annotated_by_foreign() { # the apply of foreign/annotation.yaml was accepted and edp-foreign owns the annotation
    [[ "$FOREIGN_OUT" == *serverside-applied* ]] \
        && owned_by "edp-foreign Apply" svc/edp-kafka-bootstrap "$NS_KAFKA" metadata annotations example.com/owner
}
conflict_naming() { # conflict_naming <output> <manager> <field>: the apply failed on a conflict with that manager
    [[ "$1" == *"Apply failed with 1 conflict: conflict with \"$2\": $3"* ]]
}
apply_as() { # apply_as <manager> <kustomize-dir> [--force-conflicts]; APPLY=client (demo code) drops server-side, manager and force
    local mgr="$1" dir="$2"; shift 2
    if [[ "$APPLY" == "client" ]]; then
        kc apply -k "$dir" 2>&1
    else
        kc apply --server-side --field-manager="$mgr" "$@" -k "$dir" 2>&1
    fi
}
count_matching() { local n; n="$(grep -cE "$1" <<<"$2" || true)"; echo "$n"; } # count_matching <regex> <text>
rolled_out() { kc -n "$NS_APPS" rollout status "deploy/$1" --timeout=5s; }
notified() { [[ "$(curl -sf "http://127.0.0.1:$PORT2/notifications?orderId=$1")" == *'"orderStatus":"PLACED"'* ]]; }
place() { curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"id\":\"$1\",\"customerId\":\"CU-77\",\"itemSku\":\"SKU-42\",\"quantity\":3,\"amount\":59.90}" "$ORDERS/orders"; }
annotation() { kc -n "$NS_KAFKA" get svc edp-kafka-bootstrap -o jsonpath="{.metadata.annotations.$1}"; } # annotation <escaped key>
reconciled() { # reconciliations of Kafka edp that the operator has finished
    kc -n "$NS_KAFKA" logs "$OP_LOG_SELECTOR" | grep -cE 'Kafka\(edp-kafka/edp\): reconciled' || true
}
reconcile_once() { # reconcile_once <n>: annotate the Kafka resource and wait for the operator to finish one more reconciliation
    local before
    before="$(reconciled)"
    kc -n "$NS_KAFKA" annotate kafka edp "r=$1" --overwrite >/dev/null
    reconciled_more() { (( $(reconciled) > before )); }
    poll 180 reconciled_more
}

# --- preconditions: the pair is up and the limit is the manifest's
both_ready() { ready 30080 && ready "$PORT2"; }
precondition "order-service and notification-service answer on /q/health/ready" both_ready
precondition "order-service starts with limits.memory 384Mi" expect "$(limit)" 384Mi

# --- install: a client-side apply stores the manifest in an annotation, a server-side apply does not
CNPG_CLIENT_OUT="$(kc apply -n cnpg-system --dry-run=server -f "$CNPG_MANIFEST" 2>&1 || true)"
output_has() { [[ "$CNPG_CLIENT_OUT" == *"$1"* ]]; }   # the output is too large for an argument list, so it is read from the variable
check "client-side apply of the CloudNativePG install fails with Too long on clusters.postgresql.cnpg.io" \
    output_has 'clusters.postgresql.cnpg.io" is invalid: metadata.annotations: Too long: may not be more than 262144 bytes'
check "the same failure on poolers.postgresql.cnpg.io" \
    output_has 'poolers.postgresql.cnpg.io" is invalid: metadata.annotations: Too long: may not be more than 262144 bytes'
# the install on this cluster was applied by edp-cluster (cluster.sh), so the dry run uses that manager
check "server-side apply of the same manifest succeeds on the server" \
    kc apply --server-side --dry-run=server --field-manager=edp-cluster -f "$CNPG_MANIFEST"
STRIMZI_CLIENT_OUT="$(kc apply -n "$NS_KAFKA" --dry-run=server -f "$STRIMZI_MANIFEST" 2>&1 || true)"
strimzi_not_too_long() { [[ -n "$STRIMZI_CLIENT_OUT" && "$STRIMZI_CLIENT_OUT" != *"Too long"* ]]; }
check "client-side apply of the Strimzi install reports no Too long" strimzi_not_too_long

# --- one writer: edp-apps owns the limit, a changed manifest shows as a diff, a dropped label is removed
check "managedFields lists edp-apps as an Apply owner of limits.memory" \
    owned_by "edp-apps Apply" deploy/order-service "$NS_APPS" "${LIMIT_PATH[@]}"
DIFF_OUT="$(kc diff --server-side --field-manager=edp-apps -k changes/memory 2>&1 || true)"
check "kubectl diff --server-side shows memory 384Mi to 400Mi and applies nothing" \
    bash -c '[[ "$1" == "1/1/384Mi" ]]' _ \
    "$(count_matching '^-[[:space:]]+memory: 384Mi$' "$DIFF_OUT")/$(count_matching '^\+[[:space:]]+memory: 400Mi$' "$DIFF_OUT")/$(limit)"
kc apply --server-side --field-manager=edp-apps -k changes/label >/dev/null
check "a label added by edp-apps is on the Deployment" \
    expect "$(kc -n "$NS_APPS" get deploy order-service -o jsonpath='{.metadata.labels.witness}')" on
kc apply --server-side --field-manager=edp-apps -k apps >/dev/null
check "the label is removed when edp-apps stops sending it" \
    expect "$(kc -n "$NS_APPS" get deploy order-service -o jsonpath='{.metadata.labels.witness}')" ""

# --- two writers: edp-platform sets the limit edp-apps owns
CONFLICT_OUT="$(apply_as edp-platform platform || true)"
FIELD='.spec.template.spec.containers[name="app"].resources.limits.memory'
check "edp-platform conflicts with edp-apps on limits.memory" conflict_naming "$CONFLICT_OUT" edp-apps "$FIELD"
check "the object is unchanged after the conflict" expect "$(limit)" 384Mi
apply_as edp-platform platform --force-conflicts >/dev/null || true
check "with --force-conflicts the limit is 512Mi" expect "$(limit)" 512Mi
check "managedFields: edp-platform owns limits.memory and edp-apps no longer does" \
    owns_only "edp-platform Apply" "edp-apps Apply" deploy/order-service "$NS_APPS" "${LIMIT_PATH[@]}"
REAPPLY_OUT="$(apply_as edp-apps apps || true)"
check "re-applying the edp-apps manifest conflicts with edp-platform" conflict_naming "$REAPPLY_OUT" edp-platform "$FIELD"
apply_as edp-apps apps --force-conflicts >/dev/null || true
check "with --force-conflicts the limit is 384Mi again" expect "$(limit)" 384Mi
check "managedFields: edp-apps owns limits.memory again and edp-platform no longer does" \
    owns_only "edp-apps Apply" "edp-platform Apply" deploy/order-service "$NS_APPS" "${LIMIT_PATH[@]}"
poll 240 rolled_out order-service >/dev/null 2>&1 || true
poll 120 ready 30080 >/dev/null 2>&1 || true
ORDER=O-3001
place "$ORDER" >/dev/null
check "O-3001 is notified after the limit changed twice" poll 90 notified "$ORDER"

# --- Strimzi applies its own fields server-side too
check "managedFields of edp-kafka-bootstrap lists strimzi-kafka-operator as an Apply owner of strimzi.io/discovery" \
    owned_by "strimzi-kafka-operator Apply" svc/edp-kafka-bootstrap "$NS_KAFKA" "${DISCOVERY_PATH[@]}"
FOREIGN_OUT="$(kc apply --server-side --field-manager=edp-foreign -f foreign/annotation.yaml 2>&1 || true)"
check "edp-foreign adds an annotation without a conflict and is listed beside the operator" annotated_by_foreign
reconcile_once 1
reconcile_once 2
check "the foreign annotation survives two reconciliations" expect "$(annotation 'example\.com/owner')" platform-team
DISCOVERY_OUT="$(kc apply --server-side --field-manager=edp-foreign -f foreign/discovery.yaml 2>&1 || true)"
check "changing strimzi.io/discovery conflicts with strimzi-kafka-operator" \
    conflict_naming "$DISCOVERY_OUT" strimzi-kafka-operator ".metadata.annotations.strimzi.io/discovery"
kc apply --server-side --force-conflicts --field-manager=edp-foreign -f foreign/discovery.yaml >/dev/null
check "with --force-conflicts the annotation holds the foreign value" expect "$(annotation 'strimzi\.io/discovery')" "$FOREIGN_VALUE"
T_REC=$SECONDS
reconcile_once 3
restored() { local v; v="$(annotation 'strimzi\.io/discovery')"; [[ -n "$v" && "$v" != "$FOREIGN_VALUE" ]]; }
check "the operator restores strimzi.io/discovery at its next reconciliation" poll 60 restored
echo "measured: annotation restored $((SECONDS - T_REC)) s after the reconciliation was triggered"
check "managedFields: strimzi-kafka-operator owns strimzi.io/discovery again and edp-foreign does not" \
    owns_only "strimzi-kafka-operator Apply" "edp-foreign Apply" svc/edp-kafka-bootstrap "$NS_KAFKA" "${DISCOVERY_PATH[@]}"

summary
