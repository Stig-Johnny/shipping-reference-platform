---
component: "vitest"
kind: npm
version: "5.1.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "devDependency of apps/shipping-web: `vitest run` in CI (web job) with the jsdom environment; installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: ["Lockfile pins vitest 5.1.0 (sha512-MzmNFnrwa8v8lF1bLxw3UJ4EREwVOVb75VqJZmUDRjJ6EluK/x9th7qTbaeDk41WxE1GxWqmTXcdsfobfH5sOA==) and @vitest/mocker 5.1.0, with vite 8.3.6", "Only `vitest run` (no watch server): no --ui, no api option, no Browser Mode, no @vitest/ui, @vitest/browser-* or coverage packages without their own review", "Test environment jsdom 30.1.3 (npm--jsdom--30.1.3); happy-dom is rejected"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: vitest 5.1.0

**Method.** Read on 2026-11-06:

- https://registry.npmjs.org/vitest/5.1.0
- https://registry.npmjs.org/@vitest/mocker/5.1.0
- deps.dev
- the osv.dev advisory pages GHSA-82fw-gwwq-j7x9, GHSA-qxg6-89mg-rqv8, GHSA-xp26-3454-mjgp and GHSA-p458-7748-mcp3
- https://raw.githubusercontent.com/vitest-dev/vitest/v5.1.0/packages/vitest/src/integrations/env/jsdom.ts
- https://vitest.dev/guide/environment

Nothing was downloaded or run.

## 1. Verdict

**APPROVE-WITH-CONDITIONS.** Provenance is verified, and publishing used trusted publishing with an approver. It has
no install scripts.

**No advisory affects 5.1.0 or the `@vitest/mocker` 5.1.0 it pins.** The October 2026 advisories all need a Vitest
or Vite **server** (UI, API, Browser Mode or the mocker WebSocket), so the conditions restrict it to `vitest run`.

## 2. What it is and why it is needed

The unit and component test runner for the React app ("Contract/frontend: component behaviour", blueprint §4, §7).

## 3. Provenance

- **Publisher.** `_npmUser` is "GitHub Actions" with `approver` `oreanno` (trustedPublisher `github`). Maintainers:
  ariperkkio, antfu, hiogawa, oreanno, yyx990803.
- **SLSA provenance.** vitest-dev/vitest commit `7a3844954d8753fc0d5bed76a96ffd3a21716c22`, `verified: true`
  (https://api.deps.dev/v3/systems/npm/packages/vitest/versions/5.1.0). `@vitest/mocker` 5.1.0 comes from the same
  commit, the same publisher and approver, with a verified attestation.
- **Install scripts:** none in either package. vitest has only `dev` and `build`.
- **Native binaries:** none.

## 4. Maintenance and adoption

- **Releases.** 5.1.0 came out 2026-11-02T16:12:37Z and is `latest`. 5.0.0 was 2026-09-03, followed by 5.0.1–5.0.5
  (to 10-15).
- **Scorecard:** none available. deps.dev has no `scorecard` for vitest-dev/vitest, and api.securityscorecards.dev
  returns 404. It has 17,340 stars.

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.** deps.dev has `advisoryKeys: []` for both packages.

| Advisory | What it needs | Fixed in | 5.1.0 |
|---|---|---|---|
| GHSA-82fw-gwwq-j7x9: mocker redirect path traversal | `vitest` 5.0.0-beta.1 to 5.0.0-rc.2 | 5.0.0 | not affected |
| GHSA-qxg6-89mg-rqv8: unauthenticated mock registration | `mockerPlugin()`/`interceptorPlugin()` dev server | `@vitest/mocker` 5.0.3 | not affected |
| GHSA-xp26-3454-mjgp: Windows backslash traversal | Windows; the same plugins | `@vitest/mocker` 5.0.3 | not affected |
| GHSA-p458-7748-mcp3: `server.fs.deny` misapplied | `--ui`, Browser Mode or `api`; "Plain `vitest`/`vitest run` don't listen on any port" | `@vitest/ui`/`@vitest/browser` 5.0.5 | not used, and outside the ranges |

## 6. License

MIT.

## 7. Execution privileges

- **When it runs:** test time.
- **What it executes:** all test files and the code under test, in Node worker processes or threads, with the runner
  user's full privileges. It also runs `vite.config`/`vitest.config` and Vite plugins.
- **jsdom environment.** Vitest constructs `new JSDOM(html, { runScripts: 'dangerously', url:
  'http://localhost:3000', … })`. It replaces `fetch`, `Response`, `Headers` and others with Node's globals, so a
  test's `fetch` is real network (`jsdom.ts`).
- **Servers.** It starts HTTP or WebSocket servers only for UI, API or Browser Mode, which the conditions exclude.

## 8. Direct dependencies as published

13 dependencies:

- `@vitest/mocker` 5.1.0 (exact): module mocking. It depends on `@vitest/spy` 5.1.0, `magic-string`,
  `estree-walker` and `@jridgewell/trace-mapping`.
- `chai` ^6.3.0 and `@types/chai` ^5.2.2: assertions.
- `expect-type` ^1.4.0: type tests.
- `tinybench` ^6.2.2: bench mode.
- `tinyexec` ^1.3.1: **spawns processes**, for example git for `--changed`. Look at its call sites in the tarball
  audit.
- `tinyglobby` ^0.2.17 and `picomatch` ^4.0.7: file selection.
- `magic-string` ^1.4.3 and `es-module-lexer` ^2.3.2: transforms.
- `std-env` ^4.3.0: CI detection.
- `obug` ^3.0.0: debug logging.
- `why-is-node-running` 3.2.1 (exact): handle diagnostics.

Peers:

- `vite` `^6.4.0 || ^7.0.0 || ^8.0.0`, the only required peer, so it accepts 8.3.6.
- `jsdom` `*` and `happy-dom` `*`, optional.
- `@vitest/ui`, browser, coverage and others, all optional.

Engines: `^22.12.0 || ^24.0.0 || >=26.0.0`.

## 9. Alternatives considered

- **Jest:** a heavier Babel-based tree and no shared Vite config.
- **`node --test`:** no JSX or DOM pipeline.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk.**

- **Tests run with full privileges.** A compromised transitive dependency would execute in CI.
- **Age.** The release was 4 days old at review.
- **Advisory history.** The October 2026 advisories show repeated server-side issues. They are kept unreachable by
  not starting those servers.

## 11. Not verified

- **Tarball contents.** Not read.
- **`tinyexec`.** Where it is invoked.
- **Scorecard.** None was available.
