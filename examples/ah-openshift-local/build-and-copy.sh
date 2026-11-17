#!/usr/bin/env bash
# Builds the order-service image with buildah from the chapter 08 Containerfile, unchanged, into rootless
# containers-storage, and copies it to the ImageStream edp/order-service with skopeo. The copy keeps the manifest
# digest (--preserve-digests), so the digest in the cluster is the digest of the local build. TLS is verified with
# the cluster's ingress CA through --dest-cert-dir. The token of the project's builder ServiceAccount lives in a
# shell variable for the one skopeo call; it is never written to a file or printed. The digest is written to
# .state/order-service.digest for deploy.sh. Nothing is pushed outside the cluster.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh --crc
source ./crc-check.sh
source ./lib.sh

oc get namespace "$NS" >/dev/null 2>&1 || die "namespace $NS does not exist; run ./install.sh first"

say "== buildah build"
mkdir -p "$STATE_DIR"
T_BUILD=$SECONDS
buildah build -f "$APP_CONTAINERFILE" -t "$APP_LOCAL_IMAGE" "$EXAMPLES_DIR" >"$STATE_DIR/build.log" 2>&1 \
    || { tail -30 "$STATE_DIR/build.log" >&2; die "buildah build failed (full log: .state/build.log)"; }
LOCAL="$(local_digest)"
say "measured: build $((SECONDS - T_BUILD)) s, local digest $LOCAL"

say "== skopeo copy"
mkdir -p "$STATE_DIR/certs.d"
fetch_ca "$STATE_DIR/certs.d/ca.crt" || die "cannot read the ingress CA from the ConfigMap default-ingress-cert"
REGISTRY="$(registry_host)"
[[ -n "$REGISTRY" ]] || die "the registry has no default route"
TOKEN="$(oc create token builder -n "$NS" --duration=15m)"
T_COPY=$SECONDS
skopeo copy --preserve-digests --quiet \
    --dest-cert-dir "$STATE_DIR/certs.d" --dest-registry-token "$TOKEN" \
    "containers-storage:$APP_LOCAL_IMAGE" "docker://$REGISTRY/$NS/$APP_IMAGESTREAM:$APP_TAG"
unset TOKEN
say "measured: copy $((SECONDS - T_COPY)) s"

REMOTE="$(istag_digest)"
[[ "$REMOTE" == "$LOCAL" ]] || die "the digest in the cluster ($REMOTE) differs from the local build ($LOCAL)"
printf '%s\n' "$LOCAL" >"$STATE_DIR/order-service.digest"
say "digest in the ImageStream equals the local build: $LOCAL"
