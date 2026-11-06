---
component: "actions/upload-artifact"
kind: github-action
version: "7.0.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "CI only, nested use only: as the step that actions/upload-pages-artifact@fc324d3547104276b827a68afc52ff2a11cc49c9 runs, pinned there as actions/upload-artifact@bbbca2ddaa5d8feaa63e36b76fdaad77386f024f. Not approved for direct 'uses:' in our workflows (those use the separately reviewed 7.0.2)"
conditions: [
  "Runs only as the nested step of actions/upload-pages-artifact@fc324d3547104276b827a68afc52ff2a11cc49c9, with the inputs that action sets: one file ${{ runner.temp }}/artifact.tar, if-no-files-found: error, overwrite and archive at their defaults",
  "No workflow in this repository references actions/upload-artifact@bbbca2ddaa5d8feaa63e36b76fdaad77386f024f directly",
  "All the conditions of the upload-pages-artifact 5.0.0 review hold (Pages build job, push to main only, symlink-free site directory)",
  "Re-review when upload-pages-artifact changes this pin, or when an upload-artifact release updates the bundled undici 6.23.0 (19 advisories); this approval lapses 30 days after such a release"
]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# actions/upload-artifact v7.0.0 (nested in upload-pages-artifact v5.0.0)

**Method.** On 2026-11-06 I read the GitHub API and these files at commit `bbbca2dd…`: `action.yml`, `package.json`, the whole `package-lock.json` (verbatim), `src/upload/upload-artifact.ts`, `src/upload/input-helper.ts`, `src/shared/search.ts`, `src/shared/upload-artifact.ts`, `LICENSE`, `.github/CODEOWNERS` and `check-dist.yml`. For each of the 156 non-dev lockfile entries I read its deps.dev record, and for each advisory found, its OSV record. I did not read `dist/` or the source of `@actions/artifact`.

## 1. Verdict
**APPROVE-WITH-CONDITIONS, for nested use only.** `upload-pages-artifact` v5.0.0 pins this exact commit, so it runs whenever we publish the site, and it needs its own approval. It is GitHub's own action, at a verified commit, and our use is narrow: it uploads one tar file that our own job just wrote. The bundled tree is the concern.
- **`undici` 6.23.0: 19 advisories**, of which 8 are HIGH.
- Also flagged: `fast-xml-parser` 5.4.1, `lodash` 4.17.23, `glob` 10.5.0 (deprecated), `brace-expansion` and `minimatch`.

Each of these needs a hostile server, attacker-chosen glob patterns or attacker-chosen XML. Here, the pattern is a fixed path, and the peers are GitHub's artifact service and its blob storage.

## 2. What it is and why it is needed
It uploads files as a workflow artifact. Here it is not chosen by us: `upload-pages-artifact` calls it. The design lists upload-artifact **7.0.2** for reports (`v01-design.md` §4). This **7.0.0** pin is an extra version that the design did not foresee, and the design says to inventory "Actions' nested dependencies" (§3).

## 3. Provenance
- **Publisher.** GitHub, org `actions`. The repo was created 2019-06-18 and is MIT-licensed ([repo](https://api.github.com/repos/actions/upload-artifact)). CODEOWNERS is `* @actions/artifacts-actions` ([CODEOWNERS](https://raw.githubusercontent.com/actions/upload-artifact/bbbca2ddaa5d8feaa63e36b76fdaad77386f024f/.github/CODEOWNERS)).
- **Tag.** `v7.0.0` is a lightweight tag on `bbbca2ddaa5d8feaa63e36b76fdaad77386f024f` ([ref](https://api.github.com/repos/actions/upload-artifact/git/ref/tags/v7.0.0)), which is exactly the nested pin.
- **Commit.** A squash of "Support direct file uploads (#764)", authored by `danwkennedy` and committed by `web-flow` at 2026-02-25T21:04:27Z. It is "verified" with reason `valid`, and it touches 10 files, `dist/upload/index.js` among them ([commit](https://api.github.com/repos/actions/upload-artifact/commits/bbbca2ddaa5d8feaa63e36b76fdaad77386f024f)).
- **Release.** Published 2026-02-26T18:21:17Z by `danwkennedy`, with `"immutable":false` and no assets. It adds the `archive` input and moves to ESM ([release](https://api.github.com/repos/actions/upload-artifact/releases/tags/v7.0.0)).
- **Build check.** [check-dist.yml](https://raw.githubusercontent.com/actions/upload-artifact/bbbca2ddaa5d8feaa63e36b76fdaad77386f024f/.github/workflows/check-dist.yml) runs `npm ci && npm run release` and fails if `git diff dist/` is not empty. Its own actions are pinned by tag.
- **Signing.** There are no signed releases and no provenance.

## 4. Maintenance and adoption
- **Releases** ([releases](https://api.github.com/repos/actions/upload-artifact/releases?per_page=15)): v6.0.0 2025-12-12, v7.0.0 2026-02-26, v7.0.1 2026-04-10, v7.0.2 2026-10-07 (latest; `v7` points there). The last push was 2026-10-07.
- **Adoption:** 4,253 stars, 1,109 forks.
- **OpenSSF Scorecard 5.2**, via [deps.dev](https://api.deps.dev/v3/projects/github.com%2Factions%2Fupload-artifact). The scan date is 2026-08-24, at the v7.0.1 commit `043fb46d…`, not this pin.
  - Strong: Code-Review 10, SAST 10, Dangerous-Workflow 10, Binary-Artifacts 10, Security-Policy 9.
  - Weak: Maintained 0, Branch-Protection 0, Token-Permissions 0, Pinned-Dependencies 1.

## 5. Known vulnerabilities at 7.0.0
**The action itself: none found on 2026-11-06.** The [GitHub Advisory Database](https://api.github.com/advisories?ecosystem=actions&affects=actions/upload-artifact) returns `[]`. OSV's POST query was not available to me.

**Bundled packages: 10 of the 156 non-dev entries have advisories on deps.dev** (`https://api.deps.dev/v3/systems/npm/packages/<name>/versions/<v>`; details at `https://osv.dev/vulnerability/<ID>`):
- **`undici` 6.23.0: 19 advisories.** 6.29.1 fixes all of them.
  - HIGH: GHSA-2m5v-pch6-74hx (TLS bypass, needs a function-valued `tls` option), GHSA-qrxm-fqhp-jwvm (TLS 1.2 renegotiation), GHSA-xjj6-f33q-929c (too many content-encodings).
  - HIGH, WebSocket client only: GHSA-f269-vfmq-vjvj, GHSA-rfgv-xxqx-mfg5, GHSA-v9p9-hfj2-hcw8, GHSA-vrm6-8vpv-qv8q, GHSA-vxpw-j846-p89q.
  - Moderate or low: GHSA-2mjp-6q6p-2qxm, GHSA-35p6-xmwp-9g52, GHSA-4992-7rv2-5pvq, GHSA-4qv8-2p9j-2685, GHSA-8xcm-r25x-g524, GHSA-9fm3-97px-rjf6, GHSA-g8m3-5g58-fq7m, GHSA-m8rv-5g2x-5cg5, GHSA-p88m-4jfj-68fv, GHSA-r53p-7pc4-xj5r, GHSA-v3r7-h72x-cjcm.
- **`fast-xml-parser` 5.4.1:** GHSA-8gc5-j5rx-235r (HIGH, numeric entity expansion on attacker XML), GHSA-jp2q-39xq-3w4g and GHSA-gh4j-gqv2-49f6. **`fast-xml-builder` 1.0.0:** GHSA-5wm8-gmm8-39j9.
- **`lodash` 4.17.23:** GHSA-r5fr-rjxr-66jc (HIGH, `_.template` with untrusted `imports` keys) and GHSA-f23m-r3pf-42rh.
- **Glob stack**, all of which need an attacker-influenced pattern:
  - `glob` 10.5.0 (deprecated): GHSA-3g3m-2fjr-6pw4.
  - `minimatch` 9.0.8 and 10.2.4: GHSA-g3hv-75wg-m249.
  - `brace-expansion` 1.1.12 and 2.0.2 have 7 advisories each, and 5.0.3 has 8. The IDs are GHSA-3jxr-9vmj-r5cp, -6j4f-fj2g-mc7p, -f886-m6hf-6m8v, -mh99-v99m-4gvg, -q2hr-2g5m-vwhr, -qhr7-859c-m2p7, -rgw5-rvv9-x895 and, for 5.0.3 only, -jxxr-4gwj-5jf2.

**Reachability: reasoned, not traced in `dist/`.**
- **Glob stack.** The only pattern is the fixed path `${{ runner.temp }}/artifact.tar` set by upload-pages-artifact.
- **XML parsing.** It applies to responses from the blob storage endpoint.
- **HTTP.** It goes to GitHub's artifact service.
- **Not found:** no WebSocket or `_.template` use in the `src/` files I read.

## 6. License
"The MIT License (MIT)", "Copyright (c) 2018 GitHub, Inc. and contributors" ([LICENSE](https://raw.githubusercontent.com/actions/upload-artifact/bbbca2ddaa5d8feaa63e36b76fdaad77386f024f/LICENSE)). The bundled packages are MIT, ISC, Apache-2.0, BSD-3-Clause, 0BSD or BlueOak-1.0.0, per the lockfile and deps.dev.

## 7. Execution privileges
It runs in CI as a `node24` action with `dist/upload/index.js` as main, and has no post step ([action.yml](https://raw.githubusercontent.com/actions/upload-artifact/bbbca2ddaa5d8feaa63e36b76fdaad77386f024f/action.yml)). It runs in the Pages build job.
- **File system.**
  - `@actions/glob` runs with `followSymbolicLinks: true`, `omitBrokenSymbolicLinks: true` and `excludeHiddenFiles` unless `include-hidden-files` is set ([search.ts](https://raw.githubusercontent.com/actions/upload-artifact/bbbca2ddaa5d8feaa63e36b76fdaad77386f024f/src/shared/search.ts)).
  - It reads the matched files. With `archive: true`, the default, it zips them at `compression-level` 6.
- **Network.**
  - `artifact.uploadArtifact(name, files, root, {retentionDays, compressionLevel, skipArchive})` sends the upload ([shared/upload-artifact.ts](https://raw.githubusercontent.com/actions/upload-artifact/bbbca2ddaa5d8feaa63e36b76fdaad77386f024f/src/shared/upload-artifact.ts)).
  - Only with `overwrite: true`, it also calls `deleteArtifact` ([upload-artifact.ts](https://raw.githubusercontent.com/actions/upload-artifact/bbbca2ddaa5d8feaa63e36b76fdaad77386f024f/src/upload/upload-artifact.ts)).
  - The transport is inside `@actions/artifact` 6.2.0, which bundles `@azure/storage-blob` 12.31.0 and `@protobuf-ts/runtime-rpc` 2.11.1. I did not read its source.
- **Outputs:** `artifact-id`, `artifact-url` and `artifact-digest`.
- **Not used, per `src/`:** `child_process`, `@actions/exec` and the workflow token. `@actions/exec` is in the tree as a dependency of `@actions/core`.

## 8. Direct dependencies as published
- **Direct**, from [package.json](https://raw.githubusercontent.com/actions/upload-artifact/bbbca2ddaa5d8feaa63e36b76fdaad77386f024f/package.json): `@actions/artifact` ^6.2.0, `@actions/core` ^3.0.0, `@actions/github` ^9.0.0, `@actions/glob` ^0.6.1, `@actions/io` ^3.0.2, `minimatch` ^10.1.1. It also overrides `uri-js` with `uri-js-replace`.
- **Bundled:** the [lockfile](https://raw.githubusercontent.com/actions/upload-artifact/bbbca2ddaa5d8feaa63e36b76fdaad77386f024f/package-lock.json) (v3) has 156 non-dev entries, fixed by the SHA pin. They include:
  - `@actions/artifact` 6.2.0
  - `@azure/*`, 13 packages
  - `@octokit/core` 7.0.6
  - `undici` 6.23.0 (**flagged**)
  - `fast-xml-parser` 5.4.1 (**flagged**)
  - `archiver` 7.0.1
  - `unzip-stream` 0.3.4 (no advisory)
  - `typescript` 5.9.3 (no dev flag, oddly)

## 9. Alternatives considered
- **Replace upload-pages-artifact with our own `tar` step plus upload-artifact 7.0.2.** This removes this extra version (see that review, §9).
- **Wait for an upload-pages-artifact release that pins 7.0.2.** None exists as of 2026-11-06.

## 10. Conditions and residual risk
The conditions are in the frontmatter. Residual risks:
- The bundled advisories, which were not traced in `dist/`.
- `dist/` was not rebuilt.
- `@actions/artifact` was not read.

## 11. Not verified
- That `dist/upload/index.js` matches `src/`.
- The `@actions/artifact` 6.2.0 source and the endpoints it contacts.
- Which flagged packages are actually bundled into `dist/` and which are only in the lockfile.
- The identity behind the web-flow signature.
