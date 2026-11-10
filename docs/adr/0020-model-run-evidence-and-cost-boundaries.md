---
title: "ADR 0020: Record model runs with explicit cost and coverage boundaries"
description: "Keep structured per-run model, usage, cost and outcome evidence while leaving receipt truth, missing work and delivery completeness to review."
status: accepted
updated: 2026-11-10
date: 2026-11-10
decision-makers: repository owner
enforcedBy:
  - "java: ModelRunsTest validates submitted records and their publication"
plannedEnforcement:
  - "Trusted detection of missing change records and preservation against earlier revisions"
---

# ADR 0020: Record model runs with explicit cost and coverage boundaries

## Context and Problem Statement

Issue #111 asks for model and cost evidence for every repository change, including unsuccessful attempts,
coordination and review. The existing contribution history records settings and approval phases but has no strict
usage/cost schema. A cheap worker response can exclude expensive remediation; a shared account counter cannot
establish a task's bill. The repository needs comparable evidence without inventing complete costs or weakening
the security and quality gates used to judge delivery.

## Considered Options

1. Keep prose-only records and infer cost from model names or shared counters.
2. Validate authored per-run records, preserve their accounting boundaries and publish their limitations.
3. Collect provider billing automatically and claim every change is covered by the resulting dashboard.

## Decision Outcome

Choose option 2. `docs/contributions/runs.md` is the structured source, with standard document metadata,
`schemaVersion: 1`, a coverage statement and a records list. Publish it as Trust sheet T-09 at
`trust/model-runs.html`; keep the existing contribution history and its earlier assertions unchanged.

Each record identifies an issue, stable ID, work date, role, tool, configured model, checkpoint, effort, base,
outcome, scope, environment, result, limitations and sanitized evidence. Usage has a measurement state and
input/output scope. Cost retains its basis, amount, currency, scope, method and receipt. Missing evidence is
unknown, never zero. Budgets and unresolved reservations belong in limitations, not the cost amount.

Record failed/rejected attempts, remediation, integration, validation, independent review and actual human
decisions as well as successful implementation. Append corrections referencing an earlier same-issue ID;
preserve the earlier assertion. Final-head reviews and approval remain separate durable PR receipts, with later
reviewed backfills, so recording approval does not itself change the approved head.

Validate submitted structure and state/value consistency through the existing safe parser and documentation
build. Keep the build script, dependency set and publication controls unchanged. No provider call, pricing lookup,
billing import, subscription allocation, cross-phase total or ranking is part of this slice.

### Consequences

Readers can inspect per-run evidence and unsuccessful work without mistaking a configured alias for a resolved
checkpoint, an estimate for an invoice, or a completed attempt for accepted secure delivery. Costs from unlike
tasks, environments and incomplete phases do not establish a fair model comparison.

Contributors must provide dated rate assumptions for estimates and reasons for unknowns. Structural validation
does not authenticate those statements. Reviewers still check missing changes/phases, receipt credibility,
accounting overlap and preservation of earlier records against the base. Present-record validity cannot prove
history immutability or complete billing/benchmark coverage.

### Confirmation

`ModelRunsTest` exercises submitted schema validation and rendered records through the existing consistency and
site paths, including unknowns, outcomes and corrections. Passing tests establish those bounded behaviors, not
provider billing, model identity beyond receipts, complete change coverage or final-head approval.

The contributor protocol, PR template and gaps document the remaining manual checks. The initial backfill keeps
historical work dates separate from its audit date and records unresolved reservations outside metered amounts.
