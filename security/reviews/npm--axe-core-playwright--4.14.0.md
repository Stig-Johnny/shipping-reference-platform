---
component: "@axe-core/playwright"
kind: npm
version: "4.14.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "devDependency of apps/shipping-web: accessibility assertions inside @playwright/test 1.65.0 runs (contract-e2e job); installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: ["Lockfile pins @axe-core/playwright 4.14.0 (sha512-SGUhG5PIHd2eMdpgBF2PyQLOhX+qyDR2PT4cmoS4Qk2ak+jX/rrSvst9l0PGv5WTHolKpUMvVmoHpr4cZgf1RQ==) resolved from registry.npmjs.org, never from git (its prepare script runs `playwright install`)", "axe-core resolves to exactly 4.14.0"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: @axe-core/playwright 4.14.0

**Method.**

- **Sources:** https://registry.npmjs.org/@axe-core/playwright/4.14.0, deps.dev (the version and package
  endpoints) and osv.dev, all read on 2026-11-06.
- **Not done:** nothing was downloaded or run.
- **Gap:** the deps.dev project for dequelabs/axe-core-npm returned 404.

## 1. Verdict

**APPROVE-WITH-CONDITIONS.**

- **Fit:** 4.14.0 is the newest release, and its `axe-core` range `~4.14.0` accepts 4.14.0.
- **Size and provenance:** it is a 48 KB adapter with verified provenance.
- **One script to keep inert:** `prepare` runs `pnpm exec playwright install`. It is inert for a registry install
  with `--ignore-scripts`, and the conditions keep it that way.

## 2. What it is and why it is needed

Deque's Playwright integration: it injects axe-core into a page and returns accessibility violations. The blueprint
requires axe as a supplement to manual accessibility checks (§4: "axe-core 4.14.0").

## 3. Provenance

- **Publisher.** `_npmUser` is "GitHub Actions" (trustedPublisher `github`).
- **npm maintainers.** dqlabs, wilcofiers, dylanb, npmdeque, stephendeque.
- **SLSA provenance.** Built from dequelabs/axe-core-npm, commit `2375f7f5523a2accaeef91a3cb12e30a46e7fbea` (the
  same as `gitHead`). Marked `verified: true`
  (https://api.deps.dev/v3/systems/npm/packages/%40axe-core%2Fplaywright/versions/4.14.0).
- **Install scripts.** There is no preinstall, install or postinstall. There is a **`prepare`** script:
  `pnpm exec playwright install && pnpm run build`.
- **Native binaries:** none.

## 4. Maintenance and adoption

- **Releases.**
  - 4.14.0 is `latest`, published 2026-10-15T13:33:17Z.
  - The previous releases were 4.13.0 (2026-08-11) and 4.12.1 (2026-06-23).
  - Snapshot prereleases `4.14.1-<sha>` appeared on 2026-11-06.
- **Scorecard:** none was available for the axe-core-npm repo.

## 5. Known vulnerabilities at this version

**None found on 2026-11-06:**

- deps.dev: `advisoryKeys: []`.
- osv.dev: no entry for this name.

## 6. License

**MPL-2.0.** These are file-level copyleft obligations, and they apply only if modified files are distributed. Test
use with the files unmodified triggers none.

## 7. Execution privileges

- **When it runs:** at test time, in Node and in the browser page.
- **What it does:** it reads `axe-core`'s source and evaluates it in the page under test through Playwright, then
  collects the results.
- **What it does not do:** it has no network access of its own beyond the page.

## 8. Direct dependencies as published

- **Dependency:** `axe-core` `~4.14.0`, reviewed separately.
- **Peer:** `playwright-core` `>= 1.0.0`, which 1.65.0 satisfies.
- **Engines:** `node >=22.12.0`.

## 9. Alternatives considered

- **Calling `axe-core` directly with `page.addScriptTag`:** one package fewer, but more test glue.
- **Playwright's own accessibility snapshot:** this is not a rules engine.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk:**

- the project has no Scorecard data;
- the release was 3 weeks old.

## 11. Not verified

- The tarball contents. *Closed by the 2026-11-07 amendment.*
- The maintainers' identities. Still not verified.

## Amendment, 2026-11-07: the tarball audit

**Method.** The security-review agent did this on 2026-11-07, as for axe-core:

- **Downloaded** the tarball into a new empty directory on the owner's MacBook and checked it against `dist.integrity`,
  `dist.shasum` and the registry signatures.
- **Verified the attestations.**
- **Listed and read** its seven entries.
- **Queried advisories** in OSV, deps.dev and GitHub's advisory database.

Nothing was installed or run.

**Identity and provenance.**

- **The tarball.** `playwright-4.14.0.tgz`, 12,112 bytes, 7 entries (= `dist.fileCount`), SHA-256
  `0d16690e81a9c80f653920da310010bd530b3e2bffe18d299769113848b40bdf`. Its integrity equals the value in the conditions.
- **Signatures.** Both registry signatures and the publish attestation are "Verified OK" against npm's key, and the
  subject's SHA-512 equals the tarball.
- **SLSA provenance: verified as far as the certificate.**
  - The DSSE signature checks against a Fulcio certificate that chains to Sigstore's Fulcio root at signing time
    (2026-10-15 13:31:37Z).
  - The certificate binds `https://github.com/dequelabs/axe-core-npm/.github/workflows/deploy.yml@refs/heads/master`,
    trigger `push`, commit `2375f7f5523a2accaeef91a3cb12e30a46e7fbea` (= `gitHead`, as in section 3) and run
    `38743173140`.
  - The registry's publish attestation is from 13:33:21Z. Rekor inclusion was not verified.

**Contents.**

- `dist/index.js` (11,756 bytes, CommonJS) and `dist/index.mjs` (9,683 bytes, ES module), from one tsup build, with
  their two `.d.ts` files, `LICENSE` (MPL-2.0), `README.md` and `package.json`.
- The only modules required are `assert` and `axe-core`. There is no inlined third-party code: no `__commonJS`, `__esm`
  or `node_modules` marker, only the package's own functions.
- No native code, no executable file, and no text addressed to AI agents (case-insensitive search).

**What it does.** It reads `axe-core`'s source and evaluates it in every frame with `frame.evaluate`, then runs
`axe.runPartial` and `axe.finishRun` (or `axe.run` in legacy mode) and returns the results. It makes no network
request of its own. The one URL in it (`errorUrl`, a GitHub documentation page) appears only in error messages.

**The `prepare` script** (`pnpm exec playwright install && pnpm run build`). It is unchanged in the tarball's
`package.json`. npm runs `prepare` for git and local installs, not for a registry tarball, and `--ignore-scripts`
stops it anyway. The existing condition (registry only, never git) stands.

**Advisories: none.** OSV, deps.dev and GitHub's advisory database return nothing for 4.14.0 on 2026-11-07.
