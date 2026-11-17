// deck.js: "Event-Driven Patterns with Quarkus and Camel", the companion deck.
// Build from lgtm-presentation/ with: node deck.js [--out <path>]
// Loads sections/NN-*.js in name order (each exports function (lib)), then the
// QR slide last. With no sections it still builds a cover plus QR skeleton.

"use strict";

const fs = require("fs");
const path = require("path");
const H = require("./deck-helpers.js");
const { createDeck } = require("./deck-lib.js");
const { COLOR, FONT, W, ASSETS, newDeck } = H;

// Image paths are relative (./png, ./assets), so run from this directory; the
// paths end up in the slide XML and must not carry an absolute location.
const CALLER_CWD = process.cwd();
process.chdir(__dirname);

const NAME = "Event-Driven-Patterns-with-Quarkus-and-Camel.pptx";
const args = process.argv.slice(2);
const oi = args.indexOf("--out");
const OUT = path.resolve(CALLER_CWD, oi >= 0 ? args[oi + 1] : path.join(__dirname, NAME));
if (oi >= 0 && !args[oi + 1]) throw new Error("--out needs a path");

const pres = newDeck();
pres.title = "Event-Driven Patterns with Quarkus and Camel";
pres.subject = "Companion deck to Event-Driven Patterns with Quarkus and Camel";
pres.author = "PatternCatalyst";
pres.company = "PatternCatalyst";
pres.lastModifiedBy = "PatternCatalyst";

const lib = createDeck(pres);

// Cover
{
  const s = pres.addSlide();
  lib.bump();
  s.background = { color: COLOR.white };
  s.addImage({ path: `${ASSETS}/cover-panel.png`, x: 0, y: 0, w: W, h: 7.5 });
  s.addText("PATTERN CATALYST", {
    x: 6.00, y: 1.98, w: 6.90, h: 0.34,
    fontFace: FONT.title, fontSize: 14, bold: true, color: COLOR.red, charSpacing: 6,
    align: "left", valign: "middle",
  });
  s.addText([
    { text: "Event-Driven Patterns", options: { breakLine: true } },
    { text: "with Quarkus and Camel" },
  ], {
    x: 5.95, y: 2.42, w: 6.95, h: 2.00,
    fontFace: FONT.title, fontSize: 40, bold: true, color: COLOR.ink,
    align: "left", valign: "top",
  });
  s.addText("Kafka, Debezium, Strimzi and the patterns that keep services in step", {
    x: 6.00, y: 4.65, w: 6.70, h: 0.90,
    fontFace: FONT.body, fontSize: 18, italic: true, color: COLOR.caption,
    align: "left", valign: "top",
  });
  s.addImage({ path: `${ASSETS}/logo-candidate-2.png`, x: 11.10, y: 6.80, w: 1.55, h: 0.37 });
  lib.notes(s, `Event-driven patterns on one stack: Quarkus services and Apache Camel routes on Kafka, run first on Docker Compose and then on Kubernetes with Strimzi. Readers are Java developers, Camel integrators, platform engineers and architects who already know what a topic is. The code comes from tested examples, and every example uses one order-to-delivery domain with order, inventory, payment, shipping and notification services.`, 45, "cover");
}

// Sections, in name order.
const dir = path.join(__dirname, "sections");
const files = fs.existsSync(dir)
  ? fs.readdirSync(dir).filter((f) => /^\d\d-.*\.js$/.test(f)).sort()
  : [];
for (const f of files) require(path.join(dir, f))(lib);

// Closing QR slide
lib.qrSlide(
  [
    {
      png: "qr-site",
      label: "The site",
      blurb: "Chapters and runnable Quarkus and Camel examples for every pattern",
      url: "patterncatalyst.github.io/event-driven-patterns-with-quarkus-and-camel",
    },
    {
      png: "qr-hub",
      label: "Workshops & Tutorials",
      blurb: "The whole series: every workshop, tutorial, and deck in one place",
      url: "patterncatalyst.github.io/patterncatalyst-workshops-tutorials-list",
    },
  ],
  `Take it with you. On the left is the site for this book, with every chapter and the runnable Quarkus and Camel example behind each pattern. On the right is the Workshops and Tutorials hub, which lists every workshop, tutorial and deck in the Pattern Catalyst series in one place. Both are public on the Pattern Catalyst GitHub organization.`,
);

// Write the file. pptxgenjs sets creator, lastModifiedBy and company from the
// properties above, so docProps needs no patching.
lib.finish(OUT).catch((e) => {
  console.error(e);
  process.exit(1);
});
