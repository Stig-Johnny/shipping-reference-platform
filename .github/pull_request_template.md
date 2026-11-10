Closes #<one issue>

Risk: <level and reason>

Evidence:
- Acceptance criteria:
- Checks and results:
- Red-first evidence, where applicable:
- Review of claims and rendered demo:

Demo: docs/demos/<pr>-<slug>.md (required for risk level 3 or visible changes), or: none needed because <reason>
Public route: demos/<pr>-<slug>.html
Recording limits:

Docs: <updated documents, or none needed because ...>
Size reason: <required above 500 authored lines>

Contribution record: <durable record reference for this issue, linked to this PR and the relevant commits; complete before claiming the issue implemented>

Model-run records: <IDs in docs/contributions/runs.md; record every change, failed/rejected attempt, remediation, integration, validation, independent review and actual human decision>

- Fixed task/base, acceptance criteria, tools/rules, environment and known comparability differences:
- Usage and cost receipts: <reported/estimated/unknown usage; billed/metered-estimate/estimate/unknown cost; scope, currency, rate date and method where relevant>
- Missing or shared costs: <subscription allocation, coordinator/reviewer work, human time, infrastructure; unknown is not zero; reservations are not spending>
- Reviewer completeness check: <missing attempts/phases and preservation of earlier records checked; valid schema alone does not establish completeness>

Contribution phases (append a row for each phase and whenever model or reasoning effort changes):

| Phase | Date (UTC) | Tool/agent | Configured model identifier | Verified resolved checkpoint | Reasoning effort | Role | Verification source/receipt |
|---|---|---|---|---|---|---|---|
| <n> | <YYYY-MM-DD> | <tool/agent> | <exact identifier> | <checkpoint or unknown> | <effort or unknown> | <implementation, integration, independent review> | <evidence reference> |

- Corrections are new dated records with correctionOf naming the earlier same-issue ID; keep all earlier rows unchanged.
- A completed run is not acceptance of its patch or proof of secure delivery; link validation, review findings and later remediation separately.
- Final-head reviews and human/delegated approval stay in durable PR comments; do not change an approved head to record its own approval.
- Mark missing evidence `unknown` and explain what is missing. Never infer settings from product names or defaults; a configured alias does not establish a resolved checkpoint.
- Retain human-only phases; their model, checkpoint and reasoning-effort fields are `not applicable`.
- Keep credentials, raw transcripts, private names and machine paths out of the public record.

AI review: <reviewing tool/agent, reviewed commit SHA, decision and review reference; or not yet performed>
Owner approval: <record separately: approved commit SHA, date and approval reference; or pending / not required under the applicable policy. Never treat AI review as human approval.>
