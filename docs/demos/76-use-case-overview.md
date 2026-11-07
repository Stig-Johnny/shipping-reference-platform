---
title: "Each use case shows what its tests showed"
description: "A reader sees, for every capability, who uses it, whether it is built, and whether every test named for it passed in the build that made this site, without trusting a status written by hand."
status: accepted
updated: 2026-11-07
pr: 76
kind: visual
recorded: 2026-11-07
tests: [UseCasePageTest, CapabilityActorsTest, TestEvidenceTest]
---

## What changed

Before, the site listed capabilities and their status, but not who uses each one, and a reader could not see at a
glance which of their tests had passed. Now the Model section has a use case sheet. Every capability names its actors
from the model, and the documentation tool rejects an actor the model does not have or does not connect to the
capability's module. A table, grouped by module, stamps each use case with what its named tests showed in the build
that generated the site: all passed, some failing, not run or missing, or no tests yet. A use case is stamped "All
passed" only when every test named for it passed; an empty list, a skipped test or a missing report never is. A map
of actors and use cases follows the table.

## See it

- [The use case sheet](https://stig-johnny.github.io/shipping-reference-platform/model/use-cases.html): the table
  first, with each use case's status and stamp; then the actor map, which enlarges and scrolls. The legend says what a
  stamp means and what it does not.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-07 on macOS with Temurin 25.0.5+8, on revision a7f174e of pull request #76: `./gradlew check`
exited 0, and the site task wrote the site. The site task's own line, after the build's warnings (left out):

```text
docs: wrote the site, 64 files
```

Inspection record: [the use case sheet](https://stig-johnny.github.io/shipping-reference-platform/model/use-cases.html)
was read in headless Chrome at 1440 pixels in light and dark, at 390 pixels in light and dark, and at 360 pixels in
light. The table comes before the map, every stamp carries its words as well as its border, and no width scrolls the
page sideways; the map's labels are readable once it is enlarged. The test classes named above check the page, the
actors and the evidence reader in every build. Reviewed in pull request #76.

## Limits

- A stamp says that the named tests passed in this build. It is not a trace of the use case being carried out, and
  the arrows of the map are declared in the registry, not observed.
- The site reads the reports of the ordinary test tasks. Tests that other producers run, such as database or browser
  tests, show as not run until the site reads their reports.
- A use case without an actor names none in the registry; that does not establish how it is triggered.
