#!/usr/bin/env bash
# The same Kafka and PostgreSQL settings on two targets, read from the running systems.
#   ./verify.sh compose   starts Kafka and PostgreSQL from examples/_infra fresh, captures their effective settings
#   ./verify.sh k8s       creates the Kafka resource with two node pools and a CloudNativePG Cluster on fresh
#                         namespaces of the minikube profile, captures the same settings
#   ./verify.sh compare   compares the two captures against the allowlist of expected differences in configs.py
# Captures are written to target/captures (gitignored); compare runs no stack. An empty capture fails every check
# that reads it. DRIFT=partitions (demo code) starts the compose broker with num.partitions=6. KEEP=1 leaves the
# stack or the namespaces in place.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../_k8s/lib.sh
source ../_k8s/lib.sh

MODE="${1:-}"
CAP=target/captures
CFG=(python3 -I configs.py)
KAFKA_IMAGE_FILE=../_infra/compose-kafka.yaml
PG_IMAGE_FILE=../_infra/compose-postgres.yaml
DOC=../../_docs/ab-compose-to-kubernetes.md

usage() { echo "usage: ./verify.sh compose|k8s|compare" >&2; exit 2; }
file_image() { sed -n "s|^[[:space:]]*image:[[:space:]]*||p" "$1" | head -1; }
file_env() { sed -n "s|^[[:space:]]*-[[:space:]]*$2=||p" "$1" | head -1; }

# ---- the compose half ---------------------------------------------------------------------------

compose_kafka() { # compose_kafka <broker id>: the filtered settings of one broker; nothing for an id that is no broker
    docker exec edp-kafka /opt/kafka/bin/kafka-configs.sh --bootstrap-server localhost:9092 \
        --entity-type brokers --entity-name "$1" --describe --all 2>/dev/null | "${CFG[@]}" kafka
}
compose_pg() {
    "${CFG[@]}" sql | docker exec -i -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -At -v ON_ERROR_STOP=1 -f -
}
capture_until_complete() { # capture_until_complete <kind> <file> <command...>: retry a capture until it holds every key
    local kind="$1" out="$2"; shift 2
    "$@" > "$out" 2>/dev/null || true
    "${CFG[@]}" complete "$kind" "$out"
}
compose_image_tag_matches() { # the running broker's image is the tag in compose-kafka.yaml
    local want got
    want="$(file_image "$KAFKA_IMAGE_FILE")"
    got="$(docker inspect -f '{{.Config.Image}}' edp-kafka)"
    [[ -n "$want" && "$got" == "$want" ]] && printf 'kafka_version=%s\n' "${want##*:}" > "$CAP/compose/version.txt"
}
witness_rejects_empty() { # witness_rejects_empty <capture command...>: a node id that is no broker gives nothing, and nothing is rejected
    local out
    out="$(mktemp)"
    "$@" > "$out" 2>/dev/null || true
    [[ ! -s "$out" ]] && ! "${CFG[@]}" complete kafka "$out"
    local rc=$?
    rm -f "$out"
    return $rc
}

compose_half() {
    # shellcheck source=../../scripts/stack-guard.sh
    source ../../scripts/stack-guard.sh edp-ab || exit 1
    local files=(-f compose.yaml)
    [[ "${DRIFT:-}" == partitions ]] && files+=(-f drift.yaml)
    local COMPOSE=(docker compose "${files[@]}")
    compose_cleanup() {
        if [[ "${KEEP:-0}" == "1" ]]; then
            echo "KEEP=1: stack left running; run \`docker compose down -v --remove-orphans\` in this directory to remove it"
        else
            docker compose -f compose.yaml down -v --remove-orphans >/dev/null 2>&1 || true
        fi
    }
    trap compose_cleanup EXIT
    rm -rf "$CAP/compose"; mkdir -p "$CAP/compose"
    "${COMPOSE[@]}" down -v --remove-orphans
    "${COMPOSE[@]}" up -d --force-recreate --renew-anon-volumes --wait

    local id wrong=0 d="$CAP/compose"
    id="$(file_env "$KAFKA_IMAGE_FILE" KAFKA_NODE_ID)"
    precondition "Kafka answers and its capture holds all 13 settings" poll 60 capture_until_complete kafka "$d/kafka.txt" compose_kafka "$id"
    precondition "PostgreSQL answers and its capture holds all 5 settings" poll 60 capture_until_complete pg "$d/pg.txt" compose_pg
    check "a node id that is not a broker gives an empty capture, and the capture check rejects it" witness_rejects_empty compose_kafka "$wrong"
    check "the running broker's image is the tag in compose-kafka.yaml" compose_image_tag_matches
    check "every setting compose-kafka.yaml sets is what the broker reports" "${CFG[@]}" explicit compose-kafka "$KAFKA_IMAGE_FILE" set "$d/kafka.txt"
    check "every setting it leaves unset is the Kafka default" "${CFG[@]}" explicit compose-kafka "$KAFKA_IMAGE_FILE" unset "$d/kafka.txt"
    check "every parameter the postgres command line sets is what PostgreSQL reports" "${CFG[@]}" explicit start-sh ../_infra/postgres/start.sh set "$d/pg.txt"
    check "the parameter it leaves unset is the PostgreSQL default" "${CFG[@]}" explicit start-sh ../_infra/postgres/start.sh unset "$d/pg.txt"
    check "PostgreSQL's major.minor is the image tag's" "${CFG[@]}" version "$d/pg.txt" "$(file_image "$PG_IMAGE_FILE")"
    summary
}

# ---- the Kubernetes half ------------------------------------------------------------------------

k8s_kafka() { # k8s_kafka <pod> <broker id>
    kafka_raw_on "$1" kafka-configs.sh --bootstrap-server localhost:9092 \
        --entity-type brokers --entity-name "$2" --describe --all 2>/dev/null | "${CFG[@]}" kafka
}
k8s_pg() { "${CFG[@]}" sql | psql_exec edp -; }
kafka_version_reported() { # the Kafka resource reports the version it was asked for; kept for the comparison
    local want got
    want="$(kc -n "$NS_KAFKA" get kafka edp -o jsonpath='{.spec.kafka.version}')"
    got="$(kc -n "$NS_KAFKA" get kafka edp -o jsonpath='{.status.kafkaVersion}')"
    [[ -n "$want" && "$got" == "$want" ]] && printf 'kafka_version=%s\n' "$got" > "$CAP/k8s/version.txt"
}
node_id() { kc -n "$NS_KAFKA" get kafkanodepool "$1" -o jsonpath='{.status.nodeIds[0]}'; }

k8s_half() {
    trap k8s_teardown EXIT
    rm -rf "$CAP/k8s"; mkdir -p "$CAP/k8s"
    k8s_fresh .

    local d="$CAP/k8s" bid cid
    bid="$(node_id broker)"; cid="$(node_id controller)"
    kc -n "$NS_KAFKA" get kafka edp -o json > "$d/kafka-resource.json"
    kc kustomize ../_k8s/postgres | kc create --dry-run=client -f - -o json > "$d/cluster-manifest.json"
    precondition "Kafka answers and its capture holds all 13 settings" poll 60 capture_until_complete kafka "$d/kafka.txt" k8s_kafka "edp-broker-$bid" "$bid"
    precondition "PostgreSQL answers and its capture holds all 5 settings" poll 60 capture_until_complete pg "$d/pg.txt" k8s_pg
    check "a node id that is not a broker gives an empty capture, and the capture check rejects it" witness_rejects_empty k8s_kafka "edp-broker-$bid" "$cid"
    check "the Kafka resource reports the version it was asked for" kafka_version_reported
    check "every setting the Kafka resource sets is what the broker reports" "${CFG[@]}" explicit kafka-resource "$d/kafka-resource.json" set "$d/kafka.txt"
    check "every setting it leaves unset is the Kafka default" "${CFG[@]}" explicit kafka-resource "$d/kafka-resource.json" unset "$d/kafka.txt"
    check "the Cluster manifest sets none of the four PostgreSQL parameters compared" "${CFG[@]}" explicit cluster "$d/cluster-manifest.json" set "$d/pg.txt"
    check "every parameter reports the CloudNativePG or PostgreSQL default" "${CFG[@]}" explicit cluster "$d/cluster-manifest.json" unset "$d/pg.txt"
    check "PostgreSQL's major.minor is the Cluster image tag's" "${CFG[@]}" version "$d/pg.txt" "$(kc -n "$NS_APPS" get cluster.postgresql.cnpg.io edp-pg -o jsonpath='{.spec.imageName}')"
    summary
}

# ---- the comparison -----------------------------------------------------------------------------

compare_half() {
    local c="$CAP/compose" k="$CAP/k8s" key
    precondition "both Kafka captures exist and hold all 13 settings" bash -c \
        'python3 -I configs.py complete kafka "$1/kafka.txt" && python3 -I configs.py complete kafka "$2/kafka.txt"' _ "$c" "$k"
    precondition "both PostgreSQL captures exist and hold all 5 settings" bash -c \
        'python3 -I configs.py complete pg "$1/pg.txt" && python3 -I configs.py complete pg "$2/pg.txt"' _ "$c" "$k"
    while read -r key; do
        check "$key is equal on both targets" "${CFG[@]}" equal "$key" "$c" "$k"
    done < <("${CFG[@]}" keys equal)
    while read -r key; do
        check "$key differs as expected" "${CFG[@]}" differs "$key" "$c" "$k"
    done < <("${CFG[@]}" keys allowed)
    check "no other setting differs" "${CFG[@]}" others "$c" "$k"
    check "the appendix explains every allowed difference" "${CFG[@]}" documented "$DOC"
    summary
}

case "$MODE" in
    compose) compose_half ;;
    k8s) k8s_half ;;
    compare) compare_half ;;
    *) usage ;;
esac
