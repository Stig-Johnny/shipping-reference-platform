---
component: "openapi-typescript"
kind: npm
version: "7.13.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "devDependency of packages/shipping-client: build-time generation of TypeScript types from contracts/shipping.openapi.yaml, in CI (regeneration/no-diff check) and the lock-maintenance environment; installed by npm ci --ignore-scripts"
conditions: ["Its required peer is a separately reviewed typescript 5.x (5.9.3 was newest on 2026-11-06), declared in packages/shipping-client so npm nests it there; it never resolves typescript 7.0.2. Until that review exists this approval cannot be used", "Input is only the local file contracts/shipping.openapi.yaml, never a URL", "Run with an explicit --redocly <reviewed config> that has no plugins, no URL extends and no resolve.http.headers, with no @theme/plugin.* beside it; that config is CODEOWNERS-protected", "The schema contains no absolute-URL $ref or externalValue (gate: fail on '://' in those values), so generation makes no network request", "No secrets in the generating job's environment", "@redocly/openapi-core resolves to a lockfile-pinned 1.34.x that passes the transitive audit"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: openapi-typescript 7.13.0

**Method.** Read on 2026-11-06:

- the registry docs for `openapi-typescript@7.13.0` and `@redocly/openapi-core@1.34.24`, plus deps.dev and osv.dev;
- `src/lib/ts.ts`, `src/lib/redoc.ts` and `bin/cli.js` at tag `openapi-typescript@7.13.0`;
- Redocly's `config/config-resolvers.ts`, `config/load.ts`, `resolve.ts` and `utils.ts` at tag
  `@redocly/openapi-core@1.34.24`;
- https://openapi-ts.dev/cli.

Nothing was downloaded or run.

## 1. Verdict

**APPROVE-WITH-CONDITIONS.** Provenance is verified and there are no install scripts. Two findings shape the
conditions.

1. **It cannot run on the pinned TypeScript 7.0.2.**
   - It declares `peerDependencies: {"typescript": "^5.x"}`.
   - It imports TypeScript's compiler API at runtime: `import ts … from "typescript"`, then `ts.factory.*`,
     `ts.createSourceFile` and `ts.createPrinter` (`src/lib/ts.ts`).
   - typescript 7.0.2 exports no such API (`npm--typescript--7.0.2.md`).
   - So it needs its own TypeScript 5.x. That is **a component outside this batch that still needs a review.**
2. **Its schema loader can fetch and execute.**
   - The CLI accepts URLs and auto-loads `redocly.yaml` from the working directory.
   - Redocly config `plugins:` are JavaScript that gets imported and run.
   - `$ref`s to http(s) URLs are fetched by default, with no allowlist.

## 2. What it is and why it is needed

It generates TypeScript types from the OpenAPI contract. The blueprint pins "openapi-typescript 7.13.0, consumed
through openapi-fetch 0.17.0; regeneration … block[s] drift" (§4).

## 3. Provenance

- **Publisher.** `_npmUser` is "GitHub Actions" (trustedPublisher `github`). npm maintainers: `drewpowers`, `gzm0`.
- **SLSA provenance.** Source repo openapi-ts/openapi-typescript at commit
  `5709d33a5977c4908b9e331f01cd0f9e181b1c37`, `verified: true`
  (https://api.deps.dev/v3/systems/npm/packages/openapi-typescript/versions/7.13.0). openapi-fetch 0.17.0 was built
  from the same commit.
- **Integrity.** `sha512-EFP392gcqXS7ntPvbhBzbF8TyBA+baIYEm791Hy5YkjDYKTnk/Tn5OQeKm5BIZvJihpp8Zzr4hzx0Irde1LNGQ==`,
  878,193 bytes unpacked.
- **Install scripts:** none. Only `prepublish` and `version` exist.
- **Native binaries:** none.

## 4. Maintenance and adoption

- **Releases.** 7.13.0 was published 2026-02-11 and is still `latest`. Nothing has been released in about 9 months.
  Before it: 7.12.0 (2026-02-08) and 7.10.1 (2025-10-15).
- **TypeScript support.** `main` still declares `typescript ^5.x`, and the docs say nothing about TypeScript 7.
- **Scorecard:** none found (deps.dev has no scorecard; securityscorecards.dev returns 404). The repo has 8,424 stars
  and 287 open issues.

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.** deps.dev shows `advisoryKeys: []`. osv.dev has nothing for `openapi-typescript` or
`@redocly/openapi-core`. The one similar-named entry is a look-alike: MAL-2025-49307, `sechub-openapi-typescript`.

## 6. License

MIT. @redocly/openapi-core is MIT too.

## 7. Execution privileges

All of this happens at build time, in Node.

- **Remote inputs.** `redoc.ts` treats `http://`, `https://` and `file://` inputs as URLs, resolved by Redocly's
  `BaseResolver`.
- **Config loading.**
  - `cli.js` uses `--redocly <path>` when given; otherwise `findConfig()`. That looks for `redocly.yaml`,
    `redocly.yml`, `.redocly.yaml` or `.redocly.yml` in the working directory (`load.ts`).
  - `resolveConfig` then **imports and calls `plugins:`**: `new Function('modulePath','return import(modulePath)')`.
  - It also auto-loads `@theme/plugin.{js,cjs,mjs}` from the config directory.
  - `extends:` may be a URL; remote `plugins` are refused (`config-resolvers.ts`).
- **Remote `$ref`s.** An absolute-URL `$ref`, or `externalValue`, is fetched with `fetch`, adding headers taken from
  environment variables when the config names them (`resolve.ts`, `utils.ts`).
- **Stored tokens.** `loadConfig` adds stored Redocly tokens as `Authorization` headers for
  `https://api.<domain>/registry/**`.
- **Output.** It writes the generated `.d.ts`.

## 8. Direct dependencies as published

- `@redocly/openapi-core` `^1.34.6`. It resolves to **1.34.24, published 2026-11-05**, one day before this review.
  - It has trusted publishing and verified provenance (Redocly/redocly-cli).
  - Its 9 dependencies are exact-pinned: `js-yaml` 4.3.2, `@redocly/ajv` 8.11.2, `minimatch` 5.1.9,
    `https-proxy-agent` 7.0.6, `yaml-ast-parser` 0.0.43, `@redocly/config` 0.22.0 and three small utilities.
  - **Flag it for the transitive audit.**
- `parse-json` ^8.3.0, `yargs-parser` ^21.1.1, `change-case` ^5.4.4, `ansi-colors` ^4.1.3 and `supports-color`
  ^10.2.2: CLI and parsing helpers.
- **Peer:** `typescript` `^5.x`, required.

## 9. Alternatives considered

- **Hand-written types with contract tests:** no generator, but the drift check is lost.
- **Other generators** (for example `@hey-api/openapi-ts`): not reviewed, and larger.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk.**

- A second TypeScript in the tree: version skew, and one more compiler to review.
- 9 months without a release.
- Only 2 npm maintainers.
- Redocly core is about 3 MB and 687 files, freshly published.

## 11. Not verified

- **Tarball contents.**
- **The `isAbsoluteUrl` definition**, and whether a relative `$ref` inside a local file can reach the network.
- **Where the stored Redocly tokens are read from.**
- **Whether npm workspaces nest TypeScript 5.x** as described. This is checked when the lockfile is regenerated.
