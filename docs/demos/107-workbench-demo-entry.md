---
title: "The demo entry point leads to the working shipping app"
description: "A short route connects the presentation, memory workbench and visual Brain, with planned flow steps labelled."
status: accepted
updated: 2026-11-10
pr: 107
kind: docs
recorded: 2026-11-10
tests: [DemoPagesTest, DemoCheckTest]
---

## What changed

The README's first demo command served an old placeholder. It now leads with the existing commands that stage and
launch the real workbench. An early route connects the deck, memory-demo instructions and visual Brain; the static
fixture remains an optional serving check.

The main flow's authored sequence labels its planned publication and audit steps. Its new caption explains that
the PostgreSQL target includes work the attended memory demo does not execute. All 29 messages and their order
are preserved, as are the acceptance tests and historical evidence. A semicolon in an existing Mermaid note
caused GitHub to reject the diagram; replacing it with a comma preserves the note and fixes its syntax.

## See it

Follow the [memory-demo instructions](../flows/attended-demo.md#memory-mode), open the
[milestone deck](deck-milestone.md), then explore the [visual Brain](../../BRAIN.md).
The [main flow](../flows/quote-book-label.md) shows the target-design caption and a disclosure of the authored
sequence source. The model-derived dynamic diagram above it is unchanged.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-10, the coordinator read the rendered flow page in the Codex in-app browser on macOS. At 1280 by 720
pixels, the new caption and source disclosure were readable with no horizontal page overflow. The source contained
two planned participant labels and five planned message labels. The memory-demo link opened the intended runbook
section, and the console query returned no warnings or errors.

GitHub preview exposed the existing Mermaid parse error at the tenant-binding note. The note separator was
changed from a semicolon to a comma. The rendered README route and its memory-workbench link were also checked.
The final GitHub diagram render is recorded separately in the PR against the corrected revision.

On macOS with Temurin 25.0.5+8, Brain generation wrote 22 files without drift. The subsequent ordinary check exited 0:

```sh
./gradlew check
```

Selected verbatim output before this numbered demo was added; other task output is omitted:

```text
docs: the 22 generated files are up to date
docs: wrote the site, 101 files
BUILD SUCCESSFUL in 2s
```

Test tasks were reused from the preceding generator dependency run. No new test was added for this prose change.
`DemoPagesTest` and `DemoCheckTest` are separately named as evidence of the publishing build. Independent review and
final revision receipts belong to pull request #107. The [contribution record](../contributions/history.md#issue-106)
identifies the Codex editorial and integration phases.

## Limits

This is a navigation and documentation correction. It changes no runtime, dependency, model, contract, capability
status, test threshold or historical result. The Pages site keeps the authored sequence as source; this change adds
no new rendered diagram there. The local recording is not a new app launch, PostgreSQL rehearsal, automated browser
acceptance or full accessibility audit. The workbench still requires the reviewed toolchain and uses synthetic data.
