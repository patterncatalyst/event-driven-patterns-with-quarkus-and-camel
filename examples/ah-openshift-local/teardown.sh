#!/usr/bin/env bash
# Removes everything the example created, in dependency order, and then prints an inventory of what is left of it:
# the connector, the application, Connect, Kafka, the PostgreSQL Cluster, the ImageStreams, the Subscriptions and
# CSVs, the OperatorGroup, the operators' CRDs and the ClusterRoles that OLM leaves behind, the namespaces, the
# Retain volumes and the images in the cluster registry, then the local buildah image. It never strips a
# finalizer: a delete that does not finish stops the script with the resource named. It exits non-zero when
# anything of ours remains. It does not stop CRC.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
source ./crc-check.sh
source ./lib.sh
T0=$SECONDS

CRD_GROUPS='\.(kafka\.strimzi\.io|core\.strimzi\.io|postgresql\.cnpg\.io)$'
CLUSTERROLE_NAMES='cnpg-.*-(editor|viewer)-role|strimzi-kafka-(broker|client)'
our_crds() { oc get crd -o name | grep -E "$CRD_GROUPS" || true; }
our_roles() { oc get clusterrole -o name | sed 's|^clusterrole.rbac.authorization.k8s.io/|clusterrole/|' | grep -E "^clusterrole/($CLUSTERROLE_NAMES)$" || true; }

del() { # del <what...> -n <ns>: delete, wait, never force
    oc delete --ignore-not-found --wait=true --timeout=300s "$@" >/dev/null \
        || die "teardown: a delete did not finish: oc delete $*; finalizers are never stripped, look at the resource"
}
ns_exists() { oc get namespace "$1" >/dev/null 2>&1; }
cr_kinds_present() { oc api-resources --api-group="$1" -o name >/dev/null 2>&1 && [[ -n "$(oc api-resources --api-group="$1" -o name 2>/dev/null)" ]]; }

if ns_exists "$NS"; then
    say "== workloads in $NS"
    if cr_kinds_present kafka.strimzi.io; then
        del kafkaconnector --all -n "$NS"
    fi
    del deployment,service,route,pod -l app=order-service -n "$NS"
    if cr_kinds_present kafka.strimzi.io; then
        del kafkaconnect --all -n "$NS"
        del kafka,kafkanodepool --all -n "$NS"
    fi
    if cr_kinds_present postgresql.cnpg.io; then
        del cluster.postgresql.cnpg.io --all -n "$NS"
    fi
    del imagestream,buildconfig,build --all -n "$NS"
fi

if ns_exists "$NS_OP"; then
    say "== operators"
    del subscription --all -n "$NS_OP"
    del clusterserviceversion --all -n "$NS_OP"
    del installplan --all -n "$NS_OP"
    del operatorgroup --all -n "$NS_OP"
fi

say "== CRDs and ClusterRoles of the operators"
objs="$(our_crds; our_roles)"
for obj in $objs; do
    del "$obj"
done
say "deleted $(wc -w <<<"$objs") CRD(s) and ClusterRole(s)"

say "== namespaces"
for ns in "$NS" "$NS_OP"; do
    ns_exists "$ns" && del namespace "$ns"
done

say "== Retain volumes of the project"
pvs="$(oc get pv -o json | jq -r --arg ns "$NS" '.items[] | select(.spec.claimRef.namespace == $ns) | .metadata.name')"
for pv in $pvs; do
    del "persistentvolume/$pv"
done
say "deleted $(wc -w <<<"$pvs") volume(s)"

say "== images in the cluster registry"
# The prune is cluster-wide, so it first lists what it would delete and stops unless every image is ours.
REGISTRY="$(registry_host)"
mkdir -p "$STATE_DIR/certs.d"
fetch_ca "$STATE_DIR/certs.d/ca.crt"
prune() { oc adm prune images --keep-younger-than=0 --registry-url="$REGISTRY" --certificate-authority="$STATE_DIR/certs.d/ca.crt" "$@" 2>&1; }
candidates="$(prune | sed -n 's/^Deleting image //p')"
foreign=0
for image in $candidates; do
    ref="$(oc get image "$image" -o 'jsonpath={.dockerImageReference}')"
    [[ "$ref" == *":5000/$NS/"* ]] || { say "prune would delete an image that is not ours: $ref" >&2; foreign=1; }
done
(( foreign == 0 )) || die "teardown: not pruning; remove the project's images by hand"
say "prune candidates, all ours: $(wc -w <<<"$candidates") image object(s)"
prune --confirm | tail -1

say "== local image"
podman rmi --ignore "$APP_LOCAL_IMAGE" >/dev/null 2>&1 || true
rm -rf "$STATE_DIR"

say "== leftover inventory"
LEFT=0
report() { # report <title> <lines>
    if [[ -n "$2" ]]; then LEFT=$((LEFT + 1)); say "LEFT $1:"; sed 's/^/  /' <<<"$2"; else say "none  $1"; fi
}
report "namespaces edp, edp-operators" "$(oc get namespace -o name | grep -E '/(edp|edp-operators)$' || true)"
report "Subscriptions and CSVs of the operators" "$(oc get subscription,clusterserviceversion -A --no-headers 2>/dev/null | grep -Ei 'strimzi|cloudnative-pg|cnpg' || true)"
report "CRDs of Strimzi and CloudNativePG" "$(our_crds)"
report "ClusterRoles left by the operators" "$(our_roles)"
report "volumes claimed from edp" "$(oc get pv -o json | jq -r --arg ns "$NS" '.items[] | select(.spec.claimRef.namespace == $ns) | .metadata.name')"
report "Image objects of the project" "$(oc get image -o json | jq -r '.items[] | select(.dockerImageReference | test("/edp/")) | .metadata.name')"
report "local image $APP_LOCAL_IMAGE" "$(podman image ls --noheading --filter "reference=$APP_LOCAL_IMAGE" 2>/dev/null || true)"
say "kept on purpose: the UBI 10 base images in rootless containers-storage"
podman image ls --format '  {{.Repository}}:{{.Tag}}' 2>/dev/null || true
say "teardown: $((SECONDS - T0)) s, $LEFT item group(s) left"
[[ "$LEFT" == 0 ]] || exit 1
