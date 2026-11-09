---
title: "Each implemented issue has a model and effort record"
description: "Find the settings and receipts behind implementation, review and human approval without guessing missing history."
status: accepted
updated: 2026-11-09
pr: 98
kind: docs
recorded: 2026-11-09
tests: [ContributionPagesTest]
---

## What changed

Model and reasoning-effort records previously lived in scattered comments. The contribution register now groups
42 historical issues by stable issue headings, with PRs, commits and evidence-backed phases, plus the current work.
Missing settings stay unknown. Implementation, independent review and human approval have separate rows.

## See it

Open [Contribution records](../contributions.md), follow its history link and use the contents list to select
[Issue 93](../contributions/history.md#issue-93). Its implementation and review rows show the configured model
and effort; its human approval row says not applicable. [Issue 90](../contributions/history.md#issue-90) shows
how missing historical settings are recorded. [Issue 96](../contributions/history.md#issue-96) separates the
bounded worker's contribution from the coordinator and initial reviewer.

The Brain links to the convention. Search for `Issue 93` to find the history through its published heading.
GitHub reference labels are readable, but the existing publication policy does not make them clickable on this site.

## Evidence

Recorded output — not rerun by this site build.

Captured on 2026-11-09 on macOS ARM64 with reviewed Temurin 25.0.5+8. Before implementation,
`./gradlew :tools:docs:test --tests '*ContributionPagesTest'` exited 1:

```text
2 tests completed, 2 failed
BUILD FAILED in 12s
```

After adding the source routes and Brain discovery, the same command exited 0:

```text
BUILD SUCCESSFUL in 4s
```

The integrated `./gradlew check :tools:docs:checkGenerated` run exited 0:

```text
docs: the 22 generated files are up to date
docs: wrote the site, 96 files
BUILD SUCCESSFUL in 1m 25s
```

That recording precedes this numbered demo; it is not a statement of the final site's file count. The local JUnit
reports totalled 4,456 tests, zero failures, errors or skips. PolicyCheck reported a clean 519-entry allowlist.
Initial independent review checked historical associations, settings receipts, unknowns and publication boundaries;
final-head review remains required and must be recorded on PR #98. The author inspected the rendered convention, history and issue navigation.
Only ContributionPagesTest passing in the publishing build supplies this sheet's current-build test evidence.

## Limits

The register records provenance; the build does not authenticate receipts, infer model settings, enforce append-only
history or guarantee completeness. Earlier unrecorded work remains unknown, and a configured alias does not establish
a resolved checkpoint. This local recording ran neither PostgreSQL nor mutation tests. Initial review is not
final-head approval, and AI review never authorizes a human-required merge.
