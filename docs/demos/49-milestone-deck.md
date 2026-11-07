---
title: "The milestone is presented as slides on the site itself"
description: "A meeting can be held from a page anyone can open, built and checked from the same sources as the rest of the site, with no separate slide tool."
status: accepted
updated: 2026-11-07
pr: 49
kind: visual
recorded: 2026-11-07
tests: [DemoCheckTest, DemoPagesTest, MarkdownRendererTest]
---

## What changed

Before, a presentation of the repository's state had to live in another tool and could drift from what the
repository shows. Now the Demos section holds a milestone deck: ten slides and four appendix slides in English, built
from one Markdown file, with an index, Previous, Next and Overview links on every slide, and speaker notes on a page
of their own. The documentation tool checks the deck before the site is published, including that the number of
planned capabilities on a slide matches the capabilities file.

## See it

- [The milestone deck](deck-milestone.md): "Start slides" opens slide one; each slide shows its number, its links and
  the revision it was built from. Slide two carries the status ledger.
- [The speaker notes](https://stig-johnny.github.io/shipping-reference-platform/demos/deck-milestone-notes.html):
  one section per slide, each linked back to it.

## Try it

From the repository root, with the reviewed JDK as `JAVA_HOME`, build the site; the build runs the checks first.

```sh
./gradlew :tools:docs:site
```

Then open `tools/docs/build/site/demos/deck-milestone.html`; for a printed copy, print it in landscape.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-07 on macOS with Temurin 25.0.5+8, on the branch of pull request #49 with this demonstration added:
`./gradlew :tools:docs:site` exited 0. The site task's own line, after the build's warnings (left out):

```text
docs: wrote the site, 60 files
```

Inspection record: [the deck](deck-milestone.md) and its notes were read in headless Chrome at 1920 by 1080, 1366 by
768 and 390 by 844 pixels, in light and dark, and printed to PDF: one slide per page, fourteen pages, no controls,
each with its code and revision. With Tab and Shift+Tab, at 1280 by 720, 1280 by 800, 1366 by 768 and 1920 by 1080,
every focused element stayed in view. The test classes named above check decks and their pages in every build.

## Limits

- The deck's words were reviewed by hand; the check cannot tell whether a sentence is true or names nobody it should
  not.
- The live demonstration on slide three is shown from a recording; this build did not rerun it.
- Text only until a media gate is reviewed.
