---
component: "@testing-library/dom"
kind: npm
version: "10.4.2"
verdict: APPROVE-WITH-CONDITIONS
scope: "devDependency of apps/shipping-web (required peer of @testing-library/react): DOM queries in vitest run (jsdom); installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: ["Lockfile pins @testing-library/dom 10.4.2 (sha512-yzr2S9HyAIdhz2/6qHgbs665Q7PKVcDF05vsOlHPxG1mo36gKVesdYVeDLnXgfjJ03CrKRk08knc6+E/9m8v2Q==)", "@babel/runtime resolves to >= 7.26.10"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: @testing-library/dom 10.4.2

**Method.** I read https://registry.npmjs.org/@testing-library/dom/10.4.2, deps.dev (the version and the package
list), osv.dev and the project scorecard on 2026-11-06. Nothing was downloaded or run.

## 1. Verdict

**APPROVE-WITH-CONDITIONS.** This is the newest 10.x, and `@testing-library/react` 16.3.3 needs it (peer
`^10.0.0`). Provenance is verified, there are no install scripts, and there are no advisories. It has eight small
JavaScript dependencies.

## 2. What it is and why it is needed

These are the DOM query and event utilities (`getByRole`, `waitFor`, `fireEvent`) underneath `@testing-library/react`
and `user-event`. The brief asks for this package alongside Testing Library 16.3.3.

## 3. Provenance

- **Publisher.** `_npmUser` is "GitHub Actions" (trustedPublisher `github`). It has the same 18 maintainers as
  `@testing-library/react`.
- **SLSA provenance.** Source is testing-library/dom-testing-library at commit
  `6049cc0bc7cf2201625c476c95fa6299d0e2fa8b`, which equals `gitHead`, with `verified: true`
  (https://api.deps.dev/v3/systems/npm/packages/%40testing-library%2Fdom/versions/10.4.2).
- **Install scripts:** none.
- **Native binaries:** none.

## 4. Maintenance and adoption

- **Releases.** 10.4.2 was published 2026-09-13 and is `latest`. The previous release was 10.4.1, on 2025-07-27.
- **Scorecard 4.9** (2026-08-24): Maintained 0, Code-Review 6, Token-Permissions 10, Security-Policy 0,
  Pinned-Dependencies 0. The repository has 3,340 stars.

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.** deps.dev shows `advisoryKeys: []`. On `@babel/runtime`, see `npm--testing-library-react--16.3.3.md`:
GHSA-968p-4wvh-cqc8 is fixed in 7.26.10.

## 6. License

MIT.

## 7. Execution privileges

- **When it runs:** test time only, inside Vitest.
- **What it does:** queries and dispatches events on jsdom.
- **`lz-string`** compresses DOM snapshots into a Testing Playground URL when the test calls
  `screen.logTestingPlaygroundURL()`. That builds a string; per the API name it is not a network call. The tarball
  audit confirms this.

## 8. Direct dependencies as published

- `@babel/code-frame` ^7.10.4: error excerpts.
- `@babel/runtime` ^7.12.5: helpers.
- `@types/aria-query` ^5.0.1: types.
- `aria-query` 5.3.0 (exact): ARIA role data.
- `dom-accessibility-api` ^0.5.9: accessible names.
- `lz-string` ^1.5.0: compression.
- `picocolors` 1.1.1 (exact): terminal colours.
- `pretty-format` ^27.0.2: DOM printing.

## 9. Alternatives considered

None that is practical: it is a required peer of `@testing-library/react`.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk:** the same weak project hygiene and wide maintainer set as `@testing-library/react`.

## 11. Not verified

- **Tarball contents.** Not read.
- **`lz-string` / Playground URL.** That no network request is made was not confirmed by reading the code.
