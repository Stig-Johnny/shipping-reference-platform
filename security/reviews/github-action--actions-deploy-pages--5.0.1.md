---
component: "actions/deploy-pages"
kind: github-action
version: "5.0.1"
verdict: APPROVE-WITH-CONDITIONS
scope: "CI only: the single step of the Pages workflow's deploy job (push to main, environment github-pages), pinned as actions/deploy-pages@368f82528645a54fb793d4d04e342629a3f51346"
conditions: [
  "uses: actions/deploy-pages@368f82528645a54fb793d4d04e342629a3f51346 (full SHA; never the v5 or v5.0.1 tag)",
  "Only in the deploy job, which has needs: <build job>, environment: github-pages, and permissions exactly { pages: write, id-token: write } (nothing else)",
  "The deploy job has no other step: no checkout, no run:, no other action (any step there could mint OIDC tokens or use the pages: write token)",
  "The workflow triggers only on push to main (workflow_dispatch allowed only if the github-pages environment's OpenTofu-managed branch policy restricts it to main); never pull_request, pull_request_target or workflow_run",
  "It deploys only the github-pages artifact made in the same run by actions/upload-pages-artifact@fc324d3547104276b827a68afc52ff2a11cc49c9 under that review's conditions; artifact_name, token and preview stay at their defaults (preview false)",
  "The workflow sets concurrency: { group: pages, cancel-in-progress: false } so deployments are serialised",
  "The first deployment's log is checked to show oidc_token masked as *** in the logged payload",
  "Re-review when a deploy-pages release updates the bundled undici 5.28.2 or @actions/artifact 2.1.8; this approval lapses 30 days after such a release"
]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# actions/deploy-pages v5.0.1

**Method.** On 2026-11-06 I read the GitHub API and these files at commit `368f8252…`: `action.yml`, `package.json`, `package-lock.json`, `src/index.js`, `src/internal/{deployment,api-client,context}.js`, `README.md`, `LICENSE` and `check-dist.yml`. I read only parts of `dist/index.js` (5.85 M characters), namely the OIDC client and the payload log line. For each of the 180 non-dev lockfile entries I read its deps.dev record, and for each advisory found, its OSV record. `dist/` was not rebuilt.

## 1. Verdict
**APPROVE-WITH-CONDITIONS.** This is GitHub's own action, at a verified commit, and it does very little:
- It lists the current run's artifacts.
- It obtains an OIDC token.
- It asks the Pages API to deploy the artifact by ID, then polls that deployment.

It never downloads or unpacks the artifact, and it runs no processes. The risk is in the grant, not in the code: the job holds `pages: write` and `id-token: write`. The conditions therefore keep the deploy job to this one step, on `main`, behind the environment.

The bundled tree is stale: the lockfile still has `@actions/artifact` 2.1.8 and `undici` 5.28.2. **21 of 180 package versions carry 49 distinct advisories**, one of them CRITICAL (`form-data` 4.0.0). Each of them needs a hostile peer or attacker-supplied input, and I found no such path in what this action calls.

## 2. What it is and why it is needed
It publishes the uploaded site to GitHub Pages. The design calls for a separate deploy job that alone has `pages:write` and `id-token:write`, deploys only the passing main-run artifact, and never deploys PR artifacts (`pages-design.md` §5).

## 3. Provenance
- **Publisher.** GitHub, org `actions`. The repo was created 2021-12-14, is MIT-licensed and is not archived ([repo](https://api.github.com/repos/actions/deploy-pages)).
- **Tag.** `v5.0.1` is a lightweight tag on `368f82528645a54fb793d4d04e342629a3f51346` ([ref](https://api.github.com/repos/actions/deploy-pages/git/ref/tags/v5.0.1)). The floating [v5](https://api.github.com/repos/actions/deploy-pages/git/ref/tags/v5) tag points at the same commit today.
- **Commit.** A merge of PR #444 (backoff and jitter for polling), authored by `yoannchaudet` and committed by `web-flow` at 2026-09-01T21:29:43Z. It is "verified" with reason `valid` ([commit](https://api.github.com/repos/actions/deploy-pages/commits/v5.0.1)). It changes `src/internal/deployment.js` (+30/−3), and `dist/index.js` (+30/−3) gets the same change.
- **Release.** Published 2026-09-01T21:31:19Z by `github-actions[bot]`, with `"immutable":false` and no assets ([release](https://api.github.com/repos/actions/deploy-pages/releases/latest)).
- **Build check.** [check-dist.yml](https://raw.githubusercontent.com/actions/deploy-pages/368f82528645a54fb793d4d04e342629a3f51346/.github/workflows/check-dist.yml) rebuilds with ncc and fails on any `dist/` diff. Its actions are pinned by tag, and it has no `permissions:` block.
- **Signing.** There are no signed releases and no provenance.

## 4. Maintenance and adoption
- **Releases** ([releases](https://api.github.com/repos/actions/deploy-pages/releases?per_page=8)): v4.0.5 2024-03-18, v5.0.0 2026-03-25 (Node 24), v5.0.1 2026-09-01. The last push was 2026-09-07.
- **Adoption:** 967 stars, 145 forks, 61 open issues.
- **OpenSSF Scorecard 5.5**, via [deps.dev](https://api.deps.dev/v3/projects/github.com%2Factions%2Fdeploy-pages). The scan date is 2026-08-24, at `5e98f10c…`, which is the release commit's first parent.
  - Strong: Code-Review 10, Dangerous-Workflow 10, Binary-Artifacts 10, Security-Policy 9, SAST 7.
  - Weak: Maintained 1, Token-Permissions 0, Branch-Protection 1, Pinned-Dependencies 6.

## 5. Known vulnerabilities at 5.0.1
**The action itself: none found on 2026-11-06.** The [GitHub Advisory Database](https://api.github.com/advisories?ecosystem=actions&affects=actions/deploy-pages) and [repository advisories](https://api.github.com/repos/actions/deploy-pages/security-advisories) both return `[]`. OSV's POST query was not available to me.

**Bundled packages: 21 of the 180 non-dev entries have advisories on deps.dev** (`https://api.deps.dev/v3/systems/npm/packages/<name>/versions/<v>`; details at `https://osv.dev/vulnerability/<ID>`):
- **`undici` 5.28.2: 21 advisories.** These are the 18 listed for configure-pages, plus GHSA-3787-6prv-h9w3, GHSA-m4v8-wqvr-p9f7 (Proxy-Authorization kept on cross-origin redirect) and GHSA-9qxr-qj54-h672. The HIGH ones are TLS-option and WebSocket bugs.
- **`form-data` 4.0.0:**
  - **GHSA-fjxv-7rqg-78g4 (CRITICAL).** Predictable multipart boundary; it needs user data in a multipart body and leaked `Math.random()` output.
  - GHSA-hmw2-7cc7-3qxx (HIGH). CRLF in field names.
- **`unzip-stream` 0.3.1: GHSA-6jrj-vc65-c983 (HIGH).** A zip-slip in `Extract()` on an attacker zip.
- **ReDoS and regex DoS on attacker input:**
  - `cross-spawn` 7.0.3: GHSA-3xgq-45jj-v275.
  - `path-to-regexp` 6.2.1: GHSA-9wv6-86v2-598j.
  - `brace-expansion` 1.1.11 and 2.0.1: 8 advisories each.
  - `minimatch` 3.1.2 and 5.1.6 (3 each) and 9.0.4 (4).
  - `glob` 10.3.12: GHSA-3g3m-2fjr-6pw4, and GHSA-5j98-mcp5-4vw2, which affects the CLI only.
- **Other:**
  - `lodash` 4.17.21: GHSA-r5fr-rjxr-66jc (HIGH, `_.template`) and GHSA-xxjr-mmjv-4gpg.
  - `@octokit/plugin-paginate-rest` 9.1.5 and 2.21.3, `@octokit/request` 8.1.6 and 5.6.3, and `@octokit/request-error` 5.0.1 and 2.1.0: the three Octokit ReDoS advisories.
  - `@fastify/busboy` 2.1.0 (2).
  - `uuid` 8.3.2 (1).
  - `yaml` 1.10.2: GHSA-48c2-rrv3-qjmp.

**Reachability: reasoned from `src/`, not traced in `dist/`.**
- The action calls `listArtifacts`, not download. That leaves the `unzip-stream` extraction and the `form-data` and multipart upload paths unused.
- It sends JSON to `GITHUB_API_URL`, and talks to the artifact service at `ACTIONS_RESULTS_URL`.
- The glob, spawn, YAML and router packages arrive through `@actions/artifact`'s protobuf and twirp tooling. I did not establish whether `ncc` bundled them.

## 6. License
MIT, "Copyright (c) 2021 GitHub, Inc." ([LICENSE](https://raw.githubusercontent.com/actions/deploy-pages/368f82528645a54fb793d4d04e342629a3f51346/LICENSE)). The bundled packages are MIT, ISC, Apache-2.0, BSD or BlueOak-1.0.0. deps.dev shows no license for `buffers` or `http-status-messages`.

## 7. Execution privileges
It runs in CI as a `node24` action with `dist/index.js` as main, and has no pre or post step ([action.yml](https://raw.githubusercontent.com/actions/deploy-pages/368f82528645a54fb793d4d04e342629a3f51346/action.yml)). The inputs are `token` (default `github.token`), `timeout` (600000, capped), `error_count`, `reporting_interval`, `artifact_name` (`github-pages`) and `preview` (`false`).

What it does, from [index.js](https://raw.githubusercontent.com/actions/deploy-pages/368f82528645a54fb793d4d04e342629a3f51346/src/index.js), [deployment.js](https://raw.githubusercontent.com/actions/deploy-pages/368f82528645a54fb793d4d04e342629a3f51346/src/internal/deployment.js) and [api-client.js](https://raw.githubusercontent.com/actions/deploy-pages/368f82528645a54fb793d4d04e342629a3f51346/src/internal/api-client.js):
1. **OIDC.** `core.getIDToken()`, with no audience argument, so the default is the owner URL. This is why the job needs `id-token: write`, which "only enables fetching and setting the OIDC token; it does not grant write access to other resources" ([OIDC reference](https://docs.github.com/en/actions/reference/security/oidc)).
2. **Artifact lookup.** `DefaultArtifactClient().listArtifacts()` goes to the artifact service (`ACTIONS_RESULTS_URL` twirp). The action selects by name and fails on 0 or more than 1 match. It warns above 1 GiB. It reads metadata only and never downloads the artifact.
3. **Deploy.** `POST /repos/{o}/{r}/pages/deployments` with the body `{artifact_id, pages_build_version: GITHUB_SHA, oidc_token}`, using the `token`. This is why the job needs `pages: write`. GitHub validates the token's claims server-side ([README](https://raw.githubusercontent.com/actions/deploy-pages/368f82528645a54fb793d4d04e342629a3f51346/README.md)).
4. **Polling and cancel.** It polls `GET …/pages/deployments/{id}` with backoff. It calls `POST …/{id}/cancel` on timeout, after too many errors, or on SIGINT/SIGTERM.
5. **Outputs.** `page_url` and `status`.

**Logging the token.** It logs the whole payload, `oidc_token` included, with `core.info`. In `dist/index.js`, the bundled `OidcClient.getIDToken` calls `core_1.setSecret(id_token)` straight after fetching it, which issues `add-mask`, so the runner should print `***`. I verified this in the code, not in a run log, hence the condition.

**Not used, per `src/`:** `child_process`, `fs` writes, containers, and the checkout.

## 8. Direct dependencies as published
- **Direct**, from [package.json](https://raw.githubusercontent.com/actions/deploy-pages/368f82528645a54fb793d4d04e342629a3f51346/package.json): `@actions/artifact` ^2.1.8, `@actions/core` ^1.10.1, `@actions/github` ^6.0.0, `@octokit/request-error` ^5.0.1, `http-status-messages` ^1.1.0.
- **Bundled:** the [lockfile](https://raw.githubusercontent.com/actions/deploy-pages/368f82528645a54fb793d4d04e342629a3f51346/package-lock.json) (v3) has 180 non-dev entries, fixed by the SHA pin. They include:
  - `@azure/storage-blob` 12.17.0
  - `@azure/core-http` 3.0.4 (deprecated)
  - `@actions/http-client` 2.2.0, with `undici` 5.28.2 (**flagged**)
  - `form-data` 4.0.0 (**flagged**)
  - `unzip-stream` 0.3.1 (**flagged**)
  - The top-level `undici` 6.19.2 is dev-only.

## 9. Alternatives considered
- **Call `POST /pages/deployments` ourselves with `curl` and an OIDC token.** That means re-implementing polling and cancel, with no gain.
- **Legacy branch-based Pages.** That needs `contents: write` on a branch, which is a larger grant.

## 10. Conditions and residual risk
The conditions are in the frontmatter. Residual risks:
- The stale bundled tree.
- A run-scoped OIDC token whose audience is the owner URL. No external OIDC trust should accept the `github-pages` environment's subject.
- Whatever the build job put in the artifact is published as-is.

## 11. Not verified
- That `dist/index.js` matches `src/`. About 3.8 M characters of it were not read.
- The `@actions/artifact` 2.1.8 source.
- Which flagged packages `ncc` actually bundled.
- The masking in a real run log.
- That `listArtifacts()` with no arguments is limited to the current run (inferred from its default).
- The identity behind the web-flow signature.
