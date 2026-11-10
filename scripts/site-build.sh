#!/usr/bin/env bash
# Build (or serve) the Jekyll site in a ruby container; the host needs no Ruby.
#   scripts/site-build.sh            jekyll build into _site/
#   scripts/site-build.sh --serve    jekyll serve on http://127.0.0.1:4000/
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
IMAGE="docker.io/library/ruby:4.0.9"
ENGINE="${CONTAINER_ENGINE:-docker}"
mkdir -p vendor/bundle
common=(run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -e BUNDLE_PATH=vendor/bundle
        -e JEKYLL_ENV="${JEKYLL_ENV:-development}" -v "$PWD:/site:Z" -w /site)
if [[ "${1:-}" == "--serve" ]]; then
    exec "$ENGINE" "${common[@]}" -p 127.0.0.1:4000:4000 "$IMAGE" \
        bash -c 'bundle install && bundle exec jekyll serve --host 0.0.0.0 --port 4000 --force_polling'
fi
exec "$ENGINE" "${common[@]}" "$IMAGE" \
    bash -c 'bundle install && bundle exec jekyll build'
