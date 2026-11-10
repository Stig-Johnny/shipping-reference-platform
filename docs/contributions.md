---
title: "Contribution records"
description: "Record models, usage, cost and observed outcomes for every change, with failed attempts, separate review and explicit gaps in the evidence."
status: accepted
updated: 2026-11-10
---

# Contribution records

[Find an issue in the contribution history](contributions/history.md), then open its
[model-run records](contributions/runs.md). The history keeps scope and revision references; structured runs keep
models, usage, cost and outcomes. Missing evidence remains `unknown`.

The purpose is to compare model-assisted delivery under the same repository rules and automation. Secure, correct
work remains a gate: lower cost never waives dependency review, tests, refusals or independent review. These are
observations of real work, not a controlled benchmark or a model ranking. A completed run is not an accepted patch.

## Read a record

| Question | Where to look |
|---|---|
| What was implemented? | The issue's scope, PR and relevant commit references. A partial slice is labelled partial. |
| Which model and effort? | The implementation or integration phase, with its verification receipt. |
| Was it independently reviewed? | A separate review phase naming the reviewed head and review receipt. |
| Did the owner approve it? | A separate human phase naming the approved head and approval receipt. |
| What did it cost? | The run's basis, amount, currency, accounting scope and receipt; unallocated or unavailable cost stays unknown. |
| What did the attempt achieve? | Its outcome, validation/review result and limitations, including failed work and later remediation. |

A configured model identifier is what the tool requested. It does not establish the provider's resolved checkpoint.
`unknown` means evidence is missing; `not applicable` is for a field that does not apply, such as a human's model.
Neither a known model nor a merged PR proves correctness, independent review or human approval.

## Record work as it happens

For every repository change, start a structured record when work starts. Before claiming completion, add its
`## Issue N` narrative to [the history](contributions/history.md), link the records from the PR and account for every
contributing phase. Include planning, retries, failed or rejected attempts, remediation, integration, validation,
independent review and actual human decisions. Use these fields:

| Field | Required content |
|---|---|
| Scope and revision | Issue, PR, role and relevant full commit SHA; distinguish partial work from completion. |
| Date | Actual work date/time in UTC when known; otherwise `unknown`. Keep the receipt and backfill dates separate. |
| Tool or agent | The observed tool and whether it is the implementer, integrator or independent reviewer. |
| Configured model | Exact identifier from runtime or request metadata; product names and defaults are insufficient. |
| Resolved checkpoint | Only a checkpoint actually established by a receipt; otherwise `unknown`. |
| Reasoning effort | Observed setting, or `unknown` with the missing evidence explained. |
| Verification | A public, sanitized receipt or reference identifying which fields it establishes and its limits. |
| Usage | Reported or estimated input/output counts, or explicit unknown/not-applicable values, with the measurement boundary. |
| Cost | Billed, metered-estimate, estimate, unknown or not-applicable basis; exact decimal amount/currency where known, scope, method and receipt. |
| Outcome | Completed, partial, rejected, failed, blocked or abandoned, with the actual result and remaining limits. |

Append a phase when the model, effort or role changes, including handoffs and worker contributions. Human-only
phases remain in the record with model, checkpoint and effort `not applicable`. Record independent AI review and
owner approval separately; neither substitutes for the other. Approval names the exact reviewed or approved head.
For a current PR, record its implementation commit after the first commit exists and append later review/head
references; do not try to embed a commit's own hash in itself.
Receipts issued after the last commit, especially final-head review and owner approval, stay as durable PR
comments linked from the issue record. A later reviewed backfill can append them here. Do not change an approved
head merely to record its approval.

Keep earlier phases unchanged. A correction is a new dated row that identifies the earlier phase, corrected field,
new value and supporting receipt. Later approval does not rewrite an earlier pending state. The PR template is a
checklist; committed history and structured runs are the durable record. The build validates submitted records,
not receipt truth, missing changes or preservation against earlier revisions. Reviewers compare the issue and PR
against all phases and inspect corrections/deletions. A missing record is a review finding even when the build is
green. Final-head review and human/delegated approval remain distinct; standing delegation is not a new personal
approval of a particular head.

## Repeatable comparison protocol

1. Record the fixed task, full base revision, acceptance criteria and provided context before comparing runs. Name
   the configured model/effort, tools, environment, rules and available allowance, or the missing evidence.
2. Retain every attempt, interruption, security refusal, retry and handoff. Record receipt-scoped usage and cost
   without charging a shared account counter to several phases. Preserve failed and rejected output in the record.
3. Record tests, independent findings, corrections, accepted scope and subsequent defects separately. Same rules
   and a passing check do not establish equal task difficulty or one combined quality score.
4. Compare only evidenced scopes with their limitations. This slice publishes per-run records without totals,
   currency conversion, automatic billing collection, subscription allocation or rankings.

## Structured source and accounting

`docs/contributions/runs.md` contains schemaVersion `1`, a nonempty coverage statement and a records list in its
frontmatter. Each stable record ID names one phase/attempt; `correctionOf` is `none` or an earlier ID for the same
issue. Dates are quoted ISO dates or `unknown`; bases are full lower-case commit hashes or `unknown`. Text fields
are quoted strings, at most 2,000 characters each. Use only the keys and enums in the existing records; the reader
rejects extra/missing fields, invalid identities, incompatible states and malformed numeric values.

Usage states are `reported`, `estimated`, `unknown`, `not-applicable`; input/output values are nonnegative integers
or the corresponding quoted marker. Cost bases are `billed`, `metered-estimate`, `estimate`, `unknown`,
`not-applicable`; numeric amounts are quoted nonnegative decimals with at most six places and a three-letter
uppercase currency. Known values need receipts; estimates need a method and rate date. Unknown values need a
reason. Human model settings can be not applicable while human economic cost remains unknown.

Reservations and budgets are exposure/permission limits, not incurred cost. Describe them separately in scope or
limitations; never put them in the cost amount. Metered estimates use recorded rates and may differ from invoices,
particularly for cache discounts. Subscription or shared-session cost is unknown until an allocation is evidenced.
Zero needs evidence of zero for that exact basis; a tool's placeholder zero is not a billing receipt. Cached input
and reasoning tokens may be subsets of provider totals: do not add them again.

[ADR 0020](adr/0020-model-run-evidence-and-cost-boundaries.md) records this boundary.

## Backfill and privacy

Backfill from actual session metadata, review receipts and owner-approval records. A merge date is not a work date,
a commit author is not a model identifier, and a product footer is not evidence of reasoning effort. Use `unknown`
where no reliable receipt is available; do not attribute an entire issue to one observed continuation session.
The history's initial inventory covers merged work through PR #97, including the partial issue #66 slice.
The 2026-11-10 usage/cost backfill covers only the named DeepSeek jobs for issues #69, #37 and #101. Their work
dates remain 2026-11-09. It is not a reconstruction of every phase or total delivery cost. The exhausted DeepSeek
request allowance blocks further worker work; the proposed increase remains unapproved and this feature makes
no provider calls.

Keep raw transcripts, credentials, private names and machine paths out of public records. Publish only the minimum
settings, scope and sanitized receipt necessary. GitHub references remain links in the repository source; the
current publication policy renders repository references as plain text on the public site. PR numbers, full hashes
and receipt identifiers therefore stay readable in their labels. Enabling repository links is separate work.

## Publication

The convention and history publish as Trust sheets T-07 and T-08; structured runs publish as T-09 at
`trust/model-runs.html`. The human Brain links here, generated `BRAIN.md`
points to this convention, and site search indexes the issue headings. Edit the source and run the existing docs
checks/generator; a successful Pages deployment updates the public register. No model settings are inferred from
Brain prose, and the graph does not add authorship edges. The build checks record structure, links and publication, not receipt truth or complete task coverage.
