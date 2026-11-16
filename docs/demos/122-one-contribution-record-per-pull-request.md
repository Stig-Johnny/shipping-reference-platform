---
title: "Each pull request now has one short contribution record"
description: "Recording a change takes one record and one history entry however many review rounds it needs, so review time goes to the change instead of its records."
status: accepted
updated: 2026-11-15
pr: 122
kind: docs
recorded: 2026-11-15
tests: []
---

## What changed

Before, every phase of a change was its own record in the model runs: planning, implementation, each validation
attempt, each review and each correction, with corrections appended rather than edited. Pull request #115, one
documentation page, ended with 26 records and 15 history paragraphs.

Now each pull request has one record and one short history entry. Both are edited while the pull request is open and
frozen once it is merged, and failed attempts and fixes are summarised in the record. Reviews of the final head and
the merge decision stay as pull request comments. ADR 0021 supersedes ADR 0020 for this; the record schema, the cost
rules and every earlier record are unchanged.

## See it

- [The contribution convention](../contributions.md): the section "Record each pull request once".
- [ADR 0021](../adr/0021-one-contribution-record-per-pull-request.md): the decision, the options and the consequences.
- [The model runs](../contributions/runs.md): issue 121 has a single record for this whole pull request.
- [The contribution history](../contributions/history.md): the short entry for issue 121 at the end.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-15 on macOS with Temurin 25.0.5+8, in a clone of this pull request's branch.
`./gradlew :tools:docs:generate` and `./gradlew check :tools:docs:checkGenerated` each exited 0, and
`java tools/policy/PolicyCheck.java .` exited 0. Generation also ran the documentation tests: 760 tests, with no
failures, errors or skips. The documentation lines of the two Gradle commands, in order; the test and warning lines
are omitted:

```text
docs: wrote 22 generated files
docs: the 22 generated files are up to date
docs: wrote the site, 111 files
```

The rendered [convention](../contributions.md), ADR 0021, model runs and history pages were read in the generated
site. Review: pull request #122. Every documentation build validates the real records through its consistency check:
in the review's mutation check, one record with an unsupported role failed 94 documentation tests. `ModelRunsTest`
tests that validator with synthetic records, so no test class is named as this change's evidence.

## Limits

Nothing checks that a merged pull request has its record, or that a merged record stays unchanged; reviewers check
both. Records written before this pull request keep one record per phase. Per-phase cost comparisons are no longer
possible for new work.
