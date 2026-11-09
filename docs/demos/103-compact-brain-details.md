---
title: "Brain details show the overview before the long lists"
description: "Counted disclosures make module details easier to scan while every relationship and source stays available."
status: accepted
updated: 2026-11-09
pr: 103
kind: visual
recorded: 2026-11-09
tests: [BrainMapTest]
---

## What changed

Selecting Quotes used to repeat the same explanation and source beside six decisions and five capabilities.
Those lists now sit in two counted disclosures. The module description, status, capability counts, specification
link and dependencies stay visible. Expanding a group shows its shared explanation once and every target link.
Capability and control test evidence remains visible without opening another group.

## See it

Open the [visual Brain](https://stig-johnny.github.io/shipping-reference-platform/brain/index.html). Select Quotes
and open its decisions or capabilities. Follow a decision, then select Tenants and follow that same decision:
the map keeps the module you came from. Search for `simulated-label` to find a capability even while its group is
closed. Evidence and Walk the demo retain their existing meanings.

The complete HTML records keep these groups open by default, so their contents remain available without script.
Read the [visual design](../ui/documentation-site.md#search-and-the-visual-brain) for the presentation rules.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-09, the corrected renderer regressions ran on macOS ARM64 with the reviewed Temurin 25.0.5+8 before
the renderer change. The focused test command exited 1, with four feature assertions failing:

```text
12 tests completed, 4 failed
```

After integration, all 12 renderer tests passed. The full offline check and generation command exited 0;
its ordinary JUnit reports contained 4464 tests with no failures, errors or skips. Recorded excerpts:

```text
docs: the 22 generated files are up to date
docs: wrote 22 generated files
docs: wrote the site, 98 files
BUILD SUCCESSFUL in 1m 39s
```

The excerpts omit task listings and toolchain warnings. This recording preceded addition of this demo page.

Attended inspection of the local [Brain preview](https://stig-johnny.github.io/shipping-reference-platform/brain/index.html)
used the Codex in-app browser at 1280 by 720 and 1280 by 900 pixels. Enter and Space toggled the native groups;
nested links focused their destination heading and preserved Quotes or Tenants context for ADR 0013. Search found
the collapsed label capability, capability and control evidence stayed visible, the guided journey selected
Shipments, and a direct catalogue fragment revealed its ancestors. No duplicate IDs or browser warnings/errors
were observed. A comparison with the published baseline retained all 118 relationships with identical endpoints,
kinds, meanings and sources. All ten canonical groups were open, while the selected module's groups started closed.

Layout measurements at widths 390 and 360 showed a single column and no horizontal document overflow. These were
DOM layout checks; the narrow-screen screenshot capture was unreliable. Review and exact-head check receipts are
recorded separately in pull request #103. The [contribution history](../contributions/history.md#issue-101) separates
DeepSeek's partial implementation from the coordinator's corrections and integration.

## Limits

This changes presentation, not architecture, capability status or evidence. A decision mention is not proof of
enforcement, and a module marked built does not make all its capabilities shipped. The browser checks are an
attended recording, not automated end-to-end coverage or a complete accessibility audit. Full no-script browser
execution was not tested; the renderer tests verify the canonical open markup. No new database or mutation run
is claimed by this local recording.
