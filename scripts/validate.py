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
import xml.dom.minidom
import zipfile
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parent.parent
MAX_WORDS = 2500
MAX_BRO_WORDS = 150
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
SELFREF_RE = re.compile(r"\b(this|the) (chapter|section)\b|\bthe example shows\b|\bwe will\b", re.I)
IMG_RE = re.compile(r"^\s*image:\s*(\S+)", re.M)


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
            if p.is_file() and not any(x in p.parts for x in ("_site", "target", "vendor", "node_modules")):
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
                   "README.md", "bibliography.md", "examples/**/README.md", "diagrams/*.svg"):
        for i, ln in enumerate(p.read_text(encoding="utf-8", errors="replace").split("\n"), 1):
            if DASH_RE.search(ln):
                rep.err(p, i, "em dash or en dash (use a comma, colon, parentheses or 'to')")


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
        rep.err(p, start + off, f"'{BRO_HEAD}' has {words} prose words, limit {MAX_BRO_WORDS}")
    bullets = [i for i, t in sec if re.match(r"\s*[-*] ", t)]
    if len(bullets) > MAX_BRO_BULLETS:
        rep.err(p, start + off, f"'{BRO_HEAD}' has {len(bullets)} bullets, limit {MAX_BRO_BULLETS}")
    for i, t in sec:
        m = CHECKNUM_RE.search(t)
        if m:
            rep.err(p, i + off, f"check number or run statistic '{m.group(0)}' in '{BRO_HEAD}' (belongs in the README)")


def check_chapters(rep: Report):
    for p in files(rep.root, "_docs/*.md"):
        text = p.read_text(encoding="utf-8")
        _, body, off = split_front(text)
        n = chapter_words(body)
        if n > MAX_WORDS:
            rep.err(p, None, f"{n} prose words, limit {MAX_WORDS}")
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
        for i, t in plines:
            if not t.lstrip().startswith(">"):
                sr = SELFREF_RE.search(t)
                if sr:
                    rep.err(p, i + off, f"self-reference '{sr.group(0)}' in prose")
            m = TESTNAME_RE.search(t)
            if m:
                rep.err(p, i + off, f"test-name token '{m.group(0)}' in prose")
            if not p.name.startswith(("00-", "a")):
                v = VERSION_RE.search(URL_RE.sub("", t))
                if v:
                    rep.err(p, i + off, f"version string '{v.group(0)}' outside chapter 00 and appendices")


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


def check_examples(rep: Report):
    ex = rep.root / "examples"
    if not ex.is_dir():
        return
    for d in sorted(ex.iterdir()):
        if not (d.is_dir() and re.match(r"\d+-", d.name)):
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
            if not (re.search(r"down -v --remove-orphans", body)
                    and re.search(r"up -d --build --force-recreate --renew-anon-volumes --wait", body)):
                rep.err(v, None, "verify.sh must start fresh: `down -v --remove-orphans`, then "
                                 "`up -d --build --force-recreate --renew-anon-volumes --wait`")
        if not (d / "README.md").is_file():
            rep.err(d / "README.md", None, "missing README.md")
    for c in files(rep.root, "examples/**/compose*.yaml", "examples/**/compose*.yml"):
        text = c.read_text(encoding="utf-8")
        for m in IMG_RE.finditer(text):
            ref = m.group(1).strip("\"'")
            ln = line_of(text, m.start())
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


def check_podman(rep: Report):
    roots = ["_docs/*", "_parts/*", "_example_pages/*", "_includes/*", "_layouts/*", "index.html", "chapters.html",
             "README.md", "bibliography.md", "examples/**/*", ".github/**/*", "diagrams/*", "scripts/*.sh"]
    for p in files(rep.root, *roots):
        rel = p.relative_to(rep.root)
        if rel.parts[0] == "_docs" and (re.search(r"-openshift", p.name) or p.name.startswith("ae-")):
            continue
        try:
            text = p.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for m in re.finditer(r"podman", text, re.I):
            rep.err(p, line_of(text, m.start()), "'podman' is only allowed in _plans/ and the Appendix G chapter")
            break


FORWARDING = re.compile(
    r"port-forward|minikube\s+tunnel|\bssh\s+(?:\S+\s+)*-[LRD]\b|ip_forward|\biptables\b|\bnft\b|"
    r"firewall-cmd|sysctl\s+-w|\bsocat\b", re.I)


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


CHECKS = [check_front_matter, check_liquid, check_diagrams, check_dashes, check_chapters,
          check_examples, check_camel_schemes, check_podman, check_no_forwarding, check_domain_names]


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

        r = build("bro")
        long_bro = "\n## Build, run, observe\n\n```bash\n./verify.sh\n```\n\n" + " ".join(["word"] * 151) + "\n"
        (r / "_docs/01-demo.md").write_text(good_doc + long_bro)
        e = run(r).errors
        results.append(("151-word build section caught", any("prose words, limit 150" in x for x in e), e))

        r = build("bro-ok")
        ok_bro = ("\n## Build, run, observe\n\n```bash\n./verify.sh\n```\n\n" + " ".join(["word"] * 144)
                  + "\n\n- Try one.\n- Try two.\n- Try three.\n")
        (r / "_docs/01-demo.md").write_text(good_doc + ok_bro)
        e = run(r).errors
        results.append(("150-word build section with three bullets allowed", not any("Build, run" in x for x in e), e))

        r = build("bro-check")
        (r / "_docs/01-demo.md").write_text(good_doc + "\n## Build, run, observe\n\nOnly check 12 fails.\n")
        e = run(r).errors
        results.append(("check number in build section caught", any("check number" in x for x in e), e))

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
