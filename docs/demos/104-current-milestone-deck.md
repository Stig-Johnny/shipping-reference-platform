---
title: "The milestone deck describes the app that can now be demonstrated"
description: "Slides and speaker notes distinguish the runnable workbench, recorded database evidence and the work still planned."
status: accepted
updated: 2026-11-10
pr: 104
kind: docs
recorded: 2026-11-10
tests: [DemoPagesTest, DemoCheckTest]
---

## What changed

The deck still said that the app ran only in tests and JDBC persistence was planned. It now describes the attended
memory workbench and implemented JDBC adapters, with separate links to recorded PostgreSQL evidence. The ten main
slides and four appendix slides retain their order. Detailed qualifications stay in speaker notes.

The notes provide a route through booking, safe replay, the Brain and contribution records, plus fallback pages.
They distinguish the owner's delegated merge workflow from a separate human approval of each commit.

## See it

Open the [milestone deck](deck-milestone.md). Slide 2 describes the runnable app, slide 6 opens the Brain and
contribution records, and slide 7 describes the JDBC adapters. The deck's X-N01 link opens its speaker notes.
The [attended runbook](../flows/attended-demo.md) explains the memory demo and separately recorded database checks.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-10, the coordinator read all rendered slides and speaker notes in the Codex in-app browser on macOS,
at laptop widths of 1280 pixels with 720- and 900-pixel heights. The workflow and JDBC slides were readable;
speaker notes stayed separate. All fourteen slide sections had no horizontal overflow at 1280 by 720. The
evidence-heavy second slide still required vertical scrolling at that height. The notes' runbook link and the
slide-to-Brain link opened their intended pages. No browser warnings or errors were recorded.

The existing real-deck test initially failed because its fixture removed the real demonstration pages now linked
by the notes. Restoring the complete repository fixture for that test preserves every assertion and all negative
fixtures. On macOS with Temurin 25.0.5+8, after regenerating the source-date change, the ordinary check exited 0:

```sh
./gradlew check
```

Selected verbatim output from that recording, before this numbered demo was added; other task output is omitted:

```text
docs: the 22 generated files are up to date
docs: wrote the site, 99 files
BUILD SUCCESSFUL in 1m 25s
```

The valid ordinary reports contained 4464 tests with no failures, errors or skips. Some unchanged tasks were
reused. `DemoPagesTest` and `DemoCheckTest` are separately named as evidence of the publishing build. Independent
review and final revision receipts belong to pull request #104. The [contribution record](../contributions/history.md#issue-100)
identifies the separate Codex editorial and integration phases.

## Limits

This is a documentation correction, not new application functionality or a new PostgreSQL rehearsal. Historical
recordings retain their original dates and limits. The November 9 memory rehearsal reached "Label ready" but did
not establish a newly saved file; earlier saved SVG evidence remains a separate recording. The deck does not prove
production readiness, automated browser acceptance, complete accessibility or an enforced approval gate. No new
database or mutation run is claimed by this local recording.
