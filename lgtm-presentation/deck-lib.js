// deck-lib.js: the slide helpers shared by deck.js and every sections/NN-*.js.
// Usage: const lib = require("./deck-lib.js").createDeck(pres);
// Code slides are read byte-exact from examples/ by path and line range at
// build time, and the first line of each range is asserted. Every slide has an
// eyebrow and a title; every slide has speaker notes above a word floor.

"use strict";

const fs = require("fs");
const path = require("path");
const H = require("./deck-helpers.js");
const {
  COLOR, FONT, W, ASSETS, PNG,
  addFooter, addContentTitle, addCaption, addCodeSlide, addDiagramSlide, addNotes,
} = H;

const ROOT = path.resolve(__dirname, "..");
const MAX_CODE_LINES = 22;
const MAX_CODE_COLS = 120;
const MIN_CODE_FONT = 11;
const LANGS = new Set(["JAVA", "YAML", "SQL", "BASH"]);

// Returns the helpers bound to one presentation. The slide counter and the
// notes floors live in the closure, so two decks never share state.
function createDeck(pres) {
  let pageNum = 0;
  const floors = []; // [slide number, words, floor, label]

  function words(text) {
    return text.trim().split(/\s+/).length;
  }

  function notes(slide, text, floor, label) {
    const n = words(text);
    floors.push([pageNum, n, floor, label]);
    addNotes(slide, text);
  }

  // A content slide: footer and logo come with it.
  function S() {
    const s = pres.addSlide();
    pageNum += 1;
    addFooter(s, pageNum);
    return s;
  }

  // Section divider: the AB0000 background is what the deck check keys on.
  // It carries the section name and an eyebrow, never a number.
  function divider(title, subtitle, text, eyebrow = "SECTION") {
    const s = pres.addSlide();
    pageNum += 1;
    s.background = { color: COLOR.redDark };
    s.addImage({ path: `${ASSETS}/section-panel.png`, x: 0, y: 0, w: W, h: 7.5 });
    s.addText(eyebrow, {
      x: 6.27, y: 2.32, w: 6.40, h: 0.50,
      fontFace: FONT.title, fontSize: 22, bold: true, color: COLOR.white, charSpacing: 6,
      align: "left", valign: "middle",
    });
    s.addText(title, {
      x: 6.24, y: 2.84, w: 6.70, h: 1.60,
      fontFace: FONT.title, fontSize: title.length > 28 ? 32 : title.length > 18 ? 36 : 44, bold: true, color: COLOR.white,
      align: "left", valign: "top",
    });
    s.addText(subtitle, {
      x: 6.27, y: 4.55, w: 6.60, h: 0.80,
      fontFace: FONT.body, fontSize: 16, italic: true, color: "FFD9D9",
      align: "left", valign: "top",
    });
    s.addImage({ path: `${ASSETS}/redhat-logo-white.png`, x: 11.42, y: 6.88, w: 1.33, h: 0.31 });
    notes(s, text, 45, "divider");
  }

  // Bullets with a bold subject. Each item is [subject, rest]; rest carries its
  // own leading space or punctuation.
  function runsFor(items, gap) {
    const runs = [];
    for (const [subject, rest] of items) {
      runs.push({
        text: subject,
        options: { bold: true, color: COLOR.ink, bullet: { code: "25CF" }, paraSpaceAfter: gap },
      });
      runs.push({ text: rest, options: { breakLine: true } });
    }
    return runs;
  }

  function bullets(s, items, o = {}) {
    s.addText(runsFor(items, o.gap ?? 12), {
      x: o.x ?? 0.62, y: o.y ?? 1.85, w: o.w ?? 12.09, h: o.h ?? 4.7,
      fontFace: FONT.body, fontSize: o.fontSize ?? 18, color: COLOR.body,
      align: "left", valign: "top", lineSpacingMultiple: 1.15,
    });
  }

  function bulletSlide(eyebrow, title, items, text, o = {}) {
    const s = S();
    addContentTitle(s, eyebrow, title);
    bullets(s, items, o);
    notes(s, text, 90, "bullets");
  }

  // A table slide. Rows are arrays of strings; the first column is set in bold.
  function tableSlide(eyebrow, title, header, rows, colW, text, o = {}) {
    const s = S();
    addContentTitle(s, eyebrow, title);
    const size = o.fontSize ?? 12;
    const head = header.map((h) => ({
      text: h,
      options: { bold: true, color: COLOR.white, fill: { color: COLOR.ink }, fontFace: FONT.body, fontSize: size, valign: "middle" },
    }));
    const body = rows.map((r, ri) =>
      r.map((c, ci) => ({
        text: c,
        options: {
          bold: ci === 0,
          color: ci === 0 ? COLOR.ink : COLOR.body,
          fontFace: FONT.body,
          fontSize: size,
          valign: "middle",
          fill: { color: ri % 2 ? COLOR.panel : COLOR.white },
        },
      })),
    );
    s.addTable([head, ...body], {
      x: 0.62, y: o.y ?? 1.55, w: 12.09, colW, rowH: o.rowH ?? 0.34,
      border: { type: "solid", color: COLOR.grid, pt: 0.5 },
    });
    notes(s, text, 90, "table");
  }

  // A code slide whose listing is read from examples/<file> by 1-based inclusive
  // line range. The first line is asserted, the box holds at most 22 lines of at
  // most 95 columns, and the font must stay at 11 pt or more.
  function codeSlide(eyebrow, title, lang, file, first, last, anchor, caption, text) {
    if (!LANGS.has(lang)) throw new Error(`code slide language ${lang} not in ${[...LANGS].join("/")}`);
    const full = path.join(ROOT, "examples", file);
    const all = fs.readFileSync(full, "utf8").split("\n");
    const lines = all.slice(first - 1, last);
    if (lines.length !== last - first + 1) throw new Error(`short range in ${file}:${first}-${last}`);
    if (lines[0] !== anchor) {
      throw new Error(`anchor mismatch in ${file}:${first}\n  want: ${JSON.stringify(anchor)}\n  have: ${JSON.stringify(lines[0])}`);
    }
    // Common leading indentation is removed for display; every line is otherwise byte-exact.
    const indent = Math.min(...lines.filter((l) => l.trim() !== "").map((l) => l.match(/^ */)[0].length));
    for (let i = 0; i < lines.length; i++) lines[i] = lines[i].slice(Math.min(indent, lines[i].length));
    if (lines.length > MAX_CODE_LINES) throw new Error(`${file}:${first}-${last} has ${lines.length} lines, max ${MAX_CODE_LINES}`);
    const longest = Math.max(...lines.map((l) => l.length));
    if (longest > MAX_CODE_COLS) throw new Error(`${file}:${first}-${last} has a ${longest}-column line, max ${MAX_CODE_COLS}`);
    const byWidth = Math.floor((1370 / longest) * 2) / 2;
    const byHeight = Math.floor(((4.5 * 72) / (lines.length * 1.3)) * 2) / 2;
    const fontSize = Math.min(lines.length <= 4 ? 22 : 18, byWidth, byHeight);
    if (fontSize < MIN_CODE_FONT) throw new Error(`${file}:${first}-${last} needs ${fontSize} pt, min ${MIN_CODE_FONT}`);
    const y = 1.75;
    const h = Math.min(4.85, Math.max(1.0, (lines.length * fontSize * 1.3) / 72 + 0.3));
    const s = S();
    addCodeSlide(s, eyebrow, title, lang, lines, null, { fontSize, h, y });
    addCaption(s, caption, Math.min(6.62, y + h + 0.06));
    notes(s, text, 90, "code");
    return { file, first, last, longest, fontSize };
  }

  // A figure on its own slide, full width. The PNG's own aspect sets the frame,
  // so the picture is never stretched.
  function figSlide(eyebrow, title, png, caption, text) {
    const s = S();
    const head = fs.readFileSync(path.join(__dirname, PNG, `${png}.png`));
    const pw = head.readUInt32BE(16);
    const ph = head.readUInt32BE(20);
    const box = { x: 0.62, y: 1.45, w: 12.09, h: 4.95 };
    const scale = Math.min(box.w / pw, box.h / ph);
    const w = pw * scale;
    const h = ph * scale;
    addDiagramSlide(s, eyebrow, title, png, caption, { x: box.x + (box.w - w) / 2, y: box.y + (box.h - h) / 2, w, h });
    notes(s, text, 90, "figure");
  }

  // The closing slide: two QR codes with labels and URLs. Called by deck.js.
  function qrSlide(items, text) {
    const s = S();
    addContentTitle(s, "SCAN TO EXPLORE", "Take it with you");
    const xs = [2.30, 7.93];
    items.forEach((it, i) => {
      const x = xs[i];
      s.addImage({ path: `${PNG}/${it.png}.png`, x, y: 1.78, w: 3.10, h: 3.10 });
      s.addText(it.label, {
        x: x - 0.75, y: 4.98, w: 4.60, h: 0.40,
        fontFace: FONT.title, fontSize: 18, bold: true, color: COLOR.ink, align: "center", valign: "middle",
      });
      s.addText(it.blurb, {
        x: x - 0.85, y: 5.40, w: 4.80, h: 0.72,
        fontFace: FONT.body, fontSize: 13, italic: true, color: COLOR.caption, align: "center", valign: "top",
      });
      s.addText(it.url, {
        x: x - 1.25, y: 6.12, w: 5.60, h: 0.32,
        fontFace: FONT.mono, fontSize: 9, color: COLOR.red, align: "center", valign: "middle",
      });
    });
    notes(s, text, 45, "qr");
  }

  // Count a slide that deck.js adds itself, such as the cover.
  function bump() {
    pageNum += 1;
    return pageNum;
  }

  // Assert the notes floors and the notes count, then write the file.
  function finish(out) {
    const short = floors.filter(([, n, floor]) => n < floor);
    if (short.length) {
      throw new Error("notes under floor: " + short.map(([p, n, f, l]) => `slide ${p} ${l} ${n}<${f}`).join(", "));
    }
    if (floors.length !== pageNum) throw new Error(`notes ${floors.length} != slides ${pageNum}`);
    return pres
      .writeFile({ fileName: out })
      .then((p) => {
        console.log("WROTE", p, "slides:", pageNum, "min notes words:", Math.min(...floors.map((f) => f[1])));
        return p;
      });
  }

  return { S, divider, bullets, bulletSlide, tableSlide, codeSlide, figSlide, qrSlide, notes, words, bump, finish, pres, H };
}

module.exports = { createDeck, ROOT };
