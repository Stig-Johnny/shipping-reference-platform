---
title: "Shipment documentation separates working behavior from planned features"
description: "Readers can distinguish the runnable booking flow, recorded database checks and future cancellation and event delivery."
status: accepted
updated: 2026-11-10
pr: 105
kind: docs
recorded: 2026-11-10
tests: [DemoPagesTest, DemoCheckTest]
---

## What changed

The shipment summary described cancellation as available while its implementation notes still called persistence
and concurrent booking unproven. The reference now describes the implemented booking, listing and label behavior,
links the recorded PostgreSQL evidence, and marks cancellation, durable event delivery and audit consumption as
planned. The existing shipment mutation-testing target is also explicitly planned; its threshold is unchanged.

## See it

Read the [shipment reference](../modules/shipments.md), starting with its summary and implementation state.
The API, event, lifecycle and invariant sections use the same distinction. The
[attended runbook](../flows/attended-demo.md#recorded-database-evidence) keeps the historical database evidence
separate from the memory demo. Capability statuses are unchanged.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-10, the coordinator read the complete rendered shipment reference in the Codex in-app browser on macOS.
At 1280 by 720 pixels, the summary and status ledger were readable without overlap or horizontal page overflow.
The recorded-CI link opened the runbook's database-evidence section. No browser warnings or errors were recorded.
The lifecycle remains a disclosure of diagram source; this change does not add a rendered diagram.

The ordinary check on macOS with Temurin 25.0.5+8 exited 0:

```sh
./gradlew check
```

Selected verbatim output from the initial check, before this numbered demo and the additional mutation-target
clarification were added; other task output is omitted:

```text
docs: the 22 generated files are up to date
docs: wrote the site, 100 files
BUILD SUCCESSFUL in 1m 26s
```

The valid ordinary reports contained 4464 tests with no failures, errors or skips. Unchanged tasks were reused.
`DemoPagesTest` and `DemoCheckTest` are separately named as evidence of the publishing build. Independent review
and final revision receipts belong to pull request #105. The [contribution record](../contributions/history.md#issue-102)
identifies the editorial and integration phases.

## Limits

This corrects documentation claims. It adds no runtime behavior, cancellation, durable event delivery or mutation
coverage. The historical database recording retains its original date and limits. This local check and browser
inspection are not a fresh PostgreSQL rehearsal, automated browser acceptance or a full accessibility audit.
