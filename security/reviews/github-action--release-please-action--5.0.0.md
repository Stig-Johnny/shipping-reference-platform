---
component: "googleapis/release-please-action@45996ed1f6d02564a971a2fa1b5860e934307cf7"
kind: github-action
version: "5.0.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "CI only: one release job in a dedicated release workflow, on GitHub-hosted runners, triggered by `push` to `main` (and owner `workflow_dispatch`). Not in PR, verification or documentation workflows; not on local hosts."
conditions: ["Referenced only as `googleapis/release-please-action@45996ed1f6d02564a971a2fa1b5860e934307cf7 # v5.0.0` and allowed by that exact SHA in the repository's selected-actions policy; never `@v5`, `@v5.0` or `@v5.0.0`", "Pre-use gate: in the controlled maintenance environment, rebuild `dist/` from that commit (`npm ci --ignore-scripts`, `npm run build`, Node 20 as upstream) and record that `dist/index.js` is byte-identical; until the owner approves that evidence this entry is not effective, and a mismatch turns it into REJECT", "Never triggered by `pull_request`, `pull_request_target` or fork code; the job has no `actions/checkout` step", "Token: a short-lived GitHub App installation token for this repository only, with Contents, Pull requests and Issues write and nothing else, minted by a separately reviewed step; job `permissions: {}`; no PAT; the 'Allow GitHub Actions to create and approve pull requests' setting stays off", "Inputs limited to `token`, `config-file`, `manifest-file` and `target-branch`; `github-api-url`, `github-graphql-url`, `proxy-server`, `repo-url`, `changelog-host` and `fork` keep their defaults", "release-please-config.json uses only the `simple`/`node` release types and generic or JSON updaters; no XML, pom, YAML or TOML updaters", "Release PRs need the required checks and the owner's approval like any PR; a ruleset lets only the release App create `v*` tags"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: googleapis/release-please-action 5.0.0

**Method.** On 2026-11-06 the following were read through WebFetch: the git ref advertisement, the release and commit pages, `action.yml`, `src/index.ts`, the README, CHANGELOG, `package.json`, `package-lock.json` and the workflows at `v5.0.0`, plus deps.dev and OSV for every locked runtime package of note, and the GitHub docs. Nothing was downloaded, built or run. **`dist/index.js`, the code that actually runs, was not read** (§11). `api.github.com` refused the fetches (HTTP 403).

## 1. Verdict
**APPROVE-WITH-CONDITIONS, CI only, with a pre-use gate.**
- **What is in its favour.** The publisher is Google (the googleapis org). The entry point contains no shell or file access. It talks only to the GitHub API.
- **The pinned code carries known advisories.** Per its lockfile, 5.0.0 bundles undici 5.29.0, @xmldom/xmldom 0.8.10 (deprecated on npm), js-yaml 4.1.0, yaml 2.3.4 and diff 5.0.0. Every one of the 38 advisories affects the locked version: 21 HIGH, 13 MODERATE, 4 LOW, no CRITICAL (§5).
- **Reachability, as an inference.** Their attack inputs are malicious XML, YAML, diff text or HTTP peers. On our path, those inputs are our own protected `main` and GitHub's API (§7). So the advisories become reachable only after an attacker could already write to `main`.
- **What the approval rests on.** The bundle was built upstream by workflows that pin no actions by SHA, and we did not inspect it. So the approval rests on the rebuild check (condition 2) and on a token scoped to this one repository.
- **An alternative to weigh.** The owner should consider the dependency-free alternative in §9.

## 2. What it is and why it is needed
It reads conventional commits on `main` and keeps a release PR (version bump and CHANGELOG) open. After merge, it tags the release and creates the GitHub release. The blueprint (§8) says: "Use release-please-action 5.0.0, pinned by SHA". It also enforces "conventional squash titles", which this action consumes.

## 3. Provenance
- **Tag resolution.** From the [git ref advertisement](https://github.com/googleapis/release-please-action.git/info/refs?service=git-upload-pack), read directly:
  - `refs/tags/v5.0.0` is a **lightweight** tag on commit `45996ed1f6d02564a971a2fa1b5860e934307cf7`.
  - The annotated floating tags `v5` and `v5.0` peel to the same commit.
  - `main` has moved on, to `0b6b3fc0186a2f7118bfd88fab9ea481e1839504`.
- **Commit.** `45996ed…` is "chore(main): release 5.0.0 (#1200)" by `yoshi-code-bot`, with parent `a8121b99c9779b09ad890de46417b7cad74eb3a2`. It changes only CHANGELOG, `package.json` and `package-lock.json` ([commit](https://github.com/googleapis/release-please-action/commit/45996ed1f6d02564a971a2fa1b5860e934307cf7)), and shows GitHub's verified signature ([release](https://github.com/googleapis/release-please-action/releases/tag/v5.0.0)).
- **Release.** Published by `github-actions` on 2026-04-22, marked Latest. There is no newer release ([atom](https://github.com/googleapis/release-please-action/releases.atom)).
- **How `dist/` is built** ([release-please.yaml](https://github.com/googleapis/release-please-action/blob/v5.0.0/.github/workflows/release-please.yaml)):
  - `npm ci` and `npm run build` (ncc) run on Node 20.
  - The result arrives as a bot PR, "chore: build dist", opened by `googleapis/code-suggester@v4` with a bot token.
  - **No `uses:` is SHA-pinned.** The workflow uses `actions/checkout@v4`, `setup-node@v4` and the action's own `@main`.
  - The release job puts step outputs into `run:` lines and re-pushes the floating tags.
- **Signing.** There are no signed artifacts, SHA-256 files or attestations: an Action is a git commit. Our pin is the commit SHA.

## 4. Maintenance and adoption
- **Releases.**
  - 4.2.0 (2025-03-07), 4.3.0 (2025-08-20), 4.4.0 (2025-10-23) ([atom](https://github.com/googleapis/release-please-action/releases.atom)).
  - 4.4.1 (2026-02-20) and 5.0.0 (2026-04-22) ([CHANGELOG](https://raw.githubusercontent.com/googleapis/release-please-action/v5.0.0/CHANGELOG.md)).
  - 5.0.0's only breaking change is "upgrade to node24"; it also bumped release-please from 17.3.0 to 17.6.0.
- **Pending.** A Dependabot branch for release-please 17.11.2 is open ([refs](https://github.com/googleapis/release-please-action.git/info/refs?service=git-upload-pack)). The README at v5.0.0 still shows `@v4` in its examples.
- **Usage.** 2,560 stars, 335 forks, 139 open issues ([deps.dev](https://api.deps.dev/v3/projects/github.com%2Fgoogleapis%2Frelease-please-action)).
- **Scorecard: not available.** api.securityscorecards.dev returns 404, and deps.dev holds no scorecard.
- **Security policy.** Reports go to g.co/vulnz; there are no published advisories ([security](https://github.com/googleapis/release-please-action/security)).

## 5. Known vulnerabilities at 5.0.0
- **The action itself: none on 2026-11-06.** The OSV list in the GitHub Actions ecosystem is empty ([OSV](https://osv.dev/list?ecosystem=GitHub+Actions&q=googleapis%2Frelease-please-action)). release-please 17.6.0 has `advisoryKeys: []` ([deps.dev](https://api.deps.dev/v3/systems/npm/packages/release-please/versions/17.6.0)).
- **Bundled packages** ([lock](https://raw.githubusercontent.com/googleapis/release-please-action/v5.0.0/package-lock.json)); none of these entries is `dev`. Every ID was fetched from `api.osv.dev/v1/vulns/<ID>`:

| Package@locked | Pulled in by | OSV advisories affecting it |
|---|---|---|
| undici@5.29.0 | @actions/core → @actions/http-client 2.2.0 | 16: 5 HIGH (e.g. GHSA-2m5v-pch6-74hx, a TLS check bypass when function-valued `tls` options are passed; GHSA-qrxm-fqhp-jwvm, a renegotiation crash; three WebSocket DoS), 8 MODERATE, 3 LOW. Fixed in 6.29.1 / 7.30.1 |
| @xmldom/xmldom@0.8.10 | release-please | 15: 13 HIGH (serialization injection, quadratic parsing, recursion), 2 MODERATE. Fixed in 0.8.15. npm-deprecated: "critical issues" |
| js-yaml@4.1.0 | release-please | 5: 3 HIGH (merge-key / `!!omap` CPU DoS), 2 MODERATE (including GHSA-mh29-5h37-fv8m, prototype pollution). Fixed in 4.3.2 |
| yaml@2.3.4 | release-please | GHSA-48c2-rrv3-qjmp, MODERATE (deep-nesting stack overflow). Fixed in 2.8.3 |
| diff@5.0.0 | code-suggester 5.0.0 | GHSA-73rr-hh4g-fpgx, LOW (parsePatch loop). Fixed in 5.2.2 |

All other locked packages checked return `[]`: @actions/core 1.10.1, @octokit/* (rest 20.1.2, request 8.4.1, graphql 7.1.1), jsonpath-plus 10.3.0, xpath 0.0.34, node-html-parser 6.1.11, semver 7.5.4, http(s)-proxy-agent, @iarna/toml 3.0.0 and the nested diff 8.0.3.

## 6. License
Apache-2.0 ([package.json](https://raw.githubusercontent.com/googleapis/release-please-action/v5.0.0/package.json)). release-please is also Apache-2.0.

## 7. Execution privileges
- **Phase: CI.** It runs as `node24` JavaScript, `dist/index.js` ([action.yml](https://raw.githubusercontent.com/googleapis/release-please-action/v5.0.0/action.yml)), with no `pre` or `post` step. It has the runner's full rights: network, the file system, the job's environment, and the token passed as input.
- **Entry point** ([src/index.ts](https://raw.githubusercontent.com/googleapis/release-please-action/v5.0.0/src/index.ts)):
  - It imports only `@actions/core` and `release-please`.
  - It has no `child_process` and no `fs`.
  - It passes `token`, `apiUrl`, `graphqlUrl` and `proxy` to `GitHub.create`, and the token goes to whatever host `github-api-url` and `github-graphql-url` name. Hence condition 5.
- **What it does with the token.** Through the GitHub API it reads repository files and commits, and it writes branches, commits, PRs, labels, tags and releases.
- **Permissions.** The README asks for `contents: write`, `issues: write` and `pull-requests: write` ([README](https://raw.githubusercontent.com/googleapis/release-please-action/v5.0.0/README.md)).
- **GITHUB_TOKEN versus an App token.** Events from `GITHUB_TOKEN` do not start ordinary workflow runs; PRs it opens get runs only in an approval-required state. An App installation token avoids this ([GitHub docs](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow)). Using `GITHUB_TOKEN` would also need the setting that lets Actions create and *approve* PRs ([settings](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository)), which would weaken owner-only approval. Hence condition 4.
- **Reachability of §5** (inference, not traced in the bundle):
  - The XML and YAML parsers serve release-please's file updaters. Condition 6 keeps those updaters unused, and their inputs would be our own `main` anyway.
  - undici sits behind `@actions/http-client`, and `src/index.ts` calls only `getInput`, `setOutput` and `setFailed`.
  - The worst case for the DoS advisories is a failed release job.

## 8. Direct dependencies as published
- `@actions/core` ^1.10.0 (locked 1.10.1): the Actions toolkit for inputs and outputs. It brings `@actions/http-client`, which brings undici (**flag**).
- `release-please` ^17.6.0 (locked 17.6.0): the release engine. It has 31 direct and 145 indirect npm dependencies ([deps.dev](https://api.deps.dev/v3/systems/npm/packages/release-please/versions/17.6.0:dependencies)). **Flag:** @xmldom/xmldom, js-yaml, yaml, and code-suggester's diff 5.0.0.
- None of these is fetched at run time; they are bundled by ncc into `dist/`. The bundle is the unit we pin and verify (condition 2).

## 9. Alternatives considered
- **A first-party release step.** On merge to `main`, run `gh release create vX.Y.Z --generate-notes`, using GitHub's own release notes and the `gh` CLI preinstalled on the runner (itself runner tooling to inventory). A version bump would be a first-party script, with no third-party JavaScript holding a write token. Recommended if the rebuild gate fails.
- **Manual releases for v0.1.** The demo uses a frozen release (blueprint §9).

## 10. Conditions and residual risk
**Conditions:** as in the frontmatter.

**Residual risk:**
- A 177-package npm tree with 38 known advisories runs with a write token. The token is scoped to one repository and is short-lived.
- The upstream dist pipeline pins no actions by SHA. The rebuild check tests the bundle against its lockfile, not that lockfile's packages.
- Upstream releases are slow, and fixes for the bundled libraries wait on a release after 5.0.0, which will be a new review.
- No Scorecard data.

## 11. Not verified
- **`dist/index.js` was not read.** That means the bundled versions, whether `child_process` is absent, and network targets beyond `src/index.ts`. Condition 2 is the substitute.
- Whether undici, @xmldom/xmldom, js-yaml and yaml are reachable in our configuration. That is inferred from the module structure.
- The commit date, contributor counts and the dist PR's review history (`api.github.com` 403).
- How the App token is minted. It is out of scope and needs its own review.
- OSV's `POST /v1/query` was not used. The osv.dev list, deps.dev and per-ID OSV records stand in.
