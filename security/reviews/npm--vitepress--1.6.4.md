---
component: "vitepress"
kind: npm
version: "1.6.4"
verdict: APPROVE-WITH-CONDITIONS
scope: "devDependency of the docs workspace: `vitepress build docs` in the docs-brain CI job only; `vitepress dev` only on the owner's host bound to localhost; installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: ["Lockfile pins vitepress 1.6.4 (sha512-+2ym1/+0VVrbhNyRoFFesVvBvHAVMZMK0rw60E3X/5349M1GuVdKeazuksqopEdvkKwKGs21Q729jX81/bkBJg==), vue 3.5.45, and its nested vite 5.4.21 / esbuild 0.21.5 / rollup 4.x, all passing the transitive audit", "CI runs only `vitepress build`; `vitepress preview`/`serve` is never run (it listens on all interfaces); `vitepress dev` never with --host and never in CI (vite 5.4.21 and esbuild 0.21.5 carry unfixed dev-server advisories)", "Search is off or provider 'local'; no Algolia/DocSearch configuration", "The built site loads nothing from third-party origins (CI check on the dist output)", "Docs Markdown, .vitepress config and theme come only from this repository and are CODEOWNERS-protected (VitePress executes them at build)", "No markdown-it-mathjax3 or postcss peers without their own review"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: vitepress 1.6.4

**Method.**

- Read on 2026-11-06: https://registry.npmjs.org/vitepress/1.6.4; deps.dev for vitepress, vite 5.4.21, esbuild
  0.21.5 and @docsearch/js 3.8.2; osv.dev (GHSA-67mh-4wv8-2f99 and the vite advisories); `src/node/serve/serve.ts`,
  `cli.ts` and `server.ts` at v1.6.4; https://vitepress.dev/reference/default-theme-search.
- Nothing was downloaded or run.

## 1. Verdict

**APPROVE-WITH-CONDITIONS, for static builds only.** VitePress 1.6.4 is the newest stable release; 2.0 has only
alphas. It has no install scripts. The concerns:

- **No provenance.** It was published 15 months ago **from a maintainer's machine**, with no provenance.
- **It drags in an unsupported Vite 5.4.21.** That version has **eight dev-server advisories with no 5.x fix**, plus
  esbuild 0.21.5, which has a `postinstall` script and the GHSA-67mh dev-server advisory.
- **Why that is acceptable here.** None of these is reachable from `vitepress build`, so the conditions confine use to
  builds.

## 2. What it is and why it is needed

A static-site generator for the docs and evidence console. The blueprint says: "Build docs with stable VitePress
1.6.4" (§8).

## 3. Provenance

- **Publisher.** `_npmUser` is `brc-dd` (a personal account). `_resolved` is a local `.tgz`, `_npmVersion` 11.4.2.
  Maintainers: yyx990803, posva, kiaking, antfu, brc-dd.
- **No provenance.** There are **no `dist.attestations`**; deps.dev shows `slsaProvenances: []`.
- **Install scripts:** none (36 development scripts, plus a `simple-git-hooks` config).
- **Native binaries:** none in vitepress itself. Transitively there are esbuild (`@esbuild/<platform>`) and Rollup
  (`@rollup/rollup-<platform>`).

## 4. Maintenance and adoption

- **Releases.** 1.6.0–1.6.3 came out in January 2025 and 1.6.4 on 2025-08-05. 2.0.0-alpha.1 through alpha.21 run to
  2026-10-25; there is no beta, rc or stable 2.x (https://api.deps.dev/v3/systems/npm/packages/vitepress).
- **Scorecard 4.3** (2026-08-24): Code-Review 5, Maintained 10, Security-Policy 0, Token-Permissions 0,
  Pinned-Dependencies 0, Branch-Protection 0. 18,453 stars.

## 5. Known vulnerabilities at this version

- **vitepress 1.6.4 itself:** none found. deps.dev shows `advisoryKeys: []` and osv.dev has "No results".
- **vite 5.4.21** (from `vite ^5.4.14`, the last 5.x, 2025-10-20). deps.dev lists GHSA-4w7w-66w2-5vf9,
  GHSA-587h-7wmj-h5pm, GHSA-698v-4r3x-3vcv, GHSA-9jrq-w75r-8gcw, GHSA-fx2h-pf6j-xcff, GHSA-r825-w8mm-5pxw,
  GHSA-rq7h-c2jc-7f22 and GHSA-v6wh-96g9-6wx3. They are fixed only in 6.4.x/7.3.x/8.x, and every one read describes
  the dev server.
- **esbuild 0.21.5:** GHSA-67mh-4wv8-2f99 ("any website [can] send any requests to the development server"), fixed in
  0.25.0. It concerns esbuild's serve feature.

## 6. License

MIT.

## 7. Execution privileges

- **Build.**
  - It executes `.vitepress/config.*` and theme code in Node.
  - It compiles Markdown into Vue templates, so docs content is code.
  - It runs Vite 5 with Rollup and esbuild native binaries.
  - It server-renders every page with `@vue/server-renderer`.
- **`preview`/`serve`.** `ServeOptions` has only `base`, `root` and `port`, and it calls `polka(...).listen(port)`
  with no host. Node then listens on `::`/`0.0.0.0` (https://nodejs.org/api/net.html).
- **`dev`.** It passes `serverOptions` through to Vite 5's `createServer` (`server.ts`).
- **Runtime.** The Vue app hydrates in readers' browsers. Local search runs in the browser (minisearch). Algolia is
  used only with `provider: 'algolia'` and keys.

## 8. Direct dependencies as published

18 dependencies:

- **Vue:** `vue` ^3.5.13 (resolves 3.5.45, reviewed separately), `@vue/shared` ^3.5.13, `@vue/devtools-api` ^7.7.0.
- **Vite:** `vite` ^5.4.14 (resolves 5.4.21, see §5; its dependencies are `rollup` ^4.20.0, `esbuild` ^0.21.3 and
  `postcss`), `@vitejs/plugin-vue` ^5.2.1.
- **Highlighting:** `shiki`, `@shikijs/core`, `@shikijs/types` and `@shikijs/transformers`, all ^2.1.0.
- **Search:** `minisearch` ^7.1.1 (local search); `@docsearch/js` and `@docsearch/css` 3.8.2 (Algolia UI, published
  by the personal account `shortcuts`, no provenance).
- **Theme utilities:** `@vueuse/core` and `@vueuse/integrations` ^12.4.0, `focus-trap` ^7.6.4, `mark.js` 8.11.1.
- **Types and icons:** `@types/markdown-it` ^14.1.2, `@iconify-json/simple-icons` ^1.2.21.
- **Peers (optional):** `markdown-it-mathjax3` ^4, `postcss` ^8.
- **Flag for the transitive audit:** esbuild 0.21.5. It has `"postinstall": "node install.js"`, published by
  `evanw` with no provenance, and 23 platform packages.

## 9. Alternatives considered

- **VitePress 2.0.0-alpha.21:** current Vite, but alpha.
- **Astro Starlight:** a larger tree.
- **Plain Vite 8 with a Markdown plugin:** more work.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk.**

- An unprovenanced, maintenance-mode release.
- Two Vite majors in one lockfile.
- An end-of-life Vite 5 whose dev-server bugs stay open; they are avoided, not fixed.

## 11. Not verified

- **Tarball contents.**
- **Third-party origins.** Whether the default theme loads any (the CI check settles it).
- **esbuild with `--ignore-scripts`.** That esbuild works from its optional platform package without running
  `postinstall`.
- **Rollup's native packages.**
- **The v1 docs site** (vuejs.github.io/vitepress/v1/) was not read; the search docs read were the 2.0-alpha pages.
