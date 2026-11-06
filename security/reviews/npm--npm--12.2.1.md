---
component: "npm"
kind: npm
version: "12.2.1"
verdict: REJECT
scope: "Not approved anywhere. CI uses the npm 11.20.0 bundled in Node.js 26.13.0 (runtime--node--26.13.0)."
conditions: ["If an npm 12 feature turns out to be required, review the then-current npm 12 release (12.2.2 was latest on 2026-11-06), not 12.2.1"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: npm CLI 12.2.1

**Method.** Registry metadata, dist-tags, deps.dev, the npm/cli release pages and the Node.js release index were read
on 2026-11-06. Nothing was downloaded or run. OSV `POST /v1/query` could not be sent with the GET-only fetch tool, so
advisory status comes from deps.dev and osv.dev pages.

## 1. Verdict

**REJECT.**

- **Bundled or not: not bundled.** Node.js 26.13.0 bundles npm **11.20.0** (https://nodejs.org/dist/index.json).
- **Using 12.2.1 would mean a second package manager** fetched from the registry and installed globally in CI.
- **It has no build provenance.**
- **It is already superseded on `latest`** by 12.2.2. That release ships a shell-escaping fix in `npm exec`.
- **The npm inside the signed Node tarball is enough.** Nothing in the blueprint was found to need npm 12: it uses
  `npm ci --ignore-scripts`.

## 2. What it is and why it is needed

npm, the package manager that installs the root workspace lockfile (blueprint §2, §5: "use `npm ci
--ignore-scripts`"). The blueprint pins npm 12.2.1 and links the v12 `npm ci` docs. It names no v12-only feature.

## 3. Provenance

- **Version doc** (https://registry.npmjs.org/npm/12.2.1):
  - `_npmUser` is `reggi` (a personal account).
  - Maintainers: saquibkhan, npm-cli-ops, reggi, martinrrm.
  - `gitHead` is `136fc5dacc1a2f3d9a051dbf03a4951228144658`.
  - `dist.integrity` is `sha512-bKCmFRVT/BB+YPEYbZwQ1XUI3N5t4xKSpU1MxOjo7z3iWzKBJ0ZXRC1xFdEN+TC7N5TdywaBzf2dMXGJq/e2PQ==`.
  - Registry signatures are present (keyid `SHA256:DhQ8wR5APBvFHLF/+Tc+AYvPOdTpcIDqOhxsBHRwC7U`).
  - **`dist.attestations` is absent.**
- **deps.dev agrees:** `slsaProvenances: []` and `attestations: []`
  (https://api.deps.dev/v3/systems/npm/packages/npm/versions/12.2.1).
- **Release.** GitHub release v12.2.1, commit `ae98c62586b6fea44114f72e249e246a2981de70`, notes dated 2026-10-14
  (https://github.com/npm/cli/releases/tag/v12.2.1).
- **Sibling releases are the same.** 12.2.0 and 11.20.0 were also published by `reggi` without attestations
  (https://registry.npmjs.org/npm/12.2.0, …/11.20.0).
- **Install scripts:** none; `prepack` is development-only.
- **Native binaries:** none shipped. The bundled `node-gyp` builds native addons only when a package asks for it.

## 4. Maintenance and adoption

- **Dist-tags** (https://registry.npmjs.org/-/package/npm/dist-tags): `latest` is **12.2.2**, `next-12` 12.2.2,
  `next-11` 11.21.2.
- **Releases** (https://github.com/npm/cli/releases):
  - v12.2.2: notes dated 2026-11-03, marked Latest.
  - v12.2.1: 2026-10-14, a regular release with no pre-release badge.
- **Scorecard 6.8** (2026-08-24) for npm/cli (https://api.deps.dev/v3/projects/github.com%2Fnpm%2Fcli). Code-Review
  10, Maintained 10, Token-Permissions 0, Pinned-Dependencies 0.

## 5. Known vulnerabilities at this version

- **None found on 2026-11-06.** deps.dev shows `advisoryKeys: []`, and the osv.dev search had no entry for the exact
  name `npm` on its first page.
- **A fix not in 12.2.1.** 12.2.2 bundles libnpmexec 11.0.5, "escape inferred executable names for selected shell"
  (#10044). That fix is **not** in 12.2.1. It is not labelled as a security fix.

## 6. License

Artistic-2.0 (registry `license`, deps.dev).

## 7. Execution privileges

- **When it runs:** build and test setup.
- **What it does:**
  - resolves and downloads tarballs from the registry;
  - writes `node_modules`;
  - runs lifecycle scripts unless `--ignore-scripts` is given;
  - `npm exec`/`npx` download and run packages;
  - reads `.npmrc` credentials.
- **Global install.** Installing it with `npm install -g` would replace the toolchain's own package manager in the
  runner's Node prefix.

## 8. Direct dependencies as published

All 68 `dependencies` are also listed in `bundleDependencies`, so they ship inside the tarball (for example
`@npmcli/arborist`, `pacote`, `tar`, `node-gyp`, `@sigstore/tuf`, `semver`). The lockfile does not lock them
separately. `engines.node` is `^22.22.2 || ^24.15.0 || >=26.0.0`.

## 9. Alternatives considered

- **npm 11.20.0 bundled with Node 26.13.0 (chosen).** It is covered by the Node release signature and needs no extra
  download.
- **npm 12.2.2,** if a 12.x feature is needed. That needs its own review.

## 10. Conditions and residual risk

- **Rejected.** The single frontmatter condition says what a re-review must target.
- **What the alternative leaves.** The bundled npm 11.20.0 has the same publisher profile: a personal account and no
  provenance. Its integrity rests on the Node tarball hash and signature.

## 11. Not verified

- **The tarball.** Its contents and its 68 bundled dependencies were not inspected.
- **The 12.2.2 fix.** Whether it addresses an exploitable issue.
- **npm 11.20.0 for the blueprint's commands.** That it covers every command the blueprint uses was not exercised; it
  was read from documentation only.
