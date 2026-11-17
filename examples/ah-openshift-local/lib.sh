# shellcheck shell=bash
# Helpers for the OpenShift Local scripts. Source it from this directory:
#   set -euo pipefail; cd "$(dirname "${BASH_SOURCE[0]}")"; source ./lib.sh
# Every oc call names its namespace with -n, or is cluster-scoped. Nothing here prints a token or a password.

_AH_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXAMPLES_DIR="$(cd "$_AH_DIR/.." && pwd)"
STATE_DIR="$_AH_DIR/.state"
# shellcheck source=../_k8s/versions.env
source "$EXAMPLES_DIR/_k8s/versions.env"
# shellcheck source=pins.env
source "$_AH_DIR/pins.env"
: "${NS:?NS is not set}" "${NS_OP:?NS_OP is not set}"

TOTAL=0
PASSED=0

check() { # check "<description>" <command...>
    local desc="$1"; shift
    TOTAL=$((TOTAL + 1))
    if "$@" >/dev/null 2>&1; then
        PASSED=$((PASSED + 1)); printf 'PASS %d: %s\n' "$TOTAL" "$desc"
    else
        printf 'FAIL %d: %s\n' "$TOTAL" "$desc"
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
summary() {
    echo "$PASSED/$TOTAL checks passed"
    [[ "$PASSED" == "$TOTAL" ]]
}
say() { printf '%s\n' "$*"; }
die() { printf '%s\n' "$*" >&2; exit 1; }

# wait_for <seconds> <description> <command...>
wait_for() {
    local t=$1 d=$2; shift 2
    local end=$((SECONDS + t)) start=$SECONDS
    until "$@" >/dev/null 2>&1; do
        (( SECONDS < end )) || { say "timeout after ${t}s: $d" >&2; return 1; }
        sleep 3
    done
    say "ready: $d ($((SECONDS - start)) s)"
}

# ---- the ingress CA ---------------------------------------------------------------------------------------------
# The public CA of the cluster's default ingress certificate, from a ConfigMap. The key of the certificate
# (secret router-certs-default) is never read.
fetch_ca() { # fetch_ca <file>
    oc get configmap default-ingress-cert -n openshift-config-managed -o 'jsonpath={.data.ca-bundle\.crt}' >"$1"
    [[ -s "$1" ]]
}
registry_host() { oc get route default-route -n openshift-image-registry -o 'jsonpath={.spec.host}'; }

# ---- OLM ---------------------------------------------------------------------------------------------------------
plan_for() { # plan_for <csv>: the name of the InstallPlan that installs the CSV
    oc get installplan -n "$NS_OP" -o json \
        | jq -r --arg c "$1" '[.items[] | select(.spec.clusterServiceVersionNames | index($c)) | .metadata.name][0] // empty'
}
csv_phase() { oc get csv "$1" -n "$NS_OP" -o 'jsonpath={.status.phase}' 2>/dev/null; }
csv_succeeded() { [[ "$(csv_phase "$1")" == Succeeded ]]; }

install_operator() { # install_operator <package> <channel> <catalog> <csv>
    local pkg=$1 channel=$2 catalog=$3 csv=$4 plan
    oc apply -n "$NS_OP" -f - >/dev/null <<YAML
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata: {name: $pkg}
spec:
  channel: $channel
  name: $pkg
  source: $catalog
  sourceNamespace: openshift-marketplace
  installPlanApproval: Manual
  startingCSV: $csv
YAML
    wait_for 300 "an InstallPlan for $csv" _has_plan "$csv"
    plan="$(plan_for "$csv")"
    say "approving InstallPlan $plan for $csv (the only plan approved for it)"
    oc patch installplan "$plan" -n "$NS_OP" --type merge -p '{"spec":{"approved":true}}' >/dev/null
    wait_for 600 "$csv Succeeded" csv_succeeded "$csv"
}
_has_plan() { [[ -n "$(plan_for "$1")" ]]; }

# ---- the application image ---------------------------------------------------------------------------------------
local_digest() { # the manifest digest of the image in rootless containers-storage
    skopeo inspect --format '{{.Digest}}' "containers-storage:$APP_LOCAL_IMAGE"
}
istag_digest() { # the digest the imagestream tag points at
    oc get istag "$APP_IMAGESTREAM:$APP_TAG" -n "$NS" -o 'jsonpath={.image.metadata.name}'
}
