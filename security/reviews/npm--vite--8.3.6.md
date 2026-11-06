---
component: "vite"
kind: npm
version: "8.3.6"
verdict: APPROVE-WITH-CONDITIONS
scope: "devDependency of apps/shipping-web: vite build in CI; vite dev/preview only on loopback (CI e2e and owner hosts); installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: ["Lockfile pins vite 8.3.6 (sha512-UI7dK29NnS54Fk+mmo7o+jI6U8Fp7hDl+X5Q1k45WH+d4dSlbLVUETGQ+hfkNgvLQGyoElPj837nZyv4MBc0VA==), rolldown 1.2.16 and lightningcss 1.33.0 with their linux-x64-gnu bindings; the bindings pass the transitive audit", "Dev and preview servers stay on loopback: no --host, server.host/preview.host other than localhost, server.cors true or allowedHosts true", "Only reviewed plugins (@vitejs/plugin-react 6.2.1); vite.config.* is CODEOWNERS-protected because Vite executes it", "No optional peers (esbuild, sass, less, stylus, terser, tsx, jiti, @vitejs/devtools) without their own review"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: vite 8.3.6

**Method.** Read on 2026-11-06: https://registry.npmjs.org/vite/8.3.6, its attestation bundle, deps.dev, osv.dev,
`CHANGELOG.md` at tag v8.3.6, https://vite.dev/config/server-options, and the rolldown and lightningcss registry
docs. No tarball was downloaded or run. OSV `POST /v1/query` was not possible (GET-only tool).

## 1. Verdict

**APPROVE-WITH-CONDITIONS.**

- **Strong provenance.** Trusted publishing with verified SLSA provenance from vitejs/vite.
- **No install scripts.**
- **No known advisory.** All five October 2026 dev-server advisories are fixed by 8.3.3, and 8.3.6 adds more server
  hardening.
- **Two concerns.**
  - It is a **one-day-old release**.
  - It pulls two native toolchains. One of them, **lightningcss, has no provenance** and is published from a personal
    account.

## 2. What it is and why it is needed

The frontend build tool and dev server. The blueprint pins Vite 8.3.6 (§4).

## 3. Provenance

- **Publisher.** `_npmUser` is "GitHub Actions" (trustedPublisher `github`). Maintainers: yyx990803, vitebot.
- **SLSA v1.** Workflow `.github/workflows/publish.yml` on `refs/heads/main`, event `push`, environment `Release`,
  commit `0fd9a29f57d49d496f39c952f44c1b5fc3e90769`. deps.dev marks it `verified: true`
  (https://registry.npmjs.org/-/npm/v1/attestations/vite@8.3.6).
- **Tarball.** `shasum` `ff2eb6b0b18e910c08a79c59a80faebecd422808`, 37 files, 2,377,714 bytes.
- **Install scripts:** none. Only build and dev scripts exist.
- **Native binaries:** none in vite itself. They come through its dependencies (§8).

## 4. Maintenance and adoption

- **Release.** 8.3.6 was published 2026-11-05T05:40:52Z and is `latest`.
- **Cadence.** About 40 8.x releases since 8.0.0 (2026-03-12), several a month
  (https://api.deps.dev/v3/systems/npm/packages/vite).
- **Scorecard 6.8** (2026-08-24): Code-Review 7 (19/27), Maintained 10, Token-Permissions 7, Pinned-Dependencies 6.
  It has 83,643 stars (https://api.deps.dev/v3/projects/github.com%2Fvitejs%2Fvite).

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.** deps.dev has `advisoryKeys: []`.

- **Fixed in 8.3.3:** GHSA-rq7h-c2jc-7f22, GHSA-vfpm-58rq-9qcg, GHSA-9jrq-w75r-8gcw, GHSA-r825-w8mm-5pxw,
  GHSA-587h-7wmj-h5pm and GHSA-698v-4r3x-3vcv. OSV puts their last 8.3 range at `8.3.0`→`8.3.3`
  (https://api.osv.dev/v1/vulns/GHSA-587h-7wmj-h5pm and the others).
- **6.x only:** GHSA-8g5g-mxqh-grgj.
- **8.3.6's own changelog** includes "reject `.wasm` outside `server.fs`", "escape HTML attributes" and "escape ANSI
  escape sequence for `server.forwardConsole`". Advisories against ≤8.3.5 may follow.
- **History.** All of these are dev-server issues.

## 6. License

MIT. lightningcss is MPL-2.0.

## 7. Execution privileges

- **Build.**
  - It loads and **executes `vite.config.*`** and every plugin in-process.
  - It loads native `.node` addons (rolldown, lightningcss) and reads and writes the project tree.
- **Dev and preview.**
  - It starts an HTTP and WebSocket server that serves files under `server.fs` rules.
  - The bundled `launch-editor` middleware can **spawn an editor process** on `/__open-in-editor`.
  - Defaults: `server.host` is `'localhost'`; `server.cors` allows only localhost, `127.0.0.1` and `::1` origins;
    `preview.host` defaults to `server.host` (https://vite.dev/config/server-options, `/preview-options`).
- **Runtime.** Only the small client and HMR runtime ships to the browser, in dev.

## 8. Direct dependencies as published

- **`rolldown`** `~1.2.16`, which resolves to 1.2.16, published 2026-10-29. The bundler.
  - Trusted publishing with SLSA provenance (rolldown/rolldown commit `de6facf2…`, verified), no install scripts.
  - Its native code comes from `@rolldown/binding-linux-x64-gnu@1.2.16`: a 21 MB `.node` addon with provenance and no
    scripts (https://registry.npmjs.org/@rolldown/binding-linux-x64-gnu/1.2.16).
  - It depends on `@oxc-project/types` =0.156.0 and `@rolldown/pluginutils`.
- **`lightningcss`** `^1.33.0`, which resolves to 1.33.0. CSS transform and minify. **Flag:** it is published by
  `devongovett`, its only maintainer, with **no attestations**, and its native `lightningcss-linux-x64-gnu@1.33.0` is
  a 10 MB `.node` with no provenance (https://registry.npmjs.org/lightningcss/1.33.0). It has `prepare` and
  `prepublishOnly` scripts but no install hooks.
- **`postcss`** `^8.5.32`: the CSS pipeline.
- **`picomatch`** `^4.0.7` and **`tinyglobby`** `^0.2.17`: globbing.
- **`fsevents`** `~2.3.3`, optional: a macOS native watcher, not installed on Linux.
- **Peers:** 12 optional peers, none to be installed.

## 9. Alternatives considered

- **Vite 7.x:** esbuild and Rollup instead of rolldown, with similar native-binary exposure.
- **Webpack or rspack:** larger trees. The blueprint chose Vite.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk.**

- **Age.** The release was one day old at review. Provenance binds it to vitejs/vite `main` and its `Release`
  environment, but not to a reviewed diff.
- **The dev server's history.** It has a steady record of `server.fs` bypasses. Keeping it on loopback is what makes
  them unreachable.
- **lightningcss.** Its native binary is the weakest-provenance code in the tree.

## 11. Not verified

- **Tarball contents.** The tarball and `.node` binaries were not read.
- **lightningcss loading.** Whether Vite 8 loads lightningcss by default, or only for `css.transformer` or
  `cssMinify`.
- **Changelog completeness.** Advisory coverage for 8.0.x before 2026-10-07, since OSV list pagination failed. deps.dev
  is the only source there.
