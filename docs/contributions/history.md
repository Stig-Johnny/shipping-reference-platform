---
title: "Contribution history"
description: "Per-issue implementation, integration, review and human approval records, with exact settings where evidenced and unknowns where receipts are missing."
status: accepted
updated: 2026-11-24
---

# Contribution history

Use the contents list or site search for `Issue N`. [How to read and extend this register](../contributions.md).
Initial backfill recorded on **2026-11-09** from 41 merged PRs through #97, covering 42 issues with implemented
scope, plus current issue #96. PR #71 closes both #63 and #85. PR #77 implements only phase 1 of #66.
PR #86's reference to future #82 work is excluded. A record is not a claim that the whole issue is complete.

From PR #122 on, each pull request adds one short entry here, at the end, and one record of its own to
[model runs](runs.md) ([ADR 0021](../adr/0021-one-contribution-record-per-pull-request.md)). Entries through issue
111 run newest first from the top; issues 117 and 114 follow issue 1 with their phase-by-phase detail.

## Backfill rule

The `historical implementation` phase used below has these explicit values: work date `unknown`, tool/agent
`unknown`, configured model `unknown`, resolved checkpoint `unknown`, reasoning effort `unknown`.
Its source is the linked PR and merge metadata, inspected on 2026-11-09; that establishes merged scope but contains
no verified runtime settings receipt recovered for this backfill. Independent AI review and human approval are
also `unknown` unless separately recorded. Unknown means missing evidence, not that no AI or human contributed.
Merge timestamps below are UTC and are never substituted for work dates. Product footers were not used as settings.

Known phases below identify their receipts and actual work dates. The table's date is the observed work date/time at the
precision the receipt supports. No receipt here establishes a resolved provider checkpoint. Private source logs
are not published. Model provenance, review outcome, owner approval and passing tests are different evidence.

## Issue 111

**Model runs and cost evidence.** Add structured per-run usage, cost bases and outcomes beside the preserved
contribution history. The narrow slice validates records that exist; reviewers remain responsible for missing
changes/phases, receipt credibility and history preservation. No provider calls, billing collection, allowance
increase, complete delivery total or model ranking is claimed.

[Model-run records and public-safe backfill](runs.md) contain the current implementation, separate accounting
audit, documentation and independent design-review phases. Their fresh settings, usage and allocated costs remain
unknown where no phase receipt exists. The design review is advice, not implementation or final-head approval.
Implementation revisions, checks, rendered inspection, final independent review and the exercised merge authority
will be appended through the normal PR receipts; this initial entry grants no approval.

### Usage and cost backfill recorded on 2026-11-10

The structured record adds 14 DeepSeek rounds for issues #69, #37 and #101, including interrupted runs and later
rejected worker proposals, plus four historical Codex phases supported by the existing history. Original work
dates remain 2026-11-09. This supplements the legacy narrative without changing its earlier assertions.

[The backfill method](runs.md#backfill-method) names receipt hashes, historical rates and timestamp-window
attribution limits. Reservations are excluded from metered amounts; shared or missing Codex, human and
infrastructure costs stay unknown. The DeepSeek request allowance remains exhausted; its proposed increase is
unapproved and does not authorize worker calls for this issue.

### API-equivalent snapshot added on 2026-11-10

The user's comparison rule now prefers estimated API cost even for subscription work. One
[root-window record and method](runs.md#root-window-api-comparison) captures the issue #111 coordinator interval
from **11:38:31.342Z to 12:11:49.024Z UTC** on 2026-11-10: configured `gpt-6-astra`, effort `xhigh`, resolved
checkpoint unknown. The reported 6,936,470 input tokens include 6,738,304 cached tokens; output is 38,095 tokens.
Using the published Standard API rates checked that day gives **USD 10.624714**. Actual tier is unknown, so Standard
is a stated comparison assumption; actual billed/subscription cost remains unknown. The amount covers model
tokens only, omitting tool, infrastructure and human charges.

The metadata snapshot hash is `0ec37af8ebbdd27604b899986a14d666049c9ce586df87d628bd17e0fabc280d`.
The 55 reconciled usage increments are all below 272,000 input tokens. This fixed in-progress aggregate is recorded
once, without rewriting earlier unknown-cost phases or allocating its cost among them. It excludes later work;
child-agent inclusion, exclusive issue attribution and full delivery cost are not established. Historical DeepSeek
estimates retain their original accounting basis. No new shared repository, provider call or allowance change is
part of this documentation follow-up.

## Issue 109

**Current dependency declarations.** Add evidence locations and clickable source excerpts to the allowlist,
regenerated from repository inputs when declarations change or disappear. Preserve review history and separate
approved scope from observed declarations and runtime execution.

[Issue 109](https://github.com/Stig-Johnny/shipping-reference-platform/issues/109).

| Phase / role | Work date (UTC) | Tool and observed settings | Verification and scope |
|---|---|---|---|
| 109-1 reader implementation | 2026-11-10 | Separate Codex implementation agent; configured model `unknown`; checkpoint `unknown`; effort `unknown` | Reader, exact identity and removal tests, narrow fixture inputs. No model override was requested; this receipt does not independently establish inherited runtime settings. |
| 109-2 integration and presentation | 2026-11-10 | Codex coordinator; configured model `unknown`; checkpoint `unknown`; effort `unknown` | Source integration, allowlist evidence links, documentation, generated Brain and validation. The prior handoff records configuration for earlier work; this phase has no fresh runtime-settings receipt. |
| 109-3 independent design review | 2026-11-10 | Separate Codex reviewer; configured model `unknown`; checkpoint `unknown`; effort `unknown` | Read-only review of identity semantics, source-link policy and pinned build-input constraints. This is design advice, not final code approval. |

DeepSeek performed no work on this issue because its specific request allowance remains exhausted. No provider
calls or allowance changes belong to this work. Implementation revisions, actual red/green results and rendered
inspection are recorded in the PR. Final independent review and the separate exercise of standing merge
delegation name the exact final head in durable PR receipts; these initial phases make no approval claim.

### Revision and validation receipt added on 2026-11-10

Issue 109 is [PR 110](https://github.com/Stig-Johnny/shipping-reference-platform/pull/110), starting at
[ae140d35b45346f664c00951b7554acc442d9cf4](https://github.com/Stig-Johnny/shipping-reference-platform/commit/ae140d35b45346f664c00951b7554acc442d9cf4).
Generation passed with 4,485 ordinary tests and no failures, errors or skips; the complete ordinary check passed,
including 22 current generated files and 102 site files before its numbered demo was added. The unchanged
519-entry accepted set passed PolicyCheck. Independent draft review found an Action subpath identity error; a
regression failed before the correction and the final focused suite passed all 31 tests. The coordinator inspected
Maven, npm, Action and bundled-carrier evidence links, empty states and keyboard disclosures at desktop and narrow
widths. The numbered demo records those limits. Final exact-head approval and exercised delegation remain separate
PR receipts.

## Issue 106

**A direct human demo route.** Lead README readers to the deck, staged memory workbench and visual Brain; label
planned registry and audit messages in the existing PostgreSQL target sequence. The same editorial agent also
corrected the existing Mermaid note separator after native GitHub preview exposed a parse error.

[Issue 106](https://github.com/Stig-Johnny/shipping-reference-platform/issues/106) ·
[settings and scope receipt 6755557744](https://github.com/Stig-Johnny/shipping-reference-platform/issues/106#issuecomment-6755557744).

| Phase / role | Work date (UTC) | Tool and observed settings | Verification and scope |
|---|---|---|---|
| 106-1 editorial implementation | 2026-11-10 | Separate Codex editorial agent; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | Receipt 6755557744 identifies parent runtime metadata and inheritance without override. README route, existing staging commands and planned sequence labels; no independent backend checkpoint introspection or DeepSeek contribution. |
| 106-2 integration and validation | 2026-11-10 | Codex coordinator; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | Receipt 6755557744 identifies continuing-session configuration and the integration role: contribution record, generation, numbered demo, existing checks and rendered inspection. Actual results and revision follow in the PR. |

All 29 sequence messages and their order are preserved. No runtime, model, contract, capability status, dependency,
policy or historical recording changes belong to this issue. DeepSeek remains paused at its retained request limit.
Final independent review and exercise of standing merge delegation remain separate exact-head PR receipts; this
initial checkpoint is not approval, a passing-CI claim or an individual human SHA approval.

### Revision and validation receipt added on 2026-11-10

Issue 106 is [PR 107](https://github.com/Stig-Johnny/shipping-reference-platform/pull/107), starting at
[28f2884be6b6fd948074f44b935ef5aea9fd15ba](https://github.com/Stig-Johnny/shipping-reference-platform/commit/28f2884be6b6fd948074f44b935ef5aea9fd15ba).
The initial generator run passed, followed by the ordinary check with 22 generated files current and 101 site
files. Test tasks were reused from the generator's successful dependencies. The rendered flow caption and authored
sequence disclosure were inspected at laptop size, including the five planned-message labels and memory-demo link.
The model-derived Pages diagram is unchanged; the authored sequence remains source there. These are validation
results, not final independent approval. Final-head review and exercised delegation remain separate PR receipts.

## Issue 102

**Shipment implementation claims.** Clarify booking, recorded PostgreSQL evidence and the in-process event publisher,
while marking cancellation, durable publication and audit consumption as planned. PR and initial implementation
revision are appended once available.

[Issue 102](https://github.com/Stig-Johnny/shipping-reference-platform/issues/102) ·
[settings and scope receipt 6755198869](https://github.com/Stig-Johnny/shipping-reference-platform/issues/102#issuecomment-6755198869).

| Phase / role | Work date (UTC) | Tool and observed settings | Verification and scope |
|---|---|---|---|
| 102-1 editorial proposal | 2026-11-09 23:54–23:56 | Codex editorial agent; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | Receipt 6755198869 records a November 10 clarification based on parent runtime metadata and inheritance without override. Source-based documentation proposal only; no separate backend introspection or DeepSeek implementation. |
| 102-2 integration and validation | 2026-11-10 | Codex coordinator; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | Receipt 6755198869 identifies the continuing-session settings and role: source date, contribution history, Brain generation, numbered demo and existing checks. Actual results and revision follow in the PR. |

The coordinator also labelled the existing shipment mutation-testing target as planned; its threshold is unchanged.

No runtime, model relationship, contract, acceptance status or historical evidence changes belong to this issue.
Final independent review and exercise of the owner's standing merge delegation remain separate PR receipts.
This checkpoint is not approval or a claim of passing CI; no individual human SHA approval is implied.

### Revision and validation receipt added on 2026-11-10

Issue 102 is [PR 105](https://github.com/Stig-Johnny/shipping-reference-platform/pull/105), starting at
[e3396de31b39ca532bd60aba31cf7c5a4ea59a82](https://github.com/Stig-Johnny/shipping-reference-platform/commit/e3396de31b39ca532bd60aba31cf7c5a4ea59a82).
The initial ordinary check passed with 4464 tests across the valid ordinary reports, zero failures/errors/skips, 22 generated files
current and 100 site files. A subsequent preview build included the coordinator's mutation-target clarification.
The full rendered reference was read, its laptop summary inspected and its recorded-evidence link followed.
These are validation results, not final independent approval; final-head review and exercised delegation stay in
separate PR receipts. The numbered demo preserves the earlier check's scope explicitly.

## Issue 100

**Current milestone deck.** Correct stale application and persistence claims, retain the ten main/four appendix
structure, and provide an attended demo path with explicitly recorded evidence. Initial PR and implementation
revision are appended once available.

[Issue 100](https://github.com/Stig-Johnny/shipping-reference-platform/issues/100) ·
[settings and scope receipt 6754890833](https://github.com/Stig-Johnny/shipping-reference-platform/issues/100#issuecomment-6754890833).

| Phase / role | Work date (UTC) | Tool and observed settings | Verification and scope |
|---|---|---|---|
| 100-1 editorial proposal | 2026-11-09 about 23:58 | Codex editorial agent; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | Receipt 6754890833 records inherited parent settings without override. Compared the deck with source facts and prepared the text correction. This is neither DeepSeek implementation nor independent final review. |
| 100-2 integration and validation | 2026-11-10 | Codex coordinator; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | Receipt 6754890833 identifies continuing-session settings and the integration role: date, contribution history, numbered demo, existing checks and rendered review. Actual results and revision follow in the PR. |

DeepSeek is paused at its retained request limit and has no implementation phase in this issue. Independent
exact-head review and exercised owner delegation will be separate PR receipts. Standing delegation is not an
individual human SHA approval. Do not treat this initial contribution checkpoint as approval or passing CI.

### Revision and validation receipt added on 2026-11-10

Issue 100 is [PR 104](https://github.com/Stig-Johnny/shipping-reference-platform/pull/104), starting at
[1e86bacdd5073be7c0a851c2a2867eba41bcfd12](https://github.com/Stig-Johnny/shipping-reference-platform/commit/1e86bacdd5073be7c0a851c2a2867eba41bcfd12).
The coordinator's integration also restored the complete fixture in the existing real-deck test after its actual
missing-link failure, preserving every assertion and negative fixture. After the source-date regeneration, the
full check passed with 4464 ordinary test reports, zero failures/errors/skips and 22 generated files current.
Rendered slides and notes were read at laptop size; the evidence-heavy second slide still scrolls at 720 pixels.
These are validation results, not final independent approval. Final-head review and exercised delegation stay in
separate PR receipts.

## Issue 101

**Compact Brain details.** Group named decisions and declared capabilities while preserving relationship meanings,
sources, statuses and evidence. Initial implementation revision and PR are recorded after the first commit.

[Issue 101](https://github.com/Stig-Johnny/shipping-reference-platform/issues/101) ·
[settings and checkpoint receipt 6754558658](https://github.com/Stig-Johnny/shipping-reference-platform/issues/101#issuecomment-6754558658).

| Phase / role | Work date (UTC) | Tool and observed settings | Verification and scope |
|---|---|---|---|
| 101-1 partial implementation | 2026-11-09 23:25–23:36 | OpenCode bounded worker; configured `deepseek-flash`; checkpoint `unknown`; effort `unknown` | Receipt 6754558658 records exploration, seven renderer tests, the executed red check and a partial Java renderer. Exported patch SHA-256 `ca8b6d1e7c35fc8bfa687c9d83d7c2d732e207d301a0bc7882b3eb1cecfb9be2`. No worker green or browser result is claimed. |
| 101-2 integration and validation | 2026-11-09 | Codex coordinator; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | Receipt 6754558658 establishes runtime settings at 23:30:06 UTC. Corrected test assumptions, integrated the browser reader and native groups, adjusted layout and updated documentation. Corrected tests ran red before applying the renderer, then passed; the full check passed 4464 ordinary tests and regenerated 22 current files. Browser and final-head receipts follow separately. |
| 101-3 independent checkpoint review | 2026-11-09 | Separate Codex reviewer; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | Receipt 6754558658 records inherited settings without override. Reviewed the partial patch and actual red chronology; confirmed test defects and preserved constructed edge semantics by inspection. This is not final-head approval. |

Final independent review remains required. The owner's standing delegation permits the coordinator to review and
merge demo-loop work after checks and independent review; it is not an individual human approval of a named head.
Record final-head review and the exercised delegation as separate PR receipts without rewriting these phases.

### Revision receipt added on 2026-11-09

Issue 101 implementation is [PR 103](https://github.com/Stig-Johnny/shipping-reference-platform/pull/103), starting at
[39f1de2d6abfd5419ee7cac10ecd2ced74035816](https://github.com/Stig-Johnny/shipping-reference-platform/commit/39f1de2d6abfd5419ee7cac10ecd2ced74035816).
This identifies the initial integrated revision; final review and merge authorization remain separate PR receipts.

## Issue 96

**Contribution records.** Current implementation; PR and implementation commit pending at this initial recording.
All phases below were recorded on 2026-11-09. The worker's configured alias does not identify a resolved checkpoint.

| Phase / role | Work date (UTC) | Tool and observed settings | Verification and scope |
|---|---|---|---|
| 96-1 planning attempt | 2026-11-09 | OpenCode bounded worker; configured `deepseek-flash`; checkpoint `unknown`; effort `unknown` | Coordinator inspected controller receipt `issue-96-pilot`: no accepted output or repository edit; 19 steps / 30 requests. No effort field was established. |
| 96-2 planning retry | 2026-11-09 | OpenCode bounded worker; configured `deepseek-flash`; checkpoint `unknown`; effort `unknown` | Controller receipt `issue-96-retry`: proposal produced in 5 steps / 5 requests; coordinator corrected approval wording before implementation. No effort field was established. |
| 96-3 template implementation | 2026-11-09 18:31:02 | OpenCode bounded worker; configured `deepseek-flash`; checkpoint `unknown`; effort `unknown` | Controller receipt `issue-96-template-build`: 5 steps / 5 requests, 34.3 seconds, exit 0. Exact 16-line template append independently compared against original bytes by coordinator and accepted. No effort field was established. |
| 96-4 implementation and integration | 2026-11-09 | Codex coordinator; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | Own runtime turn metadata at 18:24:12 UTC establishes model and effort; coordinator integrates template, authored history and publication behavior. Sanitized receipt recorded here. |
| 96-5 independent initial review | 2026-11-09 | Separate Codex reviewer; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | Continuing reviewer with previously verified inherited settings and no override. Read-only review found no new blockers in the uncommitted integration; no final-head approval. Sanitized receipt recorded here. |

Independent final-head review and owner merge approval are pending at this recording. The worker's accepted patch is not an
independent review of the integrated change. Append the final review, PR/commit references and human approval
receipts when available; do not treat the existing permission to implement as permission to merge.

### Revision receipt added on 2026-11-09

Issue 96 implementation is [PR 98](https://github.com/Stig-Johnny/shipping-reference-platform/pull/98), starting at
[3ffd907899fe19a37483e6a38cf478e837ac2ef4](https://github.com/Stig-Johnny/shipping-reference-platform/commit/3ffd907899fe19a37483e6a38cf478e837ac2ef4).
This adds the revision missing from the initial recording above. Later exact-head independent review and human
approval receipts belong to PR 98; their existence is not asserted by this initial revision receipt.

## Issue 93

fix(ci): restore Pages publication for larger test reports.

[Issue 93](https://github.com/Stig-Johnny/shipping-reference-platform/issues/93) · [PR 95](https://github.com/Stig-Johnny/shipping-reference-platform/pull/95) · merged 2026-11-09T11:28:37Z.
Merge commit: [9099277fe73c52e628807ea638a06c56c35ddb75](https://github.com/Stig-Johnny/shipping-reference-platform/commit/9099277fe73c52e628807ea638a06c56c35ddb75).

Reviewed and human-approved head: [fa184dc175b3a44e9f2417beef6973e40ded6232](https://github.com/Stig-Johnny/shipping-reference-platform/commit/fa184dc175b3a44e9f2417beef6973e40ded6232).

| Phase / role | Work date (UTC) | Tool and observed settings | Verification receipt |
|---|---|---|---|
| 93-1 implementation and integration | 2026-11-09 | Codex; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | [issue 93, receipt 6740948284](https://github.com/Stig-Johnny/shipping-reference-platform/issues/93#issuecomment-6740948284) |
| 93-2 independent review | 2026-11-09 | Codex; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh`; separate reviewer inherited settings | [issue 93, receipt 6740948284](https://github.com/Stig-Johnny/shipping-reference-platform/issues/93#issuecomment-6740948284) |
| 93-3 human merge approval | 2026-11-09 11:24:54 | Human owner; model/checkpoint/effort `not applicable` | [PR 95, receipt 6738912511](https://github.com/Stig-Johnny/shipping-reference-platform/pull/95#issuecomment-6738912511) |

The public receipts establish only the named phases. They do not establish settings for unrecorded work.

## Issue 92

feat(tenants): run an attended demo with static serving and strict CSP.

[Issue 92](https://github.com/Stig-Johnny/shipping-reference-platform/issues/92) · [PR 94](https://github.com/Stig-Johnny/shipping-reference-platform/pull/94) · merged 2026-11-09T12:36:52Z.
Merge commit: [d7148c716190402e65b2e057b8150a8a63fecefd](https://github.com/Stig-Johnny/shipping-reference-platform/commit/d7148c716190402e65b2e057b8150a8a63fecefd).

Reviewed and human-approved head: [f45058bf47608670a9f704638191f5517631d29f](https://github.com/Stig-Johnny/shipping-reference-platform/commit/f45058bf47608670a9f704638191f5517631d29f).

| Phase / role | Work date (UTC) | Tool and observed settings | Verification receipt |
|---|---|---|---|
| 92-0 earlier orchestration/other authorship | unknown | Tool/model/checkpoint/effort `unknown` | [issue 92, receipt 6740949036](https://github.com/Stig-Johnny/shipping-reference-platform/issues/92#issuecomment-6740949036) explicitly limits coverage |
| 92-1 earlier implementation | 2026-11-09 10:17 and 10:27 | Codex; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | [issue 92, receipt 6740949036](https://github.com/Stig-Johnny/shipping-reference-platform/issues/92#issuecomment-6740949036) |
| 92-2 integration, fixes and validation | 2026-11-09 | Codex; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | [issue 92, receipt 6740949036](https://github.com/Stig-Johnny/shipping-reference-platform/issues/92#issuecomment-6740949036) |
| 92-3 independent review | 2026-11-09 | Codex; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh`; separate reviewer inherited settings | [issue 92, receipt 6740949036](https://github.com/Stig-Johnny/shipping-reference-platform/issues/92#issuecomment-6740949036) |
| 92-4 human merge approval | 2026-11-09 12:35:12 | Human owner; model/checkpoint/effort `not applicable` | [PR 94, receipt 6740201159](https://github.com/Stig-Johnny/shipping-reference-platform/pull/94#issuecomment-6740201159) |

The public receipts establish only the named phases. They do not establish settings for unrecorded work.

## Issue 90

Database: JDBC adapters for tenants, quotes and shipments, with the V3 shipments migration up and down.

[Issue 90](https://github.com/Stig-Johnny/shipping-reference-platform/issues/90) · [PR 91](https://github.com/Stig-Johnny/shipping-reference-platform/pull/91) · merged 2026-11-09T10:16:42Z.
Merge commit: [c92710f60d60517871b7e671e112a3dccc5bc422](https://github.com/Stig-Johnny/shipping-reference-platform/commit/c92710f60d60517871b7e671e112a3dccc5bc422).

**Phase 90-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 88

Shipments over HTTP: book, list, detail and the simulated label.

[Issue 88](https://github.com/Stig-Johnny/shipping-reference-platform/issues/88) · [PR 89](https://github.com/Stig-Johnny/shipping-reference-platform/pull/89) · merged 2026-11-08T23:54:05Z.
Merge commit: [d9313c1ba078ce6011b33b223e1bf21bf3fbcfc8](https://github.com/Stig-Johnny/shipping-reference-platform/commit/d9313c1ba078ce6011b33b223e1bf21bf3fbcfc8).

**Phase 88-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 85

Web import check 2/3: the web model vocabulary, the import rules and their reports.

[Issue 85](https://github.com/Stig-Johnny/shipping-reference-platform/issues/85) · [PR 71](https://github.com/Stig-Johnny/shipping-reference-platform/pull/71) · merged 2026-11-08T17:30:58Z.
Merge commit: [81bb83f80e4ccc220a00cfc49485c6a98b483f12](https://github.com/Stig-Johnny/shipping-reference-platform/commit/81bb83f80e4ccc220a00cfc49485c6a98b483f12).

**Phase 85-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 81

CI downloads the JDK and Gradle mirror-first with official fallback and bounded retries (mirror disabled).

[Issue 81](https://github.com/Stig-Johnny/shipping-reference-platform/issues/81) · [PR 86](https://github.com/Stig-Johnny/shipping-reference-platform/pull/86) · merged 2026-11-08T13:05:17Z.
Merge commit: [63169f60cbb59d4494461a4fec5294d38b36429b](https://github.com/Stig-Johnny/shipping-reference-platform/commit/63169f60cbb59d4494461a4fec5294d38b36429b).

**Phase 81-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 79

The web workspace's npm lockfile, from the reviewed maintenance environment.

[Issue 79](https://github.com/Stig-Johnny/shipping-reference-platform/issues/79) · [PR 80](https://github.com/Stig-Johnny/shipping-reference-platform/pull/80) · merged 2026-11-08T07:41:41Z.
Merge commit: [d207ff3142e2e84eb79370a19a188b0d7845d328](https://github.com/Stig-Johnny/shipping-reference-platform/commit/d207ff3142e2e84eb79370a19a188b0d7845d328).

**Phase 79-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 74

Every class the API uses is reviewed for its configuration and whitelisted for its layer.

[Issue 74](https://github.com/Stig-Johnny/shipping-reference-platform/issues/74) · [PR 78](https://github.com/Stig-Johnny/shipping-reference-platform/pull/78) · merged 2026-11-08T12:30:46Z.
Merge commit: [a1df8ad2a921eb6f84c68545b7f32f45332fccc2](https://github.com/Stig-Johnny/shipping-reference-platform/commit/a1df8ad2a921eb6f84c68545b7f32f45332fccc2).

**Phase 74-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 69

**Type-switch evidence limit.** Characterizes the existing omission of named and unnamed switch type patterns;
the production importer remains unchanged. Custom bootstrap extraction is deferred and `enumSwitch` is unmeasured.
[Issue 69](https://github.com/Stig-Johnny/shipping-reference-platform/issues/69). PR and implementation revision
will be appended after the first commit. Phases recorded on 2026-11-09:

| Phase / role | Work date (UTC) | Tool and observed settings | Verification and scope |
|---|---|---|---|
| 69-1 implementation | 2026-11-09 | OpenCode 2.2.6 worker, DeepSeek direct API; configured `deepseek-flash`; checkpoint `unknown`; effort `unknown` | Exported `issue-69-build` patch SHA-256 `752b85a1ec524e5cd11ecdf462cc74bfcb52742c6170144ee595904c41d635c6`, independently read and hash-verified by the integrating coordinator. Two bounded rounds, 24 completed model steps. Receipt establishes only the alias; no checkpoint or effort field was recorded. |
| 69-2 initial patch review | 2026-11-09 19:40:26 | Separate Codex setup coordinator; model/checkpoint/effort `unknown` | Sanitized review receipt: accepted the three-file patch for integration after diff review, test-log hash checks and independent fixture disassembly. Worker source was `1194adbc6e85a40d170deeac79e45914f1ab65c6` with an accepted local JDK overlay excluded from the patch. This was not final-head review or merge approval. |
| 69-3 integration and validation | 2026-11-09 | Codex integrating coordinator; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | Runtime turn metadata at 20:36:46 UTC establishes model and effort. Applied the exact worker patch to `e1108baec90543f8a898b847eb79f46b8e8e26fa`, independently verified all ten exported check-log hashes and ran the 28-test focused suite with zero failures/errors/skips. Adds publication and provenance records; final integrated checks remain separate receipts. |

Final-head AI review and human merge approval are pending at this recording. Their exact-head receipts will be
recorded on the PR. Raw worker transcripts and private machine paths are not published.

### Revision receipt added on 2026-11-09

Implementation is [PR 99](https://github.com/Stig-Johnny/shipping-reference-platform/pull/99), starting at
[77c23dea4234171ed23a39e2afb8f4d93e02ea9c](https://github.com/Stig-Johnny/shipping-reference-platform/commit/77c23dea4234171ed23a39e2afb8f4d93e02ea9c).
This appends the revision missing above. The coordinator's integrated ordinary checks passed with 4,457 tests,
zero failures/errors/skips and all 22 generated files current. Final-head review and owner approval are separate
receipts on PR 99 and are not asserted by this implementation receipt.

## Issue 66

Scenario diagrams checked against what the code does at run time.

[Issue 66](https://github.com/Stig-Johnny/shipping-reference-platform/issues/66) · [PR 77](https://github.com/Stig-Johnny/shipping-reference-platform/pull/77) · merged 2026-11-08T13:39:39Z.
Merge commit: [43c8c75b2437515436adf146925a7ac4b90260ae](https://github.com/Stig-Johnny/shipping-reference-platform/commit/43c8c75b2437515436adf146925a7ac4b90260ae).

**Partial scope:** phase 1 in-process scenarios only; the PR explicitly leaves further work open.

**Phase 66-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 65

Use cases on the docs site, each stamped with its acceptance tests' outcome.

[Issue 65](https://github.com/Stig-Johnny/shipping-reference-platform/issues/65) · [PR 76](https://github.com/Stig-Johnny/shipping-reference-platform/pull/76) · merged 2026-11-07T23:43:48Z.
Merge commit: [d4c1e83b3ed536d17ed478e05fb5549d61a133a1](https://github.com/Stig-Johnny/shipping-reference-platform/commit/d4c1e83b3ed536d17ed478e05fb5549d61a133a1).

**Phase 65-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 64

Web import check 3/3: the web app's parts in the model, checked in every build.

[Issue 64](https://github.com/Stig-Johnny/shipping-reference-platform/issues/64) · [PR 72](https://github.com/Stig-Johnny/shipping-reference-platform/pull/72) · merged 2026-11-08T18:11:49Z.
Merge commit: [c2d4e850cdcf1233826d73c879174eb75a5edd82](https://github.com/Stig-Johnny/shipping-reference-platform/commit/c2d4e850cdcf1233826d73c879174eb75a5edd82).

**Phase 64-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 63

Web import check 2/3: the web model vocabulary, the import rules and their reports.

[Issue 63](https://github.com/Stig-Johnny/shipping-reference-platform/issues/63) · [PR 71](https://github.com/Stig-Johnny/shipping-reference-platform/pull/71) · merged 2026-11-08T17:30:58Z.
Merge commit: [81bb83f80e4ccc220a00cfc49485c6a98b483f12](https://github.com/Stig-Johnny/shipping-reference-platform/commit/81bb83f80e4ccc220a00cfc49485c6a98b483f12).

**Phase 63-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 62

Web import check 1/3: a JDK-only reader of TypeScript imports, with negative fixtures.

[Issue 62](https://github.com/Stig-Johnny/shipping-reference-platform/issues/62) · [PR 70](https://github.com/Stig-Johnny/shipping-reference-platform/pull/70) · merged 2026-11-08T14:23:10Z.
Merge commit: [8d89d80895bbb9b2bd4c7d2cbbe5854fda979c6f](https://github.com/Stig-Johnny/shipping-reference-platform/commit/8d89d80895bbb9b2bd4c7d2cbbe5854fda979c6f).

**Phase 62-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 61

The API's HTTP edge: problems, correlation, demo-token authentication, /me and quotes.

[Issue 61](https://github.com/Stig-Johnny/shipping-reference-platform/issues/61) · [PR 75](https://github.com/Stig-Johnny/shipping-reference-platform/pull/75) · merged 2026-11-08T08:38:14Z.
Merge commit: [4736565c39ea2b61f055708134d83c64d3e8ee95](https://github.com/Stig-Johnny/shipping-reference-platform/commit/4736565c39ea2b61f055708134d83c64d3e8ee95).

**Phase 61-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 60

feat(web): run the attended shipping workbench.

[Issue 60](https://github.com/Stig-Johnny/shipping-reference-platform/issues/60) · [PR 87](https://github.com/Stig-Johnny/shipping-reference-platform/pull/87) · merged 2026-11-09T15:00:41Z.
Merge commit: [5eae9cc120cba721d330f6504eb3a0f6ec044f6d](https://github.com/Stig-Johnny/shipping-reference-platform/commit/5eae9cc120cba721d330f6504eb3a0f6ec044f6d).

Reviewed and human-approved head: [f115a0787efb85e6d9e388a8f9db87514b290b3c](https://github.com/Stig-Johnny/shipping-reference-platform/commit/f115a0787efb85e6d9e388a8f9db87514b290b3c).

| Phase / role | Work date (UTC) | Tool and observed settings | Verification receipt |
|---|---|---|---|
| 60-0 earlier authorship/orchestration | unknown | Tool/model/checkpoint/effort `unknown` | [issue 60, receipt 6740949784](https://github.com/Stig-Johnny/shipping-reference-platform/issues/60#issuecomment-6740949784) explicitly limits coverage |
| 60-1 implementation continuation | 2026-11-08 21:38 | Codex; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | [issue 60, receipt 6740949784](https://github.com/Stig-Johnny/shipping-reference-platform/issues/60#issuecomment-6740949784) |
| 60-2 integration, fixes and validation | 2026-11-09 | Codex; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | [PR 87, receipt 6741496265](https://github.com/Stig-Johnny/shipping-reference-platform/pull/87#issuecomment-6741496265) |
| 60-3 independent review | 2026-11-09 | Codex; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh`; separate reviewer inherited settings | [PR 87, receipt 6741496265](https://github.com/Stig-Johnny/shipping-reference-platform/pull/87#issuecomment-6741496265) |
| 60-4 human merge approval | 2026-11-09 14:56:46 | Human owner; model/checkpoint/effort `not applicable` | [PR 87, receipt 6743240914](https://github.com/Stig-Johnny/shipping-reference-platform/pull/87#issuecomment-6743240914) |

The public receipts establish only the named phases. They do not establish settings for unrecorded work.

## Issue 59

Security reviews for the Tuesday slice.

[Issue 59](https://github.com/Stig-Johnny/shipping-reference-platform/issues/59) · [PR 73](https://github.com/Stig-Johnny/shipping-reference-platform/pull/73) · merged 2026-11-08T00:02:05Z.
Merge commit: [822d17538690df4de4811474eaba43c942d8a521](https://github.com/Stig-Johnny/shipping-reference-platform/commit/822d17538690df4de4811474eaba43c942d8a521).

**Phase 59-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 58

Decisions for the Tuesday slice: a plain TypeScript workbench and Spring JDBC adapters.

[Issue 58](https://github.com/Stig-Johnny/shipping-reference-platform/issues/58) · [PR 68](https://github.com/Stig-Johnny/shipping-reference-platform/pull/68) · merged 2026-11-07T22:59:49Z.
Merge commit: [b1290c5dcbd09f5d4cb8fe974581e0ecc27099f2](https://github.com/Stig-Johnny/shipping-reference-platform/commit/b1290c5dcbd09f5d4cb8fe974581e0ecc27099f2).

**Phase 58-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 57

Shipments books against quotes: the third built module, checked against the model.

[Issue 57](https://github.com/Stig-Johnny/shipping-reference-platform/issues/57) · [PR 67](https://github.com/Stig-Johnny/shipping-reference-platform/pull/67) · merged 2026-11-07T23:20:45Z.
Merge commit: [5e718481528391aff43ba6d7c3681e57c5fd5ab1](https://github.com/Stig-Johnny/shipping-reference-platform/commit/5e718481528391aff43ba6d7c3681e57c5fd5ab1).

**Phase 57-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 52

Database: migrations up and down for tenants and quotes, with forced row-level security proven in CI.

[Issue 52](https://github.com/Stig-Johnny/shipping-reference-platform/issues/52) · [PR 55](https://github.com/Stig-Johnny/shipping-reference-platform/pull/55) · merged 2026-11-08T21:38:09Z.
Merge commit: [8cf1db6039a98d53610d074bceb959480c3214ab](https://github.com/Stig-Johnny/shipping-reference-platform/commit/8cf1db6039a98d53610d074bceb959480c3214ab).

**Phase 52-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 51

CI: database tests under the reviewed runner conditions, with the image checked before it is pulled.

[Issue 51](https://github.com/Stig-Johnny/shipping-reference-platform/issues/51) · [PR 54](https://github.com/Stig-Johnny/shipping-reference-platform/pull/54) · merged 2026-11-08T20:51:38Z.
Merge commit: [a793eba310b4057e09af8afc20cda41033c63db2](https://github.com/Stig-Johnny/shipping-reference-platform/commit/a793eba310b4057e09af8afc20cda41033c63db2).

**Phase 51-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 50

Quotes and the tenant context: the first built modules, checked against the model.

[Issue 50](https://github.com/Stig-Johnny/shipping-reference-platform/issues/50) · [PR 56](https://github.com/Stig-Johnny/shipping-reference-platform/pull/56) · merged 2026-11-07T20:59:10Z.
Merge commit: [2e0d8f7e7a8e353ddc07951cb485215ee4949874](https://github.com/Stig-Johnny/shipping-reference-platform/commit/2e0d8f7e7a8e353ddc07951cb485215ee4949874).

**Phase 50-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 48

Docs site: a milestone deck in the Demos section, generated and checked like every page.

[Issue 48](https://github.com/Stig-Johnny/shipping-reference-platform/issues/48) · [PR 49](https://github.com/Stig-Johnny/shipping-reference-platform/pull/49) · merged 2026-11-07T17:57:54Z.
Merge commit: [112a12cf4553e9a78d70f0ce431e3a2d274f21b6](https://github.com/Stig-Johnny/shipping-reference-platform/commit/112a12cf4553e9a78d70f0ce431e3a2d274f21b6).

**Phase 48-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 46

Docs site: a Demos section with checked, text-only demonstration pages.

[Issue 46](https://github.com/Stig-Johnny/shipping-reference-platform/issues/46) · [PR 47](https://github.com/Stig-Johnny/shipping-reference-platform/pull/47) · merged 2026-11-07T16:30:24Z.
Merge commit: [c345c5240bf9c55c229cdb1819c2afc93802e661](https://github.com/Stig-Johnny/shipping-reference-platform/commit/c345c5240bf9c55c229cdb1819c2afc93802e661).

**Phase 46-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 41

Docs site: its own visual design, drawn as a drawing set.

[Issue 41](https://github.com/Stig-Johnny/shipping-reference-platform/issues/41) · [PR 45](https://github.com/Stig-Johnny/shipping-reference-platform/pull/45) · merged 2026-11-07T14:30:42Z.
Merge commit: [44ef312373f1f72b321db1bfe561557673baa6e3](https://github.com/Stig-Johnny/shipping-reference-platform/commit/44ef312373f1f72b321db1bfe561557673baa6e3).

**Phase 41-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 40

fix(policy): strict JSON reader and JDK-only regression tests for the allowlist checker.

[Issue 40](https://github.com/Stig-Johnny/shipping-reference-platform/issues/40) · [PR 44](https://github.com/Stig-Johnny/shipping-reference-platform/pull/44) · merged 2026-11-07T15:08:03Z.
Merge commit: [f1a239e4fcc52f1014f01ab94fee3310d88c53d2](https://github.com/Stig-Johnny/shipping-reference-platform/commit/f1a239e4fcc52f1014f01ab94fee3310d88c53d2).

**Phase 40-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 39

docs: lead with the checker and claim only what is enforced.

[Issue 39](https://github.com/Stig-Johnny/shipping-reference-platform/issues/39) · [PR 43](https://github.com/Stig-Johnny/shipping-reference-platform/pull/43) · merged 2026-11-07T14:26:27Z.
Merge commit: [11a3975783350e8ecd69aa76614db81c1084a171](https://github.com/Stig-Johnny/shipping-reference-platform/commit/11a3975783350e8ecd69aa76614db81c1084a171).

**Phase 39-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 38

feat(docs): add generated search and visual brain.

[Issue 38](https://github.com/Stig-Johnny/shipping-reference-platform/issues/38) · [PR 97](https://github.com/Stig-Johnny/shipping-reference-platform/pull/97) · merged 2026-11-09T17:17:11Z.
Merge commit: [1194adbc6e85a40d170deeac79e45914f1ab65c6](https://github.com/Stig-Johnny/shipping-reference-platform/commit/1194adbc6e85a40d170deeac79e45914f1ab65c6).

Reviewed and human-approved head: [62323dd1dee44a1e907b1ed523b88e7ed80a476d](https://github.com/Stig-Johnny/shipping-reference-platform/commit/62323dd1dee44a1e907b1ed523b88e7ed80a476d).

| Phase / role | Work date (UTC) | Tool and observed settings | Verification receipt |
|---|---|---|---|
| 38-1 implementation and validation | 2026-11-09 | Codex; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh` | [PR 97 model/effort record](https://github.com/Stig-Johnny/shipping-reference-platform/pull/97) |
| 38-2 independent review | 2026-11-09 16:35 | Codex; configured `gpt-6-astra`; checkpoint `unknown`; effort `xhigh`; separate reviewer | [PR 97, receipt 6745530864](https://github.com/Stig-Johnny/shipping-reference-platform/pull/97#issuecomment-6745530864) |
| 38-3 human merge approval | 2026-11-09 17:15:42 | Human owner; model/checkpoint/effort `not applicable` | [PR 97, receipt 6746508771](https://github.com/Stig-Johnny/shipping-reference-platform/pull/97#issuecomment-6746508771) |

The public receipts establish only the named phases. They do not establish settings for unrecorded work.

## Issue 36

build(c4-contract-kit): declare the models the kit's tests read as test inputs.

[Issue 36](https://github.com/Stig-Johnny/shipping-reference-platform/issues/36) · [PR 42](https://github.com/Stig-Johnny/shipping-reference-platform/pull/42) · merged 2026-11-07T14:20:40Z.
Merge commit: [b2fc5dd1f25ae9f91259274003d7b66cce6d26c7](https://github.com/Stig-Johnny/shipping-reference-platform/commit/b2fc5dd1f25ae9f91259274003d7b66cce6d26c7).

**Phase 36-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 33

Docs site: diagrams fit their full bounds, readable tables on mobile, precise checker evidence, diagram links from the architecture docs.

[Issue 33](https://github.com/Stig-Johnny/shipping-reference-platform/issues/33) · [PR 34](https://github.com/Stig-Johnny/shipping-reference-platform/pull/34) · merged 2026-11-07T12:12:51Z.
Merge commit: [4c9303e19924a6531a0b9433f31ae38ab5db2ccf](https://github.com/Stig-Johnny/shipping-reference-platform/commit/4c9303e19924a6531a0b9433f31ae38ab5db2ccf).

**Phase 33-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 31

docs: Dependabot covers the Actions, not the Gradle dependencies.

[Issue 31](https://github.com/Stig-Johnny/shipping-reference-platform/issues/31) · [PR 32](https://github.com/Stig-Johnny/shipping-reference-platform/pull/32) · merged 2026-11-07T10:08:27Z.
Merge commit: [463a23ebb66a5e44ebf16481b19ea515211d842a](https://github.com/Stig-Johnny/shipping-reference-platform/commit/463a23ebb66a5e44ebf16481b19ea515211d842a).

**Phase 31-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 25

docs: the commands that exist, and what is not built yet.

[Issue 25](https://github.com/Stig-Johnny/shipping-reference-platform/issues/25) · [PR 26](https://github.com/Stig-Johnny/shipping-reference-platform/pull/26) · merged 2026-11-06T21:05:31Z.
Merge commit: [dcbb154ed7f64fd303201ee4fef8247c29fc4563](https://github.com/Stig-Johnny/shipping-reference-platform/commit/dcbb154ed7f64fd303201ee4fef8247c29fc4563).

**Phase 25-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 22

ci(java): check the candidate against the allowlist before any of its code runs.

[Issue 22](https://github.com/Stig-Johnny/shipping-reference-platform/issues/22) · [PR 23](https://github.com/Stig-Johnny/shipping-reference-platform/pull/23) · merged 2026-11-06T21:01:41Z.
Merge commit: [177b56e850301b521d29d9d9e280e8e9d4db2e02](https://github.com/Stig-Johnny/shipping-reference-platform/commit/177b56e850301b521d29d9d9e280e8e9d4db2e02).

**Phase 22-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 20

docs: say what is enforced today, and mark the rest as planned.

[Issue 20](https://github.com/Stig-Johnny/shipping-reference-platform/issues/20) · [PR 21](https://github.com/Stig-Johnny/shipping-reference-platform/pull/21) · merged 2026-11-06T20:59:04Z.
Merge commit: [9da04a46b75b2e236a28ac37c11edd8cadeac227](https://github.com/Stig-Johnny/shipping-reference-platform/commit/9da04a46b75b2e236a28ac37c11edd8cadeac227).

**Phase 20-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 18

docs: licence the repository under Apache-2.0, with its notices.

[Issue 18](https://github.com/Stig-Johnny/shipping-reference-platform/issues/18) · [PR 19](https://github.com/Stig-Johnny/shipping-reference-platform/pull/19) · merged 2026-11-06T20:56:35Z.
Merge commit: [fc040f057e2bd05cc999ca8babf18cbd4b854e88](https://github.com/Stig-Johnny/shipping-reference-platform/commit/fc040f057e2bd05cc999ca8babf18cbd4b854e88).

**Phase 18-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 14

fix(ci): preflight fails on findings, and the report guard stops the upload.

[Issue 14](https://github.com/Stig-Johnny/shipping-reference-platform/issues/14) · [PR 15](https://github.com/Stig-Johnny/shipping-reference-platform/pull/15) · merged 2026-11-06T20:35:25Z.
Merge commit: [10e1991fa36dec12dc81bca96b887427f5402ca5](https://github.com/Stig-Johnny/shipping-reference-platform/commit/10e1991fa36dec12dc81bca96b887427f5402ca5).

**Phase 14-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 11

Docs: say what GitHub enforces today, and mark the ruleset as planned.

[Issue 11](https://github.com/Stig-Johnny/shipping-reference-platform/issues/11) · [PR 12](https://github.com/Stig-Johnny/shipping-reference-platform/pull/12) · merged 2026-11-06T20:25:42Z.
Merge commit: [0c113c8ce9b257b61fc2988075dfe8e36131c8af](https://github.com/Stig-Johnny/shipping-reference-platform/commit/0c113c8ce9b257b61fc2988075dfe8e36131c8af).

**Phase 11-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 9

API dependencies, reviewed before use: the Spring Boot and Spring Modulith tree, with Jackson held at 3.1.8.

[Issue 9](https://github.com/Stig-Johnny/shipping-reference-platform/issues/9) · [PR 10](https://github.com/Stig-Johnny/shipping-reference-platform/pull/10) · merged 2026-11-06T20:06:20Z.
Merge commit: [7c2b103624421ee6a50544c0e7773bcd091e8d37](https://github.com/Stig-Johnny/shipping-reference-platform/commit/7c2b103624421ee6a50544c0e7773bcd091e8d37).

**Phase 9-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 8

Docs site: generated C4 views, decisions, API, allowlist and brain on GitHub Pages, scanned before publication.

[Issue 8](https://github.com/Stig-Johnny/shipping-reference-platform/issues/8) · [PR 28](https://github.com/Stig-Johnny/shipping-reference-platform/pull/28) · merged 2026-11-07T07:17:32Z.
Merge commit: [f0c773a476a59be3b5cc39711f81ffa1504f80eb](https://github.com/Stig-Johnny/shipping-reference-platform/commit/f0c773a476a59be3b5cc39711f81ffa1504f80eb).

**Phase 8-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 6

Docs site dependencies, reviewed before use: SnakeYAML Engine, the vendored Mermaid bundle and the Pages actions.

[Issue 6](https://github.com/Stig-Johnny/shipping-reference-platform/issues/6) · [PR 7](https://github.com/Stig-Johnny/shipping-reference-platform/pull/7) · merged 2026-11-06T19:48:12Z.
Merge commit: [313786a4950951a8f90e40d040cc22dae7b33aaa](https://github.com/Stig-Johnny/shipping-reference-platform/commit/313786a4950951a8f90e40d040cc22dae7b33aaa).

**Phase 6-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 5

c4-contract-kit: restricted parsing, bytecode evidence and the two-way comparison (slices 2–4).

[Issue 5](https://github.com/Stig-Johnny/shipping-reference-platform/issues/5) · [PR 13](https://github.com/Stig-Johnny/shipping-reference-platform/pull/13) · merged 2026-11-07T07:09:07Z.
Merge commit: [7b699269c02ab68a80d48fa1e07323ca19d8bdec](https://github.com/Stig-Johnny/shipping-reference-platform/commit/7b699269c02ab68a80d48fa1e07323ca19d8bdec).

**Phase 5-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 2

Bootstrap: the reviewed toolchain, the dependency allowlist and its enforcement.

[Issue 2](https://github.com/Stig-Johnny/shipping-reference-platform/issues/2) · [PR 3](https://github.com/Stig-Johnny/shipping-reference-platform/pull/3) · merged 2026-11-06T17:46:03Z.
Merge commit: [09ad153e1e93f80521c8fc18ef8f27f5db5b03de](https://github.com/Stig-Johnny/shipping-reference-platform/commit/09ad153e1e93f80521c8fc18ef8f27f5db5b03de).

**Phase 2-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 1

Specification first: the C4 model, the API contract and the decisions.

[Issue 1](https://github.com/Stig-Johnny/shipping-reference-platform/issues/1) · [PR 4](https://github.com/Stig-Johnny/shipping-reference-platform/pull/4) · merged 2026-11-06T17:43:09Z.
Merge commit: [7e947cd3ad682d5b44a946fbd2942ff7165d3d30](https://github.com/Stig-Johnny/shipping-reference-platform/commit/7e947cd3ad682d5b44a946fbd2942ff7165d3d30).

**Phase 1-1 — historical implementation:** [unknown settings under the backfill rule](#backfill-rule).

## Issue 117

The engine guard's `docker info` probe gets 30 seconds on freshly booted hosted runners.

[Issue 117](https://github.com/Stig-Johnny/shipping-reference-platform/issues/117) ·
[pull request 119](https://github.com/Stig-Johnny/shipping-reference-platform/pull/119).
Base: `9cd0774b731f7edf20b30de5f4bf06cd3f0f0f76`.

**Phase 117-1 — diagnosis, 2026-11-15:** six jobs failed closed at the first-step guard with exit 124 from
`timeout 5 docker info`, never with an engine found reachable. Record: `issue-117-diagnosis` in [model runs](runs.md).

**Phase 117-2 — implementation, 2026-11-15:** Claude Opus 5.5 added a slow-CLI fixture, saw it fail against the
5-second guards, raised the bound to 30 seconds in all nine guards, and saw all 90 fixture cases pass. Record:
`issue-117-implementation` in [model runs](runs.md). Independent review and the owner's decision follow.

**Phase 117-3 — independent review and fixes, 2026-11-15:** a fresh-context Claude subagent approved
`fd5625e169bf36e7a9592e04c2da5fd0f0e93aef` with changes: the guard is not weakened, but the slow-CLI fixture could not
run while the fake accepted only one bound, the workflow test did not check the bound, and the records lacked a
settings receipt. Claude Opus 5.5 made the fake accept any bound with a separate 30-second pin, added the bound to
the workflow test, proved both by mutation, and cited the settings receipt. Implementation commit:
`be2908d7ebec82118a3e9c66af29541256deca7c`. Records: `issue-117-settings-receipt`, `issue-117-independent-review` and
`issue-117-review-fixes` in [model runs](runs.md).

## Issue 114

A Java architecture walkthrough for an experienced Java/Spring developer, part of epic 113.

[Issue 114](https://github.com/Stig-Johnny/shipping-reference-platform/issues/114) ·
[epic 113](https://github.com/Stig-Johnny/shipping-reference-platform/issues/113).
Base: `9cd0774b731f7edf20b30de5f4bf06cd3f0f0f76`; no implementation commit or PR number yet.

**Phase 114-1 — planning, 2026-11-15:** Codex read the issues and repository documentation conventions.
Scope is one source-checked walkthrough, its site registration, a local memory run and the requested checks.
The user excludes git writes, containers, PIT, dependencies and security changes. Configured model, checkpoint,
effort, usage, API-equivalent estimate and actual billed allocation remain unknown without a receipt.
The structured record is `issue-114-planning` in [model runs](runs.md).
Independent review and final-head approval have not happened in this session.

**Phase 114-2 — implementation and site integration, 2026-11-15:** the
[walkthrough](../java-architecture-walkthrough.md) follows source and existing tests, with exact port, tenant-binding,
RLS and replay-assertion excerpts. Start sheet S-04, README, navigation and search expose it. The local memory
launcher reached readiness; synthetic HTTP quote/book/replay/list/detail/label and tenant denial passed. Ctrl-C
exited 130 and the port check exited 0. No PostgreSQL or browser acceptance execution is inferred.

**Phase 114-3 — failed attempts and remediation, 2026-11-15:** the new navigation test first failed because the
page link did not exist. Its first wired run failed because the docs fixture copied tests but omitted linked main
sources; the fixture now includes those real inputs. The next run exposed a search assertion using the wrong
relative path; it now resolves the URL before checking its destination. Link validation and all existing
assertions remain. A sandbox-denied parent-process inspection during cleanup was replaced by interrupting the
owned launcher session. The source review corrected a test attribution: QuoteExpiryTest covers expiry; the
PostgreSQL offer-lookup test covers foreign tenants. Structured phases: `issue-114-implementation` and
`issue-114-wiring-remediation` in [model runs](runs.md).

**Phase 114-4 — validation failure and correction, 2026-11-15:** the first full generation rejected a new run
record with an unsupported role value; 94 docs test failures shared that metadata diagnostic. The record now uses
the existing implementation role and describes remediation in its scope. No schema rule changed. The failure is
retained as `issue-114-validation-attempt-1` in [model runs](runs.md).

**Phase 114-5 — integration and validation, 2026-11-15:** generation, the full requested Gradle check including
checkGenerated, and PolicyCheck each exited 0 before the demo-number placeholder was added. JUnit results were
1,508 API tests, 2,226 architecture-library tests and 759 documentation tests, with no failures, errors or skips.
The generated HTML contains the walkthrough sections and exact excerpts; the test verifies Start, navigation and
search reachability. Source links remain repository links in Markdown and plain labels on the site under its
existing publication rules. Computer-use exposed no browser and denied access to Safari; visual browser review
is therefore pending, not asserted. The temporary HTML preview was stopped. Structured records are
`issue-114-site-integration` and `issue-114-validation-before-demo-number` in [model runs](runs.md).

**Handoff, 2026-11-15:** the user explicitly requested a demo with `pr: 0` for later orchestrator numbering.
The docs tool rejects that placeholder; the numbered demo must be rendered and reviewed before merge. No git
writes, container runs, PIT, dependency changes or security-file edits were made. Independent final-head review
and the owner's decision remain outstanding, with no approval inferred from implementation or passing tests.

**Phase 114-6 — placeholder validation, 2026-11-15:** after adding the explicitly requested zero-number demo,
generation and the full check each exited 1. All 48 docs-test failures trace to two diagnostics for that file:
the filename must carry a positive PR number, and `pr` must be positive. No other source-consistency diagnostic
appeared; PolicyCheck and whitespace checks passed. A final prose audit initially matched the word fragment
"control" in Java Controller identifiers; word-boundary matching passed without a page edit. These attempts are
retained in `issue-114-validation-placeholder` in [model runs](runs.md). The actual PR number, a fresh passing
build and visual review of the numbered demo remain the handoff steps.

**Phase 114-7 — draft review and revision, 2026-11-15:** PR #115 now holds the draft at
`d57d7bd71df73fcb17ef20da378d99c2afdabbea`. The user supplied Claude Opus 5.5 (`claude-opus-5-5`)'s
orchestrator review: changes requested for demo numbering, a linked design overview, shorter confident prose,
concrete JDBC reasoning, tested curl calls and complete records. The review is separate from final-head approval.
The supplied Codex CLI 0.167.0 banner establishes `gpt-6-astra`, effort `xhigh`, for the earlier issue-114 phases;
checkpoint, usage and cost remain unknown. Their original receipt gaps and failed attempts remain recorded.
The revision targets `docs/demos/115-java-architecture-walkthrough.md` and preserves every source link and
excerpt. See the review, settings-backfill and revision phases in [model runs](runs.md).

**Phase 114-8 — remediation and validation, 2026-11-15:** the first numbered build exposed literal bearer-header
scan findings and a history link into demos removed by fixtures. Reusing the public token through a shell variable
and recording the demo path as text resolved both, without changing checks. The final three curl calls returned
201/201/200; cleanup and all requested checks passed. Generated S-04/X-115 HTML was read; visual browser review
remains pending because access was unavailable. Failures, fixes and validation are retained in [model runs](runs.md).

**Phase 114-9 — orchestrator integration, 2026-11-15:** Claude Opus 5.5 read the revised page in full and accepted
it with two wording edits: the launcher's JDK rule in one sentence, and a closing sentence that the code was written
with AI coding agents held to the same checks. The record is `issue-114-orchestrator-integration` in
[model runs](runs.md). A separate review and the owner's final-head decision follow.

**Phase 114-10 — independent review and fixes, 2026-11-15:** a separate read-only Codex session (gpt-6-astra,
xhigh) requested changes at `bb2132f8cc394b173bc889fce8812d22a5fbb8f7`: two P2 (an overstated cleanup-test claim;
seven earlier run records edited in place instead of corrected) and two P3 (the contract-implementation sentence;
repository jargon). It confirmed the three curl blocks, all four excerpts and all 114 links. Claude Opus 5.5
restored the seven records byte for byte, kept `issue-114-settings-backfill` as their correction row, and fixed
the three sentences. Records: `issue-114-independent-review` and `issue-114-review-fixes` in [model runs](runs.md).

**Phase 114-11 — second review and fixes, 2026-11-15:** a read-only Codex re-review at
`ab583e7009825b4b1956e5f31ba17f918677c860` stopped without a verdict when Codex credits ran out. A fresh-context Claude
subagent then approved with changes: all four earlier findings fixed, one P2 (the closing sentence overclaimed what
the README's try-it commands check) and record-wording P3s. Claude Opus 5.5 fixed the sentence, appended correction
rows for the settings backfill and the first review fixes, and noted in the PR that only the first corrected row
renders an Amended-by link. Records: `issue-114-codex-rereview-incomplete`, `issue-114-independent-review-2`,
`issue-114-backfill-correction`, `issue-114-review-fixes-correction` and `issue-114-review-2-fixes`.

**Phase 114-12 — evidence panel, 2026-11-15:** the rendered walkthrough showed "Verified evidence: None yet"
because its start sheet was built with no capabilities. A new site test failed first (the README page showed 59
passing evidence test classes; the walkthrough showed none), then passed once the sheet used every capability, as
the README page does. Record: `issue-114-evidence-panel` in [model runs](runs.md).

**Phase 114-13 — status panel, 2026-11-15:** the rendered page then showed the README's whole capability
paragraph, process terms included, as its own planned implementation. The site test now also requires that row to
say Not applicable; it failed first and passes since the page shows the README's evidence without a capability
status of its own. Record: `issue-114-status-panel` in [model runs](runs.md), a correction of
`issue-114-evidence-panel`.

**Phase 114-14 — final-head review, 2026-11-15:** a fresh-context Claude subagent reviewed
`0d568112079019bd656ffc4a4eee7ae05bdd4cf2` and approved with changes. Its P2, the capability paragraph under the title,
is the one Phase 114-13 fixes. For its P3s, the coverage sentence at the top of [model runs](runs.md) now counts the
issue-114 rows (a changed header line, not a changed record), and the PR description no longer claims screenshots on
the PR. Record: `issue-114-independent-review-3`.

**Phase 114-15 — main merge, 2026-11-15:** PR #119 (issue 117, the engine guard's 30-second bound) merged first,
as `239f5dd3fd8c1e3f0cdfce94f8e05a2e9cb5e896`. Claude Opus 5.5 merged main into this branch with a merge commit.
The only conflicts were the appended entries in this history and in [model runs](runs.md); both sides are kept
unchanged, main's first. The coverage sentence there now also counts the five issue 117 rows and this phase's row.
Generation, the full check and PolicyCheck passed before push. Record: `issue-114-main-merge`.

## Issue 121

One short contribution record per pull request instead of one per phase, part of epic 113.

[Issue 121](https://github.com/Stig-Johnny/shipping-reference-platform/issues/121) ·
[PR #122](https://github.com/Stig-Johnny/shipping-reference-platform/pull/122).
Base: `90af9163a3cd7c44e1eb6e47adde34a168a2e4ed`. Record: `issue-121-pull-request` in [model runs](runs.md), the first
under [ADR 0021](../adr/0021-one-contribution-record-per-pull-request.md).

Claude Code, the orchestrator (`claude-opus-5-5`, effort `xhigh`), wrote ADR 0021, the rule in
[the convention](../contributions.md), AGENTS.md rule 6, the README step, the pull request template, the generated
Brain line, the demo, this entry and the record. Independent reviews of heads `197ad54` and `145d48b` approved with
changes; their findings were fixed in this pull request, except the milestone deck's wording, which epic 113's step
that retires the deck replaces. The independent review of the final head and the merge decision are comments on
PR #122.

## Issue 123

The README and the site's start page lead with the Java architecture, part of epic 113 (step 2).

[Issue 123](https://github.com/Stig-Johnny/shipping-reference-platform/issues/123) ·
[PR #124](https://github.com/Stig-Johnny/shipping-reference-platform/pull/124).
Base: `5ded4d529b1aec6261a2803549722ae4915d32da`. Record: `issue-123-pull-request` in [model runs](runs.md).

Claude Code, the orchestrator (`claude-opus-5-5`, effort `xhigh`), rewrote the README's opening, put the Java reading
path first on the start page, changed `SitePagesTest` first (it failed, then passed) and wrote the demo, this entry
and the record. An independent review of head `3910876` approved with changes; its findings were fixed in this pull
request, except two left for epic 113's steps 3 and 4. The independent review of the final head and the merge decision
are comments on PR #124.

## Issue 125

The milestone deck and the "attended demo" wording are retired, part of epic 113 (step 3).

[Issue 125](https://github.com/Stig-Johnny/shipping-reference-platform/issues/125) ·
[PR #126](https://github.com/Stig-Johnny/shipping-reference-platform/pull/126).
Base: `91dbe457ed496148cd36f8a51562717f46f28169`. Record: `issue-125-pull-request` in [model runs](runs.md).

Claude Code, the orchestrator (`claude-opus-5-5`, effort `xhigh`), deleted the deck, reworded "attended" to "local" or
"interactive" where a reader meets it, kept the file names that security reviews and accepted decisions name, changed
`SitePagesTest` first (it failed, then passed), and wrote the demo, this entry and the record. An independent review of
head `4c7d0e4` approved with changes; its findings were fixed in this pull request. The independent review of the final
head and the merge decision are comments on PR #126.

## Issue 127

The README describes the application first and how it was built after it, part of epic 113 (step 4).

[Issue 127](https://github.com/Stig-Johnny/shipping-reference-platform/issues/127) ·
[PR #129](https://github.com/Stig-Johnny/shipping-reference-platform/pull/129).
Base: `94a4bc4c3a3525ad6740aabba8cdc8a4fe26196b`. Record: `issue-127-pull-request` in [model runs](runs.md).

Claude Code, the orchestrator (`claude-opus-5-5`, effort `xhigh`), moved the README's AI-assisted delivery and
governance material into subsections of "How this was built", unchanged apart from one merged ledger statement, one
positional reference that became a link and one lead-in line. It gave the README sheet the start sheet's counts,
changed `SitePagesTest` first (it failed, then passed), and wrote the demo, this entry and the record. An independent
review of head `c09ff0d` approved with changes; its findings were fixed in this pull request, except that the
specification paragraph stays, as that review allowed. A review of `38f3d19` approved with changes; its findings were
fixed too. The independent review of the final head and the merge decision are comments on PR #129.

## Issue 118

The API's comments say what is built, with issue 130's wording items from PR 129's final review, part of epic 113.

[Issue 118](https://github.com/Stig-Johnny/shipping-reference-platform/issues/118) ·
[Issue 130](https://github.com/Stig-Johnny/shipping-reference-platform/issues/130) ·
[PR #131](https://github.com/Stig-Johnny/shipping-reference-platform/pull/131).
Base: `bb4065417b6e7e94c162c01fb11597e6f6ab8e0a`. Record: `issue-118-pull-request` in [model runs](runs.md).

Claude Code, the orchestrator (`claude-opus-5-5`, effort `max`), working through a background fork of its session,
changed the API's comments and Javadoc that still called built parts planned to say what exists, and checked each
claim against the code. It fixed issue 130's wording in demo X-129, a `SitePagesTest` comment and the site design doc,
and wrote the demo, this entry and the record. An independent review of head `493f9a1` approved with changes: its
P2, a shipments configuration comment that still called the built persistence adapters not yet built, and the two of
its four P3s that concerned code comments were fixed in this pull request, and the other two were met in the PR body.
A delta review of `3b15770` approved with changes: its P2, an `ApiTest` comment that still said the JDBC adapters
come in the next pull request, and three stale comments among its P3s were fixed in this pull request, its P3 on this
entry's wording too, and its P3 on a whitelist label, a string rather than a comment, became issue 133. The review of
the final head and the merge decision are comments on PR #131.

## Issue 138

Claude Code loads `CLAUDE.md` but not `AGENTS.md`, so a `CLAUDE.md` beside each `AGENTS.md` now imports the rules for
Claude agents, step 2 of issue 136.

[Issue 138](https://github.com/Stig-Johnny/shipping-reference-platform/issues/138) ·
[PR #139](https://github.com/Stig-Johnny/shipping-reference-platform/pull/139).
Base: `3eb06772fe07a628c62e61bbd79dbf845c177784`. Record: `issue-138-pull-request` in [model runs](runs.md).

Claude Code, the orchestrator (`claude-opus-5-5`, effort unknown), added the five `CLAUDE.md` files, extended the
`agent-instructions` risk rule and `CODEOWNERS` to them, said why they exist in the root `AGENTS.md`, and wrote demo
139, this entry and the record. Issues 135 and 137 measured documentation setups with Claude Haiku 5.5 and Sonnet 5.5
on the command line; this issue's validation through Claude Code's import path found that also importing `BRAIN.md`
raised the tokens processed to 1.32 to 1.36 times in three of four comparisons, with no accuracy improvement in
the sample, so only the rules are imported. The review of the final
head and the merge decision are comments on PR #139.

## Issue 137

The documentation benchmark's held-out development tasks are recorded, together with the decision the benchmark
supported, as ADR 0022, step 1 of issue 136.

[Issue 137](https://github.com/Stig-Johnny/shipping-reference-platform/issues/137) ·
[PR #140](https://github.com/Stig-Johnny/shipping-reference-platform/pull/140).
Base: `8850d7d8f0669fa58283b20b66245b1ef4e30659`. Record: `issue-137-pull-request` in [model runs](runs.md).

Codex (`gpt-6-astra`, effort xhigh) checked the approach first. Claude Code, the orchestrator (`claude-opus-5-5`,
effort unknown), then wrote ADR 0022 with the results of issues 135, 137 and 138, the questions, tasks, answer keys,
overrides and prompts under `tools/docs-bench/` with a README on the method, the entry in the known gaps, demo 140,
this entry and the record. Issue 135 stays open until the harness that ran the benchmark is in the repository. The
review of the final head and the merge decision are comments on PR #140.

## Issue 141

The documentation check now enforces the `AGENTS.md` and `CLAUDE.md` pairs that bring the rules to Claude Code, step 4
of issue 136.

[Issue 141](https://github.com/Stig-Johnny/shipping-reference-platform/issues/141) ·
[PR #142](https://github.com/Stig-Johnny/shipping-reference-platform/pull/142).
Base: `2233bfb07edae0f7146b987a4574c6839da9060a`. Record: `issue-141-pull-request` in [model runs](runs.md).

Codex (`gpt-6-astra`, effort xhigh) checked the design twice before any code was written. Claude Code, the
orchestrator (`claude-opus-5-5`, effort unknown), probed with Claude Haiku 5.5 how Claude Code reads `AGENTS.md`,
wrote the check's tests first and then the check, reduced the five
`CLAUDE.md` files to their import, added `control.agent-instructions`, and wrote demo 142, this entry and the record.
Correction to the entry for issue 138: it says that Claude Code loads `CLAUDE.md` but not `AGENTS.md`. In fact Claude
Code reads `AGENTS.md` by itself where no `CLAUDE.md` exists, by default and not in every session; the import remains
how the rules reach every session that loads `CLAUDE.md` files. ADR 0022 and demo 139 carry the same marked
correction. The reviews of the final head and the merge decision are comments on PR #142.

## Issue 143

Inline code that names a test class is now checked: the class exists, or the name carries the planned marker and is
registered as planned. ADR 0023 records the grammar, the first part of issue 136's explicit reference syntax.

[Issue 143](https://github.com/Stig-Johnny/shipping-reference-platform/issues/143) ·
[PR #147](https://github.com/Stig-Johnny/shipping-reference-platform/pull/147).
Base: `660926c6ec050f740ca2c5b4190818a74e3f3b3d`. Record: `issue-143-pull-request` in [model runs](runs.md).

Codex (`gpt-6-astra`, effort xhigh) checked the design: it proposed explicit markers over a check of the names against
the register alone, which cannot tell a current claim from a plan, and the repository owner chose the markers. Claude
Code, the orchestrator (`claude-opus-5-5`, effort unknown), wrote the tests first and then the check, a reader of inline
code in the Markdown renderer, ADR 0023, the planned marker on the 47 mentions of planned tests in current documents,
`control.inline-references`, demo 147, this entry and the record. Issues 144 to 146 add capability ids, the site's
labels and paths. The reviews of the final head and the merge decision are comments on PR #147.

## Issue 144

A capability or control named in inline code now carries the cap: marker and must be an entry of
`docs/capabilities/capabilities.yaml`, the second part of ADR 0023's reference grammar.

[Issue 144](https://github.com/Stig-Johnny/shipping-reference-platform/issues/144) ·
[PR #148](https://github.com/Stig-Johnny/shipping-reference-platform/pull/148).
Base: `2a91180858e86758d8e5438f588b57939a2045ae`. Record: `issue-144-pull-request` in [model runs](runs.md).

Claude Code, the orchestrator (`claude-opus-5-5`, effort unknown), wrote the tests first and then the rules in the
reference check, the marker on the 38 mentions of capabilities and controls in current documents, ADR 0023's grammar
line, the rule in `AGENTS.md`, demo 148, this entry and the record. The design came from Codex's earlier design check
for issue 136, step 4. The reviews of the final head and the merge decision are comments on PR #148.

## Issue 145

The documentation site now shows the reference markers of ADR 0023 as labels in current prose: a planned test as its
name and a dashed planned box, a capability or control id without its prefix. GitHub keeps the markers as written.

[Issue 145](https://github.com/Stig-Johnny/shipping-reference-platform/issues/145) ·
[PR #150](https://github.com/Stig-Johnny/shipping-reference-platform/pull/150).
Base: `8d18a058fa93d9486d399227c474f0aeafa60f87`. Record: `issue-145-pull-request` in [model runs](runs.md).

Claude Code, the orchestrator (`claude-opus-5-5`, effort unknown), rendered three label designs on the real pages,
wrote the tests first and then the labels in the Markdown renderer, the shared rule for current prose, the
stylesheet, ADR 0023's enforcement, the site design, the README row, `docs/gaps.md`, demo 150, this entry and the
record. Codex (`gpt-6-astra`, effort `xhigh`) chose the design before the build and set its conditions. The reviews
of the final head and the merge decision are comments on PR #150.

## Issue 146

A path named in inline code must now exist, or carry a marker that says it is planned, meant to be absent or an
example; the site shows those markers as labels, an absent path in a muted box.

[Issue 146](https://github.com/Stig-Johnny/shipping-reference-platform/issues/146) ·
[PR #151](https://github.com/Stig-Johnny/shipping-reference-platform/pull/151).
Base: `37161e079856107d74b7da61dcd80eba0cf240f9`. Record: `issue-146-pull-request` in [model runs](runs.md).

Claude Code, the orchestrator (`claude-opus-5-5`, effort unknown), surveyed the paths in current prose, rendered three
designs of the absent label, wrote the tests first and then the path grammar, the check, the labels and the full-copy
fixture, migrated the mentions, and updated ADR 0023, the README row, `docs/gaps.md`, `capabilities.yaml`, `AGENTS.md`,
the site design, demo 151, this entry and the record. Codex (`gpt-6-astra`, effort `xhigh`) chose the label and
required six changes to the grammar before the build. The reviews of the final head and the merge decision are
comments on PR #151.

## Issue 152

A sequence or state diagram in the prose now carries the label Unchecked design right before it, or the documentation
check fails, and every dynamic view of the model carries the label from the generator, on GitHub and on the site.

[Issue 152](https://github.com/Stig-Johnny/shipping-reference-platform/issues/152) ·
[PR #154](https://github.com/Stig-Johnny/shipping-reference-platform/pull/154).
Base: `9cc5342c714707cf652c4ffcc8f38ecbec688d58`. Record: `issue-152-pull-request` in [model runs](runs.md).

Claude Code, the orchestrator (`claude-opus-5-5`, effort unknown), surveyed the behavioural diagrams, rendered three
designs of the label, wrote the tests first and then the shared reader, the check, the closed sources at any depth, the
generated labels and the migration, and updated ADR 0024, ADR 0013, the README row, `docs/gaps.md`,
`capabilities.yaml`, `AGENTS.md`, the site design, demo 154, this entry and the record. Codex (`gpt-6-astra`, effort
`xhigh`) chose the label's look, required every dynamic view to stay labelled until a complete comparison is proven,
and accepted moving that evidence to issue 153. The reviews of the final head and the merge decision are comments on
PR #154.

## Issue 156

Every example in the OpenAPI contract is now validated against its schema in every build, and a problem example
against the error registry and its response.

[Issue 156](https://github.com/Stig-Johnny/shipping-reference-platform/issues/156) ·
[PR #158](https://github.com/Stig-Johnny/shipping-reference-platform/pull/158).
Base: `455d858e89a6f70c89a42c54f0bd2b44592b7f55`. Record: `issue-156-pull-request` in [model runs](runs.md).

Claude Code, the orchestrator (`claude-opus-5-5`, effort unknown), counted the contract's examples, wrote the tests
first and then the reader and the checks in the HTTP contract tests' support class, and updated the HTTP contract
control, the README, ADR 0012 and its evidence sentence on the site, demo 158, this entry and the record. Codex
(`gpt-6-astra`, effort `xhigh`) split this work from issue 155 in its design review and asked that a problem example's
type be one its response names; reviewing the head, it found three gaps, fixed before the merge. The reviews of the
final head and the merge decision are comments on PR #158.

## Issue 159

The documentation check reads the contract's problem registry strictly, and every problem response must start its
description with registered types of its own status (ADR 0025).

[Issue 159](https://github.com/Stig-Johnny/shipping-reference-platform/issues/159) ·
[PR #160](https://github.com/Stig-Johnny/shipping-reference-platform/pull/160).
Base: `6d3575e82b3076c995555fd444ffb8b957275121`. Record: `issue-159-pull-request` in [model runs](runs.md).

Claude Code, the orchestrator (`claude-opus-5-5`, effort unknown), wrote the tests first and then the registry reader,
the response check, ADR 0025, the control, the README row, the gaps entry, the contract-first rule, demo 160, this entry
and the record. Codex (`gpt-6-astra`, effort `xhigh`) set the strictness in its design review of issue 155. The reviews
of the final head and the merge decision are comments on PR #160.

## Issue 157

Each HTTP operation and module problem type of the contract names the capabilities that serve or raise it; the brain
and the API pages show each operation's state, and the cancellation operation shows as planned (ADR 0025).

[Issue 157](https://github.com/Stig-Johnny/shipping-reference-platform/issues/157) ·
[PR #161](https://github.com/Stig-Johnny/shipping-reference-platform/pull/161).
Base: `d81b9f4e0592bb106252f7710481660de85fb2cf`. Record: `issue-157-pull-request` in [model runs](runs.md).

Claude Code, the orchestrator (`claude-opus-5-5`, effort unknown), wrote the tests first and then the bindings, the
check, the planned rule, the brain's mark and the API panels, and updated ADR 0025, the control, the README row, the
gaps entry, `AGENTS.md`, demo 161, this entry and the record. Codex (`gpt-6-astra`, effort `xhigh`) set the bindings
in its design review of issue 155. The reviews of the final head and the merge decision are comments on PR #161.

## Issue 155

Each module document's HTTP operations section is generated from the contract and the capabilities; an edit of it
fails the documentation check, and generate restores it (ADR 0025).

[Issue 155](https://github.com/Stig-Johnny/shipping-reference-platform/issues/155) ·
[PR #162](https://github.com/Stig-Johnny/shipping-reference-platform/pull/162).
Base: `d50a1c4170f242c848507a87b4e4bb6547ebf955`. Record: `issue-155-pull-request` in [model runs](runs.md).

Claude Code, the orchestrator (`claude-opus-5-5`, effort unknown), wrote the tests first and then the generator, the
composed-text reads of the reference and anchor checks and the two commands, regenerated the three module documents,
and updated ADR 0025, the control, the README row, the gaps entry, `AGENTS.md`, demo 162, this entry and the record.
Codex (`gpt-6-astra`, effort `xhigh`) set the notice, the columns and the edge cases in its design review of issue 155.
The reviews of the final head and the merge decision are comments on PR #162.
