---
component: "@vitejs/plugin-react"
kind: npm
version: "6.2.1"
verdict: APPROVE-WITH-CONDITIONS
scope: "devDependency of apps/shipping-web: Vite plugin at build/dev/test time; installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: ["Lockfile pins @vitejs/plugin-react 6.2.1 (sha512-9KyWCij3r0+qLG3Q62vBmv0VyevmYibAtxaCIVK9o0k6CXgaajZRIe8tpfchQcdZArJ5b/7OrIz4W3b2uxVRDA==) with vite 8.3.6", "None of its optional peers (@rolldown/plugin-babel, babel-plugin-react-compiler, oxc-transform-react) without their own review"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: @vitejs/plugin-react 6.2.1

**Method.**

- **Read on 2026-11-06:**
  - https://registry.npmjs.org/@vitejs/plugin-react/6.2.1
  - https://api.deps.dev/v3/systems/npm/packages/%40vitejs%2Fplugin-react and its versions endpoint
  - the vitejs/vite-plugin-react project on deps.dev
- **Nothing was downloaded or run.**

## 1. Verdict

**APPROVE-WITH-CONDITIONS.** 6.2.1 is the newest release, and its `vite` peer `^8.0.0` accepts 8.3.6. It has one
dependency, no install scripts, and verified provenance. Its repository's Scorecard Code-Review result is weak.

## 2. What it is and why it is needed

The official Vite plugin for React: JSX transform and Fast Refresh in dev. It is needed with Vite 8.3.6 (blueprint
§4; the brief asks for "the version compatible with Vite 8.3.6").

## 3. Provenance

- **Publisher.** `_npmUser` is "GitHub Actions" (trustedPublisher `github`). Maintainers: yyx990803, vitebot.
- **SLSA provenance.** The source is `https://github.com/vitejs/vite-plugin-react`, commit
  `29efd1cc6d875185f91a3325beb981bb45af876f`, which equals `gitHead`. deps.dev marks it `verified: true`.
- **Install scripts:** none. Scripts are `dev`, `build`, `test-unit` and `prepublishOnly`.
- **Native binaries:** none.

## 4. Maintenance and adoption

- **Releases.** 6.2.1 came out 2026-11-02T09:18:16Z, about 90 minutes after 6.2.0. Earlier: 6.1.2 (10-05) and 6.1.1
  (08-28); 6.0.0 arrived with Vite 8 on 2026-03-12.
- **Scorecard 7** (2026-08-24): **Code-Review 1** ("Found 3/21 approved changesets"), Maintained 10,
  Token-Permissions 8, Pinned-Dependencies 9, Branch-Protection 3. It has 1,178 stars
  (https://api.deps.dev/v3/projects/github.com%2Fvitejs%2Fvite-plugin-react).

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.** deps.dev has `advisoryKeys: []`.

## 6. License

MIT.

## 7. Execution privileges

- **When it runs:** build, dev and test time, inside Vite's Node process.
- **What it does:** it transforms our JSX/TSX sources and injects the React Refresh runtime in dev.
- **What it does not do:** no network or process use was indicated by its metadata. Its contents are left to the
  tarball audit.

## 8. Direct dependencies as published

- **`@rolldown/pluginutils`** `^1.0.1`: filter helpers from the rolldown project.
- **Peers.**
  - `vite` `^8.0.0`, required.
  - Optional: `oxc-transform-react` (`>=0.152.0 <0.157.0`), `@rolldown/plugin-babel` and
    `babel-plugin-react-compiler`. None is to be installed.
- **Engines:** `^20.19.0 || >=22.12.0`.

## 9. Alternatives considered

- **`@vitejs/plugin-react-swc`:** brings the native SWC binaries.
- **No plugin:** Vite's built-in JSX transform, which has no Fast Refresh.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk.**

- **Code review upstream.** Most upstream changes merge without recorded approval, per Scorecard Code-Review 1.
- **Age.** The release was 4 days old at review.

## 11. Not verified

- **Tarball contents.** Not read.
- **Peer-range behaviour.** That npm installs none of the optional peers. The lockfile shows it.
