---
title: "Contribution history"
description: "Per-issue implementation, integration, review and human approval records, with exact settings where evidenced and unknowns where receipts are missing."
status: accepted
updated: 2026-11-09
---

# Contribution history

Use the contents list or site search for `Issue N`. [How to read and extend this register](../contributions.md).
Initial backfill recorded on **2026-11-09** from 41 merged PRs through #97, covering 42 issues with implemented
scope, plus current issue #96. PR #71 closes both #63 and #85. PR #77 implements only phase 1 of #66.
PR #86's reference to future #82 work is excluded. A record is not a claim that the whole issue is complete.

## Backfill rule

The `historical implementation` phase used below has these explicit values: work date `unknown`, tool/agent
`unknown`, configured model `unknown`, resolved checkpoint `unknown`, reasoning effort `unknown`.
Its source is the linked PR and merge metadata, inspected on 2026-11-09; that establishes merged scope but contains
no verified runtime settings receipt recovered for this backfill. Independent AI review and human approval are
also `unknown` unless separately recorded. Unknown means missing evidence, not that no AI or human contributed.
Merge timestamps below are UTC and are never substituted for work dates. Product footers were not used as settings.

Known phases below use receipts recorded on 2026-11-09. The table's date is the observed work date/time at the
precision the receipt supports. No receipt here establishes a resolved provider checkpoint. Private source logs
are not published. Model provenance, review outcome, owner approval and passing tests are different evidence.

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
