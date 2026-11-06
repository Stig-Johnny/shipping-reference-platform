---
component: "react"
kind: npm
version: "19.3.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "Runtime dependency of apps/shipping-web: shipped in the browser bundle and loaded by Vitest/Playwright tests; installed by npm ci --ignore-scripts from the reviewed lockfile (CI and the lock-maintenance environment)"
conditions: ["Lockfile pins react 19.3.0 with integrity sha512-E8LUcbtBWt20bbl2YoHfx4ZDBdxVTfOKtCZn9cDSJ4l6/nuoApcpIBcj47t2wZoVX8g2ZHuMHbiShgCR1T5Sog==", "Client-side rendering only: no React Server Components, Server Functions or react-server-dom-* packages (the area 19.3.1 changed)", "Plan the move to 19.3.1 as a new review"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: react 19.3.0

**Method.**

- **Read on 2026-11-06:** registry metadata (https://registry.npmjs.org/react/19.3.0), the npm attestation bundle,
  deps.dev, osv.dev, https://react.dev/versions, the React 19.3 blog post and https://github.com/react/react/releases.
- **Not done:** no tarball was downloaded or run, so contents are left to the orchestrator's tarball audit. OSV
  `POST /v1/query` was not possible with the GET-only fetch tool.

## 1. Verdict

**APPROVE-WITH-CONDITIONS.** The package has no dependencies and no install scripts. It was published by npm trusted
publishing from React's own release workflow, with verified SLSA provenance, and it has no known advisory.

**19.3.0 is no longer the newest release.** 19.3.1 shipped on 2026-10-14, together with 19.2.9, 19.1.10 and 19.0.9.
The only note is "Improve transport protocol", under "React Server Components". This app is a Vite SPA and uses no
Server Components, so 19.3.0 is acceptable here on that condition. Plan the move to 19.3.1.

## 2. What it is and why it is needed

The UI library for `apps/shipping-web`. The blueprint pins "React 19.3.0" (§4).

## 3. Provenance

- **Publisher.** `_npmUser` is "GitHub Actions" (trustedPublisher `github`). npm maintainers: `fb`, `react-bot`.
- **Repository.** `git+https://github.com/react/react.git`. Releases now come from the `react` organisation.
- **Attestations.** https://registry.npmjs.org/-/npm/v1/attestations/react@19.3.0 holds npm publish v0.1 and SLSA
  provenance v1:
  - workflow `.github/workflows/runtime_release_from_ci.yml`
  - `refs/heads/main`, event `workflow_dispatch`
  - commit `1d34f91dfde6bba84d08b683aaba164c7194dacb`
  - builder `https://github.com/actions/runner/github-hosted`
  - deps.dev marks it `verified: true`.
- **Integrity.** `dist.integrity` is shown in the frontmatter; `shasum` is
  `e20aa3a14687075ce863351b5878b0608c3fbfb2`. There are registry signatures with keyid `SHA256:DhQ8wR5A…`.
- **Release commit.** The GitHub release v19.3.0 is at the same commit `1d34f91d…`.
- **Install scripts:** none (no `scripts` key).
- **Native binaries:** none.

## 4. Maintenance and adoption

- **Release history.**
  - 19.3.0 was published 2026-09-09.
  - Patch batches across all 19.x lines appeared on 2025-12-03, 12-11, 2026-01-26, 04-08, 05-06, 06-01, 07-21 and
    10-14.
  - Latest is 19.3.1 (deps.dev `isDefault`: https://api.deps.dev/v3/systems/npm/packages/react).
- **Docs lag.** react.dev/versions still lists 19.3 (September 9, 2026) as latest.
- **Scorecard 7.0** (2026-08-24, for github.com/facebook/react): Code-Review 10, Maintained 10, Token-Permissions 10,
  Pinned-Dependencies 0, Branch-Protection 5. It has 252,018 stars
  (https://api.deps.dev/v3/projects/github.com%2Ffacebook%2Freact). deps.dev has no scorecard for react/react yet.

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.**

- **deps.dev** has `advisoryKeys: []` (https://api.deps.dev/v3/systems/npm/packages/react/versions/19.3.0).
- **OSV.** Every React Server Components advisory names only `react-server-dom-webpack`, `-turbopack` and `-parcel`.
  The newest is GHSA-wx67-qw84-cm4g (2026-07-24), fixed in 19.2.8, 19.1.9 and 19.0.8
  (https://osv.dev/vulnerability/GHSA-wx67-qw84-cm4g).
- **Inference, not a published fact.** The July fixes shipped as "performance improvements" three days before their
  advisory went public. The October "transport protocol" batch fits that pattern, so an RSC advisory may follow.

## 6. License

MIT (registry, deps.dev).

## 7. Execution privileges

- **Where it runs:** in end users' browsers, inside the production bundle.
- **At test time:** in Node, under Vitest/jsdom and in Playwright-driven Chromium.
- **At build time:** nothing; it runs no code during the build.
- **Not observed:** network, process or file-system access by the package itself. That is left to the tarball audit.

## 8. Direct dependencies as published

None (no `dependencies`, `peerDependencies` or `optionalDependencies`). `engines.node` is `>=0.10.0`.

## 9. Alternatives considered

- **react 19.3.1:** the same line plus the RSC transport fix. It needs its own review.
- **Preact:** smaller, but `@testing-library/react` and `@vitejs/plugin-react` target React.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk.**

- **New maintainers.** The repo moved from facebook/react to react/react, so the governance and maintainer set are
  new. The release workflow runs on `workflow_dispatch`, so anyone with dispatch rights on `main` can start a
  release.
- **Type definitions are not covered.** TypeScript type-checking needs `@types/react` and `@types/react-dom`, which
  are not covered by this review.

## 11. Not verified

- **Tarball contents.** They were not read and are left to the tarball audit.
- **What 19.3.1 changed.** The diff between 19.3.0 and 19.3.1, and whether it touches `react` itself.
- **Signature cryptography.** The attestation and registry signatures were decoded or trusted through deps.dev, not
  checked with `npm audit signatures`.
