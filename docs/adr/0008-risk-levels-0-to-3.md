---
title: "ADR 0008: Risk levels 0 to 3 decide who must approve a change"
description: "Each pull request gets a level from path, behaviour and uncertainty signals using base-branch policy; level 3 needs the owner's approval of the current head before merge."
status: accepted
updated: 2026-11-06
date: 2026-11-06
decision-makers: repository owner
enforcedBy:
  - "branch protection on main: pull request required, required status checks preflight and java (strict, administrators included), stale reviews dismissed, conversation resolution required, no force push, no deletion"
  - "GitHub Actions setting: selected actions only, SHA pinning required, six allowed patterns"
plannedEnforcement:
  - "ruleset on main: stored in policy/rulesets.json, applied by a trusted admin workflow that fails on drift"
  - "code owner review for owned paths"
  - "risk-approval: classifier from the base branch plus approval binding to the head SHA"
  - "ai-review: recorded findings bound to the final SHA"
  - "identities: a dedicated GitHub App that opens pull requests, and a separate reviewer identity"
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

- **Planned.** `risk-approval` (required check) runs the classifier from the base branch, posts the level and every
  matching rule, and fails a level 3 pull request until the owner has approved its current head SHA.
- **Planned.** `ai-review` (required check) fails unless an AI review record exists for the final SHA with findings
  and their disposition.
- **Planned.** `RiskClassifierTest` (`:tools:policy`) covers each rule with positive and negative fixtures, including:
  unknown path gives 3; SQL in a level 2 file gives 3; rename from a level 3 path keeps 3; an AI attempt to lower is
  rejected.
- **Planned.** `CodeownersRiskConsistencyTest` fails when a CODEOWNERS path is below level 3 or a level 3 path glob
  lacks an owner.

## Implementation status (2026-11-06)

What is built, and where it differs from the decision above. The decision stands; the differences are open work.

- **What GitHub enforces.** Branch protection on `main` (not a ruleset) requires a pull request with 0 approvals,
  dismisses stale reviews, requires conversation resolution and forbids force pushes and deletion. It requires the
  status checks `preflight` and `java`, strict (the branch must be up to date with `main`), for administrators too.
  The Actions setting allows selected actions only, requires SHA pinning and lists six allowed patterns, with no
  GitHub-owned or verified wildcard. The default `GITHUB_TOKEN` permission is read, and Actions cannot approve pull
  requests. A tag ruleset makes existing tags immutable.
- **What is planned.** No branch ruleset exists, and `policy/rulesets.json` does not exist. No trusted admin workflow
  applies settings or reports drift. Code owner review is not required. No `risk-approval` or `ai-review` check exists,
  so nothing computes a level or records an AI approval yet; `policy/risk.json` is policy data that no check reads.
  `mutation`, `web`, `contract-e2e` and `docs-brain` are not required checks: the `mutation` job arrives with pull
  request #13, and no frontend or API code exists for `web` and `contract-e2e`. No dedicated GitHub App and no
  separate reviewer identity exist. `RiskClassifierTest` and `CodeownersRiskConsistencyTest` do not exist.
- **Why code owner review cannot be required today.** The only code owner authors the pull requests: the AI agents
  open them on the owner's GitHub account, and GitHub refuses an author's approval of their own pull request.
  Requiring code owner review would block every merge. It needs the agents to open pull requests under their own
  identity first.
- **Visibility.** The decision assumes a private repository. The repository goes public, by the owner's decision,
  and so does its GitHub Pages documentation site.

## More Information

- Policy file: `policy/risk.json`; plain-language explanation: `docs/risk-levels.md`.
- GitHub rulesets: <https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets>
- Approval rules: <https://docs.github.com/en/pull-requests/how-tos/review-pull-requests/approving-a-pull-request-with-required-reviews>
