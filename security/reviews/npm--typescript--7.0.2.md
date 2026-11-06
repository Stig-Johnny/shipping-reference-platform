---
component: "typescript (with @typescript/typescript-linux-x64)"
kind: npm
version: "7.0.2"
verdict: APPROVE-WITH-CONDITIONS
scope: "devDependency of apps/shipping-web: the tsc CLI type-checks/builds the repository's own sources in CI on GitHub-hosted Linux x64; native binary @typescript/typescript-linux-x64@7.0.2 only"
conditions: ["Lockfile pins typescript 7.0.2 (sha512-8FYau96o3NKOhbjKi/qNvG/W5jhzxkbdm5sj9AbZ/5T5sWqn3hJgLfGx27sRKZWTvyzCP8dLRBTf5tBTSRVUNA==) and @typescript/typescript-linux-x64 7.0.2 (sha512-EYdf2cNg7rgCWJnxCdJ+F3V39O8ihb37eHAu1LK8oAFizgTQbPOK7zHHXbPt8rX24COqODXeI3sIf0fCXG7H/A==)", "Only the tsc CLI on repository sources; the typescript/unstable/* API is not used", "Tools that need the classic ts.* compiler API (openapi-typescript) must not resolve this package; they get a separately reviewed typescript 5.x", "Other platform packages (darwin-arm64 etc.) are not approved until a host-scoped review"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: typescript 7.0.2 (native compiler)

**Method.** Read on 2026-11-06: the registry docs for `typescript@7.0.2` and two platform packages, the npm
attestation endpoints (404), deps.dev, the typescript-go release page and assets, `getExePath.js` and `tsgo.js` at tag
`typescript/v7.0.2`, the typescript-go README and Scorecard. No tarball was downloaded or run.

## 1. Verdict

**APPROVE-WITH-CONDITIONS** for type-checking in CI.

- **How the binaries arrive.** The native compiler comes as a per-platform npm package. No install script runs, and
  nothing is downloaded at install or run time.
- **Weak provenance.** The packages carry **no provenance attestation**; their only provenance is the Microsoft
  publishing account and the registry signature.
- **No classic API.** 7.0.2 ships **no classic `ts.*` compiler API**. Tools built on it need TypeScript 5.x (see
  openapi-typescript).

## 2. What it is and why it is needed

The TypeScript compiler, rewritten in Go. The blueprint pins TypeScript 7.0.2 (§4) for the React app and the
generated client.

## 3. Provenance

- **The `typescript` package** (https://registry.npmjs.org/typescript/7.0.2):
  - `_npmUser` is `microsoft1es`. Maintainers: microsoft1es, typescript-bot, weswigham, andrewbranch,
    typescript-deploys, microsoft-oss-releases, jakebailey.
  - `repository` is microsoft/TypeScript and `gitHead` is `2bd066d87f5bafd315be9f40889d0a60b9e58e0b`.
  - `_resolved` points to a local `.tgz` under a Windows "MS.Ess.PackageManager.Publisher" path.
  - Registry signature keyid `SHA256:DhQ8wR5A…`.
  - **No `dist.attestations`.** `-/npm/v1/attestations/typescript@7.0.2` returns 404, and deps.dev has
    `slsaProvenances: []`.
- **How the binaries are delivered.**
  - `typescript` lists 20 `@typescript/typescript-<os>-<cpu>@7.0.2` packages as optional dependencies. npm installs
    only the one whose `os`/`cpu` match.
  - The linux-x64 package (https://registry.npmjs.org/@typescript/typescript-linux-x64/7.0.2) has `os: ["linux"]` and
    `cpu: ["x64"]`, 114 files and 27,945,990 bytes unpacked. It has no scripts, no dependencies, the same publisher
    and `gitHead`, and **no attestation** (404).
  - The launcher `lib/getExePath.js` resolves `@typescript/typescript-${process.platform}-${process.arch}` and runs
    its `lib/tsc`. `lib/tsgo.js` uses `process.execve` or `execFileSync`. Both import only `node:fs`, `node:module`,
    `node:path` and `node:url` (https://github.com/microsoft/typescript-go/tree/typescript/v7.0.2/_packages/native-preview/lib).
- **GitHub release** `typescript/v7.0.2` (https://github.com/microsoft/typescript-go/releases/tag/typescript%2Fv7.0.2):
  - Released by jakebailey on 2026-07-08, at commit `2bd066d`, which matches `gitHead`.
  - It has 21 `.tgz` assets. GitHub shows digests such as `typescript-linux-x64.tgz`
    `sha256:7ecad6f67377e831856367ab062ef394f21506a611405bf8ac0ff039348637d3` (expanded assets page).
  - Whether the npm tarball is byte-identical to that asset **was not checked**.
- **Install scripts:** none in any of the three manifests read.

## 4. Maintenance and adoption

- **Releases** (https://api.deps.dev/v3/systems/npm/packages/typescript):
  - 7.0.2 was published 2026-07-08 and is `latest`. There was no 7.0.0 or 7.0.1 stable; 7.0.1-rc came out
    2026-06-18.
  - 7.1.0-beta came out 2026-10-20, with nightlies to 2026-11-06.
  - The previous lines end at 6.0.3 (2026-04-16) and 5.9.3 (2025-09-30).
- **Repository status.** The typescript-go repo "Is Closed", archived 2026-09-01, and development continues in
  microsoft/TypeScript (https://raw.githubusercontent.com/microsoft/typescript-go/main/README.md).
- **Scorecard.** microsoft/TypeScript scores **8.2** (2026-11-06): Code-Review 10, Maintained 10, Token-Permissions
  10, Pinned-Dependencies 10, Signed-Releases 0 (https://api.securityscorecards.dev/projects/github.com/microsoft/TypeScript).
  None was found for typescript-go.

## 5. Known vulnerabilities at this version

- **None found on 2026-11-06.** deps.dev has `advisoryKeys: []`, and osv.dev listed nothing for the exact name.
- **Scorecard's Vulnerabilities check** on microsoft/TypeScript `main` lists GHSA-xcx6-jx8p-x3h5, GHSA-g3hv-75wg-m249,
  GHSA-6pq8-jhm2-2vcw, GO-2026-5932 and GO-2026-6865. Whether any of them are compiled into the 7.0.2 binary was
  **not verified**. tsc reads only our own sources.

## 6. License

Apache-2.0 (registry, deps.dev).

## 7. Execution privileges

- **What and when:** a native Go executable, started from Node at build and test-gate time only.
- **What it does:** reads `tsconfig.json`, sources and `node_modules` type declarations, and writes outputs, with the
  user's full file-system access. The launcher has no network code; the binary itself was not inspected.
- **Unused API.** `./unstable/*` exports a JSON-RPC API to the binary (vendored `vscode-jsonrpc`). It is not used.

## 8. Direct dependencies as published

- **20 optional platform packages, all 7.0.2:** aix-ppc64, darwin-arm64/x64, freebsd-arm64/x64, linux-arm, arm64,
  loong64, mips64el, ppc64, riscv64, s390x and x64, netbsd-arm64/x64, openbsd-arm64/x64, sunos-x64 and
  win32-arm64/x64.
- **Only linux-x64 is approved here.** darwin-arm64 (`sha512-gowzar9M…`, 27,498,111 bytes, no attestation) is
  recorded for a later host review.
- **No other dependencies.**

## 9. Alternatives considered

**TypeScript 5.9.3 or 6.0.3.** Both are JS compilers with the classic API: slower, but one compiler would serve
every tool. The blueprint chose 7.0.2.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk.**

- No npm build provenance, so the binary's origin rests on Microsoft's account security and the registry signature.
- The binary is a 28 MB opaque executable.
- **Recommended:** in the lock-maintenance environment, compare the SHA-256 of the npm platform tarball with the
  GitHub release asset digest above. That gives a second publisher channel.

## 11. Not verified

- **Tarball contents.** The npm tarball contents, including the published `bin/tsc`, were not compared with the
  source.
- **npm vs GitHub bytes.** Equality of the npm and GitHub release tarballs.
- **Go advisories.** Whether the Go-module advisories apply to 7.0.2.
- **The announcement post.** devblogs.microsoft.com was not fetched.
