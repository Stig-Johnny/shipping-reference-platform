---
component: "actions/setup-node@949feb2413d6458794dcd2491c4babbbce0c15c1"
kind: github-action
version: "v7.1.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "CI only: the web, contract-e2e and docs-brain jobs on GitHub-hosted ubuntu-24.04 runners, to install Node.js 26.13.0"
conditions: ["uses: actions/setup-node@949feb2413d6458794dcd2491c4babbbce0c15c1 (v7.1.0), never a tag", "with: node-version: 26.13.0 exactly; check-latest false; no node-version-file", "package-manager-cache: false and no cache input (blueprint: no shared caches in v0.1)", "No registry-url, scope, mirror or mirror-token inputs", "Job permissions contents: read; no secrets in the job", "The next step checks sha256 of $(command -v node) against the bin/node hash from the verified official tarball (runtime--node--26.13.0); setup-node itself verifies nothing"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: actions/setup-node v7.1.0

**Method.** Read on 2026-11-06:

- the release pages and the git ref advertisement (`https://github.com/actions/setup-node.git/info/refs?service=git-upload-pack`);
- `action.yml`, `official_builds.ts`, `base-distribution.ts`, `package.json` and `README.md` at the tag;
- the actions/node-versions release and builders;
- the runner-image readme and Scorecard.

api.github.com returned 403, so GitHub's HTML pages were used instead. `dist/` was not read.

## 1. Verdict

**APPROVE-WITH-CONDITIONS**, pinned by full SHA.

- **No integrity check.** setup-node verifies **no checksum or signature**.
- **Not the official file.** It installs **GitHub's repackaged archive** from actions/node-versions, not the
  nodejs.org file whose hash the Node review pins. That repackager checks no upstream checksum either.
- **So the approval depends on a follow-up step** that hashes the installed `node`.
- **Preferred instead:** the direct, hash-checked download in `runtime--node--26.13.0.md`. It needs no action at all,
  which is the policy's "is it needed at all?" lesson.

## 2. What it is and why it is needed

A GitHub-owned action that installs Node.js and puts it on `PATH`. The brief names it as the CI installer for Node
26.13.0 (blueprint §4, §8).

## 3. Provenance

- **Release.** v7.1.0 is "Latest" and an "Immutable release". It was released 08 Oct; the page shows no year, and
  2026 is inferred (https://github.com/actions/setup-node/releases/tag/v7.1.0).
- **Commit.** `949feb2413d6458794dcd2491c4babbbce0c15c1`, Verified, GitHub key `B5690EEEBB952194`.
- **Tags.** `refs/tags/v7.1.0` and the moving `refs/tags/v7` both point at it, with no peeled `^{}` lines, so they are
  **lightweight tags**. `main` is one commit ahead.
- **Runtime.** `runs.using: 'node24'`. Main is `dist/setup/index.js`; post is `dist/cache-save/index.js`
  (`post-if: success()`). `dist/` is an ncc bundle.
- **Download path for 26.13.0** (`official_builds.ts`, `base-distribution.ts`):
  1. tool cache (26.x is not cached on image 20261101.360.1);
  2. else the `actions/node-versions` manifest at `main`, then `downloadTool(download_url)`;
  3. on a miss **or any error**, `https://nodejs.org/dist/v26.13.0/node-v26.13.0-linux-x64.tar.gz`.

  The only "verify" step is a `node --version` string compare.
- **The archive from step 2.** Release `26.13.0-41378513395` (02 Nov) ships `node-26.13.0-linux-x64.tar.gz`. GitHub
  shows its digest as `sha256:a7511cc4a13d9812d011c6a2315dabf4623f6288654085ace80167fb8c7f614b`. The release also has
  a `hashes.sha256` file that setup-node never reads (https://github.com/actions/node-versions/releases/expanded_assets/26.13.0-41378513395).
- **How that archive is built** (`builders/nix-node-builder.psm1`). The builder downloads
  `nodejs.org/download/release/v26.13.0/node-v26.13.0-linux-x64.tar.gz` with no checksum check, adds a `setup.sh` and
  re-archives. Its digest therefore differs from SHASUMS256.

## 4. Maintenance and adoption

- **Releases:** v7.1.0 (Oct), v7.0.0 and v6.5.0 (Jul), v6.4.0 (Apr), v6.3.0 (Mar).
- **Scorecard 6.4** (2026-11-02, https://api.securityscorecards.dev/projects/github.com/actions/setup-node):
  Code-Review 10, Maintained 10, Security-Policy 9, Pinned-Dependencies 5, Binary-Artifacts 9 (`externals/7zr.exe`).
  Token-Permissions 0 (top-level `contents: write` in the release workflow). Branch-Protection 0 (release branches
  unprotected, admins not bound).

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.** "There aren't any published security advisories"
(https://github.com/actions/setup-node/security/advisories). OSV's GitHub Actions ecosystem was not queried.

## 6. License

MIT, per Scorecard's License check (10).

## 7. Execution privileges

- **When it runs:** CI setup.
- **Runtime:** a node24 process with the job's `GITHUB_TOKEN`. The `token` input defaults to `github.token`, which it
  uses to read the manifest.
- **What it changes on the runner:**
  - downloads and extracts into the tool cache and edits `PATH`;
  - with `registry-url`, writes `.npmrc`;
  - caching restores and saves `~/.npm`, which happens automatically for npm projects unless
    `package-manager-cache: false` (README).
- **Credential leak path.** `mirror-token`, if set, is sent as `Authorization` even when no mirror is set, so to
  nodejs.org.

## 8. Direct dependencies as published

Bundled into `dist/` (https://raw.githubusercontent.com/actions/setup-node/v7.1.0/package.json): GitHub's toolkit
(`@actions/cache` ^6.3.0, `core` ^3.0.1, `exec` ^3.0.0, `github` ^9.1.1, `glob` ^0.7.0, `http-client` ^4.0.1, `io`
^3.0.2, `tool-cache` ^4.0.0), `semver` ^7.8.5 for version ranges and `smol-toml` ^1.7.1 for mise.toml, with one
override, `brace-expansion` 5.0.12. Pinning the commit pins the bundle.

## 9. Alternatives considered

- **Direct `curl` + `sha256sum --check` of the official tarball:** preferred. No action, and the publisher's own hash.
- **Node 24 from the runner's tool cache:** not the blueprint's pin.

## 10. Conditions and residual risk

```yaml
- uses: actions/setup-node@949feb2413d6458794dcd2491c4babbbce0c15c1 # v7.1.0
  with: { node-version: 26.13.0, package-manager-cache: false }
- run: echo "${NODE_BIN_SHA256}  $(command -v node)" | sha256sum --check --strict -
```

`NODE_BIN_SHA256` is the SHA-256 of `bin/node` from the gpgv-verified official tarball. It is computed once in the
lock-maintenance environment.

**Residual risk.**

- The trust root for the archive is GitHub's repackager. The post-install hash is what binds it to the signed release.
- The action runs with the job token.
- It can fall back silently to nodejs.org.

## 11. Not verified

- **The bundles.** `dist/` was not compared with `src/`.
- **`@actions/tool-cache` internals:** hosts, redirects, TLS.
- **The binary itself.** Whether the repackaged `bin/node` is byte-identical; the builder code implies it.
- **The post step.** Whether it does nothing with caching off.
- **`hashes.sha256`.** Its contents.
