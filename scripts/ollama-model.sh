#!/usr/bin/env bash
# Model volume for the Ollama examples. The volume edp-ollama-models is the only state that
# survives `docker compose down -v`; examples mount it read-only and never pull.
#   scripts/ollama-model.sh pull    create the volume if missing and pull the model into it
#   scripts/ollama-model.sh check   exit non-zero unless the model (manifest digest below) is present
set -euo pipefail

IMAGE="docker.io/ollama/ollama:0.40.16@sha256:910d0d8107b538fb0894e63163eb195de0cc7aefcbc666dc62d3b18f71124592"
VOLUME="edp-ollama-models"
MODEL="qwen3:4b-instruct-2507-q4_K_M"
DIGEST="0edcdef34593"
NAME="edp-ollama-model-$$"

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

start() {  # $1 = mount options
  docker run -d --name "$NAME" --memory 4g -v "$VOLUME:/root/.ollama$1" "$IMAGE" >/dev/null
  for _ in $(seq 1 60); do
    docker exec "$NAME" ollama list >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "ollama did not start" >&2
  return 1
}

case "${1:-}" in
  pull)
    docker volume inspect "$VOLUME" >/dev/null 2>&1 || docker volume create "$VOLUME" >/dev/null
    start ""
    docker exec "$NAME" ollama pull "$MODEL"
    docker exec "$NAME" ollama list
    ;;
  check)
    if ! docker volume inspect "$VOLUME" >/dev/null 2>&1; then
      echo "volume $VOLUME is missing: run scripts/ollama-model.sh pull" >&2
      exit 1
    fi
    start ":ro"
    if docker exec "$NAME" ollama list | awk -v m="$MODEL" -v d="$DIGEST" '$1 == m && $2 == d { f = 1 } END { exit !f }'; then
      echo "ok: $MODEL ($DIGEST) is in $VOLUME"
    else
      echo "$MODEL with digest $DIGEST is not in $VOLUME: run scripts/ollama-model.sh pull" >&2
      exit 1
    fi
    ;;
  *)
    echo "usage: $0 pull|check" >&2
    exit 2
    ;;
esac
