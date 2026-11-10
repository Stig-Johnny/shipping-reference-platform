---
title: "See which model did the work and what its cost estimate covers"
description: "Recorded attempts show their outcome, evidence and cost basis, including unsuccessful work and API-equivalent estimates."
status: accepted
updated: 2026-11-10
pr: 112
kind: gate
recorded: 2026-11-10
tests:
  - ModelRunsTest
  - ContributionPagesTest
---

## What changed

Contribution history now has a structured [model-run register](../contributions/runs.md). Each attempt opens to
show its model, effort, outcome, usage, cost basis and evidence. Failed work stays visible, corrections retain
the original assertion, and missing cost is shown as unknown.

One dated coordinator snapshot demonstrates an API-equivalent estimate: **USD 10.624714** at the published
Standard rates. It covers the recorded model tokens in that window; actual subscription billing remains unknown.

## See it

Open the [model-run register](../contributions/runs.md). Select issue 111 and expand the API-equivalent entry to
inspect its usage and method. Compare it with an unknown-cost phase and a historical DeepSeek attempt. The
[recording rules](../contributions.md) explain how the next change must preserve the same distinctions.

## Try it

From a checkout with the reviewed JDK and dependencies available, run the ordinary documentation checks:

```sh
./gradlew :tools:docs:check
```

The negative fixtures submit malformed accounting states and missing receipt markers; acceptance fixtures retain
unknown cost, separately labelled estimates and visible corrections. No provider key, database or bill is needed.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-10, local macOS with the reviewed JDK 25.0.5, the focused API-equivalent regression failed before the
new basis was supported (exit 1):

```text
ModelRunsTest > apiEquivalentEstimatePublishesItsBasisWithoutClaimingAnInvoice() FAILED
    java.lang.AssertionError at ModelRunsTest.java:185

1 test completed, 1 failed
```

After the change, all seven ModelRunsTest cases and both ContributionPagesTest cases passed (exit 0):

```text
BUILD SUCCESSFUL in 16s
4 actionable tasks: 2 executed, 2 up-to-date
Configuration cache entry reused.
```

The build setup and task listing are omitted from these excerpts. Independent draft review also found and
prompted fixes for missing-marker receipts and correction labels; their failing cases were retained before
the fixes. Pull request #112 carries the final-head review and validation receipts separately. The named tests
in this sheet count as current-build evidence only when they pass in the publishing build.

## Limits

These are submitted observations, not a model ranking or total delivery cost. The checks validate structure;
they cannot authenticate a receipt or detect an omitted attempt. The API comparison is a fixed, partial
coordinator window with an assumed Standard tier, unresolved child-agent coverage and no extra tool, human or
infrastructure charges. It is not an invoice. Scheduled shared pricing updates belong to a separate proposed
repository and are not implemented here.
