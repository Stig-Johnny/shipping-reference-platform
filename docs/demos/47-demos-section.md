---
title: "What a pull request changed can now be read on the site"
description: "A reader without a GitHub account sees what a reviewed or visible change did, how to check it and what it does not prove, and every agent knows when to write one."
status: accepted
updated: 2026-11-07
pr: 47
kind: visual
recorded: 2026-11-07
tests: [DemoCheckTest, DemoPagesTest]
---

## What changed

Before, the site had no place that showed what a pull request changed: a reader had to open GitHub and read the
code. Now the register has a Demos section, second after Start, with a list of demonstrations, newest first, and a
page on how to write one. Each demonstration is a sheet: the outcome, where to see it, how to try it, the evidence
and the limits. The documentation tool checks every demonstration before the site is published.

## See it

- [The register of demos](https://stig-johnny.github.io/shipping-reference-platform/demos/index.html): Demos is the
  second section of the register, sheet X-00. Each row is an outcome, its pull request, its kind and the date its
  evidence was recorded.
- [How to write a demo](how-to.md): which pull requests need one, the template, the commands a demo may show and
  what the tool checks.

## Try it

From the repository root, with the reviewed JDK as `JAVA_HOME`, build the site; the build runs the checks first.

```sh
./gradlew :tools:docs:site
```

Then open `tools/docs/build/site/demos/index.html` and this sheet, `demos/47-demos-section.html`.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-07 on macOS with Temurin 25.0.5+8, on the branch of pull request #47 after main was merged into it,
with this demonstration added: `./gradlew :tools:docs:site` exited 0. The site task's own line, after the build's
warnings (left out):

```text
docs: wrote the site, 57 files
```

Inspection record: the [register of demos](https://stig-johnny.github.io/shipping-reference-platform/demos/index.html)
and the how-to were read in headless Chrome at 1280, 390 and 320 px, in light and dark: the register lists this
demonstration, each code sits in its column, and no page scrolls sideways. The test classes named above check the
demonstrations in every build.

## Limits

- Recorded output is not rerun when the site is built; a reviewer compares it with its source.
- Text only until a media gate is reviewed: the screenshots of a visible change go to the owner with the review
  request, not to this site.
- The check cannot tell whether a sentence is true, or whether a page names nobody it should not.
