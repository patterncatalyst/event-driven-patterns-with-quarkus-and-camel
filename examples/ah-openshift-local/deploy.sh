#!/usr/bin/env bash
# Deploys order-service by digest and then the outbox connector. The digest is the one build-and-copy.sh wrote; an
# overlay under .state/ sets it on the Deployment, so no tracked file changes. The connector comes after
# the application because the application's Flyway migration creates the table the connector captures.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh --crc
source ./crc-check.sh
source ./lib.sh
T0=$SECONDS

[[ -s "$STATE_DIR/order-service.digest" ]] || die "no digest: run ./build-and-copy.sh first"
DIGEST="$(<"$STATE_DIR/order-service.digest")"
[[ "$DIGEST" =~ ^sha256:[0-9a-f]{64}$ ]] || die "the recorded digest is malformed"
[[ "$(istag_digest)" == "$DIGEST" ]] || die "the ImageStream no longer holds the recorded digest"

OVERLAY="$STATE_DIR/overlay"
mkdir -p "$OVERLAY"
trap 'rm -rf "$OVERLAY"' EXIT
cat >"$OVERLAY/kustomization.yaml" <<YAML
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../../deploy
images:
  - name: $APP_INTERNAL_REPO
    digest: $DIGEST
YAML
say "== order-service by digest"
oc apply --server-side --field-manager=edp-ah -n "$NS" -k "$OVERLAY" >/dev/null
oc rollout status deployment/order-service -n "$NS" --timeout=300s
say "== outbox connector"
oc apply --server-side --field-manager=edp-ah -n "$NS" -k connector >/dev/null
connector_ready() { [[ "$(oc get kafkaconnector order-outbox -n "$NS" -o 'jsonpath={.status.conditions[?(@.type=="Ready")].status}')" == True ]]; }
wait_for 300 "KafkaConnector order-outbox Ready" connector_ready
say "deploy: done in $((SECONDS - T0)) s"
