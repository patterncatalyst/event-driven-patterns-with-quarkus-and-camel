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

# wait_ready <description> <oc wait ...>: the wait is oc's own; this prints how long it took
wait_ready() {
    local d=$1 start=$SECONDS; shift
    "$@" >/dev/null || { say "not met: $d" >&2; return 1; }
    say "ready: $d ($((SECONDS - start)) s)"
}

# The API groups of the two operators. Their CRDs are cluster-wide, so the example needs a cluster with no other
# Strimzi or CloudNativePG install: install.sh refuses when any of these CRDs exists, teardown.sh when another
# install owns them.
CRD_GROUPS='\.(kafka\.strimzi\.io|core\.strimzi\.io|postgresql\.cnpg\.io)$'
our_crds() { oc get crd -o name | grep -E "$CRD_GROUPS" || true; }

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
csv_exists() { oc get csv "$1" -n "$NS_OP" >/dev/null 2>&1; }
csv_succeeded() { [[ "$(csv_phase "$1")" == Succeeded ]]; }

install_operator() { # install_operator <package> <channel> <catalog> <csv>
    local pkg=$1 channel=$2 catalog=$3 csv=$4 plan
    oc apply --server-side --field-manager=edp-ah -n "$NS_OP" -f - >/dev/null <<YAML
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
    wait_ready "an InstallPlan for $csv" oc wait "subscription/$pkg" -n "$NS_OP" --for='jsonpath={.status.installPlanRef.name}' --timeout=300s
    plan="$(plan_for "$csv")"
    [[ -n "$plan" ]] || die "the Subscription $pkg has an InstallPlan, but none installs $csv"
    say "approving InstallPlan $plan for $csv (the only plan approved for it)"
    oc patch installplan "$plan" -n "$NS_OP" --type merge -p '{"spec":{"approved":true}}' >/dev/null
    poll 60 csv_exists "$csv" || die "the CSV $csv did not appear after the approval"
    wait_ready "$csv Succeeded" oc wait "csv/$csv" -n "$NS_OP" --for='jsonpath={.status.phase}'=Succeeded --timeout=600s
}

# ---- the application image ---------------------------------------------------------------------------------------
local_digest() { # the manifest digest of the image in rootless containers-storage
    skopeo inspect --format '{{.Digest}}' "containers-storage:$APP_LOCAL_IMAGE"
}
istag_digest() { # the digest the imagestream tag points at
    oc get istag "$APP_IMAGESTREAM:$APP_TAG" -n "$NS" -o 'jsonpath={.image.metadata.name}'
}
