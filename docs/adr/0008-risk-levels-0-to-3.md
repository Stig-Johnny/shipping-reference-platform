---
title: "ADR 0008: Risk levels 0 to 3 decide who must approve a change"
description: "Each pull request gets a level from path, behaviour and uncertainty signals using base-branch policy; level 3 needs the owner's approval of the current head before merge."
status: accepted
updated: 2026-11-06
date: 2026-11-06
decision-makers: repository owner
enforcedBy: [not enforced yet]
plannedEnforcement:
  - "risk-approval: classifier from the base branch plus approval binding to the head SHA"
  - "ai-review: recorded findings bound to the final SHA"
  - "tools/policy tests: RiskClassifierTest, CodeownersRiskConsistencyTest"
---

# ADR 0008: Risk levels 0 to 3 decide who must approve a change

## Context and Problem Statement

Most agent-authored changes are routine; a few touch pricing, bookings, tenant isolation, dependencies or the
controls themselves. Requiring a human for everything creates review overload; requiring a human for nothing makes
accountability a fiction. How does the repository decide which changes need the owner?

## Decision Drivers

- Deterministic classification that an agent cannot lower.
- Coverage of sensitive changes outside sensitive paths (a SQL string in an ordinary file).
- One accountable human, stated honestly.

## Considered Options

1. Levels 0 to 3 computed from `policy/risk.json` as `max(path, behaviour, uncertainty)`.
2. CODEOWNERS alone.
3. Human review of every pull request.

## Decision Outcome

Chosen option 1, with CODEOWNERS as the path-based half of it.

- Levels: 0 prose and documentation presentation; 1 UI presentation and non-sensitive prototypes; 2 ordinary
  application behaviour; 3 pricing, bookings, invoicing, personal data, authentication, tenant isolation,
  migrations, dependencies and controls.
- Level = max(path level, behaviour level, uncertainty level). Every changed path (old and new path for renames) is
  matched against all path rules; the highest match counts. A path that matches no rule is level 3. Behaviour
  signals (API or schema changes, sensitive symbols, changed SQL, event payloads, affected callers, disabled tests)
  are evaluated on the diff. The policy is always read from the base branch, so a pull request cannot relax its own
  classification.
- An AI reviewer may raise the level with a written reason. Nothing may lower a deterministic result.
- Approval: levels 0 to 2 need all required checks green and a recorded AI approval. Level 3 additionally needs the
  owner's approval of the current head SHA before merge, which is therefore before release. A push after approval
  dismisses it.
- Repository settings (private repository on GitHub Pro): a ruleset on `main` with required checks `preflight`,
  `java`, `web`, `contract-e2e`, `mutation`, `docs-brain`, `ai-review`, `risk-approval`; code owner review for
  owned paths; stale approval dismissal; no force push, no deletion, no routine bypass. Rulesets are stored as JSON in
  `policy/rulesets.json`, applied by a trusted admin workflow, and drift between file and API fails that workflow.
- Every path owned in `.github/CODEOWNERS` is level 3 in `policy/risk.json`, and every level 3 path glob has a
  CODEOWNERS entry. `risk-approval` covers semantic level 3 changes outside owned paths.
- Pull requests are created by a dedicated GitHub App, not the owner's identity, so the owner can approve them;
  GitHub forbids authors approving their own pull requests. A separate reviewer identity records AI findings.

### Consequences

- Good, because the owner sees only changes that need a human, and every such change is identified by a rule.
- Good, because the classification and its reasons are posted on the pull request and can be audited.
- Bad, because pattern-based behaviour signals can miss a semantic change; unknown paths escalate, unknown meanings do
  not (docs/gaps.md).
- Bad, because one owner is a single point of approval: this is accountable human oversight, not independent
  two-person review.

### Confirmation

- `risk-approval` (required check) runs the classifier from the base branch, posts the level and every matching
  rule, and fails a level 3 pull request until the owner has approved its current head SHA.
- `ai-review` (required check) fails unless an AI review record exists for the final SHA with findings and their
  disposition.
- `RiskClassifierTest` (`:tools:policy`) covers each rule with positive and negative fixtures, including: unknown
  path gives 3; SQL in a level 2 file gives 3; rename from a level 3 path keeps 3; an AI attempt to lower is rejected.
- `CodeownersRiskConsistencyTest` fails when a CODEOWNERS path is below level 3 or a level 3 path glob lacks an owner.

## More Information

- Policy file: `policy/risk.json`; plain-language explanation: `docs/risk-levels.md`.
- GitHub rulesets: <https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets>
- Approval rules: <https://docs.github.com/en/pull-requests/how-tos/review-pull-requests/approving-a-pull-request-with-required-reviews>
