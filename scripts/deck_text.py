#!/usr/bin/env python3
"""Extract the text of every deck (.pptx) in lgtm-presentation/ into Markdown copies under
lgtm-presentation/text/<deck-stem>.md.

Adapted from cloud-native-design-patterns scripts/deck_text.py. Standard library only (zipfile and
xml.etree), so it runs unchanged in CI. The decks here are written by pptxgenjs, which emits no
title placeholders, so the title and eyebrow are found from font size and shape order.

Usage:
    deck_text.py --write [--root DIR]        regenerate lgtm-presentation/text/*.md
    deck_text.py --check [--root DIR]        regenerate in memory and compare; exit 1 on drift, a
                                             missing or extra text copy, or a slide without notes
    deck_text.py --pptx PATH [--name STEM] [--root DIR]
                                             check one built deck (for example a fresh build in a
                                             temporary directory) against the committed text copy
                                             text/<STEM>.md; STEM defaults to the file's stem, with
                                             any directory stripped
    deck_text.py --self-test                 build a small deck in memory and check the extraction
    deck_text.py                             print this usage

Output format
    # <deck name>            then a "Source: <name>.pptx · N slides" line
    ## [NNN] EYEBROW · Title    one section per slide, in presentation order
    body paragraphs          shape order in spTree, groups recursed, tables flattened row by row
                             (cells joined with " | "), bullets as "- " / "1. "
    [chip] JAVA              the language chip that sits above a code box
    ```java ... ```         code boxes, tagged with the chip language
    p. 12                    the static page-number text box
    > notes                  speaker notes (notesSlide body placeholder), one ">" block

Heuristics (pptxgenjs output)
    Title    among the non-monospace text shapes whose top edge is in the upper 2 inches, the one
             with the largest run font size (ties: first in shape order), provided it is 24 pt or
             more; otherwise (cover, dividers) the largest on the slide. Multi-line titles are
             joined with a space.
    Eyebrow  the first text shape before the title that is a single line of at most 60
             characters and a smaller font than the title (the red kicker line, or the part code
             on a divider).
    Code     a shape whose non-empty runs are all monospace (font name matches Mono, Courier,
             Consolas, Menlo, Monaco). A single-line monospace shape of at most 11 pt and 60
             characters is a chip, not code; its language is the text before " · ", lower cased,
             and tags the next code box.
    Page     a shape whose whole text is 1 to 3 digits at 11 pt or less.

Output is deterministic: normalised whitespace, LF endings, trailing newline. Any `{{` or `{%` is
wrapped in {% raw %} so a stray include of these files cannot be read as Liquid.
"""
from __future__ import annotations

import argparse
import difflib
import io
import posixpath
import re
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

A = "http://schemas.openxmlformats.org/drawingml/2006/main"
P = "http://schemas.openxmlformats.org/presentationml/2006/main"
R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

MONO_RE = re.compile(r"mono|courier|consolas|menlo|monaco", re.I)
REPO = Path(__file__).resolve().parent.parent
DEFAULT_ROOT = REPO / "lgtm-presentation"
TITLE_BAND_EMU = 2 * 914400  # a title box starts in the upper 2 inches


def q(ns: str, tag: str) -> str:
    return f"{{{ns}}}{tag}"


def norm(s: str) -> str:
    return re.sub(r"[ \t ]+", " ", s).strip()


def guard_liquid(text: str) -> str:
    if "{{" in text or "{%" in text:
        return "{% raw %}" + text + "{% endraw %}"
    return text


def fence(code: str, lang: str = "") -> list[str]:
    ticks = "```"
    while ticks in code:
        ticks += "`"
    out = [ticks + lang] + code.split("\n") + [ticks]
    if "{{" in code or "{%" in code:
        out = ["{% raw %}"] + out + ["{% endraw %}"]
    return out


def rels_of(z: zipfile.ZipFile, part: str) -> dict[str, tuple[str, str]]:
    d, b = posixpath.split(part)
    rp = posixpath.join(d, "_rels", b + ".rels")
    out: dict[str, tuple[str, str]] = {}
    if rp not in z.namelist():
        return out
    for rel in ET.fromstring(z.read(rp)):
        tgt = rel.get("Target", "")
        if rel.get("TargetMode") != "External":
            tgt = posixpath.normpath(posixpath.join(d, tgt)) if not tgt.startswith("/") else tgt[1:]
        out[rel.get("Id", "")] = (rel.get("Type", "").rsplit("/", 1)[-1], tgt)
    return out


class Para:
    __slots__ = ("text", "mono", "size", "bullet", "level")

    def __init__(self, text, mono, size, bullet, level):
        self.text, self.mono, self.size, self.bullet, self.level = text, mono, size, bullet, level


def para_of(p: ET.Element) -> Para:
    parts: list[str] = []
    mono_flags: list[bool] = []
    size = 0
    for el in p:
        if el.tag == q(A, "r") or el.tag == q(A, "fld"):
            t = el.find(q(A, "t"))
            txt = (t.text or "") if t is not None else ""
            parts.append(txt)
            if txt.strip():
                rpr = el.find(q(A, "rPr"))
                font = None
                if rpr is not None:
                    lat = rpr.find(q(A, "latin"))
                    font = lat.get("typeface") if lat is not None else None
                    if rpr.get("sz"):
                        size = max(size, int(rpr.get("sz")))
                mono_flags.append(bool(font and MONO_RE.search(font)))
        elif el.tag == q(A, "br"):
            parts.append("\n")
    bullet = ""
    ppr = p.find(q(A, "pPr"))
    level = 0
    if ppr is not None:
        level = int(ppr.get("lvl") or 0)
        if ppr.find(q(A, "buChar")) is not None:
            bullet = "- "
        elif ppr.find(q(A, "buAutoNum")) is not None:
            bullet = "1. "
    return Para("".join(parts), bool(mono_flags) and all(mono_flags), size, bullet, level)


class Item:
    """One text unit from a slide: kind in text|code|chip|table|page."""

    def __init__(self, kind, lines, size=0, top=0, title_ph=False):
        self.kind, self.lines, self.size, self.top, self.title_ph = kind, lines, size, top, title_ph


def text_lines(paras: list[Para]) -> list[str]:
    out = []
    for p in paras:
        lines = [norm(x) for x in p.text.split("\n")]
        lines = [x for x in lines if x]
        if not lines:
            continue
        pre = "  " * p.level + p.bullet if p.bullet else ""
        out.append(pre + "\n".join(lines))
    return out


def top_of(el: ET.Element) -> int:
    off = el.find(f"{q(P, 'spPr')}/{q(A, 'xfrm')}/{q(A, 'off')}")
    return int(off.get("y", "0")) if off is not None else 0


def shape_items(el: ET.Element, items: list[Item]) -> None:
    if el.tag == q(P, "grpSp"):
        for c in el:
            shape_items(c, items)
        return
    if el.tag == q(P, "graphicFrame"):
        tbl = el.find(f".//{q(A, 'tbl')}")
        if tbl is not None:
            rows = []
            for tr in tbl.findall(q(A, "tr")):
                cells = []
                for tc in tr.findall(q(A, "tc")):
                    tx = tc.find(q(A, "txBody"))
                    ps = [para_of(p) for p in tx.findall(q(A, "p"))] if tx is not None else []
                    cells.append(norm(" ".join(text_lines(ps)).replace("\n", " ")))
                rows.append(" | ".join(cells))
            items.append(Item("table", rows))
        return
    if el.tag != q(P, "sp"):
        return
    tx = el.find(q(P, "txBody"))
    if tx is None:
        return
    paras = [para_of(p) for p in tx.findall(q(A, "p"))]
    nonempty = [p for p in paras if p.text.strip()]
    if not nonempty:
        return
    ph = el.find(f"{q(P, 'nvSpPr')}/{q(P, 'nvPr')}/{q(P, 'ph')}")
    title_ph = ph is not None and ph.get("type") in ("title", "ctrTitle")
    size = max(p.size for p in nonempty)
    top = top_of(el)
    full = "\n".join(p.text.strip("\n") for p in nonempty)
    if all(p.mono for p in nonempty):
        one_line = len(nonempty) == 1 and "\n" not in nonempty[0].text.strip()
        if one_line and size <= 1100 and len(full) <= 60:
            items.append(Item("chip", [norm(full)], size, top))
        else:
            code = "\n".join(re.sub(r"[ \t]+$", "", p.text.replace(" ", " ").rstrip("\n")) for p in nonempty)
            items.append(Item("code", code.split("\n"), size, top))
        return
    if len(nonempty) == 1 and size <= 1100 and re.fullmatch(r"\d{1,3}", nonempty[0].text.strip()):
        items.append(Item("page", [nonempty[0].text.strip()], size, top))
        return
    items.append(Item("text", text_lines(paras), size, top, title_ph))


def lang_of(chip: str) -> str:
    head = chip.split("·")[0].strip().lower()
    return re.sub(r"[^a-z0-9+#.-]", "", head)


def find_title(items: list[Item]):
    ph = next((i for i, it in enumerate(items) if it.kind == "text" and it.title_ph), None)
    if ph is not None:
        return ph
    texts = [(i, it) for i, it in enumerate(items) if it.kind == "text"]
    if not texts:
        return None
    band = [(i, it) for i, it in texts if it.top < TITLE_BAND_EMU]
    # a content slide has a title of 24 pt or more in the band; the cover, dividers and the
    # closing slide put theirs lower, so the largest text on the slide wins there
    pool = band if band and max(it.size for _, it in band) >= 2400 else texts
    best_i, best = pool[0]
    for i, it in pool[1:]:
        if it.size > best.size:
            best_i, best = i, it
    return best_i


def slide_section(z: zipfile.ZipFile, part: str, idx: int, rels: dict) -> tuple[str, bool]:
    root = ET.fromstring(z.read(part))
    tree = root.find(f"{q(P, 'cSld')}/{q(P, 'spTree')}")
    items: list[Item] = []
    for c in tree:
        shape_items(c, items)
    title_i = find_title(items)
    title = norm(" ".join(items[title_i].lines)) if title_i is not None else "(untitled)"
    eyebrow_i = None
    if title_i is not None:
        for i, it in enumerate(items[:title_i]):
            if it.kind == "text" and len(it.lines) == 1 and "\n" not in it.lines[0] \
                    and len(it.lines[0]) <= 60 and 0 < it.size < items[title_i].size:
                eyebrow_i = i
                break
    head = f"{items[eyebrow_i].lines[0]} · {title}" if eyebrow_i is not None else title
    out = [f"## [{idx:03d}] {head}", ""]
    pending_chip = None
    for i, it in enumerate(items):
        if i == title_i or i == eyebrow_i:
            continue
        if it.kind == "chip":
            if pending_chip:
                out += [guard_liquid("[chip] " + pending_chip), ""]
            pending_chip = it.lines[0]
        elif it.kind == "code":
            lang = lang_of(pending_chip) if pending_chip else ""
            if pending_chip:
                out += [guard_liquid("[chip] " + pending_chip), ""]
                pending_chip = None
            out += fence("\n".join(it.lines), lang) + [""]
        elif it.kind == "page":
            out += [f"p. {it.lines[0]}", ""]
        else:
            for ln in it.lines:
                out += [guard_liquid(ln), ""]
    if pending_chip:
        out += [guard_liquid("[chip] " + pending_chip), ""]
    notes_text = ""
    for typ, tgt in rels.values():
        if typ == "notesSlide" and tgt in z.namelist():
            nroot = ET.fromstring(z.read(tgt))
            for sp in nroot.iter(q(P, "sp")):
                ph = sp.find(f"{q(P, 'nvSpPr')}/{q(P, 'nvPr')}/{q(P, 'ph')}")
                if ph is not None and ph.get("type") == "body":
                    tx = sp.find(q(P, "txBody"))
                    notes_text = "\n".join(text_lines([para_of(p) for p in tx.findall(q(A, "p"))]))
                    break
    if notes_text:
        nl = []
        for para in notes_text.split("\n"):
            nl += ["> " + guard_liquid(para), ">"]
        out += nl[:-1] + [""]
    tight = []
    for i, ln in enumerate(out):
        if ln == "" and tight and i + 1 < len(out) and re.match(r"\s*(- |\d+\. )", tight[-1]) \
                and re.match(r"\s*(- |\d+\. )", out[i + 1]):
            continue
        tight.append(ln)
    return "\n".join(tight), bool(notes_text)


def pptx_to_md(src, name: str) -> tuple[str, list[int]]:
    """Return (markdown, [slide numbers without notes]). src is a path or a file object."""
    with zipfile.ZipFile(src) as z:
        pres = ET.fromstring(z.read("ppt/presentation.xml"))
        prels = rels_of(z, "ppt/presentation.xml")
        sections, missing, n = [], [], 0
        for sid in pres.find(q(P, "sldIdLst")):
            n += 1
            _, part = prels[sid.get(q(R, "id"))]
            sec, has = slide_section(z, part, n, rels_of(z, part))
            sections.append(sec)
            if not has:
                missing.append(n)
    head = [f"# {name}", "", f"Source: {name}.pptx · {n} slides", ""]
    return "\n".join(head) + "\n" + "\n".join(sections).rstrip("\n") + "\n", missing


def compare(outdir: Path, name: str, md: str) -> bool:
    """Print a diff and return False when text/<name>.md is absent or differs."""
    target = outdir / f"{name}.md"
    if not target.is_file():
        print(f"MISSING text copy: {target}")
        return False
    cur = target.read_text(encoding="utf-8")
    if cur == md:
        return True
    diff = list(difflib.unified_diff(cur.splitlines(), md.splitlines(),
                                     f"text/{name}.md (committed)", f"text/{name}.md (regenerated)",
                                     lineterm="", n=1))
    print(f"STALE text copy: {target} ({len(diff)} diff lines; first 40 follow)")
    print("\n".join(diff[:40]))
    return False


def report_notes(name: str, missing: list[int]) -> bool:
    if missing:
        print(f"NOTES missing in {name}: slides {missing}")
        return False
    return True


# ---------------------------------------------------------------- self test

def _sp(sid, x, y, runs, ph="", bullet=False):
    """One text shape in the form pptxgenjs writes: runs are (text, font, size_pt)."""
    paras = []
    for run in runs:
        text, font, sz = run
        bu = '<a:buChar char="&#9679;"/>' if bullet else "<a:buNone/>"
        paras.append(
            f'<a:p><a:pPr algn="l">{bu}</a:pPr><a:r><a:rPr lang="en-US" sz="{sz * 100}">'
            f'<a:latin typeface="{font}"/></a:rPr><a:t>{text}</a:t></a:r></a:p>')
    return (f'<p:sp><p:nvSpPr><p:cNvPr id="{sid}" name="Text {sid}"/><p:cNvSpPr/><p:nvPr>{ph}</p:nvPr></p:nvSpPr>'
            f'<p:spPr><a:xfrm><a:off x="0" y="{int(y * 914400)}"/><a:ext cx="100" cy="100"/></a:xfrm></p:spPr>'
            f'<p:txBody><a:bodyPr/><a:lstStyle/>{"".join(paras)}</p:txBody></p:sp>')


def build_fixture() -> bytes:
    ns = f'xmlns:a="{A}" xmlns:p="{P}" xmlns:r="{R}"'
    body = {
        1: [_sp(2, 0, 2.3, [("PART 1", "Red Hat Display", 22)]),
            _sp(3, 0, 2.8, [("Events and state", "Red Hat Display", 44)])],
        2: [_sp(2, 0, 7, [("2", "Red Hat Text", 10)]),
            _sp(3, 0, 0.4, [("WHY EVENTS", "Red Hat Display", 12)]),
            _sp(4, 0, 0.7, [("Decouple the producer", "Red Hat Display", 30)]),
            _sp(5, 0, 1.8, [("Producers do not wait", "Red Hat Text", 18),
                            ("Consumers replay", "Red Hat Text", 18)], bullet=True)],
        3: [_sp(2, 0, 7, [("3", "Red Hat Text", 10)]),
            _sp(3, 0, 0.4, [("OUTBOX", "Red Hat Display", 12)]),
            _sp(4, 0, 0.7, [("One transaction", "Red Hat Display", 30)]),
            _sp(5, 0, 1.0, [("JAVA", "Red Hat Mono", 11)]),
            _sp(6, 0, 1.8, [("@Transactional", "Red Hat Mono", 18), ("void save() {}", "Red Hat Mono", 18)])],
    }
    notes = {1: "Divider notes.", 2: "Notes for two.", 3: "Notes for three."}
    files = {
        "[Content_Types].xml": "<Types/>",
        "ppt/presentation.xml": f'<p:presentation {ns}><p:sldIdLst>'
        + "".join(f'<p:sldId id="{255 + n}" r:id="rId{n}"/>' for n in body) + "</p:sldIdLst></p:presentation>",
        "ppt/_rels/presentation.xml.rels": '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        + "".join(f'<Relationship Id="rId{n}" Type="{R}/slide" Target="slides/slide{n}.xml"/>' for n in body)
        + "</Relationships>",
    }
    for n, shapes in body.items():
        files[f"ppt/slides/slide{n}.xml"] = (
            f'<p:sld {ns}><p:cSld><p:spTree><p:nvGrpSpPr/><p:grpSpPr/>{"".join(shapes)}</p:spTree></p:cSld></p:sld>')
        files[f"ppt/slides/_rels/slide{n}.xml.rels"] = (
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            f'<Relationship Id="rId1" Type="{R}/notesSlide" Target="../notesSlides/notesSlide{n}.xml"/></Relationships>')
        files[f"ppt/notesSlides/notesSlide{n}.xml"] = (
            f'<p:notes {ns}><p:cSld><p:spTree>'
            + _sp(2, 0, 0, [("", "Red Hat Text", 12)], ph='<p:ph type="sldNum"/>')
            + _sp(3, 0, 1, [(notes[n], "Red Hat Text", 12)], ph='<p:ph type="body" idx="1"/>')
            + "</p:spTree></p:cSld></p:notes>")
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        for k, v in files.items():
            z.writestr(k, v)
    return buf.getvalue()


SELF_TEST_EXPECT = """# Fixture

Source: Fixture.pptx · 3 slides

## [001] PART 1 · Events and state

> Divider notes.

## [002] WHY EVENTS · Decouple the producer

p. 2

- Producers do not wait
- Consumers replay

> Notes for two.

## [003] OUTBOX · One transaction

p. 3

[chip] JAVA

```java
@Transactional
void save() {}
```

> Notes for three.
"""


def self_test() -> int:
    md, missing = pptx_to_md(io.BytesIO(build_fixture()), "Fixture")
    again, _ = pptx_to_md(io.BytesIO(build_fixture()), "Fixture")
    ok = True
    if md != SELF_TEST_EXPECT:
        ok = False
        print("\n".join(difflib.unified_diff(SELF_TEST_EXPECT.splitlines(), md.splitlines(),
                                             "expected", "got", lineterm="")))
    if md != again:
        ok = False
        print("extraction is not deterministic")
    if missing:
        ok = False
        print(f"fixture slides without notes: {missing}")
    print("deck_text --self-test:", "ok" if ok else "FAILED")
    return 0 if ok else 1


# ---------------------------------------------------------------- driver

def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(
        description="Extract deck text to lgtm-presentation/text/*.md",
        usage="deck_text.py (--write | --check | --pptx PATH [--name STEM] | --self-test) [--root DIR]")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--pptx", type=Path, help="check this deck against the committed text copy")
    ap.add_argument("--name", help="text copy stem for --pptx (default: the file's stem)")
    ap.add_argument("--self-test", action="store_true")
    ap.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="the lgtm-presentation directory")
    args = ap.parse_args(argv)
    if args.self_test:
        return self_test()
    if not (args.write or args.check or args.pptx):
        print(__doc__)
        return 0
    outdir = args.root / "text"
    if args.pptx:
        if not args.pptx.is_file():
            print(f"deck_text: {args.pptx} not found", file=sys.stderr)
            return 2
        name = args.name or args.pptx.stem
        md, missing = pptx_to_md(args.pptx, name)
        rc = 0 if compare(outdir, name, md) else 1
        if not report_notes(name, missing):
            rc = 1
        print("deck_text --pptx:", "FAILED" if rc else "ok")
        return rc
    files, missing = {}, {}
    for f in sorted(args.root.glob("*.pptx")):
        files[f.stem], missing[f.stem] = pptx_to_md(f, f.stem)
    if args.write:
        outdir.mkdir(exist_ok=True)
        for p in outdir.glob("*.md"):
            if p.stem not in files:
                p.unlink()
        for name, md in sorted(files.items()):
            (outdir / f"{name}.md").write_bytes(md.encode("utf-8"))
            print(f"wrote text/{name}.md ({len(md.encode('utf-8'))} bytes)")
        return 0
    rc = 0
    on_disk = {p.stem for p in outdir.glob("*.md")} if outdir.is_dir() else set()
    for name in sorted(set(files) | on_disk):
        if name not in files:
            print(f"EXTRA text copy (no source deck): {outdir / (name + '.md')}")
            rc = 1
        elif not compare(outdir, name, files[name]):
            rc = 1
    for name, miss in sorted(missing.items()):
        if not report_notes(name, miss):
            rc = 1
    print("deck_text --check:", "FAILED" if rc else "ok")
    return rc


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
