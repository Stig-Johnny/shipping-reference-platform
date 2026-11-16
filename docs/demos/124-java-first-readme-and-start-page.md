---
title: "A Java reader meets the Java architecture first"
description: "The README and the site's start page now open with what the application is in Java terms, how to run it and where the walkthrough is, before the process material."
status: accepted
updated: 2026-11-16
pr: 124
kind: visual
recorded: 2026-11-16
tests: [SitePagesTest]
---

## What changed

Before, the README opened with the milestone deck, the scenario coverage ledger, the Brain and the contribution
records, and the site's first reading path was the COO's. A Java reader met the process before the design.

Now the README opens with what the application is in Java terms, then "The Java architecture" in eight points linked
to the walkthrough, then "Run it" with the one command that starts the API. "What exists today" lists the shipping API
and its migrations first, and the former opening paragraphs sit under "How this was built" after it; the opening's
"Start the demo" line is gone, with its milestone deck link, until step 3 retires the deck. The start page's purpose
line is the README's new first sentence, its first reading path is the Java lead's, and its README card and the
README's sheet describe the new order.

## See it

- [The start page](https://stig-johnny.github.io/shipping-reference-platform/index.html#reading-paths): the purpose
  line under the title, then "Reading path for a Java lead" before the COO's.
- [The repository's README](../../README.md): "The Java architecture" and "Run it" follow the first paragraph.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-16 on macOS with Temurin 25.0.5+8. `SitePagesTest` was changed first. It expects the new purpose
line and the Java reading path before the COO's on the start page, and on the README's sheet "The Java architecture",
"Run it" with the command, "What exists today" and "How this was built" in that order. Run with main's README and this
pull request's start page, the README test and the purpose line failed; with this pull request's README and main's
start page, the reading-path order failed. The excerpts omit the assertions' page dumps:

```text
SitePagesTest > theReadmeSheetLeadsWithTheJavaArchitectureAndTheCommandThatRunsIt() FAILED
    java.lang.AssertionError at SitePagesTest.java:99
SitePagesTest > theStartPageStatesThePurposeTheStateTheDataAndTheJavaReadingPathFirst() FAILED
    java.lang.AssertionError at SitePagesTest.java:84
36 tests completed, 2 failed
```

```text
SitePagesTest > theStartPageStatesThePurposeTheStateTheDataAndTheJavaReadingPathFirst() FAILED
    java.lang.AssertionError at SitePagesTest.java:90
36 tests completed, 1 failed
```

With both changes all 36 tests passed. Inspection record: the site built with `./gradlew :tools:docs:site`, read in
headless Chrome at 1280 px. The [start page](https://stig-johnny.github.io/shipping-reference-platform/index.html)
shows the new purpose line under its title and "Reading path for a Java lead" left of the COO's. The README's sheet
shows the Java paragraph, "The Java architecture" with its eight links and "Run it" with the command. Review: pull
request #124.

## Limits

Only 1280 px was inspected. The README's sheet still opens with its status ledger, whose planned-implementation row is
a long process paragraph above the Java opening; step 4 of epic 113 moves the process material. The milestone deck and
the "attended" wording remain until step 3.
