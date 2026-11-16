---
title: "The meeting deck is gone and the demo is simply run locally"
description: "A Java reader no longer meets a milestone deck made for one meeting, and the word \"attended\", which only made sense for that meeting, is left in kept file names and the launcher's own comments, a decision's title, a CI step's name and earlier records."
status: accepted
updated: 2026-11-16
pr: 126
kind: docs
recorded: 2026-11-16
tests: [SitePagesTest]
---

## What changed

Before, the site published a milestone deck with speaker notes, made for one meeting, and the README, the runbook,
the module documents, the known gaps and code comments called the local run "the attended demo", with an "attended
runbook" and an "attended launcher".

Now the deck and its notes are retired: the start page has no deck line and the site no deck pages. The run is
"local" everywhere a reader meets it: the runbook is titled "Run the shipping app locally" and the README's section
is "Run the workbench locally". Browser checks and recordings that were not automated are called "interactive":
several were made by AI agents in an in-app browser. File names that security reviews or accepted decisions name,
such as `tools/demo/run-attended-demo.sh`, keep their names, and the documentation tool still supports decks.

## See it

- [The runbook](../flows/attended-demo.md): its title, "Run the shipping app locally".
- [The repository's README](../../README.md): "Run the workbench locally", and "Run it" linking to it.
- [The start page](https://stig-johnny.github.io/shipping-reference-platform/index.html#reading-paths): no deck line
  beside the reading paths.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-16 on macOS with Temurin 25.0.5+8. `SitePagesTest` was changed first: it requires that no deck page
is published, that the start page has no deck line, and that the runbook, the README and the Brain use the new
words. Run with `./gradlew :tools:docs:test --tests '*SitePagesTest'` against the unchanged documents, it exited 1;
the excerpt omits the assertion's page dump:

```text
SitePagesTest > theMeetingDeckIsRetiredAndTheRunbookReadsAsALocalRun() FAILED
    java.lang.AssertionError at SitePagesTest.java:82
37 tests completed, 1 failed
```

The same test class now also requires the README's first section to be "The Java architecture"; with a section
inserted above it, that assertion failed at `SitePagesTest.java:112`. After the change all tests passed, and
`./gradlew check :tools:docs:checkGenerated` and `java tools/policy/PolicyCheck.java .` exited 0. The site loses two
pages, the deck and its notes: 110 files without this demo, 111 with it. The rendered
[runbook](../flows/attended-demo.md) and [README](../../README.md) were read with the new wording. Review: pull
request #126.

## Limits

`tools/demo/run-attended-demo.sh` (its name and its own comments), its test, `docs/flows/attended-demo.md` and its
route, ADR 0018's title and the security reviews keep "attended"; renaming them needs a security-scope update. The
launcher still prints a sign-in hint that the static fixture page does not have. Earlier demos keep their wording,
except that demos 49, 104 and 107 now name the deck as retired; contribution records keep theirs. The CI step that
tests the launcher keeps its name.
