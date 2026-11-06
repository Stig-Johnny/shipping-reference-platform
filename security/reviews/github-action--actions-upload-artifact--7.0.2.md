---
component: "actions/upload-artifact"
kind: github-action
version: "7.0.2"
verdict: APPROVE-WITH-CONDITIONS
scope: "CI only: uploading this repository's conformance, test and mutation (PIT) reports as workflow artifacts, pinned as actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9"
conditions: [
  "uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 (full SHA; never the v7 or v7.0.2 tag)",
  "path lists only report directories (build/reports/c4-contract/ and the PIT report directory); include-hidden-files: false; a preceding step fails the job if those directories contain symbolic links, because the action follows them",
  "archive: true, overwrite: false and an explicit retention-days; no other inputs",
  "No GITHUB_TOKEN scope is granted for this step: it authenticates with the runner's ACTIONS_RUNTIME_TOKEN",
  "Artifacts are untrusted output of candidate code: no workflow downloads and executes or trusts them with secrets or write permissions (e.g. workflow_run)",
  "Network-restricted workers allow this step only the Actions results service and its blob-storage endpoint",
  "Re-review when an upload-artifact release refreshes the bundled undici 6.23.0, lodash 4.17.23 or fast-xml-parser 5.4.1; this approval lapses 30 days after such a release"
]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# actions/upload-artifact v7.0.2

**Method.** On 2026-11-06 I read:
- the GitHub API;
- the repo at commit `cf430e03…`: `action.yml`, `package.json`, the lockfile, `src/upload/*`, `src/shared/*`, the README and the workflows;
- `@actions/artifact` 6.3.1 in `actions/toolkit`, at its npm-provenance commit;
- OpenSSF Scorecard, the GitHub Advisory Database, osv.dev and deps.dev.

I did not read or rebuild `dist/`.

## 1. Verdict
**APPROVE-WITH-CONDITIONS.** It is GitHub's own action at a verified commit. Its core library, `@actions/artifact` 6.3.1, carries verified npm SLSA provenance ([deps.dev](https://api.deps.dev/v3/systems/npm/packages/%40actions%2Fartifact/versions/6.3.1)), and it needs no `GITHUB_TOKEN` scope. Against that, its lockfile still bundles `undici` 6.23.0 (19 advisories), `lodash` 4.17.23 (2) and `fast-xml-parser` 5.4.1 (3). I did not trace whether any of them is reachable. Each needs a hostile GitHub or Azure endpoint, or attacker-controlled input reaching those libraries. It also follows symlinks, and what it uploads is produced by candidate code. The conditions bound these risks.

## 2. What it is and why it is needed
It uploads the conformance and mutation reports, including when a job fails (`v01-design.md` §4, §5). Further Actions are deferred.

## 3. Provenance
- **Publisher.** GitHub, org `actions`. The repo was created 2019-06-18 and is MIT-licensed ([repo](https://api.github.com/repos/actions/upload-artifact)).
- **Tag.** `v7.0.2` is a lightweight tag on commit `cf430e030ddbb5b0abf93d22962f4752f3646cd9`. The [git/ref](https://api.github.com/repos/actions/upload-artifact/git/ref/tags/v7.0.2) and [commits](https://api.github.com/repos/actions/upload-artifact/commits/v7.0.2) APIs agree, and the floating [v7](https://api.github.com/repos/actions/upload-artifact/git/ref/tags/v7) tag is the same commit.
- **Commit.** "Merge pull request #822 … prepare v7.0.2 release", by `tunc-d`, at 2026-10-07T10:00:52Z. It touches only `package.json` and the lockfile. It is verified with GitHub's web-flow key `B5690EEEBB952194`, which attests the merge, not who wrote the code.
- **Release.** Published 2026-10-07T10:08:14Z by `tunc-d`, a first-time contributor (via #821) whose account has `site_admin: true`. It is `"immutable":false`, with no uploaded assets ([release](https://api.github.com/repos/actions/upload-artifact/releases/tags/v7.0.2)).
- **Build checks.** `check-dist.yml` rebuilds `dist/` and fails on any diff ([workflows](https://github.com/actions/upload-artifact/tree/cf430e030ddbb5b0abf93d22962f4752f3646cd9/.github/workflows)). There are no signed releases.

## 4. Maintenance and adoption
- **Releases** ([releases](https://github.com/actions/upload-artifact/releases); years in brackets are inferred from order):
  - v5.0.0: 24 Oct [2025]
  - v6.0.0: 12 Dec [2025]. Node 24; requires runner ≥ 2.327.1.
  - v7.0.0: 26 Feb [2026]. ESM; adds `archive: false`.
  - v7.0.1: 10 Apr [2026]
  - v7.0.2: 2026-10-07
- **Community.** 4,253 stars, 1,108 forks. CODEOWNERS is `* @actions/artifacts-actions`. The `SECURITY.md` linked from the README returns 404 at this commit; an org-level policy exists.
- **OpenSSF Scorecard: 5.5** (2026-11-02, on this exact commit) ([scorecard](https://api.securityscorecards.dev/projects/github.com/actions/upload-artifact)):
  - 10: Code-Review, SAST, Dangerous-Workflow, Binary-Artifacts
  - 9: Security-Policy
  - 3: Maintained
  - 1: Pinned-Dependencies
  - 0: Token-Permissions, Branch-Protection

## 5. Known vulnerabilities at 7.0.2
**The action itself: none found on 2026-11-06.** The [GitHub Advisory Database](https://api.github.com/advisories?ecosystem=actions&affects=actions/upload-artifact) returns `[]`, [osv.dev](https://osv.dev/list?ecosystem=GitHub+Actions&q=actions%2Fupload-artifact) shows "No results", and `@actions/artifact` 6.3.1 has `[]`.

Bundled packages with advisories on deps.dev:

| Package (required by) | Advisories | Reachability (not traced in `dist/`) |
|---|---|---|
| [`undici` 6.23.0](https://api.deps.dev/v3/systems/npm/packages/undici/versions/6.23.0) (`@actions/http-client` 4.0.1, `@actions/github` 9.1.1) | 19, fixed in 6.29.1. They include [GHSA-2m5v-pch6-74hx](https://osv.dev/vulnerability/GHSA-2m5v-pch6-74hx) (TLS check bypass, 7.4, only with a function-valued `tls` option), GHSA-2mjp-6q6p-2qxm (request smuggling, 6.5) and seven DoS at 7.5 | The RPC goes to `ACTIONS_RESULTS_URL` over TLS. Exploiting any of these needs a hostile server or proxy, or WebSocket use |
| `lodash` 4.17.23 (`archiver-utils` 5.0.2) | GHSA-r5fr-rjxr-66jc (`_.template` imports → code injection, 8.1); GHSA-f23m-r3pf-42rh (prototype pollution in `_.unset`/`_.omit`/`_.pullAt`, 6.5) | Used for zip creation on our own file list. I did not check whether `_.template` is called |
| [`fast-xml-parser` 5.4.1](https://api.deps.dev/v3/systems/npm/packages/fast-xml-parser/versions/5.4.1) (`@azure/core-xml` 1.5.0 ← `@azure/storage-blob`) | GHSA-8gc5-j5rx-235r (entity-expansion bypass, 7.5), GHSA-jp2q-39xq-3w4g (5.9), GHSA-gh4j-gqv2-49f6 (XMLBuilder injection, 6.1) | Parses Azure Blob responses from the signed upload URL |
| [`minimatch` 10.2.4](https://api.deps.dev/v3/systems/npm/packages/minimatch/versions/10.2.4) | GHSA-g3hv-75wg-m249 (DoS) | Only our own `path` patterns reach it |

## 6. License
MIT, "Copyright (c) 2018 GitHub, Inc. and contributors" ([LICENSE](https://raw.githubusercontent.com/actions/upload-artifact/cf430e030ddbb5b0abf93d22962f4752f3646cd9/LICENSE)).

## 7. Execution privileges
It runs in CI as `node24` `dist/upload/index.js`, with no post step ([action.yml](https://raw.githubusercontent.com/actions/upload-artifact/cf430e030ddbb5b0abf93d22962f4752f3646cd9/action.yml)).

- **Files read.** Everything matching `path`; hidden files are excluded by default. It uses `followSymbolicLinks: true`, so a link's target content gets uploaded ([search.ts](https://raw.githubusercontent.com/actions/upload-artifact/cf430e030ddbb5b0abf93d22962f4752f3646cd9/src/shared/search.ts)).
- **Archive.** It zips the files at compression level 6 and computes the SHA-256 on the runner. File modes become 755/644 ([README](https://raw.githubusercontent.com/actions/upload-artifact/cf430e030ddbb5b0abf93d22962f4752f3646cd9/README.md)).
- **Network.** All calls authenticate with `ACTIONS_RUNTIME_TOKEN` ([config.ts](https://raw.githubusercontent.com/actions/toolkit/bcae5eca3f432f591cf9f052db4533fc585fb2ac/packages/artifact/src/internal/shared/config.ts), [upload](https://github.com/actions/toolkit/tree/bcae5eca3f432f591cf9f052db4533fc585fb2ac/packages/artifact/src/internal/upload)).
  - Twirp `CreateArtifact` and `FinalizeArtifact` (the latter sends `sha256:<digest>`) go to the origin of `ACTIONS_RESULTS_URL`.
  - The data goes through an Azure `BlockBlobClient` to the signed URL returned; the URL's `sig` is masked in logs.
  - With `overwrite: true` it also calls `ListArtifacts` and `DeleteArtifact`.
  - No `GITHUB_TOKEN` or Octokit is involved in uploading.
- **Integrity.** The digest is self-reported by the client; I saw no server-side check.
- **Not seen in the source read:** process execution, containers.
- **Exposure to earlier steps.** Earlier steps in the same job (the candidate build) can modify files on the runner, including this action's downloaded code, which then runs with the runtime token. That is why the artifacts must be treated as untrusted.

## 8. Direct dependencies as published
- **Declared** in [package.json](https://raw.githubusercontent.com/actions/upload-artifact/cf430e030ddbb5b0abf93d22962f4752f3646cd9/package.json): `@actions/artifact` ^6.3.1, `@actions/core` ^3.0.0, `@actions/github` ^9.0.0, `@actions/glob` ^0.6.1, `@actions/io` ^3.0.2, `minimatch` ^10.1.1.
- **Resolved** in the [lockfile](https://raw.githubusercontent.com/actions/upload-artifact/cf430e030ddbb5b0abf93d22962f4752f3646cd9/package-lock.json) and bundled into `dist/`, so the SHA pins them:
  - `@actions/artifact` 6.3.1
  - `@azure/storage-blob` 12.31.0
  - `archiver` 7.0.1
  - `unzip-stream` 0.3.4
  - `jwt-decode` 4.0.0
  - `@protobuf-ts/runtime` 2.11.1
  - Octokit 7.x/10.x
  - the packages flagged in §5
- **Licence records.** `.licenses/npm` lists only the six direct packages, not the transitive bundle.
- **Flag:** the four packages in §5 need attention at the next review of this pin.

## 9. Alternatives considered
- `$GITHUB_STEP_SUMMARY` needs no third-party code, but cannot carry PIT's HTML report.
- v6.0.0 was not reviewed; it probably bundles older versions of the same libraries.

## 10. Conditions and residual risk
The conditions are in the frontmatter. Residual risks:
- The bundled advisories in §5, not traced.
- The digest is self-reported.
- Artifacts from candidate jobs are attacker-influenced by design.

## 11. Not verified
- That `dist/` matches its source.
- `zip.ts` and `upload-zip-specification.ts`, including how symlinks are handled while zipping.
- Whether a `permissions:` block narrows `ACTIONS_RUNTIME_TOKEN`.
- The exact results-service and blob hostnames.
- Whether the flagged library code is reachable.
