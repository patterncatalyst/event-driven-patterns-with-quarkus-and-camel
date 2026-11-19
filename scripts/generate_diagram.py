#!/usr/bin/env python3
"""Tiny diagram compiler for a chaptered documentation site.

Each diagram is a spec of bands (background lanes), nodes (rounded boxes with a
bold title line + smaller grey detail lines), edges (arrows with optional
labels), and free notes. emit() writes a clean themed SVG (what the site embeds)
and a valid Excalidraw source (editable companion) into diagrams/.
"""
import html
import json
import random
import xml.dom.minidom
from pathlib import Path

OUT: str | Path = "."  # output dir; callers set: generate_diagram.OUT = "diagrams"

# ---- palette --------------------------------------------------------------
STYLES = {
    "box":    ("#ffffff", "#111111"),
    "sub":    ("#ffffff", "#999999"),
    "accent": ("#fff8ef", "#e8870c"),
    "kernel": ("#f4f4f4", "#888888"),
    "user":   ("#eef4fb", "#2f6db5"),
    "ghost":  ("#ffffff", "#999999"),  # dashed
    "ink":    ("#f1f5f9", "#64748b"),  # light slate fill, slate border, dark text
}
INK = "#111111"
GREY = "#555555"
AMBER = "#b8650a"

# Seeded per figure in emit(), so a spec regenerates byte-identical files.
_RNG = random.Random()
def _seed(): return _RNG.randint(1, 2_000_000_000)
def esc(s): return html.escape(str(s), quote=True)

# ---- EIP stencil (Hohpe & Woolf integration icons) ------------------------
# A small, house-themed approximation of the Enterprise Integration Patterns
# visual vocabulary: a message, a channel (pipe), an endpoint, and the pattern
# glyphs a Camel/event book leans on (router, translator, filter, splitter,
# aggregator, enricher). Each icon draws a pictogram in the cell (x, y, w, h)
# with an optional grey label centred underneath, in the same palette as the
# rest of the generator. Kinds are the EIP pattern names.
EIP_KINDS = ("message", "channel", "pipe", "endpoint", "router", "translator",
             "filter", "splitter", "aggregator", "enricher")


def _eip_icon(kind, x, y, w, h, label=None):
    LINE, ACC, WASH = "#555555", "#e8870c", "#fff8ef"
    o = []
    lab_h = 18 if label else 0
    gw, gh = w, h - lab_h
    cx, cy = x + gw / 2, y + gh / 2

    def box(bx, by, bw, bh, fill="#ffffff", stroke=LINE, sw=2, rx=4):
        o.append(f'<rect x="{bx:.1f}" y="{by:.1f}" width="{bw:.1f}" height="{bh:.1f}" '
                 f'rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>')

    def pipe(px, py, pw, ph, fill=WASH):
        r = ph / 2
        o.append(f'<rect x="{px:.1f}" y="{py:.1f}" width="{pw:.1f}" height="{ph:.1f}" '
                 f'rx="{r:.1f}" fill="{fill}" stroke="{LINE}" stroke-width="2"/>')
        o.append(f'<ellipse cx="{px+pw-r:.1f}" cy="{py+r:.1f}" rx="{r*0.42:.1f}" ry="{r*0.78:.1f}" '
                 f'fill="none" stroke="{LINE}" stroke-width="1.3" opacity="0.6"/>')

    def arrow(x1, y1, x2, y2, col=LINE):
        o.append(f'<path d="M{x1:.1f},{y1:.1f} L{x2:.1f},{y2:.1f}" fill="none" '
                 f'stroke="{col}" stroke-width="1.8" marker-end="url(#a)"/>')

    def dot(dx, dy, r, fill=ACC):
        o.append(f'<circle cx="{dx:.1f}" cy="{dy:.1f}" r="{r:.1f}" fill="{fill}" stroke="{LINE}" stroke-width="1.3"/>')

    if kind == "message":
        mw, mh = min(gw * 0.62, 48), min(gh * 0.66, 40)
        mx, my = cx - mw / 2, cy - mh / 2
        box(mx, my, mw, mh, fill="#ffffff", rx=3)
        o.append(f'<path d="M{mx:.1f},{my+9:.1f} h{mw:.1f}" stroke="{LINE}" stroke-width="1"/>')
        o.append(f'<rect x="{mx:.1f}" y="{my:.1f}" width="{mw:.1f}" height="9" rx="3" fill="{ACC}"/>')
        for i in range(2):
            o.append(f'<path d="M{mx+6:.1f},{my+20+i*8:.1f} h{mw-12:.1f}" stroke="{LINE}" '
                     f'stroke-width="1.4" opacity="0.6"/>')
    elif kind in ("channel", "pipe"):
        ph = min(gh * 0.5, 30)
        pipe(cx - gw * 0.4, cy - ph / 2, gw * 0.8, ph)
    elif kind == "endpoint":
        ph = min(gh * 0.5, 30)
        pipe(cx - gw * 0.42, cy - ph / 2, gw * 0.7, ph)
        dot(cx + gw * 0.30, cy, ph * 0.46)
    elif kind == "router":
        ph = min(gh * 0.42, 26)
        pipe(cx - gw * 0.42, cy - ph / 2, gw * 0.5, ph)
        hub = cx + gw * 0.06
        arrow(hub, cy, cx + gw * 0.40, cy - gh * 0.30)
        arrow(hub, cy, cx + gw * 0.40, cy + gh * 0.30)
    elif kind == "translator":
        bw, bh = min(gw * 0.74, 70), min(gh * 0.6, 40)
        bx, by = cx - bw / 2, cy - bh / 2
        box(bx, by, bw, bh, fill=WASH)
        dot(bx + bh * 0.5, cy, bh * 0.28, fill=LINE)
        arrow(bx + bh * 0.9, cy, bx + bw - bh * 0.9, cy)
        tx = bx + bw - bh * 0.5
        o.append(f'<path d="M{tx:.1f},{cy-bh*0.28:.1f} L{tx+bh*0.26:.1f},{cy+bh*0.24:.1f} '
                 f'L{tx-bh*0.26:.1f},{cy+bh*0.24:.1f} Z" fill="{ACC}" stroke="{LINE}" stroke-width="1.2"/>')
    elif kind == "filter":
        ph = min(gh * 0.46, 28)
        pipe(cx - gw * 0.4, cy - ph / 2, gw * 0.8, ph)
        fw = ph * 1.2
        o.append(f'<path d="M{cx-fw/2:.1f},{cy-ph*0.6:.1f} L{cx+fw/2:.1f},{cy-ph*0.6:.1f} '
                 f'L{cx+fw*0.18:.1f},{cy+ph*0.1:.1f} L{cx+fw*0.18:.1f},{cy+ph*0.6:.1f} '
                 f'L{cx-fw*0.18:.1f},{cy+ph*0.6:.1f} L{cx-fw*0.18:.1f},{cy+ph*0.1:.1f} Z" '
                 f'fill="{ACC}" stroke="{LINE}" stroke-width="1.3"/>')
    elif kind in ("splitter", "aggregator"):
        s = min(gh * 0.2, 13)
        many_x = x + gw * (0.12 if kind == "splitter" else 0.0)
        one_x = x + gw * (0.74 if kind == "splitter" else 0.80)
        if kind == "aggregator":
            many_x, one_x = x + gw * 0.08, x + gw * 0.70
        ys = [cy - gh * 0.26, cy, cy + gh * 0.26]
        if kind == "splitter":
            box(many_x, cy - s / 2, s, s, fill=WASH)
            for yy in ys:
                box(one_x, yy - s / 2, s, s)
                arrow(many_x + s, cy, one_x, yy)
        else:
            for yy in ys:
                box(many_x, yy - s / 2, s, s)
                arrow(many_x + s, yy, one_x, cy)
            box(one_x, cy - s / 2, s, s, fill=WASH)
    elif kind == "enricher":
        ph = min(gh * 0.46, 28)
        pipe(cx - gw * 0.4, cy - ph / 2, gw * 0.8, ph)
        r = ph * 0.52
        dot(cx, cy, r, fill="#ffffff")
        o.append(f'<path d="M{cx-r*0.55:.1f},{cy:.1f} h{r*1.1:.1f} M{cx:.1f},{cy-r*0.55:.1f} '
                 f'v{r*1.1:.1f}" stroke="{ACC}" stroke-width="2.2"/>')
    else:
        box(x, y, gw, gh)  # unknown kind: plain box, keeps the build valid

    if label:
        o.append(f'<text x="{cx:.1f}" y="{y+h-4:.1f}" font-size="11" fill="#555555" '
                 f'text-anchor="middle">{esc(label)}</text>')
    return o


# ---- SVG ------------------------------------------------------------------
def _svg(width, height, bands, nodes, edges, notes, icons):
    o = []
    o.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" '
             f'font-family="\'Red Hat Text\', system-ui, sans-serif" role="img">')
    o.append('<defs>'
             '<marker id="a" viewBox="0 0 10 10" refX="8.5" refY="5" '
             'markerWidth="9" markerHeight="9" orient="auto-start-reverse">'
             '<path d="M0,0 L10,5 L0,10 z" fill="#555"/></marker>'
             '<marker id="am" viewBox="0 0 10 10" refX="8.5" refY="5" '
             'markerWidth="9" markerHeight="9" orient="auto-start-reverse">'
             '<path d="M0,0 L10,5 L0,10 z" fill="#b8650a"/></marker>'
             '</defs>')
    for b in bands:
        x, y, w, h = b["x"], b["y"], b["w"], b["h"]
        label, fill = b.get("label", ""), b.get("fill", "#fafafa")
        o.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="{fill}" '
                 f'stroke="#ddd"/>')
        if label:
            o.append(f'<text x="{x+12}" y="{y+20}" font-size="12" font-weight="700" '
                     f'fill="#888">{esc(label)}</text>')
    # Nodes are drawn BEFORE edges so arrowheads render on top of the boxes
    # (otherwise a box painted afterwards hides the head and the arrow looks
    # like it stops short or disappears behind the box).
    for n in nodes:
        fill, stroke = STYLES[n.get("style","box")]
        dash = ' stroke-dasharray="6 4"' if n.get("style")=="ghost" else ""
        sw = 2 if n.get("style") in ("box","accent","user","ink") else 1.5
        x,y,w,h = n["x"],n["y"],n["w"],n["h"]
        o.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="9" fill="{fill}" '
                 f'stroke="{stroke}" stroke-width="{sw}"{dash}/>')
        lines = n["lines"]
        cx = x+w/2
        tcol = INK
        scol = GREY
        n_extra = len(lines)-1
        block_h = 17 + n_extra*15
        ty = y + (h-block_h)/2 + 14
        o.append(f'<text x="{cx}" y="{ty}" font-size="13.5" font-weight="700" fill="{tcol}" '
                 f'text-anchor="middle">{esc(lines[0])}</text>')
        for i,ln in enumerate(lines[1:]):
            o.append(f'<text x="{cx}" y="{ty+17+i*15}" font-size="11.5" fill="{scol}" '
                     f'text-anchor="middle">{esc(ln)}</text>')
    for e in edges:
        col = "#b8650a" if e.get("amber") else "#555"
        mk = "am" if e.get("amber") else "a"
        dash = ' stroke-dasharray="5 4"' if e.get("dashed") else ""
        ms = f' marker-start="url(#{mk})"' if e.get("bidir") else ""
        o.append(f'<path d="M{e["x1"]},{e["y1"]} L{e["x2"]},{e["y2"]}" fill="none" '
                 f'stroke="{col}" stroke-width="2"{dash}{ms} marker-end="url(#{mk})"/>')
        if e.get("label"):
            mx,my = (e["x1"]+e["x2"])/2, (e["y1"]+e["y2"])/2
            lx,ly = mx+e.get("lx",6), my+e.get("ly",-6)
            # white halo under the label so it stays legible over a line/box
            o.append(f'<text x="{lx}" y="{ly}" font-size="11" fill="#ffffff" stroke="#ffffff" '
                     f'stroke-width="3" paint-order="stroke" text-anchor="middle">'
                     f'{esc(e["label"])}</text>')
            o.append(f'<text x="{lx}" y="{ly}" font-size="11" fill="{col}" '
                     f'text-anchor="middle">{esc(e["label"])}</text>')
    for t in notes:
        anchor = t.get("anchor","start")
        bold = ' font-weight="700"' if t.get("bold") else ''
        o.append(f'<text x="{t["x"]}" y="{t["y"]}" font-size="{t.get("size",11)}" '
                 f'fill="{t.get("color","#888")}" text-anchor="{anchor}"{bold}>'
                 f'{esc(t["text"])}</text>')
    for ic in icons:
        o.extend(_eip_icon(ic["kind"], ic["x"], ic["y"], ic["w"], ic["h"], ic.get("label")))
    o.append('</svg>')
    return "\n".join(o)

# ---- Excalidraw -----------------------------------------------------------
def _exc(bands, nodes, edges, notes, icons):
    els = []
    def rect(x,y,w,h,stroke,bg,dashed=False):
        els.append({"id":f"r{_seed()}","type":"rectangle","x":x,"y":y,"width":w,"height":h,"angle":0,
            "strokeColor":stroke,"backgroundColor":bg,"fillStyle":"solid","strokeWidth":2,
            "strokeStyle":"dashed" if dashed else "solid","roughness":1,"opacity":100,"groupIds":[],
            "frameId":None,"roundness":{"type":3},"seed":_seed(),"versionNonce":_seed(),"isDeleted":False,
            "boundElements":[],"updated":1,"link":None,"locked":False})
    def text(x,y,s,size=14,color="#111111"):
        els.append({"id":f"t{_seed()}","type":"text","x":x,"y":y,"width":max(40,len(s)*size*0.55),
            "height":size*1.25,"angle":0,"strokeColor":color,"backgroundColor":"transparent","fillStyle":"solid",
            "strokeWidth":1,"strokeStyle":"solid","roughness":1,"opacity":100,"groupIds":[],
            "frameId":None,"roundness":None,"seed":_seed(),"versionNonce":_seed(),
            "isDeleted":False,"boundElements":[],"updated":1,"link":None,"locked":False,
            "text":s,"fontSize":size,"fontFamily":1,"textAlign":"left",
            "verticalAlign":"top","containerId":None,"originalText":s,"lineHeight":1.25,
            "baseline":size})
    def arrow(x1,y1,x2,y2,color="#555555",dashed=False):
        els.append({"id":f"a{_seed()}","type":"arrow","x":x1,"y":y1,"width":abs(x2-x1),"height":abs(y2-y1),
            "angle":0,"strokeColor":color,"backgroundColor":"transparent","fillStyle":"solid","strokeWidth":2,
            "strokeStyle":"dashed" if dashed else "solid","roughness":1,"opacity":100,"groupIds":[],
            "frameId":None,"roundness":{"type":2},"seed":_seed(),"versionNonce":_seed(),
            "isDeleted":False,"boundElements":[],"updated":1,"link":None,"locked":False,
            "points":[[0,0],[x2-x1,y2-y1]],"lastCommittedPoint":None,
            "startBinding":None,"endBinding":None,"startArrowhead":None,
            "endArrowhead":"arrow"})
    for b in bands:
        rect(b["x"],b["y"],b["w"],b["h"],"#dddddd",b.get("fill","#fafafa"))
        if b.get("label"):
            text(b["x"]+12,b["y"]+6,b["label"],12,"#888888")
    for n in nodes:
        fill,stroke = STYLES[n.get("style","box")]
        rect(n["x"],n["y"],n["w"],n["h"],stroke,fill,dashed=(n.get("style")=="ghost"))
        text(n["x"]+10,n["y"]+8,n["lines"][0],14,"#111111")
        for i,ln in enumerate(n["lines"][1:]):
            text(n["x"]+10,n["y"]+28+i*15,ln,11,"#555555")
    for e in edges:
        col = "#b8650a" if e.get("amber") else "#555555"
        arrow(e["x1"],e["y1"],e["x2"],e["y2"],col,dashed=e.get("dashed",False))
        if e.get("label"):
            text((e["x1"]+e["x2"])/2,(e["y1"]+e["y2"])/2-14,e["label"],11,col)
    for t in notes:
        text(t["x"],t["y"]-10,t["text"],t.get("size",11),t.get("color","#888888"))
    # EIP icons: the SVG carries the true pictogram; the editable companion gets
    # a labelled accent box so the source stays valid and adjustable.
    for ic in icons:
        lab_h = 18 if ic.get("label") else 0
        rect(ic["x"], ic["y"], ic["w"], ic["h"]-lab_h, "#e8870c", "#fff8ef")
        text(ic["x"]+6, ic["y"]+ic["h"]-lab_h-22, ic["kind"], 11, "#b8650a")
        if ic.get("label"):
            text(ic["x"]+6, ic["y"]+ic["h"]-16, ic["label"], 11, "#555555")
    return {"type":"excalidraw","version":2,"source":"https://excalidraw.com","elements":els,
            "appState":{"viewBackgroundColor":"#ffffff","gridSize":None},"files":{}}

def emit(name, width, height, bands=None, nodes=None, edges=None, notes=None, icons=None):
    bands = bands or []
    nodes = nodes or []
    edges = edges or []
    notes = notes or []
    icons = icons or []
    _RNG.seed(name)
    svg = Path(OUT) / f"{name}.svg"
    exc = Path(OUT) / f"{name}.excalidraw"
    svg.write_text(_svg(width, height, bands, nodes, edges, notes, icons), encoding="utf-8")
    exc.write_text(json.dumps(_exc(bands, nodes, edges, notes, icons), indent=1), encoding="utf-8")
    # validate
    xml.dom.minidom.parseString(svg.read_text(encoding="utf-8"))
    json.loads(exc.read_text(encoding="utf-8"))
    print("emit", name)
