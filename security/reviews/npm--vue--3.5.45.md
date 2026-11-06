---
component: "vue"
kind: npm
version: "3.5.45"
verdict: APPROVE-WITH-CONDITIONS
scope: "Dependency of vitepress 1.6.4 in the docs workspace: Vue compiler and server renderer during `vitepress build`, Vue runtime in the published docs site; installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: ["Lockfile pins vue 3.5.45 (sha512-3cvsib8m9c0vB24+MDKsgcW4+Da4xaBl9Z/5Tbm2pmSIsRPxcil4cdXjXlVsmJ2g64XJo5h+IRd7rsdGT9pZaw==) and every @vue/* package at exactly 3.5.45", "@vue/server-renderer is used only for static generation at build time, never as a request-time server", "Template and Markdown content come only from this repository"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: vue 3.5.45 (VitePress's Vue)

**Method.** Read on 2026-11-06: https://registry.npmjs.org/vue/3.5.45, deps.dev (the version and the package list),
osv.dev (including GHSA-g2v6-rqmx-r4w6) and the vuejs/core scorecard. Nothing was downloaded or run.

## 1. Verdict

**APPROVE-WITH-CONDITIONS.** The brief calls Vue VitePress's "peer". In 1.6.4 it is in fact a **direct dependency**
(`vue ^3.5.13`), and that range resolves to **3.5.45**, published one day before this review.

- **Provenance:** verified, with the five `@vue/*` subpackages pinned exactly.
- **Install scripts:** none.
- **Advisories:** the only recent one (GHSA-g2v6) is fixed in 3.5.42.

## 2. What it is and why it is needed

The UI framework that VitePress renders the docs with (blueprint §8: VitePress 1.6.4). The app itself uses React, not
Vue.

## 3. Provenance

- **Publisher.** `_npmUser` is "GitHub Actions" (trustedPublisher `github`). npm maintainers: `yyx990803`, `posva`.
- **SLSA provenance.** Source vuejs/core, commit `8b479a89445868d474f17e2d837765e7c9ece5e4`, `verified: true`
  (https://api.deps.dev/v3/systems/npm/packages/vue/versions/3.5.45).
- **Size.** 2,621,006 bytes unpacked.
- **Install scripts:** none (no `scripts` key).
- **Native binaries:** none.

## 4. Maintenance and adoption

- **Releases.** 3.5.45 was published 2026-11-05T09:16:21Z and is `latest`. 3.6.0 is at rc.14 (2026-10-30).
- **Scorecard 6.3** (2026-08-24): Code-Review 6, Maintained 10, Security-Policy 10, Token-Permissions 0,
  Pinned-Dependencies 9. 54,831 stars (https://api.deps.dev/v3/projects/github.com%2Fvuejs%2Fcore).

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.** deps.dev shows `advisoryKeys: []`.

GHSA-g2v6-rqmx-r4w6 ("@vue/server-renderer: XSS via missing CR in attribute-name blacklist", 7.2) affects versions
below 3.5.42 and 3.6.0-rc.0 to rc.6. 3.5.45 is outside that range.

## 6. License

MIT.

## 7. Execution privileges

- **Build time, in Node:**
  - `@vue/compiler-sfc` and `@vue/compiler-dom` compile templates and Markdown into JavaScript;
  - `@vue/server-renderer` pre-renders the pages.
- **Runtime:** `@vue/runtime-dom` runs in docs readers' browsers.
- **Optional TypeScript peer.** `typescript` `*` is optional. compiler-sfc can use it to resolve imported prop types.
  With typescript 7.0.2 hoisted, that path is untested (§11).

## 8. Direct dependencies as published

All are pinned at exactly 3.5.45:

- `@vue/shared`: utilities;
- `@vue/compiler-dom` and `@vue/compiler-sfc`: compilers;
- `@vue/runtime-dom`: the browser runtime;
- `@vue/server-renderer`: SSR/SSG.

Peer: `typescript` `*`, optional.

## 9. Alternatives considered

- **Pin an older 3.5.x:** nothing to gain, and it loses the GHSA-g2v6 fix if older than 3.5.42.
- **Wait for 3.6:** it is still a release candidate.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk.**

- The release was one day old at review.
- Docs content is compiled as code, so anyone who can edit docs can ship script to readers. That is the same trust as
  editing the site.

## 11. Not verified

- The tarball contents.
- How `compiler-sfc` behaves if the hoisted typescript 7.0.2 is picked up for type resolution.
