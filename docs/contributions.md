---
title: "Contribution records"
description: "Find the model and reasoning effort used for an issue, the receipt behind each value, and its separate review and human approval."
status: accepted
updated: 2026-11-09
---

# Contribution records

[Find an issue in the contribution history](contributions/history.md). Each issue has a stable heading, PR and
commit references, and dated contribution phases. Missing settings are visible as `unknown`.

## Read a record

| Question | Where to look |
|---|---|
| What was implemented? | The issue's scope, PR and relevant commit references. A partial slice is labelled partial. |
| Which model and effort? | The implementation or integration phase, with its verification receipt. |
| Was it independently reviewed? | A separate review phase naming the reviewed head and review receipt. |
| Did the owner approve it? | A separate human phase naming the approved head and approval receipt. |

A configured model identifier is what the tool requested. It does not establish the provider's resolved checkpoint.
`unknown` means evidence is missing; `not applicable` is for a field that does not apply, such as a human's model.
Neither a known model nor a merged PR proves correctness, independent review or human approval.

## Record work as it happens

Before claiming an issue implemented, add its `## Issue N` section to
[the history](contributions/history.md), link it from the PR and record every contributing phase. Use these fields:

| Field | Required content |
|---|---|
| Scope and revision | Issue, PR, role and relevant full commit SHA; distinguish partial work from completion. |
| Date | Actual work date/time in UTC when known; otherwise `unknown`. Keep the receipt and backfill dates separate. |
| Tool or agent | The observed tool and whether it is the implementer, integrator or independent reviewer. |
| Configured model | Exact identifier from runtime or request metadata; product names and defaults are insufficient. |
| Resolved checkpoint | Only a checkpoint actually established by a receipt; otherwise `unknown`. |
| Reasoning effort | Observed setting, or `unknown` with the missing evidence explained. |
| Verification | A public, sanitized receipt or reference identifying which fields it establishes and its limits. |

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
checklist; the committed history is the durable record. This is a contributor rule, not an automated completeness
or provenance gate. A reviewer checks coverage and receipts.

## Backfill and privacy

Backfill from actual session metadata, review receipts and owner-approval records. A merge date is not a work date,
a commit author is not a model identifier, and a product footer is not evidence of reasoning effort. Use `unknown`
where no reliable receipt is available; do not attribute an entire issue to one observed continuation session.
The history's initial inventory covers merged work through PR #97, including the partial issue #66 slice.

Keep raw transcripts, credentials, private names and machine paths out of public records. Publish only the minimum
settings, scope and sanitized receipt necessary. GitHub references remain links in the repository source; the
current publication policy renders repository references as plain text on the public site. PR numbers, full hashes
and receipt identifiers therefore stay readable in their labels. Enabling repository links is separate work.

## Publication

These two source documents publish as Trust sheets T-07 and T-08. The human Brain links here, generated `BRAIN.md`
points to this convention, and site search indexes the issue headings. Edit the source and run the existing docs
checks/generator; a successful Pages deployment updates the public register. No model settings are inferred from
Brain prose, and the graph does not add authorship edges. The build checks links and publication, not receipt truth.
