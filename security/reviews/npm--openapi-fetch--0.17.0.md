---
component: "openapi-fetch (with openapi-typescript-helpers 0.1.0)"
kind: npm
version: "0.17.0"
verdict: APPROVE
scope: "Runtime dependency of packages/shipping-client and apps/shipping-web: shipped in the browser bundle; installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: []
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: openapi-fetch 0.17.0

**Method.** On 2026-11-06 I read https://registry.npmjs.org/openapi-fetch/0.17.0,
https://registry.npmjs.org/openapi-typescript-helpers/0.1.0, their deps.dev records and osv.dev. Nothing was
downloaded or run.

## 1. Verdict

**APPROVE.** It is a small, type-safe wrapper around the platform `fetch`. Its only dependency is a helpers package
from the same monorepo. Both have verified provenance from the same commit, no install scripts and no advisories.
The exact pins in the lockfile are the only requirement.

## 2. What it is and why it is needed

The runtime HTTP client the React app uses with the generated types: "consumed through openapi-fetch 0.17.0"
(blueprint §4).

## 3. Provenance

**openapi-fetch 0.17.0:**

- `_npmUser` is "GitHub Actions" (trustedPublisher `github`). Maintainers: drewpowers, mpaucot, gzm0.
- SLSA provenance: openapi-ts/openapi-typescript commit `5709d33a5977c4908b9e331f01cd0f9e181b1c37`,
  `verified: true`.
- `dist.integrity` is
  `sha512-PsbZR1wAPcG91eEthKhN+Zn92FMHxv+/faECIwjXdxfTODGSGegYv0sc1Olz+HYPvKOuoXfp+0pA2XVt2cI0Ig==`; 228,795 bytes
  unpacked.

**openapi-typescript-helpers 0.1.0:**

- Same publisher and commit, `verified: true`.
- `dist.integrity` is
  `sha512-OKTGPthhivLw/fHz6c3OPtg72vi86qaMlqbJuVJ23qOvQ+53uw1n7HdmkJFibloF7QEjDrDkzJiOJuockM/ljw==`; 28,278 bytes.
- Its `repository.directory` says `packages/openapi-fetch`, a metadata quirk.

**Both packages:**

- **Install scripts:** none. openapi-fetch has `pretest` and `version`, which are development hooks.
- **Native binaries:** none.

## 4. Maintenance and adoption

- **Release.** Published 2026-02-11T16:02:21Z. It is still `latest`, about 9 months later. Earlier releases: 0.16.0
  (2026-02-08) and 0.15.0 (2025-10-14).
- **Helpers.** 0.1.0 is the only 0.1.x release.
- **Scorecard.** None found for openapi-ts/openapi-typescript.

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.** deps.dev shows `advisoryKeys: []` for both. osv.dev has no results for `openapi-fetch`.

## 6. License

MIT (both).

## 7. Execution privileges

**Runtime, in end users' browsers:**

- It calls the global `fetch` with the configured `baseUrl`.
- It serialises request bodies and parses responses.
- It runs any middleware we register.
- Beyond `fetch` it should need nothing else; the tarball audit confirms this.

**Test time:** under Vitest and Playwright.

## 8. Direct dependencies as published

- `openapi-typescript-helpers` `^0.1.0`, which resolves to 0.1.0. Its description is "TypeScript helpers for
  consuming openapi-typescript types", and it has no dependencies.
- There are no peers.
- There are no engines constraints.

## 9. Alternatives considered

- **Plain `fetch` with generated types:** no dependency, but every call site needs hand-written typing.
- **`openapi-typescript-codegen` / axios clients:** these are larger.

## 10. Conditions and residual risk

**Conditions:** none beyond the exact lockfile pins and the integrity values above.

**Residual risk:**

- The package has not been released in 9 months.
- It has only 3 npm maintainers.

## 11. Not verified

- The tarball contents.
- Whether the `.d.ts` files type-check under TypeScript 7.0.2. That is a compatibility question, not a security one.
