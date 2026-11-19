#!/usr/bin/env python3
"""Static content checks for the book. Python 3, PyYAML only.

    python3 scripts/validate.py              check the tree, exit 1 on any failure
    python3 scripts/validate.py --self-test  plant defects in a temp tree and assert each is caught

Directories may be empty or missing; checks that have nothing to look at pass.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import html
import posixpath
import xml.dom.minidom
import zipfile
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import host_ids  # noqa: E402  (rules read host values at run time; none are stored)

MAX_WORDS = 2500
MAX_BRO_WORDS = 250  # soft cap (warning), owner 2026-11-13
MAX_BRO_BULLETS = 3
BRO_HEAD = "## Build, run, observe"
CHECKNUM_RE = re.compile(r"\bchecks? \d+|\b\d+ of \d+ runs\b|\bin \d+ runs\b", re.I)
CALLOUT = "Explore with your agent"
FENCE = re.compile(r"^\s*(`{3,}|~{3,})")
RAW_RE = re.compile(r"\{%-?\s*(raw|endraw)\s*-?%\}")
INCLUDE_RE = re.compile(r"\{%-?\s*include\s+excalidraw\.html\s+(.*?)-?%\}", re.S)
FIG_PARAM = re.compile(r"""\b(?:file|name)\s*=\s*["']([^"']+)["']""")
LIQUID_OK = re.compile(
    r"""\{\{-?\s*(?:(?:site|page|include|forloop|content|part|part_docs|this_part|doc|prev_\w+|next_\w+)\b
      |['"][^'"]*['"]\s*\|)""", re.X)
JAVA_URI = re.compile(r'(?:from|toD?|enrich|pollEnrich|wireTap)\s*\(\s*"([a-zA-Z][\w+.-]*):')
NON_COMPONENT = {"http", "https", "classpath", "file", "jar", "ref", "bean"}
BUILTIN_SCHEMES = {
    "direct", "direct-vm", "seda", "vm", "timer", "log", "mock", "kafka", "jms", "amqp", "activemq",
    "rest", "platform-http", "jdbc", "sql", "jpa", "file", "ftp", "sftp", "netty-http", "paho-mqtt5",
    "paho", "mqtt", "jackson", "bean", "controlbus", "stub", "quartz", "scheduler", "cron", "language",
    "jslt", "freemarker", "velocity", "xslt", "mail", "smtp", "imap", "kamelet", "dataformat",
    "micrometer", "opentelemetry", "grpc", "websocket", "http", "https", "drools", "kie", "cql",
    "mongodb", "redis", "infinispan", "caffeine-cache", "cache", "elasticsearch", "graphql", "salesforce",
    "slack", "telegram", "twitter", "aws2-s3", "aws2-sqs", "aws2-sns", "azure-storage-blob", "google-pubsub",
    "pgevent", "debezium-postgres", "knative", "cxf", "cxfrs", "ai", "langchain4j-chat", "langchain4j-tools",
    "langchain4j-embeddings", "openai", "pdf", "tika", "json-validator", "validator", "xj", "atom", "rss",
    "weather", "exec", "ssh", "zipfile", "hazelcast-map", "ignite-cache", "jgroups", "etcd3", "consul",
}

DASH_RE = re.compile("[—–]")
VERSION_RE = re.compile(r"(?<![\d.])\d+\.\d+\.\d+(?!\.?\d)")
URL_RE = re.compile(r"https?://\S+")
TESTNAME_RE = re.compile(r"@Test\b|\b[A-Z]\w*[a-z0-9](?:Test|IT)\b|\bshould[A-Z]\w+")
# Owner rule 2026-11-16: every page stands alone. No reference to another chapter, appendix or part of this
# book in prose (back or forward); related pages are linked only under `## References`. Appendix D (the
# pattern map and cross-reference) is exempt. Chapter numbers of other books, cited as sources, are allowed.
XREF_RE = re.compile(r"\b(chapters?|appendix|appendices|parts?)\s+(\d+|[A-I])\b|\]\(\{\{\s*'/docs/|\]\(/docs/|\]\(\.\./docs/", re.I)
# Cross-references written in words: "the previous chapter", "the outbox chapter", "earlier chapters", "Part three".
XREF_WORD_RE = re.compile(
    r"\b(?:the\s+)?(?:previous|preceding|prior|next|following|earlier|later|last|first)\s+(?:chapters?|appendix|appendices|parts?)\b"
    r"|\bthe\s+(?!same\b)[\w-]+(?:\s+[\w-]+)?\s+(?:chapter|appendix)\b"
    r"|\b(?:chapters|parts|appendices)\s+(?:\d+|[A-I]|one|two|three|four|five|six|seven|eight)\b"
    r"|\bparts?\s+(?:one|two|three|four|five|six|seven|eight)\b"
    # "examples 02 and 03", "these examples", "the earlier examples" point at other chapters' examples
    r"|\bexamples?\s+\d{2}\b|\b(?:these|those|the\s+earlier|the\s+other)\s+examples\b", re.I)
ACCOMPANIES_RE = re.compile(r"accompanies \[[^\]]*\]\(\{\{[^)]*\)")
XREF_SOURCE_RE = re.compile(r"EIP Camel|Enterprise Integration Patterns|Bellemare|Lawrence|Kleppmann|Vernon|Hohpe|Fowler|"
                            r"Richardson|(?-i:Young)|Narkhede|Shapira|Stopford|Seymour|Riccomini|Bellemare's|\*[A-Z][^*]+\*,? (ch|chapter)", re.I)
SELFREF_RE = re.compile(r"\b(this|the) (chapter|section)\b|\bthe example shows\b|\bwe will\b", re.I)
IMG_RE = re.compile(r"^\s*-?\s*image:\s*(\S+)", re.M)
K8S_SETUP_HEADING = "## Setting up the cluster"


def xref_hit(t: str) -> str | None:
    """The first cross-reference in a prose line, or None. A chapter of another book cited as a source is allowed."""
    for rx in (XREF_RE, XREF_WORD_RE):
        for x in rx.finditer(t):
            if not XREF_SOURCE_RE.search(t[max(0, x.start() - 90):x.start() + len(x.group(0)) + 40]):
                return x.group(0)
    return None


class Report:
    def __init__(self, root: Path):
        self.root = root
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def err(self, path: Path | str, line: int | None, msg: str) -> None:
        p = path.relative_to(self.root) if isinstance(path, Path) and path.is_absolute() else path
        self.errors.append(f"{p}{f':{line}' if line else ''}: {msg}")

    def warn(self, msg: str) -> None:
        self.warnings.append(msg)


def files(root: Path, *globs: str):
    for g in globs:
        for p in sorted(root.glob(g)):
            if p.is_file() and not any(x in p.relative_to(root).parts for x in ("_site", "target", "vendor", "node_modules")):
                yield p


def line_of(text: str, pos: int) -> int:
    return text.count("\n", 0, pos) + 1


def split_front(text: str):
    """Return (front_matter_text | None, body, number of lines before body)."""
    m = re.match(r"---[ \t]*\n(.*?\n)?---[ \t]*(?:\n|$)", text, re.S)
    if not m:
        return None, text, 0
    return (m.group(1) or ""), text[m.end():], text[:m.end()].count("\n")


def fence_mask(body: str) -> list[bool]:
    """True per line when the line is a fence delimiter or inside a fenced block."""
    out, fence = [], None
    for ln in body.split("\n"):
        m = FENCE.match(ln)
        if fence is None:
            if m:
                fence = m.group(1)[0] * 3
                out.append(True)
            else:
                out.append(False)
        else:
            out.append(True)
            if m and m.group(1)[0] == fence[0] and ln.strip().startswith(fence):
                fence = None
    return out


def prose_lines(body: str) -> list[tuple[int, str]]:
    mask = fence_mask(body)
    return [(i + 1, ln) for i, ln in enumerate(body.split("\n")) if not mask[i]]


def mask_raw(text: str, path: Path, rep: Report) -> str:
    out, start = list(text), None
    for m in RAW_RE.finditer(text):
        if m.group(1) == "raw":
            if start is not None:
                rep.err(path, line_of(text, m.start()), "nested or repeated {% raw %}")
                continue
            start = m.start()
        else:
            if start is None:
                rep.err(path, line_of(text, m.start()), "{% endraw %} without a matching {% raw %}")
                continue
            for i in range(start, m.end()):
                if out[i] != "\n":
                    out[i] = " "
            start = None
    if start is not None:
        rep.err(path, line_of(text, start), "{% raw %} is never closed")
        for i in range(start, len(text)):
            if out[i] != "\n":
                out[i] = " "
    return "".join(out)


# ---- checks ---------------------------------------------------------------

def check_front_matter(rep: Report):
    root = rep.root
    part_names: set[str] = set()
    for p in files(root, "_parts/*.md"):
        fm, _, _ = split_front(p.read_text(encoding="utf-8"))
        d = load_fm(rep, p, fm, required=True)
        if d and d.get("part_name"):
            part_names.add(str(d["part_name"]))
    for p in files(root, "_docs/*.md"):
        fm, _, _ = split_front(p.read_text(encoding="utf-8"))
        d = load_fm(rep, p, fm, required=True)
        if d is None:
            continue
        part = d.get("part")
        if part is None:
            rep.err(p, 1, "front matter has no `part`")
        elif str(part) not in part_names:
            rep.err(p, 1, f"part '{part}' matches no _parts part_name ({', '.join(sorted(part_names)) or 'none'})")
    for p in files(root, "_example_pages/*.md"):
        fm, _, _ = split_front(p.read_text(encoding="utf-8"))
        load_fm(rep, p, fm, required=True)
    for p in files(root, "_plans/*.md"):
        fm, _, _ = split_front(p.read_text(encoding="utf-8"))
        load_fm(rep, p, fm, required=False)


def load_fm(rep: Report, p: Path, fm, required: bool):
    if fm is None:
        if required:
            rep.err(p, 1, "no front matter")
        return None
    try:
        d = yaml.safe_load(fm) if fm.strip() else {}
    except yaml.YAMLError as e:
        rep.err(p, 1, f"front matter does not parse: {str(e).splitlines()[0]}")
        return None
    if not isinstance(d, dict):
        rep.err(p, 1, "front matter is not a mapping")
        return None
    return d


def check_liquid(rep: Report):
    for p in files(rep.root, "_docs/*.md", "_example_pages/*.md"):
        text = p.read_text(encoding="utf-8")
        masked = mask_raw(text, p, rep)
        for m in re.finditer(r"\{\{", masked):
            if not LIQUID_OK.match(masked, m.start()):
                snip = masked[m.start():m.start() + 30].split("\n")[0]
                rep.err(p, line_of(masked, m.start()), f"stray `{{{{` outside {{% raw %}}: {snip!r}")


def check_diagrams(rep: Report):
    root = rep.root
    dg = root / "diagrams"
    for p in files(root, "_docs/*.md", "_example_pages/*.md", "index.html", "chapters.html"):
        text = p.read_text(encoding="utf-8")
        masked = mask_raw(text, p, rep)
        for m in INCLUDE_RE.finditer(masked):
            ln = line_of(masked, m.start())
            fp = FIG_PARAM.search(m.group(1))
            if not fp:
                rep.err(p, ln, "excalidraw include has no file=/name= parameter")
                continue
            name = fp.group(1)
            for rel in (f"{name}.svg", f"{name}.excalidraw", f"specs/{name}.py"):
                if not (dg / rel).is_file():
                    rep.err(p, ln, f"figure '{name}': diagrams/{rel} is missing")
    for spec in files(root, "diagrams/specs/*.py"):
        if spec.name.startswith("_"):
            continue
        if not (dg / f"{spec.stem}.svg").is_file():
            rep.err(spec, None, f"orphan spec: diagrams/{spec.stem}.svg does not exist (run scripts/build-diagrams.sh)")
    for svg in files(root, "diagrams/*.svg"):
        try:
            xml.dom.minidom.parseString(svg.read_bytes())
        except Exception as e:  # noqa: BLE001
            rep.err(svg, None, f"SVG does not parse as XML: {e}")
    for ex in files(root, "diagrams/*.excalidraw"):
        try:
            json.loads(ex.read_text(encoding="utf-8"))
        except ValueError as e:
            rep.err(ex, None, f"excalidraw file is not valid JSON: {e}")


def check_dashes(rep: Report):
    for p in files(rep.root, "_docs/*", "_parts/*", "_example_pages/*", "index.html", "chapters.html",
                   "README.md", "bibliography.md", "examples/**/README.md", "diagrams/*.svg",
                   "lgtm-presentation/text/*.md", "lgtm-presentation/BUILD.md", "lgtm-presentation/edits/*.md",
                   "lgtm-presentation/deck.js", "lgtm-presentation/deck-lib.js", "lgtm-presentation/sections/*.js"):
        for i, ln in enumerate(p.read_text(encoding="utf-8", errors="replace").split("\n"), 1):
            if DASH_RE.search(ln):
                rep.err(p, i, "em dash or en dash (use a comma, colon, parentheses or 'to')")


SVG_TEXT_RE = re.compile(r"<text\b[^>]*>(.*?)</text>", re.S)


def svg_text_lines(svg: str) -> list[str]:
    return [html.unescape(re.sub(r"<[^>]+>", "", m.group(1))).strip() for m in SVG_TEXT_RE.finditer(svg)]


def check_svg_xrefs(rep: Report):
    """Figure text stands alone too: no reference to another chapter, appendix or part (ad-* is the pattern map)."""
    for svg in files(rep.root, "diagrams/*.svg"):
        if svg.name.startswith("ad-"):
            continue
        for t in svg_text_lines(svg.read_text(encoding="utf-8", errors="replace")):
            hit = xref_hit(t)
            if hit:
                rep.err(svg, None, f"figure text references another chapter, appendix or part: {hit!r}")


# ---- deck (.pptx) checks: standard library only -------------------------------------------------

DECK_OWNER = "PatternCatalyst"
DECK_NOTES_FLOOR = 90
DECK_NOTES_FLOOR_PANEL = 45
DECK_PANEL_FILL = "AB0000"
DECK_BANNED = (
    (DASH_RE, "em or en dash"),
    (re.compile(r"\bslides?\s*\d+\b", re.I), "slide number reference"),
    (re.compile(
        r"\b(next|previous|following|closing|last|earlier|later)\s+(\w+\s+)?(slides?|sections?)\b"
        r"|\b(that|which)\s+follows?\b(?!\s+from)"
        r"|\b(listings?|slides?|figures?|tables?|sections?)\s+(before|after)\s+it\b"
        r"|\b(?:tables?|listings?|figures?|slides?|code|lists?|bullets?|diagrams?|examples?)"
        r"\s+(?:above|below)\b|\bthe\s+(?:above|below)\b|\b(?:above|below)\s*(?:[.,;:)]|$)"
        r"|\b(?:shown|listed|given|see|as)\s+(?:above|below)\b", re.I), "positional slide reference"),
    (re.compile(
        r"\b(?:from|as)\s+before\b|\b(?:shown|seen|covered|mentioned|discussed|described|noted)\s+(?:earlier|before)\b"
        r"|\bearlier\s+on\b|\bas\s+we\s+(?:saw|saw\s+earlier|discussed|covered)\b"
        r"|\b(?:discussed|covered|shown|described)\s+later\b|\b(?:detail|rest|more)\s+comes?\s+later\b|\bsee\s+later\b", re.I),
     "indirect back or forward pointer"),
    (re.compile(r"\b(this|these) (deck|slides?)\b|\bthe deck\b", re.I), "self-reference to the deck"),
    # Service and product identifiers keep their case at the start of a sentence (order-service, ksqlDB).
    (re.compile(r"\b(?:Order|Inventory|Payment|Shipping|Notification)-service\b|\bKsqlDB\b"), "capitalised identifier"),
    (SELFREF_RE, "self-reference to the chapter or section"),
)
DECK_BOOK_RE = re.compile(r"\b(the|this) (book|site)('s)?\b", re.I)
DECK_SLIDE_RE = re.compile(r"ppt/slides/slide(\d+)\.xml$")
DECK_FIELD_RE = re.compile(r"<a:fld\b.*?</a:fld>", re.S)
DECK_PARA_RE = re.compile(r"<a:p\b.*?</a:p>|<a:p/>", re.S)
DECK_RUN_RE = re.compile(r"<a:t(?:\s[^>]*)?>(.*?)</a:t>", re.S)
DECK_BG_RE = re.compile(r'<p:bg>.*?<a:srgbClr val="([0-9A-Fa-f]{6})"', re.S)
DECK_NOTES_REL_RE = re.compile(r'Target="[^"]*notesSlides/(notesSlide\d+\.xml)"')
_DECK_RULES: list | None = None


def deck_text(xml_text: str, *, drop_fields: bool) -> str:
    if drop_fields:
        xml_text = DECK_FIELD_RE.sub("", xml_text)
    paras = ["".join(html.unescape(r) for r in DECK_RUN_RE.findall(p)) for p in DECK_PARA_RE.findall(xml_text)]
    return "\n".join(p for p in paras if p.strip())


def deck_field(xml_text: str, tag: str) -> str | None:
    m = re.search(rf"<{tag}(?:\s[^>]*)?>(.*?)</{tag}>", xml_text, re.S)
    return html.unescape(m.group(1)) if m else None


def deck_rules() -> list:
    global _DECK_RULES
    if _DECK_RULES is None:
        _DECK_RULES = host_ids.all_rules(host_ids.gather(None))
    return _DECK_RULES


def check_deck(rel: str, path: Path) -> list[str]:
    out: list[str] = []

    def err(msg: str) -> None:
        out.append(f"{rel}: {msg}")

    try:
        with zipfile.ZipFile(path) as zf:
            parts = {n: zf.read(n) for n in zf.namelist() if not n.endswith("/")}
    except (zipfile.BadZipFile, OSError) as exc:
        return [f"{rel}: not a readable pptx: {exc}"]
    xml_parts: dict[str, str] = {}
    for name, raw in parts.items():
        if name.endswith((".xml", ".rels")):
            try:
                xml_parts[name] = raw.decode("utf-8")
            except UnicodeDecodeError:
                err(f"{name}: part is not UTF-8")
    slides = sorted((int(m.group(1)), n) for n in xml_parts if (m := DECK_SLIDE_RE.search(n)))
    if not slides:
        err("no slides")
    texts: list[tuple[str, str]] = []
    with_notes = 0
    for number, name in slides:
        body = xml_parts[name]
        texts.append((f"slide {number}", deck_text(body, drop_fields=False)))
        rels = xml_parts.get(posixpath.join("ppt/slides/_rels", f"slide{number}.xml.rels"), "")
        m = DECK_NOTES_REL_RE.search(rels)
        notes = deck_text(xml_parts.get(f"ppt/notesSlides/{m.group(1)}", ""), drop_fields=True) if m else ""
        texts.append((f"notes {number}", notes))
        bg = DECK_BG_RE.search(body.split("</p:bg>")[0] + "</p:bg>") if "<p:bg>" in body else None
        panel = bg is not None and bg.group(1).upper() == DECK_PANEL_FILL
        floor = DECK_NOTES_FLOOR_PANEL if (panel or number in (1, len(slides))) else DECK_NOTES_FLOOR
        words = len(notes.split())
        if words:
            with_notes += 1
        if words < floor:
            err(f"slide {number}: notes have {words} words, floor {floor}")
    if slides and with_notes != len(slides):
        err(f"{len(slides)} slides but {with_notes} notes slides with text")
    core, app = xml_parts.get("docProps/core.xml", ""), xml_parts.get("docProps/app.xml", "")
    for label, value in (("creator", deck_field(core, "dc:creator")),
                         ("lastModifiedBy", deck_field(core, "cp:lastModifiedBy")),
                         ("company", deck_field(app, "Company"))):
        if value != DECK_OWNER:
            err(f"docProps {label} is {value!r}, expected {DECK_OWNER!r}")
    last = f"{len(slides)}"
    for where, text in texts:
        for rx, label in DECK_BANNED:
            if where == "notes 1" and label == "self-reference to the deck":
                # the cover notes may name the deck once, in their opening sentence
                parts_ = re.split(r"(?<=[.!?])\s", text, maxsplit=1)
                if rx.search(parts_[1] if len(parts_) > 1 else ""):
                    err(f"{where}: {label}")
            elif rx.search(text):
                err(f"{where}: {label}")
        hit = xref_hit(text)
        if hit:
            err(f"{where}: cross-reference {hit!r}")
        if where.split()[-1] != last and DECK_BOOK_RE.search(text):
            err(f"{where}: book or site pointer outside the last slide")
    cfg = host_ids.Config()
    for name, body in xml_parts.items():
        if host_ids.hits(body, deck_rules(), cfg, "-"):
            err(f"{name}: host_ids rule matches this part")
    return out


def check_decks(rep: Report):
    for deck in sorted((rep.root / "lgtm-presentation").glob("*.pptx")):
        for msg in check_deck(deck.relative_to(rep.root).as_posix(), deck):
            rep.err(deck.relative_to(rep.root).as_posix(), None, msg.split(": ", 1)[1] if ": " in msg else msg)


def chapter_words(body: str) -> int:
    body = re.sub(r"<!--.*?-->", " ", body, flags=re.S)
    return len(re.findall(r"[A-Za-z0-9][\w'’-]*", "\n".join(t for _, t in prose_lines(body))))


def check_bro(rep: Report, p: Path, plines, off: int):
    """Build, run, observe stays EIP-Camel short: commands, what to watch, at most three Try bullets."""
    sec, start = [], None
    for i, t in plines:
        if t.startswith(BRO_HEAD):
            start = i
            continue
        if start is not None:
            if t.startswith("## "):
                break
            sec.append((i, t))
    if start is None:
        return
    words = len(re.findall(r"[A-Za-z0-9][\w'’-]*", "\n".join(t for _, t in sec)))
    if words > MAX_BRO_WORDS:
        rep.warn(f"{p.relative_to(rep.root)}:{start + off}: '{BRO_HEAD}' has {words} prose words, soft cap {MAX_BRO_WORDS}")
    bullets = [i for i, t in sec if re.match(r"\s*[-*] ", t)]
    if len(bullets) > MAX_BRO_BULLETS:
        rep.warn(f"{p.relative_to(rep.root)}:{start + off}: '{BRO_HEAD}' has {len(bullets)} bullets, soft cap {MAX_BRO_BULLETS}")
    for i, t in sec:
        m = CHECKNUM_RE.search(t)
        if m:
            rep.err(p, i + off, f"check number or run statistic '{m.group(0)}' in '{BRO_HEAD}' (belongs in the README)")


def check_example_pages(rep: Report):
    """The cross-reference rule also covers the example pages (_example_pages/*.md prose)."""
    for p in files(rep.root, "_example_pages/*.md"):
        fm, body, off = split_front(p.read_text(encoding="utf-8"))
        section = ""
        for i, t in prose_lines(re.sub(r"<!--.*?-->", lambda m: "\n" * m.group(0).count("\n"), body, flags=re.S)):
            if re.match(r"##\s", t):
                section = t.strip()
            if section.lower().startswith("## references"):
                continue
            # the one link an example page may carry is to the page it accompanies
            hit = xref_hit(ACCOMPANIES_RE.sub("accompanies this page", t))
            if hit:
                rep.err(p, i + off, f"cross-reference '{hit}' in example page prose: each page stands alone; "
                                    "link related pages only under ## References")


def check_chapters(rep: Report):
    for p in files(rep.root, "_docs/*.md"):
        text = p.read_text(encoding="utf-8")
        fm, body, off = split_front(text)
        if not p.name.startswith("ad-"):
            d = re.search(r"^description:\s*(.*)$", text.split("\n---", 2)[0] if text.startswith("---") else "", re.M)
            if d and XREF_RE.search(d.group(1)) and not XREF_SOURCE_RE.search(d.group(1)):
                rep.err(p, None, f"cross-reference '{XREF_RE.search(d.group(1)).group(0)}' in the description")
        n = chapter_words(body)
        # No hard word cap or floor (owner decision 2026-11-18): about 2,000 words is a target,
        # never a limit. Warn, do not fail, when a page runs well past it. Appendix I is exempt.
        if n > MAX_WORDS and not p.name.startswith("ai-"):
            rep.warn(f"{p.relative_to(rep.root)}: {n} prose words, soft cap {MAX_WORDS} (target ~2000, no hard cap)")
        plines = prose_lines(re.sub(r"<!--.*?-->", lambda m: "\n" * m.group(0).count("\n"), body, flags=re.S))
        check_bro(rep, p, plines, off)
        has_pattern = any(re.match(r"##\s+Pattern:", t) for _, t in plines)
        callouts = [i for i, t in plines if CALLOUT in t]
        if has_pattern and len(callouts) != 1:
            rep.err(p, None, f"pattern chapter needs exactly one '{CALLOUT}' callout, found {len(callouts)}")
        elif not has_pattern and len(callouts) > 1:
            rep.err(p, None, f"at most one '{CALLOUT}' callout allowed, found {len(callouts)}")
        if len(callouts) == 1:
            check_callout_shape(rep, p, body.split("\n"), callouts[0], off)
        section = ""
        for i, t in plines:
            if re.match(r"##\s", t):
                section = t.strip()
            if not t.lstrip().startswith(">"):
                sr = SELFREF_RE.search(t)
                if sr:
                    rep.err(p, i + off, f"self-reference '{sr.group(0)}' in prose")
            m = TESTNAME_RE.search(t)
            if m:
                rep.err(p, i + off, f"test-name token '{m.group(0)}' in prose")
            if not p.name.startswith("ad-") and not section.lower().startswith("## references"):
                hit = xref_hit(t)
                if hit:
                    rep.err(p, i + off, f"cross-reference '{hit}' in prose: each page stands alone; "
                                        "link related pages only under ## References")
            # Versions live in chapter 00, the appendices and Part 6's setup section (chapter 28).
            if not (p.name.startswith(("00-", "a"))
                    or (p.name.startswith("28-") and section == K8S_SETUP_HEADING)):
                v = VERSION_RE.search(URL_RE.sub("", t))
                if v:
                    rep.err(p, i + off, f"version string '{v.group(0)}' outside chapter 00, appendices and chapter 28's setup section")


def check_callout_shape(rep: Report, p: Path, lines: list[str], at: int, off: int):
    i = at - 1
    block = []
    while i < len(lines) and lines[i].lstrip().startswith(">"):
        block.append(lines[i].lstrip())
        i += 1
    ln = at + off
    if not re.match(r">\s*\*\*\U0001F916 Explore with your agent\*\*\s*$", block[0]):
        rep.err(p, ln, "callout heading must be exactly '> **\U0001F916 Explore with your agent**'")
    if len(block) < 3 or not re.match(r'>\s*["“]', block[1]):
        rep.err(p, ln, "callout line 2 must be the quoted runnable prompt")
    checks = [b for b in block if re.match(r">\s*Check:", b)]
    if len(checks) != 1:
        rep.err(p, ln, f"callout needs exactly one 'Check:' line in the same blockquote, found {len(checks)}")
    if any(re.match(r">\s*>", b) for b in block):
        rep.err(p, ln, "callout must not contain a nested quote")


VENDOR_RE = re.compile(r"/vendor/|/platform/cnpg/cnpg-[^/]*\.yaml$|/strimzi/strimzi-cluster-operator-[^/]*\.yaml$|/platform/kueue/kueue-[^/]*\.yaml$")


def is_vendor(p: Path) -> bool:
    """Upstream release manifests downloaded by examples/_k8s/cluster.sh (gitignored, sha256-checked)."""
    return bool(VENDOR_RE.search(p.as_posix()))


# Chapter examples NN-slug and appendix examples aX-slug (Appendix A to I).
EXAMPLE_DIR_RE = re.compile(r"(\d+|a[a-i])-")


def check_examples(rep: Report):
    ex = rep.root / "examples"
    if not ex.is_dir():
        return
    for d in sorted(ex.iterdir()):
        if not (d.is_dir() and EXAMPLE_DIR_RE.match(d.name)):
            continue
        v = d / "verify.sh"
        if not v.is_file():
            rep.err(v, None, "missing verify.sh")
        else:
            if not os.access(v, os.X_OK):
                rep.err(v, None, "verify.sh is not executable")
            r = subprocess.run(["bash", "-n", str(v)], capture_output=True, text=True)
            if r.returncode:
                rep.err(v, None, f"bash -n failed: {r.stderr.strip()}")
            # Every live run starts from fresh containers and volumes, never a leftover stack.
            body = v.read_text(encoding="utf-8")
            k8s_ok = (re.search(r"^\s*(source|\.)\s+\S*_k8s/lib\.sh", body, re.M)
                      and re.search(r"^\s*k8s_fresh\b", body, re.M)
                      and re.search(r"\bk8s_teardown\b", body))
            if d.name.startswith("aa-"):
                # Appendix A: tests only, on Dev Services; no compose stack of its own.
                if not re.search(r"\bmvn\b[^\n]*\s-B\b", body) or re.search(r"docker compose[^\n]*\bup\b", body):
                    rep.err(v, None, "Appendix A verify.sh runs `mvn -B` on Dev Services and starts no compose stack")
            elif d.name.startswith("ab-"):
                # Appendix B: a compose half and a minikube half, each from fresh state.
                if not (k8s_ok and re.search(r"down -v --remove-orphans", body)
                        and re.search(r"--force-recreate --renew-anon-volumes", body)):
                    rep.err(v, None, "Appendix B verify.sh needs both fresh halves: compose `down -v --remove-orphans` "
                                     "and `--force-recreate --renew-anon-volumes`, and k8s_fresh / k8s_teardown")
            elif d.name.startswith("ai-"):
                # Appendix I: Kueue on the shared minikube profile; installed after k8s_fresh, removed before k8s_teardown.
                if not (k8s_ok and re.search(r"^\s*kueue_fresh\b", body, re.M)
                        and re.search(r"\bkueue_teardown\b", body)):
                    rep.err(v, None, "Appendix I verify.sh must source ../_k8s/lib.sh and call k8s_fresh, k8s_teardown, "
                                     "kueue_fresh and kueue_teardown")
            elif d.name.startswith("ah-"):
                # Appendix H: CRC, started by the orchestrator; fresh state comes from install.sh after teardown.sh.
                if not (re.search(r"^\s*source\s+\S*stack-guard\.sh\s+--crc\b", body, re.M)
                        and re.search(r"^\s*(source|\.)\s+\S*crc-check\.sh", body, re.M)
                        and (d / "install.sh").is_file() and (d / "teardown.sh").is_file()):
                    rep.err(v, None, "Appendix H verify.sh must source stack-guard.sh --crc and crc-check.sh, "
                                     "with install.sh and teardown.sh beside it")
            elif (d / "kustomization.yaml").is_file():
                # Part 6: fresh namespaces on the shared minikube profile (examples/_k8s/lib.sh).
                if not k8s_ok:
                    rep.err(v, None, "Part 6 verify.sh must source ../_k8s/lib.sh, call k8s_fresh "
                                     "and tear down with k8s_teardown")
            elif not (re.search(r"down -v --remove-orphans", body)
                    and re.search(r"up -d --build --force-recreate --renew-anon-volumes --wait", body)):
                rep.err(v, None, "verify.sh must start fresh: `down -v --remove-orphans`, then "
                                 "`up -d --build --force-recreate --renew-anon-volumes --wait`")
        if not (d / "README.md").is_file():
            rep.err(d / "README.md", None, "missing README.md")
    for m_ in files(rep.root, "examples/_k8s/**/*.yaml", "examples/[0-9]*/**/*.yaml",
                     "examples/a[a-i]-*/**/*.yaml"):
        if "compose" in m_.name or is_vendor(m_):
            continue
        lines = m_.read_text(encoding="utf-8").split("\n")
        for n, ln_ in enumerate(lines):
            if re.match(r"\s*imagePullPolicy:\s*Never\b", ln_):
                near = [x for x in lines[max(0, n - 8):n + 9] if re.match(r"\s*-?\s*image:", x)]
                if not near or not all("localhost/edp/" in x for x in near):
                    rep.err(m_, n + 1, "imagePullPolicy: Never only for localhost/edp/ images")
    for c in files(rep.root, "examples/**/compose*.yaml", "examples/**/compose*.yml",
                   "examples/_k8s/**/*.yaml", "examples/[0-9]*/**/*.yaml", "examples/a[a-i]-*/**/*.yaml"):
        if is_vendor(c) or "/target/" in c.as_posix():
            continue
        text = c.read_text(encoding="utf-8")
        for m in IMG_RE.finditer(text):
            ref = m.group(1).strip("\"'")
            ln = line_of(text, m.start())
            # Strimzi KafkaConnect build.output of type imagestream names an ImageStream tag, not a pulled image.
            # Only in Appendix H examples, and the tag is still required and may not be latest.
            if ("/examples/ah-" in c.as_posix() or c.as_posix().startswith("examples/ah-")) and re.search(
                    r"^\s*type:\s*imagestream\s*$", "\n".join(text.split("\n")[max(0, ln - 3):ln - 1]), re.M):
                istag = ref.rsplit(":", 1)[1] if ":" in ref else ""
                if not istag:
                    rep.err(c, ln, f"imagestream output '{ref}' has no tag")
                elif istag == "latest":
                    rep.err(c, ln, f"imagestream output '{ref}' uses :latest")
                continue
            dm = re.search(r"\$\{[^}:]+:?-([^}]*)\}", ref)
            if "${" in ref:
                if not dm:
                    rep.err(c, ln, f"image '{ref}' has no default to check")
                    continue
                ref = ref.replace(dm.group(0), dm.group(1))
            first = ref.split("/")[0]
            if "/" not in ref or not ("." in first or ":" in first or first == "localhost"):
                rep.err(c, ln, f"image '{ref}' is not fully qualified (registry host missing)")
            tail = ref.rsplit("/", 1)[-1]
            if "@sha256:" in tail:
                continue
            if ":" not in tail:
                rep.err(c, ln, f"image '{ref}' has no tag")
            elif tail.split(":", 1)[1] == "latest":
                rep.err(c, ln, f"image '{ref}' uses :latest")


def load_catalog_schemes():
    base = Path.home() / ".m2/repository/org/apache/camel/camel-catalog"
    jars = sorted(base.glob("*/camel-catalog-*.jar"), key=lambda p: [int(x) if x.isdigit() else 0 for x in re.split(r"[.-]", p.parent.name)])
    jars = [j for j in jars if not j.name.endswith(("-sources.jar", "-javadoc.jar"))]
    if not jars:
        return None, None
    schemes = set()
    with zipfile.ZipFile(jars[-1]) as z:
        for n in z.namelist():
            if re.match(r".*/components/[a-z0-9-]+\.json$", n):
                comp = json.loads(z.read(n)).get("component", {})
                if comp.get("scheme"):
                    schemes.add(comp["scheme"])
                if comp.get("alternativeSchemes"):
                    schemes.update(comp["alternativeSchemes"].split(","))
    return schemes, jars[-1].parent.name


def check_camel_schemes(rep: Report):
    java = list(files(rep.root, "examples/**/*.java"))
    if not java:
        return
    schemes, ver = load_catalog_schemes()
    if schemes is None:
        rep.warn("camel-catalog jar not found in the local Maven repository; using the built-in scheme list (unknown schemes are not failures)")
        schemes = BUILTIN_SCHEMES
        fatal = False
    else:
        fatal = True
    for p in java:
        text = p.read_text(encoding="utf-8")
        for m in JAVA_URI.finditer(text):
            s = m.group(1)
            if s in NON_COMPONENT or s in schemes:
                continue
            msg = f"unknown Camel component scheme '{s}:'"
            if fatal:
                rep.err(p, line_of(text, m.start()), msg + f" (camel-catalog {ver})")
            else:
                rep.warn(f"{p.relative_to(rep.root)}:{line_of(text, m.start())}: {msg}")


REDIS_PIN = "docker.io/library/redis:8.10.3-alpine"


def check_redis_devservices(rep: Report):
    """Quarkus Redis Dev Services defaults to the floating `redis:7` (7.4.x, RSALv2/SSPLv1, not open source):
    every example using the Redis client must name the pinned image."""
    for pom in files(rep.root, "examples/*/pom.xml", "examples/*/*/pom.xml"):
        if "quarkus-redis-client" not in pom.read_text(encoding="utf-8"):
            continue
        props = pom.parent / "src/main/resources/application.properties"
        text = props.read_text(encoding="utf-8") if props.is_file() else ""
        if not re.search(r"^quarkus\.redis\.devservices\.image-name\s*=\s*" + re.escape(REDIS_PIN) + r"\s*$", text, re.M):
            rep.err(props, None, f"Redis Dev Services must use the pinned image: "
                                 f"quarkus.redis.devservices.image-name={REDIS_PIN}")


def check_podman(rep: Report):
    roots = ["_docs/*", "_parts/*", "_example_pages/*", "_includes/*", "_layouts/*", "index.html", "chapters.html",
             "README.md", "bibliography.md", "examples/**/*", ".github/**/*", "diagrams/*", "scripts/*.sh"]
    for p in files(rep.root, *roots):
        rel = p.relative_to(rep.root)
        if (rel.parts[0] in ("_docs", "_example_pages") and p.name.startswith("ah-")) or (
                rel.parts[0] == "examples" and len(rel.parts) > 1 and rel.parts[1].startswith("ah-")):
            continue
        try:
            text = p.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for m in re.finditer(r"podman", text, re.I):
            rep.err(p, line_of(text, m.start()), "'podman' is only allowed in _plans/ and Appendix H (OpenShift Local)")
            break


FORWARDING = re.compile(
    r"port-forward|minikube\s+tunnel|\bssh\s+(?:\S+\s+)*-[LRD]\b|ip_forward|\biptables\b|\bnft\b|"
    r"firewall-cmd|sysctl\s+-w|\bsocat\b|\b(?:oc|kubectl)\s+proxy\b", re.I)
# Appendix H verifies every TLS connection against the cluster's ingress CA.
TLS_OFF = re.compile(r"--tls-verify[= ]+false|--dest-tls-verify[= ]+false|--src-tls-verify[= ]+false|"
                     r"\bcurl\b(?:[^\n]|\\\n)*?\s(?:-[a-zA-Z]*k[a-zA-Z]*|--insecure)\b|--insecure-skip-tls-verify|--insecure\b|"
                     r"insecureSkipTLSVerify:\s*true")


def check_no_forwarding(rep: Report):
    """No tunnels, port forwarding, IP forwarding or host network changes: publish ports instead."""
    roots = ["_docs/*", "_parts/*", "_example_pages/*", "_includes/*", "_layouts/*", "index.html", "chapters.html",
             "README.md", "bibliography.md", "examples/**/*", ".github/**/*", "scripts/*.sh"]
    for p in files(rep.root, *roots):
        try:
            text = p.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for m in FORWARDING.finditer(text):
            rep.err(p, line_of(text, m.start()),
                    f"forwarding or host network change '{m.group(0)}' is banned: publish ports "
                    "(compose ports, minikube start --ports, NodePorts) instead")


def check_ah_tls(rep: Report):
    """Appendix H examples never turn TLS verification off."""
    for p in files(rep.root, "examples/ah-*/**/*"):
        try:
            text = p.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for m in TLS_OFF.finditer(text):
            rep.err(p, line_of(text, m.start()),
                    f"TLS verification off '{m.group(0)}' is banned in Appendix H: verify against the ingress CA")


BANNED_DOMAIN = re.compile(
    r"\b(?:Consignment\w*|consignment\w*|HandlingEvent|TrackingHistory|Tariff|RateQuote|Invoice|Settlement|"
    r"CustomsDeclaration|Vessel|Sailing|PortCall|SlotId|Manifest|BOOKED|LOADED|CLEARED|NLRTM|SGSIN|"
    r"weightKg|weight_kg|freight|FedEx|UPS|DHL)\b")
DOMAIN_SUFFIXES = {".java", ".kt", ".xml", ".yaml", ".yml", ".sh", ".md", ".sql", ".properties", ".json",
                   ".html", ".py", ".txt", ".svg", ".excalidraw", ".toml", ".adoc"}


def check_domain_names(rep: Report):
    """The r02 shipping domain is order-to-delivery: the old freight identifiers must not come back."""
    roots = ["_docs/*", "_parts/*", "_example_pages/*", "index.html", "chapters.html", "README.md",
             "bibliography.md", "CLAUDE.md", "_plans/domain-model.md", "examples/**/*",
             "diagrams/specs/*.py", "diagrams/*.svg", "diagrams/*.excalidraw"]
    seen: set[Path] = set()
    for p in files(rep.root, *roots):
        if p in seen:
            continue
        seen.add(p)
        if p.suffix not in DOMAIN_SUFFIXES:
            continue
        try:
            text = p.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for i, ln in enumerate(text.split("\n"), 1):
            if "freight model" in ln:
                continue
            m = BANNED_DOMAIN.search(ln)
            if m:
                rep.err(p, i, f"banned freight-domain name '{m.group(0)}' (use the order-to-delivery domain)")


CHECKS = [check_front_matter, check_liquid, check_diagrams, check_svg_xrefs, check_dashes, check_decks, check_chapters, check_example_pages,
          check_examples, check_camel_schemes, check_redis_devservices, check_podman, check_no_forwarding, check_ah_tls, check_domain_names]


def run(root: Path) -> Report:
    rep = Report(root)
    for c in CHECKS:
        c(rep)
    return rep


# ---- self-test ------------------------------------------------------------

def self_test() -> int:
    good_doc = (
        '---\ntitle: "Demo"\norder: 1\npart: demo\n---\n\nIntro text.\n\n'
        '{% include excalidraw.html file="01-demo" alt="Demo" %}\n\n## Pattern: Demo\n\n'
        '> **\U0001F916 Explore with your agent**\n> "Open the demo and check the rule."\n> Check: it holds.\n'
    )
    results = []
    with tempfile.TemporaryDirectory() as tmp:
        def build(name: str) -> Path:
            r = Path(tmp) / name
            for d in ("_parts", "_docs", "diagrams/specs"):
                (r / d).mkdir(parents=True)
            (r / "_parts/demo.md").write_text('---\ntitle: Demo\norder: 1\npart_name: "demo"\n---\n')
            (r / "_docs/01-demo.md").write_text(good_doc)
            (r / "diagrams/01-demo.svg").write_text('<svg xmlns="http://www.w3.org/2000/svg"/>')
            (r / "diagrams/01-demo.excalidraw").write_text("{}")
            (r / "diagrams/specs/01-demo.py").write_text("")
            return r

        base = run(build("base"))
        results.append(("baseline tree passes", not base.errors, base.errors))

        r = build("dash")
        (r / "_docs/01-demo.md").write_text(good_doc + "\nA dash — here.\n")
        e = run(r).errors
        results.append(("em dash caught", any("em dash" in x for x in e), e))

        r = build("orphan")
        (r / "diagrams/specs/lonely.py").write_text("")
        e = run(r).errors
        results.append(("orphan spec caught", any("orphan spec" in x for x in e), e))

        r = build("selfref")
        (r / "_docs/01-demo.md").write_text(good_doc + "\nIn this chapter we cover it.\n")
        e = run(r).errors
        results.append(("self-reference caught", any("self-reference" in x for x in e), e))

        r = build("forward")
        (r / "_docs/01-demo.md").write_text(good_doc + "\nRun kubectl port-forward svc/x 8080.\n")
        e = run(r).errors
        results.append(("port-forward caught", any("forwarding" in x for x in e), e))

        (r / "_docs/01-demo.md").write_text(good_doc + "\nStart oc proxy and browse.\n")
        e = run(r).errors
        results.append(("oc proxy caught", any("forwarding" in x for x in e), e))

        r = build("tlsoff")
        (r / "examples/ah-demo").mkdir(parents=True)
        (r / "examples/ah-demo/copy.sh").write_text("#!/bin/bash\nskopeo copy --dest-tls-verify=false a b\n")
        e = run(r).errors
        results.append(("--tls-verify=false in an Appendix H example caught", any("TLS verification off" in x for x in e), e))
        (r / "examples/ah-demo/copy.sh").write_text("#!/bin/bash\ncurl -s \\\n  -k https://x\n")
        e = run(r).errors
        results.append(("curl -k on a continued line caught", any("TLS verification off" in x for x in e), e))
        (r / "examples/ah-demo/copy.sh").write_text("#!/bin/bash\n")
        (r / "examples/ah-demo/kc.yaml").write_text("insecureSkipTLSVerify: true\n")
        e = run(r).errors
        results.append(("insecureSkipTLSVerify in an Appendix H manifest caught", any("TLS verification off" in x for x in e), e))
        (r / "examples/ah-demo/kc.yaml").unlink()
        (r / "examples/ah-demo/copy.sh").write_text("#!/bin/bash\ncurl -k https://x\n")
        e = run(r).errors
        results.append(("curl -k in an Appendix H example caught", any("TLS verification off" in x for x in e), e))
        (r / "examples/ah-demo/copy.sh").write_text("#!/bin/bash\ncurl --insecure https://x\n")
        e = run(r).errors
        results.append(("curl --insecure in an Appendix H example caught", any("TLS verification off" in x for x in e), e))
        (r / "examples/ah-demo/copy.sh").write_text("#!/bin/bash\ncurl --cacert ca.crt -sS https://x\n")
        e = run(r).errors
        results.append(("verified curl in an Appendix H example passes", not any("TLS verification off" in x for x in e), e))

        r = build("xref")
        (r / "_docs/01-demo.md").write_text(good_doc + "\nA Kafka transaction (chapter 12) stops at the cluster.\n"
                                            "\n## References\n\n- Chapter 12, exactly once.\n")
        e = run(r).errors
        results.append(("cross-reference in prose caught, allowed under References",
                        sum("cross-reference" in x for x in e) == 1, e))
        (r / "_docs/01-demo.md").write_text(good_doc + "\nBellemare's chapter 14 calls it streaming SQL.\n")
        e = run(r).errors
        results.append(("another book's chapter cited as a source allowed", not any("cross-reference" in x for x in e), e))

        for label, sentence in (("previous chapter", "The previous chapter showed it."),
                                ("next chapters", "The next chapters extend it."),
                                ("the outbox chapter", "See the outbox chapter for the table."),
                                ("earlier chapters", "As the earlier chapters did."),
                                ("Part three", "Part three covers it."),
                                ("Parts 2 and 3", "Parts 2 and 3 cover it.")):
            r = build("xref-words-" + label.replace(" ", "-"))
            (r / "_docs/01-demo.md").write_text(good_doc + "\n" + sentence + "\n")
            e = run(r).errors
            results.append((f"cross-reference in words caught: {label}", any("cross-reference" in x for x in e), e))
        r = build("xref-words-ok")
        (r / "_docs/01-demo.md").write_text(good_doc + "\nBellemare's previous chapter calls it streaming SQL. The young parts of a spare part stay.\n"
                                            "\n## References\n\n- The previous chapter, listed.\n")
        e = run(r).errors
        results.append(("words allowed for another book's chapter, under References and in 'young'/'part'",
                        not any("cross-reference" in x for x in e), e))
        r = build("xref-example")
        (r / "_example_pages").mkdir()
        (r / "_example_pages/01-demo.md").write_text("---\ntitle: x\n---\n\nIt reuses the services of chapter 2.\n")
        e = run(r).errors
        results.append(("cross-reference in an example page caught",
                        any("cross-reference" in x and "_example_pages" in x for x in e), e))
        (r / "_example_pages/01-demo.md").write_text(
            "---\ntitle: x\n---\n\nIt accompanies [Demo]({{ '/docs/01-demo/' | relative_url }}).\n")
        e = run(r).errors
        results.append(("the accompanying link of an example page allowed", not any("cross-reference" in x for x in e), e))

        r = build("xref-examples")
        (r / "_docs/01-demo.md").write_text(good_doc + "\nThe services of examples 02 and 03 run unchanged.\n")
        e = run(r).errors
        results.append(("'examples NN' caught", any("examples 02" in x for x in e), e))

        r = build("domain")
        (r / "_docs/01-demo.md").write_text(good_doc + "\nA Consignment is booked.\n")
        e = run(r).errors
        results.append(("freight name caught", any("freight-domain" in x for x in e), e))

        r = build("k8s")
        (r / "_docs/01-demo.md").write_text(good_doc + "\nApply the Kubernetes manifests.\n")
        e = run(r).errors
        results.append(("lowercase manifests allowed", not any("freight-domain" in x for x in e), e))

        r = build("callout")
        (r / "_docs/01-demo.md").write_text(
            good_doc + '\n> **\U0001F916 Explore with your agent**\n> "Again."\n> Check: no.\n')
        e = run(r).errors
        results.append(("second callout caught", any("exactly one" in x for x in e), e))

        r = build("wordcap")
        (r / "_docs/01-demo.md").write_text(good_doc + "\n" + " ".join(["word"] * 2600) + "\n")
        rr = run(r)
        results.append(("chapter well over target warns, does not fail",
                        any("soft cap" in x for x in rr.warnings) and not any("prose words" in x for x in rr.errors),
                        rr.warnings + rr.errors))
        (r / "_docs/01-demo.md").write_text(good_doc)
        (r / "_docs/ai-demo.md").write_text(good_doc.replace("01-demo", "ai-demo") + "\n" + " ".join(["word"] * 2600) + "\n")
        rr = run(r)
        results.append(("Appendix I exempt from the word soft cap",
                        not any("soft cap" in x and "ai-demo" in x for x in rr.warnings), rr.warnings))

        r = build("bro")
        long_bro = "\n## Build, run, observe\n\n```bash\n./verify.sh\n```\n\n" + " ".join(["word"] * 251) + "\n"
        (r / "_docs/01-demo.md").write_text(good_doc + long_bro)
        rr = run(r)
        results.append(("251-word build section warned, not failed",
                        any("soft cap 250" in x for x in rr.warnings) and not any("Build, run" in x for x in rr.errors),
                        rr.errors + rr.warnings))

        r = build("bro-ok")
        ok_bro = ("\n## Build, run, observe\n\n```bash\n./verify.sh\n```\n\n" + " ".join(["word"] * 244)
                  + "\n\n- Try one.\n- Try two.\n- Try three.\n")
        (r / "_docs/01-demo.md").write_text(good_doc + ok_bro)
        e = run(r).errors
        results.append(("250-word build section with three bullets allowed", not any("Build, run" in x for x in e), e))

        r = build("bro-check")
        (r / "_docs/01-demo.md").write_text(good_doc + "\n## Build, run, observe\n\nOnly check 12 fails.\n")
        e = run(r).errors
        results.append(("check number in build section caught", any("check number" in x for x in e), e))

        r = build("k8s-version")
        (r / "_docs/28-demo.md").write_text(good_doc.replace("01-demo", "28-demo")
            + "\n## Setting up the cluster\n\nminikube 1.39.0 runs it.\n\n## Other\n\nNot 1.37.2 here.\n")
        (r / "diagrams/28-demo.svg").write_text('<svg xmlns="http://www.w3.org/2000/svg"/>')
        (r / "diagrams/28-demo.excalidraw").write_text("{}")
        (r / "diagrams/specs/28-demo.py").write_text("")
        e = run(r).errors
        results.append(("ch 28 setup versions allowed, elsewhere caught",
                        not any("1.39.0" in x for x in e) and any("1.37.2" in x for x in e), e))

        r = build("k8s-example")
        ex = r / "examples/28-demo"
        ex.mkdir(parents=True)
        (ex / "README.md").write_text("x")
        (ex / "kustomization.yaml").write_text("resources: []\n")
        (ex / "app.yaml").write_text("      - image: quay.io/x/y:1.0\n        imagePullPolicy: Never\n")
        (ex / "verify.sh").write_text("#!/bin/bash\necho no fresh\n")
        (ex / "verify.sh").chmod(0o755)
        e = run(r).errors
        results.append(("k8s verify.sh without k8s_fresh caught", any("k8s_fresh" in x for x in e), e))
        results.append(("Never on upstream image caught", any("imagePullPolicy" in x for x in e), e))
        (ex / "verify.sh").write_text("#!/bin/bash\nsource ../_k8s/lib.sh\nk8s_fresh .\ntrap k8s_teardown EXIT\n")
        (ex / "app.yaml").write_text("      - image: localhost/edp/02-x:local\n        imagePullPolicy: Never\n")
        e = run(r).errors
        results.append(("k8s example with k8s_fresh and local image passes",
                        not any("k8s_fresh" in x or "imagePullPolicy" in x for x in e), e))

        # r12: the in-cluster registry reference and images pinned by digest are fully qualified and not latest
        (ex / "connect.yaml").write_text(
            "kind: KafkaConnect\nspec:\n  build:\n    output:\n      type: docker\n"
            "      image: 10.96.100.50:5000/edp/connect:35\n")
        (ex / "pinned.yaml").write_text(
            "      - image: docker.io/library/registry:3.1.2@sha256:" + "0" * 64 + "\n")
        e = run(r).errors
        results.append(("registry ClusterIP image and digest-pinned image allowed",
                        not any("image '" in x for x in e), e))
        (ex / "connect.yaml").write_text("      - image: 10.96.100.50:5000/edp/connect:latest\n")
        e = run(r).errors
        results.append(("registry ClusterIP image with :latest caught", any("uses :latest" in x for x in e), e))
        (ex / "connect.yaml").write_text("      - image: connect:35\n")
        e = run(r).errors
        results.append(("unqualified image caught", any("not fully qualified" in x for x in e), e))
        (ex / "connect.yaml").write_text(
            "topics: edp.orders.placed edp-connect-offsets edp-connect-configs edp-connect-status "
            "mirrormaker2-cluster-offsets __consumer_offsets edp.dr.heartbeats\n")
        e = run(r).errors
        results.append(("Connect, MirrorMaker 2 and Cruise Control names pass the domain check",
                        not any("freight-domain" in x for x in e), e))

        # r13: appendix examples get the same rules
        r = build("appendix")
        ax = r / "examples/ae-demo"
        ax.mkdir(parents=True)
        (ax / "README.md").write_text("x")
        (ax / "verify.sh").write_text("#!/bin/bash\ndocker compose up -d\n")
        (ax / "verify.sh").chmod(0o755)
        e = run(r).errors
        results.append(("appendix compose example without fresh start caught", any("start fresh" in x for x in e), e))
        aa = r / "examples/aa-demo"
        aa.mkdir(parents=True)
        (aa / "README.md").write_text("x")
        (aa / "verify.sh").write_text("#!/bin/bash\ndocker compose up -d\n")
        (aa / "verify.sh").chmod(0o755)
        e = run(r).errors
        results.append(("Appendix A verify.sh without mvn caught", any("Appendix A" in x for x in e), e))
        (aa / "verify.sh").write_text("#!/bin/bash\nmvn -B -ntp verify\n")
        ab = r / "examples/ab-demo"
        ab.mkdir(parents=True)
        (ab / "README.md").write_text("x")
        (ab / "kustomization.yaml").write_text("resources: []\n")
        (ab / "verify.sh").write_text("#!/bin/bash\nsource ../_k8s/lib.sh\nk8s_fresh .\ntrap k8s_teardown EXIT\n")
        (ab / "verify.sh").chmod(0o755)
        e = run(r).errors
        results.append(("Appendix B without the compose half caught", any("Appendix B" in x for x in e), e))
        (ab / "verify.sh").write_text("#!/bin/bash\nsource ../_k8s/lib.sh\nk8s_fresh .\ntrap k8s_teardown EXIT\n"
                                      "docker compose down -v --remove-orphans\n"
                                      "docker compose up -d --force-recreate --renew-anon-volumes --wait\n")
        (ax / "verify.sh").write_text("#!/bin/bash\ndocker compose down -v --remove-orphans\n"
                                      "docker compose up -d --build --force-recreate --renew-anon-volumes --wait\n")
        e = run(r).errors
        results.append(("fresh appendix examples A, B, E pass",
                        not any("verify.sh" in x for x in e), e))
        (ax / "app.yaml").write_text("      - image: quay.io/x/y:1.0\n        imagePullPolicy: Never\n")
        e = run(r).errors
        results.append(("appendix manifest Never on upstream image caught", any("imagePullPolicy" in x for x in e), e))
        (ax / "app.yaml").unlink()
        ah = r / "examples/ah-demo"
        ah.mkdir(parents=True)
        (ah / "README.md").write_text("x")
        (ah / "verify.sh").write_text("#!/bin/bash\noc get pods -n edp\n")
        (ah / "verify.sh").chmod(0o755)
        (ah / "kafkaconnect.yaml").write_text("  build:\n    output:\n      type: imagestream\n      image: edp-connect:35\n")
        e = run(r).errors
        ai = r / "examples/ai-demo"
        ai.mkdir(parents=True)
        (ai / "README.md").write_text("x")
        (ai / "kustomization.yaml").write_text("resources: []\n")
        (ai / "verify.sh").write_text("#!/bin/bash\nsource ../_k8s/lib.sh\nk8s_fresh .\ntrap k8s_teardown EXIT\n")
        (ai / "verify.sh").chmod(0o755)
        e = run(r).errors
        results.append(("Appendix I verify.sh without kueue_fresh and kueue_teardown caught",
                        any("Appendix I" in x for x in e), e))
        (ai / "verify.sh").write_text("#!/bin/bash\nsource ../_k8s/lib.sh\ntrap 'kueue_teardown; k8s_teardown' EXIT\n"
                                      "k8s_fresh .\nkueue_fresh\n")
        e = run(r).errors
        results.append(("Appendix I verify.sh with all four calls passes", not any("Appendix I" in x for x in e), e))
        (ai / "app.yaml").write_text("      - image: quay.io/x/y:1.0\n        imagePullPolicy: Never\n")
        e = run(r).errors
        results.append(("appendix I manifest Never on upstream image caught", any("imagePullPolicy" in x for x in e), e))
        (ai / "app.yaml").unlink()
        (r / "examples/_k8s/platform/kueue").mkdir(parents=True)
        (r / "examples/_k8s/platform/kueue/kueue-v1-manifests.yaml").write_text("      - image: quay.io/x/y:latest\n")
        e = run(r).errors
        results.append(("Kueue vendor manifest skipped by the image checks", not any("kueue-v1" in x for x in e), e))
        results.append(("Appendix H verify.sh without the CRC guard caught", any("Appendix H" in x for x in e), e))
        results.append(("imagestream output tag not taken for an image",
                        not any("edp-connect:35" in x for x in e), e))
        (ah / "kafkaconnect.yaml").write_text("  build:\n    output:\n      type: imagestream\n      image: edp-connect\n")
        e = run(r).errors
        results.append(("imagestream output without a tag caught", any("has no tag" in x for x in e), e))
        (ah / "kafkaconnect.yaml").write_text("  build:\n    output:\n      type: imagestream\n      image: edp-connect:latest\n")
        e = run(r).errors
        results.append(("imagestream output with :latest caught", any("uses :latest" in x for x in e), e))
        (ah / "kafkaconnect.yaml").write_text("      image: edp-connect:35\n")
        e = run(r).errors
        results.append(("bare image outside an imagestream output caught",
                        any("edp-connect:35" in x for x in e), e))
        (ah / "kafkaconnect.yaml").unlink()
        (ah / "install.sh").write_text("#!/bin/bash\n")
        (ah / "teardown.sh").write_text("#!/bin/bash\n")
        (ah / "verify.sh").write_text("#!/bin/bash\nsource ../../scripts/stack-guard.sh --crc\nsource ./crc-check.sh\n")
        e = run(r).errors
        results.append(("guarded Appendix H verify.sh passes", not any("Appendix H" in x for x in e), e))
        (r / "_docs/ah-demo.md").write_text("Build with podman and buildah.\n")
        (r / "_docs/02-demo.md").write_text("Build with podman.\n")
        e = run(r).errors
        results.append(("podman allowed only in Appendix H",
                        not any("ah-demo" in x and "podman" in x for x in e)
                        and any("02-demo" in x and "podman" in x for x in e), e))

        r = build("redis")
        rx = r / "examples/ag-demo/order-service"
        (rx / "src/main/resources").mkdir(parents=True)
        (rx / "pom.xml").write_text("<artifactId>quarkus-redis-client</artifactId>\n")
        e = run(r).errors
        results.append(("Redis Dev Services without the pinned image caught", any("Redis Dev Services" in x for x in e), e))
        (rx / "src/main/resources/application.properties").write_text(
            "quarkus.redis.devservices.image-name=" + REDIS_PIN + "\n")
        e = run(r).errors
        results.append(("Redis Dev Services with the pinned image passes", not any("Redis Dev Services" in x for x in e), e))

        # ---- deck rules
        long = " ".join(["word"] * 95)
        short = " ".join(["word"] * 50)

        def deck(slides, panel=(2,), creator=DECK_OWNER, name="d.pptx", extra_part=""):
            r = Path(tmp) / f"deck-{len(results)}"
            (r / "lgtm-presentation").mkdir(parents=True)
            ns = 'xmlns:a="a" xmlns:p="p" xmlns:dc="d" xmlns:cp="c"'
            with zipfile.ZipFile(r / "lgtm-presentation" / name, "w") as zf:
                zf.writestr("docProps/core.xml", f"<cp:coreProperties {ns}><dc:creator>{creator}</dc:creator>"
                            f"<cp:lastModifiedBy>{DECK_OWNER}</cp:lastModifiedBy></cp:coreProperties>")
                zf.writestr("docProps/app.xml", f"<Properties><Company>{DECK_OWNER}</Company></Properties>")
                if extra_part:
                    zf.writestr("ppt/extra.xml", extra_part)
                for i, (text, notes) in enumerate(slides, start=1):
                    bg = f'<p:bg><a:srgbClr val="{DECK_PANEL_FILL}"/></p:bg>' if i in panel else ""
                    zf.writestr(f"ppt/slides/slide{i}.xml", f"<p:sld {ns}>{bg}<a:p><a:r><a:t>{text}</a:t></a:r></a:p></p:sld>")
                    zf.writestr(f"ppt/slides/_rels/slide{i}.xml.rels",
                                f'<Relationships><Relationship Target="../notesSlides/notesSlide{i}.xml"/></Relationships>')
                    run_ = f"<a:p><a:r><a:t>{notes}</a:t></a:r></a:p>" if notes else ""
                    zf.writestr(f"ppt/notesSlides/notesSlide{i}.xml", f"<p:notes {ns}>{run_}</p:notes>")
            return run(r).errors

        good = [("Cover", short), ("Section", short), ("Body", long), ("Close", short)]
        e = deck(good)
        results.append(("deck passes", not e, e))
        no_deck = run(build("nodeck")).errors
        results.append(("deck absent is green", not no_deck, no_deck))
        for label, slides, kw in (
            ("deck divider floor needs the AB0000 background", good, {"panel": ()}),
            ("deck empty notes", [*good[:2], ("Body", ""), good[3]], {}),
            ("deck short content notes", [*good[:2], ("Body", short), good[3]], {}),
            ("deck short closing notes", [*good[:3], ("Close", "few words")], {}),
            ("deck wrong creator", good, {"creator": "Someone"}),
            ("deck em dash", [*good[:2], ("Body", long + " a \u2014 b"), good[3]], {}),
            ("deck en dash", [*good[:2], ("Body " + "\u2013", long), good[3]], {}),
            ("deck chapter reference", [*good[:2], ("Body", long + " see Chapter 7"), good[3]], {}),
            ("deck chapter in words", [*good[:2], ("Body", long + " in the outbox chapter"), good[3]], {}),
            ("deck self-reference", [*good[:2], ("Body", long + " in this chapter"), good[3]], {}),
            ("deck this deck", [*good[:2], ("Body", long + " this deck covers"), good[3]], {}),
            ("deck slide number", [*good[:2], ("Body", long + " on slide 4"), good[3]], {}),
            ("deck next slide", [*good[:2], ("Body", long + " the next slide shows"), good[3]], {}),
            ("deck shown earlier", [*good[:2], ("Body", long + " as shown earlier"), good[3]], {}),
            ("deck from before", [*good[:2], ("Body", long + " the figure from before"), good[3]], {}),
            ("deck as we saw", [*good[:2], ("Body", long + " as we saw"), good[3]], {}),
            ("deck covered later", [*good[:2], ("Body", long + " covered later"), good[3]], {}),
            ("deck comes later", [*good[:2], ("Body", long + " the detail comes later"), good[3]], {}),
            ("deck earlier on", [*good[:2], ("Body", long + " earlier on"), good[3]], {}),
            ("deck capitalised service", [*good[:2], ("Body", long + " Order-service publishes"), good[3]], {}),
            ("deck capitalised ksqlDB", [*good[:2], ("Body", long + " KsqlDB is not used"), good[3]], {}),
            ("deck positional above", [*good[:2], ("Body", long + " the table above"), good[3]], {}),
            ("deck the book off the last slide", [*good[:2], ("Body", long + " as the book says"), good[3]], {}),
            ("deck the site off the last slide", [*good[:2], ("Body", long + " on the site"), good[3]], {}),
        ):
            e = deck(slides, **kw)
            results.append((f"{label} caught", bool(e), e))
        for label, slides in (
            ("deck plain above is fine", [*good[:2], ("Body", long + " a weight above 30 kg"), good[3]]),
            ("deck earlier offsets is fine", [*good[:2], ("Body", long + " the earlier offsets are replayed"), good[3]]),
            ("deck an earlier version is fine", [*good[:2], ("Body", long + " an earlier version of the event"), good[3]]),
            ("deck comes later than is fine", [*good[:2], ("Body", long + " the payment event comes later than the shipment"), good[3]]),
            ("deck lower-case service is fine", [*good[:2], ("Body", long + " order-service publishes"), good[3]]),
            ("deck book pointer on the last slide is fine", [*good[:3], ("Close", short + " the book and the site")]),
        ):
            e = deck(slides)
            results.append((label, not e, e))
        e = deck(good, extra_part="<x>/" + "home/zzqq9/work</x>")
        results.append(("deck host_ids rule runs over every part (home path)", bool(e), e))

        # ---- SVG cross-references
        def svg_tree(name: str, fname: str, text: str) -> list[str]:
            r = build(name)
            (r / "diagrams" / fname).write_text(f'<svg xmlns="http://www.w3.org/2000/svg"><text x="1">{text}</text></svg>')
            return run(r).errors

        e = svg_tree("svgx", "02-x.svg", "See Chapter 7")
        results.append(("svg chapter reference caught", any("figure text" in x for x in e), e))
        e = svg_tree("svgx2", "02-x.svg", "Parts 1 to 5")
        results.append(("svg part reference caught", any("figure text" in x for x in e), e))
        e = svg_tree("svgx3", "ad-map.svg", "See Chapter 7")
        results.append(("svg ad-* exempt", not any("figure text" in x for x in e), e))
        e = svg_tree("svgx4", "02-x.svg", "Order flows to the broker")
        results.append(("svg clean text passes", not any("figure text" in x for x in e), e))

        # ---- dash coverage for the deck sources
        r = build("deckdash")
        (r / "lgtm-presentation/sections").mkdir(parents=True)
        (r / "lgtm-presentation/text").mkdir()
        (r / "lgtm-presentation/sections/01-a.js").write_text("// a \u2014 b\n")
        (r / "lgtm-presentation/text/d.md").write_text("ok\n")
        e = run(r).errors
        results.append(("deck source dash caught", any("sections/01-a.js:1" in x for x in e), e))
        (r / "lgtm-presentation/sections/01-a.js").write_text("// a, b\n")
        e = run(r).errors
        results.append(("deck source clean passes", not any("em dash" in x for x in e), e))

    ok = True
    for name, passed, errs in results:
        print(f"{'ok  ' if passed else 'FAIL'} {name}")
        if not passed:
            ok = False
            for x in errs:
                print(f"     {x}")
    return 0 if ok else 1


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--self-test", action="store_true")
    ap.add_argument("--root", type=Path, default=REPO)
    a = ap.parse_args()
    if a.self_test:
        return self_test()
    rep = run(a.root.resolve())
    for w in rep.warnings:
        print(f"warning: {w}")
    for e in rep.errors:
        print(f"error: {e}")
    if rep.errors:
        print(f"validate: {len(rep.errors)} problem(s)")
        return 1
    print("validate: ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
