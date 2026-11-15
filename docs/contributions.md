---
title: "Contribution records"
description: "One record per pull request: the models, reasoning effort, usage, cost and outcome of every change, with reviews and the merge decision as pull request comments and explicit gaps in the evidence."
status: accepted
updated: 2026-11-15
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
| Which model and effort? | The pull request's record, with its verification receipt. Records added before PR #122 have one record per phase instead. |
| Was it independently reviewed? | The pull request's review comments, each naming the reviewed head and the reviewer's model and effort. Records before PR #122 also include review records. |
| Did the owner approve it? | The pull request's approval or delegated-decision comment, naming the head. Records before PR #122 also include human records. |
| What did it cost? | Prefer the API-equivalent comparison with dated rates and usage, including subscription work; keep actual billed cost separate and unknown where unallocated. |
| What did the attempt achieve? | Its outcome, validation/review result and limitations, including failed work and later remediation. |

A configured model identifier is what the tool requested. It does not establish the provider's resolved checkpoint.
`unknown` means evidence is missing; `not applicable` is for a field that does not apply, such as a human's model.
Neither a known model nor a merged PR proves correctness, independent review or human approval.

## Record each pull request once

From PR #122 on, each pull request appends one record to [model runs](contributions/runs.md) and one short entry for
its issue to [the history](contributions/history.md). Records added before it keep one record per phase and stay
unchanged as the historical log. [ADR 0021](adr/0021-one-contribution-record-per-pull-request.md) records this
decision.

- The record covers the whole pull request: scope, base, outcome, what was validated and what remains limited. Its
  `role` is `implementation`, or `integration` when the pull request mainly integrates work done elsewhere. `tool`,
  `model`, `checkpoint` and `effort` list every contributor in the same order, separated by semicolons, for example
  `tool: "Codex; Claude Code"`, `model: "gpt-6-astra; claude-opus-5-5"` and `effort: "xhigh; xhigh"`. A setting
  without a receipt is `unknown`, with the reason. A usage window the pull request measured goes inside its record.
- Failed attempts, retries and review fixes are summarised in the record's result; they get no records of their own.
- Besides its own record, a pull request appends only correction records: one for each merged record it corrects,
  with that record's issue and `correctionOf` naming it. A correction to a merged history entry is stated in the
  correcting pull request's own entry.
- While the pull request is open, edit its record and history entry rather than appending corrections. Every pushed
  version stays in the pull request's commits as long as nobody force-pushes, which is a rule rather than a check;
  main keeps only the squashed final version. Once merged, the record and the entry are frozen.
- The history entry is headed `## Issue N`. A pull request for an issue that already has an entry adds its own entry,
  headed `## Issue N, PR #M`.
- Independent reviews of the final head and the merge decision come after the last commit, so they stay as durable
  pull request comments naming that head. A review comment also names the reviewer's tool, model and effort, or
  `unknown`. The history entry says where they are. A standing delegation to merge is not a personal approval of a
  particular head.
- Human-only work keeps model, checkpoint and effort `not-applicable`. Usage and cost stay `unknown` unless measured,
  as the accounting rules below describe. The build validates each record's structure, not whether a pull request
  has one or whether a merged record changed; reviewers check both, and the pull request template asks for it.

## Repeatable comparison protocol

1. Record the fixed task, full base revision, acceptance criteria and provided context before comparing runs. Name
   the configured model/effort, tools, environment, rules and available allowance, or the missing evidence.
2. Summarise every attempt, interruption, security refusal, retry and handoff in the pull request's record. Record
   receipt-scoped usage and cost without charging a shared account counter to several records.
3. State tests, independent findings, corrections, accepted scope and subsequent defects separately in the record.
   Same rules and a passing check do not establish equal task difficulty or one combined quality score.
4. Prefer API-equivalent estimates using recorded usage and dated API prices, even when the work ran under a
   subscription. State the comparison tier, cache treatment and per-request prompt-size thresholds; distinguish
   assumptions from observed settings. State omitted tool, infrastructure and human charges when the estimate
   covers only model tokens. Actual billed cost remains separate and may be unknown.
5. Compare only evidenced scopes with their limitations. A bounded coordinator usage window is recorded once, in
   the pull request's record, with its coverage labelled; do not repeat its cost elsewhere or assume child-agent
   inclusion.
   This slice computes no cross-record totals, currency conversions, subscription allocations or rankings.

## Structured source and accounting

`docs/contributions/runs.md` contains schemaVersion `1`, a nonempty coverage statement and a records list in its
frontmatter. Each stable record ID names one pull request or a correction of a merged record; records added before
PR #122 name one phase or attempt, or one explicitly bounded aggregate usage window. `correctionOf` is `none` or an
earlier ID for the same issue. Dates are quoted ISO
dates or `unknown`; bases are full lower-case commit hashes or `unknown`. Text fields are quoted strings, at most
2,000 characters each. Use only the keys and enums in the existing records; the reader
rejects extra/missing fields, invalid identities, incompatible states and malformed numeric values.

Usage states are `reported`, `estimated`, `unknown`, `not-applicable`; input/output values are nonnegative integers
or the corresponding quoted marker. Cost bases are `api-equivalent-estimate`, `billed`, `metered-estimate`,
`estimate`, `unknown`, `not-applicable`; numeric amounts are quoted nonnegative decimals with at most six places
and a three-letter uppercase currency. Known values need receipts; estimates need a method and rate date. Unknown values need a
reason. Human model settings can be not applicable while human economic cost remains unknown.

Reservations and budgets are exposure/permission limits, not incurred cost. Describe them separately in scope or
limitations; never put them in the cost amount. Metered estimates use recorded rates and may differ from invoices,
particularly for cache discounts. An API-equivalent estimate prices an evidenced usage scope at stated API rates;
it does not claim that an API invoice was issued or allocate subscription charges. Missing usage/rate evidence
keeps the estimate unknown. Actual subscription or shared-session billed cost is unknown until an allocation is
evidenced. Preserve historical metered estimates with their original rate basis. Zero needs evidence of zero for
that exact basis; a tool's placeholder zero is not a billing receipt. Cached input and reasoning tokens may be
subsets of provider totals: do not add them again. If an aggregate window is all the receipt supports, do not
fabricate per-contributor costs or infer that other agents are included. Keep an actual-bill record separately scoped and
labelled if one later becomes available; never add it to the comparison estimate as though they were different work.

[ADR 0020](adr/0020-model-run-evidence-and-cost-boundaries.md) records this boundary. ADR 0021 supersedes it to make
the record one per pull request, edited while the pull request is open and frozen once merged; the schema and these
accounting rules carry forward unchanged.

## Backfill and privacy

Backfill from actual session metadata, review receipts and owner-approval records. A merge date is not a work date,
a commit author is not a model identifier, and a product footer is not evidence of reasoning effort. Use `unknown`
where no reliable receipt is available; do not attribute an entire issue to one observed continuation session.
The history's initial inventory covers merged work through PR #97, including the partial issue #66 slice.
The historical 2026-11-10 usage/cost backfill covers only the named DeepSeek jobs for issues #69, #37 and #101. Their
work dates remain 2026-11-09. A separate issue #111 root-window snapshot records an API-equivalent estimate for
2026-11-10; neither receipt set reconstructs every phase or total delivery cost. The exhausted DeepSeek
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
