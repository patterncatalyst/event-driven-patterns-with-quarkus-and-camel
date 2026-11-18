#!/usr/bin/env bash
# Render the deck's figure PNGs from diagrams/*.svg and the two QR PNGs.
#
#   ./render-png.sh            render every figure and both QR codes into png/
#   ./render-png.sh --check    render into a temp dir and fail if any committed
#                              PNG differs from a fresh render
#   ./render-png.sh NAME ...   render only these figures (QR codes still made)
#
# cairosvg (via the vendored svg_to_png.py), segno and zxing-cpp run through
# `uv run --no-project --with`: nothing is added to a workspace or installed
# globally. The QR codes are decoded after they are written and the build fails
# unless the exact URLs come back.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
diagrams="$here/../diagrams"
CAIROSVG="2.9.1"
SEGNO="1.6.6"
PILLOW="13.0.0"
ZXING="3.1.1"
WIDTH=1600
SITE_URL="https://patterncatalyst.github.io/event-driven-patterns-with-quarkus-and-camel/"
HUB_URL="https://patterncatalyst.github.io/patterncatalyst-workshops-tutorials-list/"

# The figures the deck uses (spec names), from the plan's Deck design table.
figs=(
  00-running-example 00-order-lifecycle 00-compose-stack 00-telemetry-path 01-row-update-vs-event-append
  02-fetch-back 02-source-down 03-notification-vs-state-transfer 03-claim-check 04-keys-to-partitions
  04-resequencer 05-batch-fill 06-concurrency-cap 07-head-of-line 07-share-acquire-ack
  08-dual-write-gap 08-outbox-wal-router 09-wal-publication-slot 10-offset-vs-slot
  11-inbox-same-transaction 12-read-process-write 13-failure-classes
  14-aggregate-command-cycle 14-conflict-redecide 15-snapshot-plus-tail 16-command-query-split
  16-cross-topic-order 17-replay-vs-projection 18-upcast-on-read
  19-enricher-vs-table-join 20-event-vs-arrival-time 21-window-close-and-grace
  22-saga-choreography 23-three-ways 24-analyse-then-decide
  25-trace-waterfall 26-where-each-signal-lives 27-log-trace-roundtrip
  28-kraft-roles 29-one-direction 30-field-managers 31-admission 32-metrics-path
  33-scale-and-move 34-two-clusters 35-build-and-run
  aa-bite-cycle ab-same-config-two-targets ac-three-ways ae-stream-vs-batch
  af-roll-mid af-bg-cutover ag-write-forward-path ah-build-and-copy ai-replay-failed-index ai-kueue-objects
)

check=0
while [[ "${1:-}" == --* ]]; do
  case "$1" in
    --check) check=1; shift ;;
    *) echo "render-png: unknown option $1" >&2; exit 2 ;;
  esac
done
[[ $# -gt 0 ]] && figs=("$@")

out="$here/png"
if [[ $check -eq 1 ]]; then
  out="$(mktemp -d)"
  trap 'rm -rf "$out"' EXIT
fi
mkdir -p "$out"

status=0
report() {  # report NAME
  if [[ $check -eq 1 ]]; then
    if cmp -s "$out/$1.png" "$here/png/$1.png"; then echo "same   png/$1.png"
    else echo "DIFFER png/$1.png" >&2; status=1; fi
  else
    echo "wrote  png/$1.png"
  fi
}

for name in "${figs[@]}"; do
  svg="$diagrams/$name.svg"
  [[ -f "$svg" ]] || { echo "render-png: missing $svg" >&2; exit 1; }
  uv run --no-project --quiet --with "cairosvg==$CAIROSVG" \
    python -I "$here/svg_to_png.py" "$svg" "$out/$name.png" "$WIDTH"
  report "$name"
done

# QR codes: generate with segno, decode with zxing-cpp, require the exact URL.
uv run --no-project --quiet --with "segno==$SEGNO" --with "zxing-cpp==$ZXING" --with "pillow==$PILLOW" \
  python -I - "$out" "qr-site=$SITE_URL" "qr-hub=$HUB_URL" <<'PY'
import sys
import segno
import zxingcpp
from PIL import Image

out = sys.argv[1]
for spec in sys.argv[2:]:
    name, url = spec.split("=", 1)
    path = f"{out}/{name}.png"
    segno.make(url, error="m", micro=False).save(path, scale=12, border=4, kind="png")
    found = [r.text for r in zxingcpp.read_barcodes(Image.open(path))]
    if found != [url]:
        sys.exit(f"render-png: {name} decoded {found!r}, wanted {url!r}")
    print(f"decoded {name}: {url}")
PY
for name in qr-site qr-hub; do report "$name"; done
exit "$status"
