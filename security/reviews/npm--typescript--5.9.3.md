---
component: "typescript"
kind: npm
version: "5.9.3"
verdict: APPROVE-WITH-CONDITIONS
scope: "devDependency of apps/shipping-web: only the tsc CLI, compiling this repository's own sources (tsc -p apps/shipping-web), on GitHub-hosted ubuntu-24.04 CI, in the controlled maintenance environment and on macOS arm64 local build hosts; installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: ["Lockfile pins typescript 5.9.3, resolved from https://registry.npmjs.org/typescript/-/typescript-5.9.3.tgz with integrity sha512-jl1vZzPDinLr9eUt3J/t7V6FgNEw9QjvBPdysz9KfQDD41fQrC2Y4vKQdiaUpFT4bXlb1RHhLpp8wtm6M5TgSw==, declared as an exact devDependency of apps/shipping-web", "Only the tsc CLI, as tsc -p apps/shipping-web (or a package script that runs exactly that), on this repository's sources: no tsserver, tsserverlibrary or typingsInstaller (Automatic Type Acquisition runs npm install), no language-service or compiler plugins, and no repository code that imports the typescript API", "Installed only by npm ci --ignore-scripts and run only on the reviewed Node.js 26.13.0 (linux-x64 in CI, darwin-arm64 locally)", "Scope is apps/shipping-web only: this review is the separately reviewed 5.x that the typescript 7.0.2 review asks for, but it does not approve openapi-typescript 7.13.0, its transitives or packages/shipping-client; that use needs its own scope extension and transitive reviews", "TypeScript 5.x gets no further releases: an advisory against 5.9.3 ends this approval, because no fixed 5.x version will exist"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-07"
expiresAt: "2027-05-07"
---

# Security review: typescript 5.9.3 (the JavaScript compiler)

**Method.** On 2026-11-07, in a new empty directory on the owner's MacBook:

- **Fetched** with `/usr/bin/curl --proto '=https'`: the version document `https://registry.npmjs.org/typescript/5.9.3`,
  the full packument, the tarball, `-/npm/v1/attestations/typescript@5.9.3` and npm's signing keys
  (`-/npm/v1/keys`).
- **Checked:** the tarball against `dist.integrity` and `dist.shasum`, the registry signature with `/usr/bin/openssl`,
  and the tarball against the GitHub release asset, byte for byte.
- **Read and listed:** the tarball's 132 entries, after checking that no entry has an absolute path, a `..` segment or a
  link.
- **Searched** the shipped JavaScript for network, process and code-loading APIs.
- **Queried** advisories in OSV (`POST /v1/query`), deps.dev and GitHub's advisory database. A control query for
  `playwright@1.55.0` returned GHSA-7mvr-c777-76hp from all three, so the queries can return a hit.

Nothing was installed, imported or run.

## 1. Verdict

**APPROVE-WITH-CONDITIONS**, for the `tsc` CLI on our own sources. It has no dependencies, no install scripts and no
native code, and `tsc` loads no network or process module. Its bytes match on two publisher channels: the npm registry
and the GitHub release. The weak points are provenance and maintenance:

- **No npm provenance.** There is no attestation, only the registry signature and Microsoft's publishing account.
- **End of the line.** 5.9.3 is the last 5.x release, and no fixes will follow.

## 2. What it is and why it is needed

The TypeScript compiler written in TypeScript, emitting browser ES modules from `apps/shipping-web`'s sources. The
reviewed plan (option D) compiles the workbench with 5.9.3 on Linux and macOS, so it avoids the 20 platform binaries
of TypeScript 7.0.2.

## 3. Provenance

| Item | Value |
|---|---|
| Tarball | `https://registry.npmjs.org/typescript/-/typescript-5.9.3.tgz`, 4,377,468 bytes, 132 entries (= `dist.fileCount`) |
| Integrity | `sha512-jl1vZzPDinLr9eUt3J/t7V6FgNEw9QjvBPdysz9KfQDD41fQrC2Y4vKQdiaUpFT4bXlb1RHhLpp8wtm6M5TgSw==`, equal to the computed value; `dist.shasum` `5b4f59e1…670f` equal too |
| SHA-256 | `10e108c9cf7d5f2879053dff18515fb405abf2ccef63eaaf017d9c571687a1d3` |
| Published | 2025-09-30T21:19:38Z by `typescript-bot` (`typescript@microsoft.com`) |
| `gitHead` | `c63de15a992d37f0d6cec03ac7631872838602cb` |

- **Registry signature: verified.** `dist.signatures` holds one ECDSA signature by keyid
  `SHA256:DhQ8wR5APBvFHLF/+Tc+AYvPOdTpcIDqOhxsBHRwC7U`.
  - `/usr/bin/openssl dgst -sha256 -verify` checked it against that key from `-/npm/v1/keys`, over
    `typescript@5.9.3:<integrity>`: "Verified OK".
  - The same check on a message with one changed character failed, so it can fail.
- **No attestation.** `-/npm/v1/attestations/typescript@5.9.3` returns HTTP 404, and deps.dev shows
  `slsaProvenances: []` and `attestations: []`. No TypeScript release from 5.8.3 to 7.0.2 has one either; each
  carries one registry signature (packument).
- **Second channel: identical bytes.** The GitHub release `v5.9.3` of microsoft/TypeScript was published
  2025-10-01T19:06:07Z by `jakebailey`.
  - Its asset `typescript-5.9.3.tgz` (4,377,468 bytes) has the recorded digest `sha256:10e108c9…a1d3`.
  - The downloaded asset equals the npm tarball byte for byte (`cmp`).
  - The tag `v5.9.3` points directly at commit `c63de15a…02cb`, which equals `gitHead`. The commit is unsigned
    (`verification.reason: unsigned`), and the release is not marked immutable.
- **Maintainers.**
  - At publication, the version document lists typescript-bot, weswigham, sanders_n, andrewbranch, sheetalkamat,
    typescript-deploys and jakebailey.
  - Today the package lists microsoft1es, typescript-bot, weswigham, andrewbranch, typescript-deploys,
    microsoft-oss-releases and jakebailey. So sanders_n and sheetalkamat have left the list since, and two Microsoft
    release accounts have joined it.
  - 6.0.3 was published by typescript-bot, 7.0.1-rc by typescript-deploys and 7.0.2 by microsoft1es (packument). These
    changes postdate 5.9.3 and cannot alter its signed, already published bytes.
- **Install scripts: none.** `scripts` holds only development commands (`build`, `test`, `lint`, `format`,
  `setup-hooks` and others). There is no `preinstall`, `install`, `postinstall` or `prepare`, and the registry sets no
  `hasInstallScript`.

## 4. Maintenance and adoption

- **The line has ended.** 5.9.3 (2025-09-30) is the last 5.x release, after 5.9.2 (2025-07-31). Since then: 6.0.2
  (2026-03-23), 6.0.3 (2026-04-16) and 7.0.2 (2026-07-08, `latest`). 7.1.0-beta and nightlies follow (packument
  `time` and `dist-tags`). 5.9.3 is not deprecated.
- **Scorecard 8.2** for microsoft/TypeScript (deps.dev, 2026-08-24):
  - Code-Review 10, Maintained 10, Dangerous-Workflow 10, Token-Permissions 10, Binary-Artifacts 10, SAST 10.
  - Pinned-Dependencies 9, Branch-Protection 5, Signed-Releases 0, CII-Best-Practices 0.
  - 111,774 stars.

## 5. Known vulnerabilities at this version

- **None found on 2026-11-07** in all three sources: OSV `POST /v1/query` returned no vulns, deps.dev
  `advisoryKeys: []`, and GitHub's `/advisories?ecosystem=npm&affects=typescript@5.9.3` returned an empty list.
- **Scorecard's Vulnerabilities findings do not ship in 5.9.3.** The typescript 7.0.2 review lists them for the
  repository's `main`, among them GHSA-g3hv-75wg-m249 in minimatch. They belong to the repository's development
  dependencies, and the 5.9.3 tarball contains no third-party JavaScript (section 8). `minimatch`, `brace-expansion` and
  `picomatch` do not occur in `lib/_tsc.js` or `lib/typescript.js`.

## 6. License

**Apache-2.0** (`LICENSE.txt`, registry, deps.dev). `ThirdPartyNoticeText.txt` names the sources of its declaration
files and Unicode data: DefinitelyTyped (MIT), Unicode, the W3C Document Object Model, Web Background Synchronization
and WebGL. They are type declarations and data, not executable code.

## 7. Execution privileges

- **What runs.** `bin/tsc` is a two-line Node script, `require('../lib/tsc.js')`. `lib/tsc.js` calls
  `module.enableCompileCache()` and loads `lib/_tsc.js` (6,213,092 bytes).
  - No native code: no `.node`, `.wasm`, `.so`, `.dylib` or `.exe` file.
  - Only `bin/tsc` and `bin/tsserver` are executable.
  - `tsc` reads `tsconfig.json`, the sources and type declarations under `node_modules`, and writes the emitted files,
    with the user's file access.
- **Modules `tsc` loads.** `lib/_tsc.js` requires only `crypto`, `fs`, `inspector`, `os`, `path`, `perf_hooks` and an
  optional `source-map-support`. It does not use `http`, `https`, `net`, `tls`, `dgram`, `dns`, `child_process`,
  `worker_threads`, `fetch`, `XMLHttpRequest`, `WebSocket`, `eval` or `new Function`.
  - `inspector` (line 5042) is used only for `--generateCpuProfile`, through an in-process `inspector.Session`. It
    opens no port.
  - `require("source-map-support")` (line 5002) runs only when `NODE_ENV` is `development` (line 133811). It would
    load that package from `node_modules`, where the lockfile allows nothing unreviewed.
  - `process.env` is read for `TSC_WATCHFILE`, `TSC_NONPOLLING_WATCHER`, `TSC_WATCHDIRECTORY`, `NODE_ENV` and the
    debugger flags.
- **Compile cache.** `module.enableCompileCache()` without a directory writes V8 code cache to `NODE_COMPILE_CACHE`, or
  else to `path.join(os.tmpdir(), 'node-compile-cache')`. `NODE_DISABLE_COMPILE_CACHE=1` turns it off (Node v26.13.0
  `doc/api/module.md`, lines 370-386). It writes outside the workspace but contains only compiled code.
- **The parts not approved, and why.**
  - `lib/_tsserver.js` requires `child_process`, `net` and `readline`. It starts `watchGuard.js` with `execFileSync`
    (line 249) and forks `typingsInstaller.js` (line 417).
  - `lib/_typingsInstaller.js` requires `child_process` and runs `npm install` for `@types` packages, which is
    Automatic Type Acquisition.
  - Editors reach these through tsserver; `tsc` never loads them.
- **Untrusted text.** The 13 `lib/*/diagnosticMessages.generated.json` files and the declaration files are data. A
  case-insensitive search of the whole package for text addressed to AI agents ("ignore previous instructions", "AI
  agent", "LLM", "Copilot", "coding agent" and similar) found none.

## 8. Direct dependencies as published

- **None.** No `dependencies`, `optionalDependencies`, `peerDependencies` or bundled dependencies (version document).
- **No inlined third-party code.** The esbuild output keeps a path comment per module: `lib/_tsc.js` has 75
  `// src/…` markers and `lib/typescript.js` 267, with no `// node_modules/…` marker in either, nor in `_tsserver.js` or
  `_typingsInstaller.js`. So there is nothing that needs its own allowlist entry.
- **Engines:** `node >=14.17`.

## 9. Alternatives considered

- **TypeScript 7.0.2 (reviewed).** The native Go compiler: faster, but it needs one reviewed binary per host platform
  and has no classic `ts.*` API.
- **TypeScript 6.0.3.** Maintained longer than 5.9.3, but it is unreviewed. The plan names 5.9.3, the newest 5.x;
  openapi-typescript 7.13.0 declares `typescript: ^5.x` as its peer (its review, section 1).
- **Node's type stripping.** It runs erasable TypeScript without checking types, and it is not a browser build.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter. The lockfile entry must read:

```json
"node_modules/typescript": {
  "version": "5.9.3",
  "resolved": "https://registry.npmjs.org/typescript/-/typescript-5.9.3.tgz",
  "integrity": "sha512-jl1vZzPDinLr9eUt3J/t7V6FgNEw9QjvBPdysz9KfQDD41fQrC2Y4vKQdiaUpFT4bXlb1RHhLpp8wtm6M5TgSw==",
  "dev": true
}
```

The `bin` and `license` fields that npm also writes there are fine.

**Residual risk.**

- **Trust rests on Microsoft's publishing account.** There is no build provenance. The match with the GitHub release
  asset shows one publisher, through two channels.
- **No future fixes.** A compiler bug or advisory in 5.9.3 will not be fixed in 5.x.
- **A large, opaque surface.** `tsc` is about 6 MB of JavaScript, run with the user's rights on every build. It only
  reads repository-controlled inputs.

## 11. Not verified

- **Source to tarball.** `lib/_tsc.js` was not rebuilt from the tag, so its correspondence to the source is not
  proven.
- **The maintainers.** Their identities and the reasons for the list changes.
- **Microsoft's announcement** of 5.9 (devblogs.microsoft.com) was not read.
