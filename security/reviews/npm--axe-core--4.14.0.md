---
component: "axe-core"
kind: npm
version: "4.14.0"
verdict: APPROVE
scope: "devDependency of apps/shipping-web (via @axe-core/playwright 4.14.0): accessibility rules evaluated inside the page under test in contract-e2e; installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: []
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: axe-core 4.14.0

**Method.** I read the following on 2026-11-06:

- https://registry.npmjs.org/axe-core/4.14.0
- https://api.deps.dev/v3/systems/npm/packages/axe-core/versions/4.14.0
- osv.dev
- the dequelabs/axe-core scorecard

Nothing was downloaded or run.

## 1. Verdict

**APPROVE.** It has no dependencies and no install-time scripts, its provenance is verified, and it has no
advisories. It runs only inside the browser page under test.

## 2. What it is and why it is needed

The accessibility rules engine. The blueprint pins axe-core 4.14.0 (§4): "axe supplements manual checks".

## 3. Provenance

- **Publisher.** `_npmUser` is "GitHub Actions" (trustedPublisher `github`).
- **npm maintainers.** dylanb, wilcofiers, dqlabs, npmdeque.
- **SLSA provenance.** The build points to dequelabs/axe-core at commit `e797173108eadfa4355fe06af331791734e1757f`,
  with `verified: true`.
- **Integrity.** `dist.integrity` is
  `sha512-9WTZxEjsZ7b13TH8JPmbV2z8CHbl80/2hm3XPEG4JgNdQLK81IBRXmSxHfMAOkSqQeRxT/0dwNDz2GOm3zzpcQ==` (3,430,531 bytes
  unpacked, 31 files).
- **Install scripts.** There is no preinstall, install, postinstall or prepare. `pretest`, `prebuild`, `postbuild` and
  `version` exist, but they are development scripts.
- **Native binaries.** None.

## 4. Maintenance and adoption

- **Release.** 4.14.0 was published 2026-10-05T17:43:36Z and is `latest`.
- **Scorecard 6.5** (2026-08-24): Code-Review 10, Maintained 10, Token-Permissions 0, Pinned-Dependencies 5,
  Branch-Protection 4. The repo has 7,764 stars (https://api.deps.dev/v3/projects/github.com%2Fdequelabs%2Faxe-core).

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.** deps.dev shows `advisoryKeys: []`. On osv.dev the only nearby entry is a look-alike,
MAL-2025-41281 `axe-core-scanner` (https://osv.dev/list?ecosystem=npm&q=axe-core).

## 6. License

**MPL-2.0.** It is used unmodified and only in tests, and it is not distributed.

## 7. Execution privileges

- **Where it runs:** at test time, as JavaScript evaluated in the browser page.
- **What it does:** it reads the DOM and computed styles.
- **Network:** none of its own was indicated. The tarball audit confirms this.

## 8. Direct dependencies as published

None. `engines.node` is `>=4`.

## 9. Alternatives considered

- **Pa11y and Lighthouse:** both wrap axe or their own engines, with larger trees.

## 10. Conditions and residual risk

- **Conditions:** none beyond the exact pin through `@axe-core/playwright`.
- **Residual risk:** the Scorecard shows Token-Permissions 0, meaning the upstream workflows have broad tokens.

## 11. Not verified

- **Tarball contents:** not read.
- **GitHub release notes:** not read, because api.github.com returned 403.
