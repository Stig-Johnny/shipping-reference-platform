---
title: "ADR 0021: One contribution record per pull request"
description: "Supersedes ADR 0020. Its record schema, cost bases and evidence rules carry forward unchanged; each pull request now has one record instead of one per phase, edited while it is open and frozen once merged."
status: accepted
updated: 2026-11-15
date: 2026-11-15
decision-makers: repository owner
enforcedBy:
  - "java: ModelRunsTest validates submitted records and their publication"
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

- Each pull request appends exactly one record to `docs/contributions/runs.md` and one short entry for its issue to
  `docs/contributions/history.md`. The record covers the whole pull request: scope, the tools and models that did
  the work with their reasoning effort, the base, the outcome, what was validated and what remains limited. Failed
  attempts, retries and review fixes are summarised in its result and get no records of their own.
- While the pull request is open, its record and history entry are edited in place. The branch's commits keep every
  version, and force-pushing is not used, so nothing is lost. Once the pull request is merged, both are frozen. A
  later correction is a new record for the same issue, added by a later pull request, with `correctionOf` naming the
  record it corrects.
- Independent reviews of the final head and the merge decision come after the last commit, so they stay as durable
  pull request comments, as ADR 0020 already required. A standing delegation to merge is not a personal approval of
  a particular head.
- ADR 0020's schema, usage and cost bases, evidence rules and publication are unchanged. The records written before
  this decision, one per phase, stay unchanged as the historical log.

### Consequences

- Good, because recording a change takes one record and one short entry however many review rounds it needs, so
  review time goes to the change rather than to its records.
- Good, because the evidence readers ask for is kept: which models did the work, with what effort, who reviewed which
  head, and who decided the merge.
- Bad, because failed attempts and retries become prose in one result field instead of records a reader can count,
  and new work no longer supports per-phase cost comparisons.
- Bad, because an open pull request's record can change between reviews; a reviewer reads it against the branch's
  commits instead of relying on append-only rows.
- Neutral, because nothing checks that a merged pull request has its record or that a frozen record stays unchanged.
  Reviewers check both, as they checked completeness and preservation under ADR 0020.

### Confirmation

`ModelRunsTest` keeps validating submitted records and their publication, corrections included. The one-record rule
and the freeze at merge are review checks, stated in `docs/contributions.md`, `AGENTS.md` and the pull request
template; they are not automated.
