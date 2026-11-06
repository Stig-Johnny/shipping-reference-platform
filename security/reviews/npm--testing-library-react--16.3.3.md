---
component: "@testing-library/react"
kind: npm
version: "16.3.3"
verdict: APPROVE-WITH-CONDITIONS
scope: "devDependency of apps/shipping-web: component tests under vitest run (jsdom); installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: ["Lockfile pins @testing-library/react 16.3.3 (sha512-Uo193NgQbPMz6lrrhtRQQFcMC6Re/ELLFbbuVL30WDlZxlpZf9/lMHTAVxPRLw1q1iu9OJmR1c2BLiENRstdBg==) with @testing-library/dom 10.4.2", "@babel/runtime resolves to >= 7.26.10 (CVE-2025-27789 fixed); 7.29.13 was newest on 2026-11-06"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: @testing-library/react 16.3.3

**Method.** Read on 2026-11-06: https://registry.npmjs.org/@testing-library/react/16.3.3, deps.dev, osv.dev
(including GHSA-968p-4wvh-cqc8) and the project scorecard. Nothing was downloaded or run.

## 1. Verdict

**APPROVE-WITH-CONDITIONS.** It is a small, test-only library with verified provenance, no install scripts and one
runtime dependency (`@babel/runtime`). The project's Scorecard is low (3.7). With test-only scope that is acceptable.
The one condition that matters is that the lockfile resolves a fixed `@babel/runtime`.

## 2. What it is and why it is needed

It renders React components into jsdom and queries them the way users do. The blueprint pins "Testing Library
16.3.3" (§4).

## 3. Provenance

- **Publisher.** `_npmUser` is "GitHub Actions" (trustedPublisher `github`).
- **npm maintainers (18):** testing-library-bot, kentcdodds, timdeschryver, patrickhulce, dfcook, gpx, mpeyper,
  mihar-22, pago, cmckinstry, thymikee, brrianalexis, jdecroock, mdjastrzebski, eps1lon, phryneas, snowystinger,
  matanbobi.
- **SLSA provenance.** testing-library/react-testing-library, commit `20ce75f2907ca0e5c5a8ae595c0e9a4e368c7800`,
  which equals `gitHead`, `verified: true`
  (https://api.deps.dev/v3/systems/npm/packages/%40testing-library%2Freact/versions/16.3.3).
- **Install scripts:** none. `install:csb` is a custom name, not the npm `install` hook.
- **Native binaries:** none.

## 4. Maintenance and adoption

- **Release.** Published 2026-08-27 and is `latest`.
- **Scorecard 3.7** (2026-08-24): Maintained 0 (no commits in the 90 days before the scan), Code-Review 5,
  Security-Policy 0, Token-Permissions 0, Pinned-Dependencies 0. It has 19,675 stars
  (https://api.deps.dev/v3/projects/github.com%2Ftesting-library%2Freact-testing-library).

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.** deps.dev has `advisoryKeys: []`.

Its dependency `@babel/runtime` has GHSA-968p-4wvh-cqc8 (CVE-2025-27789), fixed in 7.26.10. The range `^7.12.5`
allows a vulnerable resolution, hence the condition (https://osv.dev/vulnerability/GHSA-968p-4wvh-cqc8).

## 6. License

MIT.

## 7. Execution privileges

- **When it runs:** test time only, inside Vitest workers.
- **What it does:** it calls `react-dom` to render into jsdom.
- **Not observed:** network, process or file-system use. Contents are left to the tarball audit.

## 8. Direct dependencies as published

- **`@babel/runtime`** `^7.12.5`: Babel helper functions. The newest 7.x is 7.29.13 (2026-11-04), with verified
  provenance and no advisories (https://api.deps.dev/v3/systems/npm/packages/%40babel%2Fruntime/versions/7.29.13).
- **Peers:**
  - `react` and `react-dom` `^18.0.0 || ^19.0.0`;
  - `@testing-library/dom` `^10.0.0`, required;
  - `@types/react` and `@types/react-dom`, optional.

## 9. Alternatives considered

- **React's `act` with raw DOM queries:** no dependency, but much more test code.
- **Playwright component testing:** heavier.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk.**

- **Maintenance signals.** Scorecard shows little recent activity, no security policy and broad workflow tokens.
- **A wide publishing surface.** 18 npm maintainers. Trusted publishing does not by itself stop token publishing:
  whether "require 2FA / disallow tokens" is set on the package was not checked.

## 11. Not verified

- **Tarball contents.** Not read.
- **npm publishing-access settings.** Not checked.
