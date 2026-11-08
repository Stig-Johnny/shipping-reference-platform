---
component: "axe-core"
kind: npm
version: "4.14.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "devDependency of apps/shipping-web (via @axe-core/playwright 4.14.0): accessibility rules evaluated inside the page under test in contract-e2e; installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: ["Lockfile pins axe-core 4.14.0, resolved from https://registry.npmjs.org/axe-core/-/axe-core-4.14.0.tgz with integrity sha512-9WTZxEjsZ7b13TH8JPmbV2z8CHbl80/2hm3XPEG4JgNdQLK81IBRXmSxHfMAOkSqQeRxT/0dwNDz2GOm3zzpcQ==, and nothing newer is resolved for @axe-core/playwright's ~4.14.0", "It runs only inside pages of the locally started application, under the conditions of @playwright/test 1.65.0; its stylesheet preload (one XMLHttpRequest GET) then reaches only stylesheets that page references", "The 21 components inlined in axe.js and axe.min.js (the 2026-11-07 amendment, one allowlist entry of kind bundled each) are approved only as inlined there, never installed or resolved on their own", "Licences: axe-core is MPL-2.0, used unmodified in tests and never distributed; nine inlined components are ISC (d, es5-ext, es6-symbol, es6-weak-map, ext, memoizee, next-tick, timers-ext, type), which needs the owner's explicit decision (security/AGENTS.md)"]
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

**APPROVE-WITH-CONDITIONS** (amended 2026-11-07; it was APPROVE). It has no dependencies, no install-time scripts and
no advisories. On 2026-11-07 its registry signatures, the DSSE signatures of its attestations and the provenance
certificate's chain to Sigstore's Fulcio root were checked; Rekor inclusion was not. It runs only inside the browser page under test. The
tarball audit found 21 inlined third-party components, nine of them under ISC. The conditions confine them to their
inlined use and put the licence question to the owner.

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

- **Conditions:** none beyond the exact pin through `@axe-core/playwright`. *Superseded on 2026-11-07: see the
  frontmatter and the amendment.*
- **Residual risk:** the Scorecard shows Token-Permissions 0, meaning the upstream workflows have broad tokens.

## 11. Not verified

- **Tarball contents:** not read. *Closed by the 2026-11-07 amendment.*
- **GitHub release notes:** not read, because api.github.com returned 403. Still not read.

## Amendment, 2026-11-07: the tarball audit and the bundled code

**Method.** The security-review agent did this on 2026-11-07, in new empty directories on the owner's MacBook, with
`/usr/bin/curl --proto '=https'`, `/usr/bin/openssl`, `/usr/bin/tar` and `/usr/bin/python3`:

- **Downloaded** the tarball and checked it against `dist.integrity`, `dist.shasum` and the registry signatures.
- **Verified the attestations**, as for Playwright (`npm--playwright-test--1.65.0.md`, amendment A).
- **Listed and read** the 31 entries.
- **Read** `pnpm-lock.yaml` at the provenance commit from `raw.githubusercontent.com`.
- **Queried advisories** in OSV for the package and for its 21 inlined components (`POST /v1/querybatch`), and in
  deps.dev and GitHub's advisory database for the package.

Nothing was installed or run.

**Identity and provenance.**

- **The tarball.** `axe-core-4.14.0.tgz`, 706,680 bytes, 31 entries (= `dist.fileCount`), SHA-256
  `2277b0a3ef47af9b52497faa3e71598e86e0a0e5e6eb295207a5657ede4709af`. Its integrity equals section 3's value.
- **Registry signatures:** both "Verified OK" against npm's key `SHA256:DhQ8wR5A…`. The publish attestation verifies
  against the same key, and its subject's SHA-512 equals the tarball.
- **SLSA provenance: verified as far as the certificate.**
  - The DSSE signature checks against its Fulcio certificate, which chains to Sigstore's Fulcio root at signing time
    (2026-10-05 17:40:25Z).
  - The certificate binds `https://github.com/dequelabs/axe-core/.github/workflows/deploy.yml@refs/heads/master`,
    trigger `push`, commit `e797173108eadfa4355fe06af331791734e1757f` (as in section 3) and run `37348428411`.
  - The registry's publish attestation is from 17:43:39Z.
  - Rekor inclusion was not verified.

**Contents.**

- `axe.js` (1,477,661 bytes; the `main`) and `axe.min.js` (652,140 bytes): one IIFE each, MPL-2.0 header,
  `axe.version = '4.14.0'`.
- `gather-internals.js` (6,949 bytes): axe-core's own helper, built from its `lib/gather-internals`.
- `axe.d.ts`, `sri-history.json` (data), 21 `locales/*.json` with `locales/README.md`, `LICENSE`,
  `LICENSE-3RD-PARTY.txt`, `README.md` and `package.json`.
- No native code, no executable file and no install-time script.
- A case-insensitive search for text addressed to AI agents found none.

**What it does when evaluated in a page (`axe.js`).**

- **One network call.** A single `new window.XMLHttpRequest()` (line 24185), a `GET` in
  `parseCrossOriginStylesheet`, fetches stylesheets the page itself references (the CSSOM preload).
- **Code compiled from strings.** `new Function` appears at six sites: doT template compilation, memoizee's
  function-length shim, and rule metadata evaluated from axe's own embedded definitions or from `axe.configure` input.
  - The strings come from axe-core itself or from the test's configuration.
  - A page whose CSP lacks `'unsafe-eval'` would block them, unless the test context sets `bypassCSP`.
- **Frames.** `postMessage` is used between frames for the frame messenger.
- No `fetch`, `WebSocket`, `sendBeacon` or `eval`.

**The bundled code.** `LICENSE-3RD-PARTY.txt` names 21 inlined components without versions. The versions below come
from `pnpm-lock.yaml` (lockfileVersion 9.0) at the provenance commit:

| Licence | Components (version) |
|---|---|
| MIT (12) | @deque/dot 1.1.5 (doT.js), colorjs.io 0.7.1 (patched by axe-core, `patch_hash=4100ea52…`), core-js-pure 3.50.0, css-selector-parser 1.4.1, emoji-regex 11.0.0, es6-iterator 2.0.3, es6-promise 4.2.8, event-emitter 0.3.5, is-promise 2.2.2, lru-queue 0.1.0, typedarray 0.0.7, weakmap-polyfill 2.0.4 |
| ISC (9) | d 1.0.2, es5-ext 0.10.64, es6-symbol 3.1.4, es6-weak-map 2.0.3, ext 1.7.0, memoizee 0.4.17, next-tick 1.1.0, timers-ext 0.1.8, type 2.7.3 |

- **Presence by fingerprint.** The minified bundle keeps only some names: `ES6Promise`, `memoize`, `core-js`, `doT`,
  `CssSelectorParser` and `typedarray` were found. The others rest on the vendor's list.
- **esniff 2.0.1** is in the lock as a dependency of es5-ext but not in the list. No fingerprint was found, so whether
  it is inlined stays open.
- **Allowlist.** Each of the 21 has its own allowlist entry of kind `bundled` (the Playwright amendment explains the
  kind).
- **Advisories: none.** OSV `querybatch` returns nothing for any of the 21 versions, and the package itself has none in
  OSV, deps.dev or GitHub's advisory database on 2026-11-07.

**The limit of this inventory.** It is evidence, not a proven complete closure. It is the vendor's list, with versions
from the release lockfile, and minified fingerprints confirm only part of it.

**Still not verified:** whether `esniff` is inlined; Rekor inclusion; a rebuild of `axe.js` from source.
