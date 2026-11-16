---
title: "ADR 0021: One contribution record per pull request"
description: "Supersedes ADR 0020. Its record schema, cost bases and evidence rules carry forward unchanged; each pull request now has one record of its own instead of one per phase, edited while it is open and frozen once merged."
status: accepted
updated: 2026-11-15
date: 2026-11-15
decision-makers: repository owner
enforcedBy:
  - "java: the documentation build's consistency check validates every record; ModelRunsTest exercises that validator and the published page"
plannedEnforcement:
  - "Trusted detection of a merged pull request without its record, and of a record changed after its merge"
---

# ADR 0021: One contribution record per pull request

Supersedes ADR 0020. What it decided carries forward here unchanged, except where this record says otherwise; its
text stays as it was accepted.

## Context and Problem Statement

ADR 0020 records every phase of a change as its own structured record: planning, implementation, each validation
attempt, each review and each correction, with corrections appended instead of edited. Applied to real work, the
records outgrew the changes. Pull request #115 added one documentation page and ended with 26 records and 15 history
paragraphs. Each review round added records, and the reviews then found problems in the records themselves. The
records took more of the work than the page did, and they put the repository's process ahead of its Java content.

How much recording does each change need to keep its model, effort, review and approval evidence honest?

## Considered Options

1. Keep one record per phase (ADR 0020).
2. One record per pull request, edited while the pull request is open and frozen once it is merged.
3. No structured records: the pull request's description and comments are the only record.

## Decision Outcome

Choose option 2. Option 1 costs more than the changes it records. Option 3 leaves no validated, published record of
which models did the work, with what effort and outcome.

- From pull request #122 on, each pull request appends one record for itself to `docs/contributions/runs.md` and one
  short entry for its issue to `docs/contributions/history.md`. The record covers the whole pull request: scope, the
  tools and models that did the work with their reasoning effort, the base, the outcome, what was validated and what
  remains limited. A usage window the pull request measured goes inside it. Failed attempts, retries and review fixes
  are summarised in its result and get no records of their own.
- Besides its own record, a pull request appends only correction records: one for each merged record it corrects,
  with that record's issue and `correctionOf` naming it. A correction to a merged history entry is stated in the
  correcting pull request's own entry.
- While the pull request is open, its record and history entry are edited in place. Every pushed version stays in the
  pull request's commits as long as nobody force-pushes, which is a rule rather than a check; main keeps only the
  squashed final version. Once the pull request is merged, the record and the entry are frozen.
- Independent reviews of the final head and the merge decision come after the last commit, so they stay as durable
  pull request comments, as ADR 0020 already required. A review comment names the reviewer's tool, model and effort,
  or `unknown`. A standing delegation to merge is not a personal approval of a particular head.
- ADR 0020's schema, usage and cost bases, evidence rules and publication are unchanged. The records added before
  pull request #122, one per phase, stay unchanged as the historical log.

### Consequences

- Good, because recording a change takes one record and one short entry however many review rounds it needs, so
  review time goes to the change rather than to its records.
- Good, because the evidence readers ask for is kept: which models did the work, with what effort, who reviewed which
  head, and who decided the merge.
- Bad, because failed attempts and retries become prose in one result field instead of records a reader can count,
  and new work no longer supports per-phase cost comparisons.
- Bad, because an open pull request's record can change between reviews; a reviewer reads it against the branch's
  commits instead of relying on append-only rows.
- Bad, because a record that names several models has one usage block and one cost block, so per-model usage and
  cost are lost unless the result states them; and the result holds at most 2,000 characters, so a long review
  history is summarised.
- Neutral, because nothing checks that a merged pull request has its record or that a frozen record stays unchanged.
  Reviewers check both, as they checked completeness and preservation under ADR 0020.

### Confirmation

Every documentation build reads `docs/contributions/runs.md` through the record validator in its consistency check,
so an invalid record fails the build. `ModelRunsTest` exercises that validator and the published page with synthetic
records. The one-record rule, correction records and the freeze at merge are review checks: `docs/contributions.md`
and `AGENTS.md` state them, and the pull request template asks the author to confirm that earlier records and history
entries are unchanged. They are not automated.
