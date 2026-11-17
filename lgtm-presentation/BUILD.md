# Building the companion deck

This directory holds the companion slide deck for *Event-Driven Patterns with
Quarkus and Camel*, built with the `lgtm-presentation` skill's pptxgenjs engine.
`deck.js` loads `sections/NN-*.js` in name order, then adds the QR slide last.
Code slides are read from `examples/` by path and line range, so a slide is
byte-exact with its source.

## Pins

| Item | Version | Notes |
| --- | --- | --- |
| pptxgenjs | 4.0.1, exact | npm `latest`; no prerelease |
| image-size | 2.1.0, `overrides` only | clears the advisories against the 1.2.1 that pptxgenjs pulls in (`npm audit` reports 2 high without it); pptxgenjs never loads it |
| cairosvg | 2.9.1 | `uv run --no-project --with`; never added to a workspace |
| segno | 1.6.6 | QR generation, BSD-3-Clause, `uv run --no-project --with` |
| zxing-cpp | 3.1.1 | QR decode check, Apache-2.0, `uv run --no-project --with` |
| python-pptx | 1.0.2 | text extraction and checks, `uv run --no-project --with` |
| Node.js, npm | 22, 10 | host tools, not installed by this repo |

`package-lock.json` is committed (lockfileVersion 3). `npm audit` reports 0
vulnerabilities.

## Install and build

From this directory, and only here:

```bash
npm ci
node -e "console.log(require.resolve('pptxgenjs'))"   # must point under ./node_modules
./render-png.sh                                       # figure PNGs and both QR PNGs into png/
node deck.js                                          # writes Event-Driven-Patterns-with-Quarkus-and-Camel.pptx
node deck.js --out "$TMPDIR/deck.pptx"                # or anywhere else
```

Never `npm install -g`, never set `NODE_PATH`. The build must load the copy in
`node_modules/` that the lockfile pins. With no `sections/*.js` the build still
succeeds and writes a two-slide skeleton (cover and QR).

## The helper

`deck-helpers.js` is the skill's `scripts/deck-helpers.js` (md5
`ce44b9de9820ba7e115d729c5de9e63a`) with one local delta: its eight em-dash
lines are reworded, so the repo-wide dash gate passes without an exclusion. No
code changed. The new md5 is `d816b5e4dd0555d4ff31bac21b21ae64`.

| Line | What changed |
| --- | --- |
| 1 | header comment: the dash after the file name became a colon |
| 66 | default `pres.title`: the dash became a colon |
| 80 | logo comment: dash to comma |
| 137, 178, 234, 311, 341 | comments: dash to colon |

`deck.js` overrides `pres.title`, `pres.subject`, `pres.author` and
`pres.company` after `newDeck()`, so the helper's defaults never ship.

## The shared module

`deck-lib.js` exports `createDeck(pres)`, which returns the slide helpers bound
to one presentation. Every slide has an eyebrow and a title, and notes above a
word floor (90 on content slides, 45 on the cover, dividers and the QR slide).

| Export | Purpose |
| --- | --- |
| `S()` | a content slide with footer and page number |
| `divider(title, subtitle, text, eyebrow)` | section divider on an `AB0000` background; the section name, never a number |
| `bullets`, `bulletSlide` | bold-subject bullets, alone or on a titled slide |
| `tableSlide` | header row plus striped body rows, first column bold |
| `codeSlide(eyebrow, title, lang, file, first, last, anchor, caption, text)` | listing read from `examples/<file>` by line range; first line asserted; common leading indentation removed, every line otherwise byte-exact; at most 22 lines and 120 columns; font at least 11 pt or the build throws; language chip JAVA, YAML, SQL or BASH; caption names the service and file |
| `figSlide(eyebrow, title, png, caption, text)` | PNG from `png/`, aspect kept |
| `qrSlide(items, text)` | the closing slide, called by `deck.js` |
| `notes(slide, text, floor, label)`, `words` | speaker notes with a word-count floor |
| `bump()` | count a slide `deck.js` adds itself, such as the cover |
| `finish(out)` | assert the notes floors and that notes count equals slide count, then write the file |

A section module is `module.exports = function (lib) { ... }` and adds slides
only through these helpers.

## Figures and QR codes

`render-png.sh` renders the figure list (the plan's Deck design table) from
`../diagrams/<name>.svg` to `png/<name>.png` at width 1600, through the vendored
`svg_to_png.py` (a copy of the lgtm-diagram-generator skill's script, with a
provenance header). It also writes `png/qr-site.png` and `png/qr-hub.png` with
segno and decodes both with zxing-cpp; the build fails unless the exact URLs
come back:

- `https://patterncatalyst.github.io/event-driven-patterns-with-quarkus-and-camel/`
- `https://patterncatalyst.github.io/patterncatalyst-workshops-tutorials-list/`

`./render-png.sh --check` renders into a temporary directory and compares each
committed PNG; any difference means the SVG changed or the PNG is stale. A
figure that is wrong is fixed in its spec under `diagrams/specs/`, never
redrawn for the deck.

## Determinism

pptxgenjs writes the same bytes for the same input except the `created` and
`modified` stamps in `docProps/core.xml`. Image paths in the slide XML are
relative (`./png/...`), so no absolute location is embedded. Proof, from the
repo root:

```bash
node lgtm-presentation/deck.js --out "$TMPDIR/one.pptx"
sleep 2
node lgtm-presentation/deck.js --out "$TMPDIR/two.pptx"
mkdir "$TMPDIR/one" "$TMPDIR/two"
unzip -q "$TMPDIR/one.pptx" -d "$TMPDIR/one"; unzip -q "$TMPDIR/two.pptx" -d "$TMPDIR/two"
diff -rq "$TMPDIR/one" "$TMPDIR/two"    # only docProps/core.xml differs
```

Creator, last-modified-by and company come out as `PatternCatalyst` from the
properties `deck.js` sets; `docProps` needs no patching.

## Quality checks

`qa/` and `*.pdf` are ignored scratch output. Render under `$TMPDIR`, never in
this directory:

```bash
soffice -env:UserInstallation=file://$TMPDIR/lo-qa --headless \
  --convert-to pdf --outdir "$TMPDIR/qa" Event-Driven-Patterns-with-Quarkus-and-Camel.pptx
pdffonts "$TMPDIR/qa/Event-Driven-Patterns-with-Quarkus-and-Camel.pdf"
pdftoppm -jpeg -r 110 "$TMPDIR/qa/Event-Driven-Patterns-with-Quarkus-and-Camel.pdf" "$TMPDIR/qa/p"
python3 notes.py Event-Driven-Patterns-with-Quarkus-and-Camel.pptx > "$TMPDIR/qa/notes.txt"
python3 notes.py --slides Event-Driven-Patterns-with-Quarkus-and-Camel.pptx > "$TMPDIR/qa/slides.txt"
```

`notes.py` extracts the text because pptxgenjs writes a notes slide for every
slide, empty or not, so counting notes files proves nothing.

## Making an edit

1. Edit the slide in `sections/NN-*.js` (never the `.pptx`).
2. Rebuild: `node deck.js`.
3. Refresh the text copy: `python3 scripts/deck_text.py --write` (from the repo root).
4. Record the change in `edits/` (what, why, the slide numbers touched).
