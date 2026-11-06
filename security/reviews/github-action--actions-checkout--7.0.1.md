---
component: "actions/checkout"
kind: github-action
version: "7.0.1"
verdict: APPROVE-WITH-CONDITIONS
scope: "CI only: workflow steps of this repository that check out this repository's own source, pinned as actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1"
conditions: [
  "uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 (full SHA; never the v7 or v7.0.1 tag)",
  "persist-credentials: false in every job, so no token is left in RUNNER_TEMP or referenced from .git/config for later steps",
  "The job grants the token at most permissions: contents: read",
  "allow-unsafe-pr-checkout stays false; fork code is never checked out under pull_request_target or workflow_run",
  "ssh-key, submodules and lfs stay unset (code paths not reviewed); runners provide git >= 2.18 so the REST-API archive fallback is not taken",
  "Re-review when an actions/checkout release updates the bundled undici 6.27.0 (11 advisories on deps.dev); this approval lapses 30 days after such a release"
]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# actions/checkout v7.0.1

**Method.** On 2026-11-06 I read the GitHub API and these files at commit `3d3c42e5…`: `action.yml`, `package.json`, `package-lock.json`, `src/git-auth-helper.ts`, `src/git-source-provider.ts`, `src/git-command-manager.ts`, `CHANGELOG.md` and the workflows. I also checked OpenSSF Scorecard, the GitHub Advisory Database, osv.dev and deps.dev. `dist/` was not read or rebuilt.

## 1. Verdict
**APPROVE-WITH-CONDITIONS.** This is GitHub's own action, at a verified commit, and its token handling was read in source. Two things need conditions. First, the default `persist-credentials: true` leaves the workflow token readable to every later step in the job, including the candidate build. Second, the bundled `undici` 6.27.0 carries 11 advisories. I did not trace them, but each needs a hostile server or proxy, a function-valued TLS option, or WebSocket use. Pinning by SHA makes the release's mutability irrelevant.

## 2. What it is and why it is needed
It checks out the repository in CI. The design requires a pinned, reviewed full SHA, and that fork workers receive no persisted checkout credentials (`v01-design.md` §4, §5).

## 3. Provenance
- **Publisher.** GitHub, org `actions`. The repository was created 2019-07-19 and is MIT-licensed ([repo](https://api.github.com/repos/actions/checkout)).
- **Tag.** `v7.0.1` is a lightweight tag on commit `3d3c42e5aac5ba805825da76410c181273ba90b1`. The [git/ref](https://api.github.com/repos/actions/checkout/git/ref/tags/v7.0.1) and [commits](https://api.github.com/repos/actions/checkout/commits/v7.0.1) APIs agree. The floating [v7](https://api.github.com/repos/actions/checkout/git/ref/tags/v7) tag points at the same commit today.
- **Commit.** Authored by Aiqiao Yan at 2026-07-17T18:45:11Z and committed by `web-flow`.
  - It is "verified" with GitHub's key `B5690EEEBB952194`. That shows GitHub created the merge commit, not who wrote the code.
  - It changes only `CHANGELOG.md`, `package.json` and the lockfile. The fixes it ships (#2518, #2521, #2530) landed in earlier commits.
- **Release.** Published 2026-07-20T15:10:05Z by `aiqiaoy`, with `"immutable":false` and no uploaded assets ([release](https://api.github.com/repos/actions/checkout/releases/tags/v7.0.1)).
- **Build check.** `dist/` is built with `ncc`. [check-dist.yml](https://raw.githubusercontent.com/actions/checkout/3d3c42e5aac5ba805825da76410c181273ba90b1/.github/workflows/check-dist.yml) rebuilds it and fails if `git diff dist/` is not empty. That check misses untracked files, and its own actions are pinned by tag.
- **Signing.** There are no signed releases and no provenance.

## 4. Maintenance and adoption
- **Releases** ([releases](https://github.com/actions/checkout/releases); years are inferred because the page omits them):
  - v6.0.0: 20 Nov [2025]
  - v6.0.1: 02 Dec
  - v6.0.2: 09 Jan
  - v6.0.3: 02 Jun
  - v7.0.0: 18 Jun [2026]
  - v7.0.1: 2026-07-20
  - Also on 2026-07-20, the `allow-unsafe-pr-checkout` guard was backported to v2–v6.
- **Adoption.** 9,002 stars and 2,742 forks; last push 2026-10-17. CODEOWNERS is `* @actions/actions-runtime`.
- **OpenSSF Scorecard: 5.8** (2026-11-02, scanned at commit `f548e57e…`, not the tag) ([scorecard](https://api.securityscorecards.dev/projects/github.com/actions/checkout)):
  - Strong: Code-Review, Dangerous-Workflow and SAST at 10, Security-Policy 9.
  - Weak: Branch-Protection 5, Pinned-Dependencies 3, Token-Permissions 0.
  - Maintained is 0, which contradicts the 2026-10-17 push.

## 5. Known vulnerabilities at 7.0.1
**The action itself: none found on 2026-11-06.**
- [GitHub Advisory Database](https://api.github.com/advisories?ecosystem=actions&affects=actions/checkout): `[]`.
- [Repository advisories](https://api.github.com/repos/actions/checkout/security-advisories): `[]`.
- [osv.dev](https://osv.dev/list?ecosystem=GitHub+Actions&q=actions%2Fcheckout): "No results".
- OSV's POST query was not available to me.

**Bundled `undici` 6.27.0: 11 advisories on [deps.dev](https://api.deps.dev/v3/systems/npm/packages/undici/versions/6.27.0).**
- [GHSA-2m5v-pch6-74hx](https://osv.dev/vulnerability/GHSA-2m5v-pch6-74hx), TLS certificate validation bypass, CVSS 7.4. It applies only when code passes a function-valued `tls` option such as `checkServerIdentity`. None of the source files I read does that.
- DoS, CVSS 7.5: GHSA-qrxm-fqhp-jwvm, GHSA-rfgv-xxqx-mfg5, GHSA-xjj6-f33q-929c.
- Lower severity: GHSA-4qv8-2p9j-2685, GHSA-9fm3-97px-rjf6, GHSA-8xcm-r25x-g524, GHSA-r53p-7pc4-xj5r, GHSA-m8rv-5g2x-5cg5, GHSA-v3r7-h72x-cjcm, GHSA-3wwx-pv8p-q78v.
- `undici` 6.29.1 has none of these ([deps.dev](https://api.deps.dev/v3/systems/npm/packages/undici/versions/6.29.1)).
- **Reachability: not traced in `dist/`.** The action talks only to `github-server-url` over TLS.

**Every other bundled package checked has no advisory:** `@actions/*`, `@octokit/request` 10.0.10, `semver` 7.8.4, `tunnel` 0.0.6 and `json-with-bigint` 3.5.8.

## 6. License
MIT, "Copyright (c) 2018 GitHub, Inc. and contributors" ([LICENSE](https://raw.githubusercontent.com/actions/checkout/3d3c42e5aac5ba805825da76410c181273ba90b1/LICENSE)).

## 7. Execution privileges
It runs in CI as a `node24` action, with `dist/index.js` as both main and post step. It runs as the runner user, before the build ([action.yml](https://raw.githubusercontent.com/actions/checkout/3d3c42e5aac5ba805825da76410c181273ba90b1/action.yml)).
- **Processes.** It runs the `git` CLI with `GIT_TERMINAL_PROMPT=0` ([git-command-manager](https://raw.githubusercontent.com/actions/checkout/3d3c42e5aac5ba805825da76410c181273ba90b1/src/git-command-manager.ts)):
  - always: `init`, `remote add`, `config`, `-c protocol.version=2 fetch --no-tags --depth=1`, `checkout --force`
  - when the matching inputs are set: `sparse-checkout`, `lfs`, `submodule`
  - on an existing working directory: `clean -ffdx`, `reset --hard`
- **Network.** GitHub only ([git-source-provider](https://raw.githubusercontent.com/actions/checkout/3d3c42e5aac5ba805825da76410c181273ba90b1/src/git-source-provider.ts)):
  - git over HTTPS using the token
  - REST calls for the default branch, `/hash-algorithm` and commit info
  - a REST tarball download instead of git, if git is missing or older than 2.18
- **Credentials** ([git-auth-helper](https://raw.githubusercontent.com/actions/checkout/3d3c42e5aac5ba805825da76410c181273ba90b1/src/git-auth-helper.ts)):
  - The token is written as `AUTHORIZATION: basic base64(x-access-token:<token>)` into `$RUNNER_TEMP/git-credentials-<uuid>.config`, with no explicit file mode.
  - `.git/config` references that file via `includeIf.gitdir`.
  - With `persist-credentials: false` the file is removed in the main step's `finally`; otherwise it is removed in the post step.
  - SSH keys are written with mode 0600 into `RUNNER_TEMP`.
  - `safe.directory` goes into a temporary `HOME` that is deleted afterwards.
- **Fork-PR guard.** `allow-unsafe-pr-checkout` (default `false`) blocks checking out fork PRs under `pull_request_target` and `workflow_run` ([CHANGELOG](https://raw.githubusercontent.com/actions/checkout/3d3c42e5aac5ba805825da76410c181273ba90b1/CHANGELOG.md)).
- **Not used:** containers, and any token permission beyond reading contents.

## 8. Direct dependencies as published
- **Direct**, from [package.json](https://raw.githubusercontent.com/actions/checkout/3d3c42e5aac5ba805825da76410c181273ba90b1/package.json): `@actions/core` ^3.0.1, `@actions/exec` ^3.0.0, `@actions/github` ^9.1.1, `@actions/io` ^3.0.2, `@actions/tool-cache` ^4.0.0.
- **Bundled:** the [lockfile](https://raw.githubusercontent.com/actions/checkout/3d3c42e5aac5ba805825da76410c181273ba90b1/package-lock.json) resolves 24 non-dev packages. All are bundled into `dist/index.js`, so the pinned SHA fixes them. They include:
  - `@actions/http-client` 4.0.1 and 3.0.2
  - `@octokit/core` 7.0.6 and `@octokit/request` 10.0.10
  - `undici` 6.27.0 (**flagged**, see §5)
  - `tunnel` 0.0.6
  - `semver` 7.8.4

## 9. Alternatives considered
- A `run:` step with `git clone`: we would own the token handling ourselves.
- checkout v6.x: older, and without v7's default fork-PR guard.

## 10. Conditions and residual risk
The conditions are in the frontmatter. Residual risks:
- The bundled `undici` advisories, which were not traced.
- `dist/` was not rebuilt from source.
- With the conditions in place, the token exists on the runner only while the main step runs.

## 11. Not verified
- That `dist/index.js` matches its source.
- `git-directory-helper.ts`, and the SSH and submodule paths.
- Reachability of the `undici` code paths.
- The identity behind the web-flow signature.
- The runner's git version.
