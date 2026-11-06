---
component: "react-dom"
kind: npm
version: "19.3.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "Runtime dependency of apps/shipping-web (react-dom/client in the browser bundle; tests); installed by npm ci --ignore-scripts from the reviewed lockfile (CI and the lock-maintenance environment)"
conditions: ["Lockfile pins react-dom 19.3.0 with integrity sha512-JDk8dgif51OjFoDE70+OT9ICyYr+69HlmihNwp1+Nsfbna3t5sIiCa9ZJktDmQ4/1b/rn26hIAR2uYXDMr5r0Q== and react 19.3.0 (peer ^19.3.0)", "Use react-dom/client only; no react-dom/server, Server Components or react-server-dom-* packages", "Plan the move to 19.3.1 as a new review"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: react-dom 19.3.0

**Method.** The same as `npm--react--19.3.0.md`. Sources: https://registry.npmjs.org/react-dom/19.3.0, the
attestation bundle, https://api.deps.dev/v3/systems/npm/packages/react-dom/versions/19.3.0 and osv.dev, read on
2026-11-06. Nothing was downloaded or run.

## 1. Verdict

**APPROVE-WITH-CONDITIONS**, for the same reasons as react 19.3.0:

- trusted publishing with verified SLSA provenance, from the same release commit;
- no install scripts;
- one dependency, `scheduler`;
- no known advisory.

The 19.3.1 "transport protocol" fix concerns React Server Components (§5), which the app does not use.

## 2. What it is and why it is needed

React's DOM renderer, `createRoot` from `react-dom/client`, for `apps/shipping-web`. The blueprint pins React 19.3.0
(§4).

## 3. Provenance

- **Publisher.** `_npmUser` is "GitHub Actions" (trustedPublisher `github`). Maintainers: `fb`, `react-bot`.
- **SLSA provenance v1.** The same workflow (`runtime_release_from_ci.yml`, `main`, `workflow_dispatch`) and commit
  `1d34f91dfde6bba84d08b683aaba164c7194dacb` as react. deps.dev marks it `verified: true`.
- **Tarball.** `shasum` `a0ff19161d7f4e9be5469a07278b0c06e00f9001`, 43 files, 8,063,782 bytes unpacked.
- **Install scripts:** none. The only script is `"start": "node server.js"`, which is not a lifecycle hook.
- **Native binaries:** none.

## 4. Maintenance and adoption

- **Release.** Published 2026-09-09T17:17:39Z. The newer 19.3.1 came out on 2026-10-14
  (https://api.deps.dev/v3/systems/npm/packages/react-dom/versions/19.3.1).
- **Cadence and scorecard:** as for react (facebook/react Scorecard 7.0, 2026-08-24).

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.**

- **deps.dev** has `advisoryKeys: []`.
- **The osv.dev searches** for `react-dom` and `react-dom-server` showed only look-alike malware entries (`MAL-*`),
  not the package itself.
- **The 2025–2026 React Server Components advisories** (for example GHSA-fv66-9v8q-g76r, critical, 2025-12-03, and
  GHSA-wx67-qw84-cm4g, 2026-07-24) list only the `react-server-dom-*` packages (https://osv.dev/list?ecosystem=npm&q=react-server-dom-webpack).

## 6. License

MIT.

## 7. Execution privileges

- **Where it runs:** in the browser, where it manipulates the DOM. It also runs in Node during tests.
- **At build time:** nothing.
- **Not used:** the package also contains server-rendering entry points (`react-dom/server`). The conditions keep them
  out of use.
- **Hardening.** The 19.3 release notes add Trusted Types support (react.dev blog 2026/09/09).

## 8. Direct dependencies as published

- **`scheduler` ^0.28.0.** It resolves to 0.28.0: no dependencies, no scripts, trusted publishing with SLSA
  provenance, `integrity` `sha512-juorfCmIkIw8tT+p5BXSm6PJjQF/ycEYmKyzURCIt/RaZIhL+PulbQ9Yu2z1HdOJDdqDTlxA1+xKBmHXJsczAw==`
  (https://registry.npmjs.org/scheduler/0.28.0). Whether a newer 0.28.x exists was not checked; the lockfile decides.
- **Peer dependency:** `react` ^19.3.0.

## 9. Alternatives considered

**react-dom 19.3.1.** It requires react ^19.3.1 and needs its own review.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk:** as for react. Rendering untrusted HTML through `dangerouslySetInnerHTML` is an application-code
concern, outside this review.

## 11. Not verified

- **Tarball contents.** Not read.
- **The 19.3.0 → 19.3.1 diff.** Not read.
- **Newer scheduler releases.** Whether a newer 0.28.x is published.
- **Signatures.** Not verified cryptographically.
