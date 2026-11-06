---
component: "actions/setup-java"
kind: github-action
version: "6.2.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "CI only: installing the reviewed Temurin 25.0.5+8 archive as JAVA_HOME in this repository's jobs, pinned as actions/setup-java@77dd5b2ce225ea11469928ac317850245a74ae78, with distribution: jdkfile"
conditions: [
  "uses: actions/setup-java@77dd5b2ce225ea11469928ac317850245a74ae78 (full SHA; never v6 or v6.2.0; the floating v6 tag still points at v6.1.0)",
  "Approved invocation only: distribution: jdkfile, jdk-file = the Temurin archive the controller staged and checked against the allowlist SHA-256; distribution: temurin (download at run time) is not approved here, because its checksum comes from the Adoptium API at run time rather than from the allowlist",
  "cache, cache-jdk, gpg-private-key, server-*, mvn-* and settings-path stay unset; token: '' (not needed for jdkfile)",
  "Gradle's Maven-settings reuse (org.gradle.mirror.maven.settings) stays off, since this action writes ~/.m2/settings.xml on every run",
  "Any other version is a new review"
]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# actions/setup-java v6.2.0

**Method.** On 2026-11-06 I read the GitHub API and the repository at commit `77dd5b2c…`:
- `action.yml`, `package.json` and the lockfile;
- the Temurin installer and the local (`jdkfile`) installer;
- `base-installer.ts`, `checksum.ts`, `gpg.ts`, `auth.ts`, `toolchains.ts`, `cache.ts`, `util.ts`, `constants.ts`;
- the README.

I also checked OpenSSF Scorecard, the GitHub Advisory Database, osv.dev and deps.dev. `dist/` was not read or rebuilt.

## 1. Verdict
**APPROVE-WITH-CONDITIONS, for the `jdkfile` path only.** v6 added vendor-checksum and GPG verification for Temurin downloads ([README](https://raw.githubusercontent.com/actions/setup-java/77dd5b2ce225ea11469928ac317850245a74ae78/README.md)). Two gaps remain on the network path:
- The expected checksum comes from the same API response that supplies the download link.
- A JDK already in the runner's tool cache is used **without any verification**, and ubuntu-24.04 ships one as 25.0.5+8.

The design already has a controller that stages approved binaries. The approved use is therefore narrow: the controller verifies the archive against the allowlist, and this action only extracts it and sets `JAVA_HOME`. Confidence is lowered because v6.2.0 is two days old, its Scorecard Code-Review score is 2, and the TypeScript compiler that built `dist/` came from a non-npmjs feed (§8).

## 2. What it is and why it is needed
It provisions Temurin in CI. The design requires the action to be pinned by a reviewed full SHA, and it defers caching (`v01-design.md` §4).

## 3. Provenance
- **Publisher.** GitHub, org `actions`. The repository was created 2019-07-09 and is MIT-licensed ([repo](https://api.github.com/repos/actions/setup-java)).
- **Tag.** `v6.2.0` is a lightweight tag on commit `77dd5b2ce225ea11469928ac317850245a74ae78`. The [git/ref](https://api.github.com/repos/actions/setup-java/git/ref/tags/v6.2.0) and [commits](https://api.github.com/repos/actions/setup-java/commits/v6.2.0) APIs agree. The floating [v6](https://api.github.com/repos/actions/setup-java/git/ref/tags/v6) tag still points at `b843ebf9…`, which is v6.1.0.
- **Commit.** Authored by Bruno Borges at 2026-11-04T22:10:04Z and committed via `web-flow`. It is verified with GitHub's key `B5690EEEBB952194`, which attests the merge only.
- **Release.** Published 2026-11-04T22:12:26Z by `brunoborges`. It is marked `"immutable":true` and has no uploaded assets ([release](https://api.github.com/repos/actions/setup-java/releases/tags/v6.2.0)).
- **Changes since v6.1.0.** 7 commits touching 44 files, including 12 npm bumps and a `dist/` rebuild ([compare](https://github.com/actions/setup-java/compare/v6.1.0...v6.2.0)).
- **Inconsistencies.**
  - The commit for #1312 is titled "Save uncommitted changes", but the release notes call it "Add repository-specific Copilot instructions".
  - `package.json` still says `"version": "6.0.0"`.
- **dist/ check and signing.** `check-dist` delegates to `actions/reusable-workflows/…/check-dist.yml@main`. That workflow is referenced by branch, not SHA, and I did not read it. There are no signed releases and no provenance.

## 4. Maintenance and adoption
- **Releases** ([releases](https://github.com/actions/setup-java/releases); year 2026 inferred from order): v6.0.0 24 Aug, v6.0.1 09 Sep, v6.1.0 14 Oct, v6.2.0 2026-11-04.
  - The v5.x line ended at v5.7.0 (31 Jul).
  - v1–v4 received deprecation releases on 04 Aug.
- **Adoption.** 2,026 stars and 884 forks. CODEOWNERS is `* @actions/setup-actions-team`. The repository runs `zizmor.yml`, a workflow linter.
- **OpenSSF Scorecard: 5.3** (2026-11-02, scanned at `8cd57eb6…`, the second of v6.2.0's seven commits) ([scorecard](https://api.securityscorecards.dev/projects/github.com/actions/setup-java)):
  - Strong: Maintained 10, SAST 10, Dangerous-Workflow 10, Security-Policy 9.
  - Weak: **Code-Review 2** (5 of 21 changesets approved), Token-Permissions 0, Pinned-Dependencies 0, Branch-Protection 0.

## 5. Known vulnerabilities at 6.2.0
**The action itself: none found on 2026-11-06.** The [GitHub Advisory Database](https://api.github.com/advisories?ecosystem=actions&affects=actions/setup-java) returns `[]`, and [osv.dev](https://osv.dev/list?ecosystem=GitHub+Actions&q=actions%2Fsetup-java) has "No results".

**Bundled packages, checked on deps.dev:**
- `minimatch` 10.2.6 has [GHSA-g3hv-75wg-m249](https://api.deps.dev/v3/advisories/GHSA-g3hv-75wg-m249) (CVE-2026-118764, a globparts-amplification DoS, no CVSS). It can only be reached through glob patterns from our own inputs, namely `cache-dependency-path`, which stays unset.
- No advisories: `undici` 6.29.1, `fast-xml-parser` 5.11.2, `@actions/cache` 6.3.0, `semver` 7.8.5, `brace-expansion` 5.0.12, `@azure/core-rest-pipeline` 1.23.0 and 1.25.0, and the `@actions/*` packages.

## 6. License
MIT, "Copyright (c) 2018 GitHub, Inc. and contributors" ([LICENSE](https://raw.githubusercontent.com/actions/setup-java/77dd5b2ce225ea11469928ac317850245a74ae78/LICENSE)).

## 7. Execution privileges
It runs in CI as a `node24` action: `dist/setup/index.js`, with `dist/cleanup/index.js` as the post step ([action.yml](https://raw.githubusercontent.com/actions/setup-java/77dd5b2ce225ea11469928ac317850245a74ae78/action.yml)).

- **`jdkfile` (approved).** No network. It extracts the given archive with `tar` (or `pigz` if present) into `$RUNNER_TOOL_CACHE/Java_jdkfile_jdk/<version>` and checks no checksum ([local installer](https://raw.githubusercontent.com/actions/setup-java/77dd5b2ce225ea11469928ac317850245a74ae78/src/distributions/local/installer.ts)). If a matching `Java_jdkfile_jdk` entry already exists in the tool cache, it uses that entry instead.
- **`temurin` (not approved; this is what it would download).** It sends a GET to `https://api.adoptium.net/v3/assets/version/[1.0,100.0]?…&os=linux&architecture=x64&image_type=jdk&release_type=ga&jvm_impl=hotspot`, following pagination only on that origin ([temurin installer](https://raw.githubusercontent.com/actions/setup-java/77dd5b2ce225ea11469928ac317850245a74ae78/src/distributions/temurin/installer.ts)).
  - **Download.** It fetches `binaries[0].package.link` and `signature_link`, normally `github.com/adoptium/temurin25-binaries` release assets. It does not check their host and sends no token.
  - **Checksum.** It compares the archive's SHA-256 with the API's `package.checksum` in constant time ([checksum.ts](https://raw.githubusercontent.com/actions/setup-java/77dd5b2ce225ea11469928ac317850245a74ae78/src/checksum.ts)). If the API gives no checksum, the check is skipped and only a debug message is logged ([base-installer](https://raw.githubusercontent.com/actions/setup-java/77dd5b2ce225ea11469928ac317850245a74ae78/src/distributions/base-installer.ts)).
  - **Signature.** It checks the GPG signature against the bundled key `3B04D753…F04B` ([gpg.ts](https://raw.githubusercontent.com/actions/setup-java/77dd5b2ce225ea11469928ac317850245a74ae78/src/gpg.ts)). A failure only warns, unless `verify-signature: true` is set.
  - **Tool cache.** A matching preinstalled JDK is used **without any check**, unless `force-download: true` is set.
- **On every run.**
  - It writes `~/.m2/settings.xml`, overwriting it by default. The file holds environment-variable **names** (`${env.GITHUB_ACTOR}`, `${env.GITHUB_TOKEN}`), never their values ([auth.ts](https://raw.githubusercontent.com/actions/setup-java/77dd5b2ce225ea11469928ac317850245a74ae78/src/auth.ts), [constants.ts](https://raw.githubusercontent.com/actions/setup-java/77dd5b2ce225ea11469928ac317850245a74ae78/src/constants.ts)).
  - It merges an entry into `~/.m2/toolchains.xml` ([toolchains.ts](https://raw.githubusercontent.com/actions/setup-java/77dd5b2ce225ea11469928ac317850245a74ae78/src/toolchains.ts)); Gradle auto-detects Maven toolchains from it.
  - It sets `JAVA_HOME` and `PATH` and registers a problem matcher.
- **Optional, off in the approved use.** With a key input, it runs `gpg --import` into a 0700 home under `RUNNER_TEMP`, which the post step deletes. The Actions cache stays off unless `cache` is set ([cache.ts](https://raw.githubusercontent.com/actions/setup-java/77dd5b2ce225ea11469928ac317850245a74ae78/src/cache.ts)).

## 8. Direct dependencies as published
**Declared** in [package.json](https://raw.githubusercontent.com/actions/setup-java/77dd5b2ce225ea11469928ac317850245a74ae78/package.json):
- `@actions/cache` ^6.3.0, `@actions/core` ^3.0.1, `@actions/exec` ^3.0.0, `@actions/glob` ^0.7.0
- `@actions/http-client` ^4.0.1, `@actions/io` ^3.0.2, `@actions/tool-cache` ^4.0.0
- `fast-xml-parser` ^5.11.2, `semver` ^7.8.5

**Bundled into `dist/`** (resolved in the [lockfile](https://raw.githubusercontent.com/actions/setup-java/77dd5b2ce225ea11469928ac317850245a74ae78/package-lock.json)). All are fixed by the pinned SHA:
- `undici` 6.29.1
- `minimatch` 10.2.6 (flagged, §5)
- `@azure/storage-blob` 12.31.0
- `@protobuf-ts/runtime` 2.11.1
- `http-proxy-agent` 7.0.2, `https-proxy-agent` 7.0.6
- `tunnel` 0.0.6
- `is-unsafe` 2.0.0

**Flag: the compiler that built `dist/`.** The dev dependency `typescript` 6.0.3 resolves from `ms-feed-25.pkgs.visualstudio.com`, not npmjs, and is pinned only by a SHA-1 integrity hash. It is not shipped, but it compiled what is shipped.

## 9. Alternatives considered
- **A plain `run:` step** that extracts the staged archive and exports `JAVA_HOME`. It needs no third-party code and is the fallback if this approval lapses.
- **`distribution: temurin` with `force-download: true` and `verify-signature: true`.** Stronger than the defaults, but still not bound to the allowlist hash.

## 10. Conditions and residual risk
The conditions are in the frontmatter. Residual risks:
- The release is young, and Code-Review coverage is low.
- `dist/` was not rebuilt from source.
- The compiler provenance noted in §8.

## 11. Not verified
- That `dist/` matches its source, and what the `check-dist` reusable workflow does.
- Callers of `getGitHubHttpHeaders` for other distributions.
- Which `java-version` string formats `jdkfile` accepts; test this in the first CI run.
- How `overwrite-settings: false` behaves on a fresh runner.
