---
component: "actions/configure-pages"
kind: github-action
version: "6.0.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "CI only: one step in the Pages workflow (push to main), pinned as actions/configure-pages@45bfe0192ca1faeb007ade9deae92b16b8254a0d, to read the site URL of an already-enabled Pages site"
conditions: [
  "uses: actions/configure-pages@45bfe0192ca1faeb007ade9deae92b16b8254a0d (full SHA; never the v6 or v6.0.0 tag)",
  "enablement stays unset or 'false'; OpenTofu enables Pages (build_type = workflow). No token other than the default github.token is ever passed",
  "static_site_generator and generator_config_file stay unset (those paths rewrite and log config files; not needed for plain HTML)",
  "The job grants the token at most contents: read and pages: read; the step never runs on pull_request, pull_request_target or workflow_run",
  "Re-review when a configure-pages release updates the bundled undici 5.28.4 or @octokit/* packages; this approval lapses 30 days after such a release"
]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# actions/configure-pages v6.0.0

**Method.** On 2026-11-06 I read the GitHub API and these files at commit `45bfe019…`: `action.yml`, `package.json`, `package-lock.json`, `src/index.js`, `src/context.js`, `src/api-client.js`, `src/set-pages-config.js`, `src/config-parser.js`, `src/output-pages-base-url.js`, `LICENSE`, `.github/CODEOWNERS` and `check-dist.yml`. For each of the 28 non-dev lockfile entries I read its deps.dev record, and for each advisory found, its OSV record. `dist/` was not read or rebuilt.

## 1. Verdict
**APPROVE-WITH-CONDITIONS.** This is GitHub's own action, at a verified commit. With our inputs it does one thing: it makes one authenticated `GET /repos/{owner}/{repo}/pages` call and exports the site URL. Two code paths do more, so conditions keep them off. `enablement` creates a Pages site using an admin token. `static_site_generator` rewrites the generator's config file. The bundled `undici` 5.28.4 carries 18 advisories and three `@octokit` packages carry ReDoS advisories. Each of them needs a hostile server or attacker-controlled input, and this action talks only to the GitHub API. **It is optional.** With the default Pages URL, the base path is known in advance, so dropping this step is a valid choice (§9).

## 2. What it is and why it is needed
It reads the Pages configuration and outputs `base_url`, `origin`, `host` and `base_path`. The design requires reviewed, SHA-pinned `configure-pages`, `upload-pages-artifact` and `deploy-pages`, with `enablement = false` because OpenTofu owns enablement (`pages-design.md` §4, §5; `v01-design.md` §3).

## 3. Provenance
- **Publisher.** GitHub, org `actions`. The repo was created 2022-07-13, is MIT-licensed and is not archived ([repo](https://api.github.com/repos/actions/configure-pages)). CODEOWNERS is `* @actions/pages` ([CODEOWNERS](https://raw.githubusercontent.com/actions/configure-pages/45bfe0192ca1faeb007ade9deae92b16b8254a0d/.github/CODEOWNERS)).
- **Tag.** `v6.0.0` is a lightweight tag on `45bfe0192ca1faeb007ade9deae92b16b8254a0d` ([ref](https://api.github.com/repos/actions/configure-pages/git/ref/tags/v6.0.0)). The floating [v6](https://api.github.com/repos/actions/configure-pages/git/ref/tags/v6) tag points at the same commit today.
- **Commit.** A merge of PR #186 ("upgrade to node 24"), authored by `tsusdere` and committed by `web-flow` at 2026-03-24T23:32:07Z. It is "verified" with reason `valid`, which shows GitHub made the merge, not who wrote the code. It changes only `.node-version` (20.10.0 → 24.4.0) and `action.yml` (`node20` → `node24`) ([commit](https://api.github.com/repos/actions/configure-pages/commits/v6.0.0)).
- **Release.** Published 2026-03-25T17:00:49Z by `github-actions[bot]`, with `"immutable":false` and no assets ([release](https://api.github.com/repos/actions/configure-pages/releases/latest)). The release body's compare link points at `v5.0.0...v5.0.1`, which is a cosmetic error.
- **Build check.** [check-dist.yml](https://raw.githubusercontent.com/actions/configure-pages/45bfe0192ca1faeb007ade9deae92b16b8254a0d/.github/workflows/check-dist.yml) runs `npm ci && npm run prepare` (ncc) and fails if `git diff dist/` is not empty. It runs with `contents: read`, and its own actions are pinned by tag.
- **Signing.** There are no signed releases and no provenance.

## 4. Maintenance and adoption
- **Releases:** v5.0.0 on 2024-03-30, then v6.0.0 on 2026-03-25. The last push was 2026-04-02 ([releases](https://api.github.com/repos/actions/configure-pages/releases?per_page=8)). Cadence is low: a runtime bump every one to two years.
- **Adoption:** 250 stars, 76 forks, 25 open issues.
- **OpenSSF Scorecard 6.1**, via [deps.dev](https://api.deps.dev/v3/projects/github.com%2Factions%2Fconfigure-pages). The scan date is 2026-08-24, at the v6.0.0 commit itself.
  - Strong: Code-Review 10, Dangerous-Workflow 10, Binary-Artifacts 10, Security-Policy 9, Branch-Protection 8, SAST 7.
  - Weak: Maintained 0, Token-Permissions 0, Pinned-Dependencies 6 (0 of 18 GitHub-owned actions are hash-pinned).
  - api.securityscorecards.dev was not reachable from this session.

## 5. Known vulnerabilities at 6.0.0
**The action itself: none found on 2026-11-06.** The [GitHub Advisory Database](https://api.github.com/advisories?ecosystem=actions&affects=actions/configure-pages) and [repository advisories](https://api.github.com/repos/actions/configure-pages/security-advisories) both return `[]`. OSV's POST query was not available to me.

**Bundled packages: 6 of the 28 non-dev lockfile entries have advisories on deps.dev** (`https://api.deps.dev/v3/systems/npm/packages/<name>/versions/<v>`; details at `https://osv.dev/vulnerability/<ID>`):
- **`undici` 5.28.4: 18 advisories.**
  - HIGH: GHSA-2m5v-pch6-74hx (TLS bypass, needs a function-valued `tls` option) and GHSA-qrxm-fqhp-jwvm (TLS 1.2 renegotiation DoS).
  - HIGH, WebSocket client only: GHSA-v9p9-hfj2-hcw8, GHSA-vrm6-8vpv-qv8q and GHSA-vxpw-j846-p89q.
  - Moderate or low: GHSA-c76h-2ccp-4975, GHSA-cxrh-j4jr-qwg3, GHSA-g9mf-h72j-4rw9, GHSA-2mjp-6q6p-2qxm, GHSA-4992-7rv2-5pvq, GHSA-8xcm-r25x-g524, GHSA-9fm3-97px-rjf6, GHSA-m8rv-5g2x-5cg5, GHSA-p88m-4jfj-68fv, GHSA-v3r7-h72x-cjcm, GHSA-35p6-xmwp-9g52, GHSA-g8m3-5g58-fq7m and GHSA-r53p-7pc4-xj5r.
  - 6.29.1 fixes all of them.
- **`@octokit` ReDoS:** `@octokit/plugin-paginate-rest` 9.2.1 has [GHSA-h5c3-5r3r-rr8q](https://osv.dev/vulnerability/GHSA-h5c3-5r3r-rr8q), `@octokit/request` 8.2.0 has [GHSA-rmvr-2pp2-xj38](https://osv.dev/vulnerability/GHSA-rmvr-2pp2-xj38), and `@octokit/request-error` 5.0.1 has [GHSA-xx4v-prfh-6cgc](https://osv.dev/vulnerability/GHSA-xx4v-prfh-6cgc). All three are moderate, and each needs a crafted `link` or `authorization` header.
- **`@fastify/busboy` 2.1.1:** GHSA-x8mw-p69m-v3mx and GHSA-gxm5-99cw-xjw9. Both affect multipart parsing.
- **`uuid` 8.3.2:** [GHSA-w5hq-g745-h8pq](https://osv.dev/vulnerability/GHSA-w5hq-g745-h8pq). It affects v3/v5/v6 with a caller-supplied buffer. deps.dev also marks this version deprecated.

**Reachability: reasoned from `src/`, not traced in `dist/`.**
- The only network peer is `GITHUB_API_URL`, over TLS, using the job token.
- The source has no WebSocket, multipart, custom `tls` option or pagination call.
- An exploit would need GitHub's own API to send hostile responses.

## 6. License
MIT, "Copyright (c) 2021 GitHub, Inc." ([LICENSE](https://raw.githubusercontent.com/actions/configure-pages/45bfe0192ca1faeb007ade9deae92b16b8254a0d/LICENSE)). The bundled packages are MIT, ISC, Apache-2.0 or BSD-2-Clause (espree), per deps.dev.

## 7. Execution privileges
It runs in CI as a `node24` action with `dist/index.js` as main, and has no pre or post step ([action.yml](https://raw.githubusercontent.com/actions/configure-pages/45bfe0192ca1faeb007ade9deae92b16b8254a0d/action.yml)).
- **Network.** Through `github.getOctokit(token)` ([api-client.js](https://raw.githubusercontent.com/actions/configure-pages/45bfe0192ca1faeb007ade9deae92b16b8254a0d/src/api-client.js)):
  - It calls `GET /repos/{o}/{r}/pages`.
  - With `enablement: true` only, a failed GET is followed by `POST /repos/{o}/{r}/pages` with `build_type: 'workflow'`. That needs a PAT or App token with administration write. **Condition: never.**
  - With `enablement: false`, a failed GET fails the step.
- **Token.** The `token` input defaults to `${{ github.token }}` ([context.js](https://raw.githubusercontent.com/actions/configure-pages/45bfe0192ca1faeb007ade9deae92b16b8254a0d/src/context.js)).
- **Files written:**
  - `core.exportVariable('GITHUB_PAGES','true')` writes to `$GITHUB_ENV` ([index.js](https://raw.githubusercontent.com/actions/configure-pages/45bfe0192ca1faeb007ade9deae92b16b8254a0d/src/index.js)).
  - The four outputs go to `$GITHUB_OUTPUT` ([output-pages-base-url.js](https://raw.githubusercontent.com/actions/configure-pages/45bfe0192ca1faeb007ade9deae92b16b8254a0d/src/output-pages-base-url.js)).
  - Only when `static_site_generator` is set:
    - It finds or creates `./{next,nuxt,gatsby,svelte}.config.{js,cjs,mjs}`.
    - It parses the file with `espree`, splices values into it and overwrites it with `fs.writeFileSync`.
    - It logs the whole file before and after.
    - Sources: [set-pages-config.js](https://raw.githubusercontent.com/actions/configure-pages/45bfe0192ca1faeb007ade9deae92b16b8254a0d/src/set-pages-config.js) and [config-parser.js](https://raw.githubusercontent.com/actions/configure-pages/45bfe0192ca1faeb007ade9deae92b16b8254a0d/src/config-parser.js). **Condition: unset.**
- **Not used, per `src/`:** processes, containers, `eval`, and code from the checkout. The config file is parsed, never executed.

## 8. Direct dependencies as published
- **Direct**, from [package.json](https://raw.githubusercontent.com/actions/configure-pages/45bfe0192ca1faeb007ade9deae92b16b8254a0d/package.json): `@actions/core` ^1.10.1, `@actions/github` ^6.0.0, `error-stack-parser` ^2.1.4, `espree` ^10.1.0.
- **Bundled:** the [lockfile](https://raw.githubusercontent.com/actions/configure-pages/45bfe0192ca1faeb007ade9deae92b16b8254a0d/package-lock.json) (v2) has 28 non-dev entries, all bundled into `dist/index.js` and fixed by the SHA pin. They include:
  - `@actions/http-client` 2.2.1
  - `@octokit/core` 5.1.0 and `@octokit/request` 8.2.0
  - `undici` 5.28.4 (**flagged**)
  - `acorn` 8.12.1
  - `tunnel` 0.0.6
  - `uuid` 8.3.2
- Nothing calls for a separate review beyond the undici re-review condition.

## 9. Alternatives considered
- **Omit the step.** With plain HTML and the default URL, `base_path` is the repository name, and nothing in our build needs `GITHUB_PAGES`. This removes the component entirely. I recommend this if the build does not use the outputs.
- **`gh api repos/{o}/{r}/pages` in a `run:` step.** It makes the same call with no bundled npm tree.

## 10. Conditions and residual risk
The conditions are in the frontmatter. Residual risks:
- The bundled `undici` and `@octokit` advisories, which were not traced in `dist/`.
- `dist/` was not rebuilt from source.
- The low maintenance cadence (Scorecard Maintained 0).

## 11. Not verified
- That `dist/index.js` matches `src/`.
- The `src/blank-configurations/*` templates, which are only used on the static-site-generator path.
- The minimum token permission `GET /pages` needs (I expect `pages: read`). If it is insufficient, the step fails closed.
- The identity behind the web-flow signature.
- Whether an immutable action package exists for this release.
