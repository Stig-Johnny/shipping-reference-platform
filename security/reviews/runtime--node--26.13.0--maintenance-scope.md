---
component: "Scope extension: Node.js 26.13.0 darwin-arm64 and its bundled npm 11.20.0, as the controlled maintenance environment for npm lockfiles"
kind: runtime
version: "Node.js 26.13.0 (node-v26.13.0-darwin-arm64.tar.xz), npm 11.20.0 as bundled"
verdict: APPROVE-WITH-CONDITIONS
scope: "The controlled maintenance environment that AGENTS.md and ADR 0007 name, for npm: the owner's MacBook, the verified Node.js 26.13.0 darwin-arm64 build with its bundled npm 11.20.0, a fresh clone of main at a stated commit, an environment with no GitHub or registry token, and npm network access to registry.npmjs.org only. It authorizes lock-only generation of package-lock.json and nothing else; installs everywhere remain npm ci --ignore-scripts"
conditions: ["Host: the owner's MacBook, using only the Node.js 26.13.0 darwin-arm64 tree verified on 2026-11-07 (security/verifications/2026-11-07-frontend-toolchain.md); before each run node --version prints v26.13.0, npm --version 11.20.0 and codesign --verify --strict bin/node succeeds", "Source: a fresh clone of main over HTTPS without credentials into a new empty directory; the commit (git rev-parse HEAD) is stated in the lock pull request", "Environment: npm runs under env -i with only HOME set to a new empty directory, PATH set to the verified Node bin directory followed by /usr/bin:/bin, and TMPDIR; no other variable, so no token, proxy, npm_config_* or NODE_OPTIONS can reach it", "The only npm command: npm install --package-lock-only --ignore-scripts --no-audit --no-fund, run at the root of the clone with --registry=https://registry.npmjs.org/, --userconfig and --globalconfig set to empty files, --cache and --logs-dir in new empty directories, --no-update-notifier and --loglevel=http", "Network: every request line in the kept http log names https://registry.npmjs.org/; any other host voids the run", "After the run: no node_modules directory exists, and git status shows only package-lock.json changed besides the manifests the lock pull request adds", "Before the lock pull request: every packages entry of package-lock.json is compared with the allowlist, field by field: the version has an approved npm entry, resolved equals that entry's artifact URL exactly, and integrity equals the sha512 that its conditions or review record give; an entry without a reviewed counterpart, or any mismatch, stops the pull request; java tools/policy/PolicyCheck.java runs clean on the clone", "The lock pull request is its own pull request: the manifests and package-lock.json only, with the commit, the versions, the command, the environment check and the host summary of the http log in its body", "Installs anywhere, CI and local hosts included, remain npm ci --ignore-scripts from the committed lockfile; nothing generated here is installed or run in this environment", "npm's licence is Artistic-2.0: accepted only as an unmodified build tool that is never distributed (the owner's explicit decision, security/AGENTS.md)", "Expires with the Node.js 26.13.0 approvals (2026-11-25)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-07"
expiresAt: "2026-11-25"
---

# Scope extension: the controlled maintenance environment for npm lockfiles

**Not a new review.** This record extends two reviews to one more use:

- `runtime--node-darwin-arm64--26.13.0.md`, the darwin-arm64 archive for local build hosts, which already covers the
  npm 11.20.0 it bundles;
- `runtime--node--26.13.0.md`, the linux-x64 archive for CI, for npm 11.20.0 itself (its section 8).

Their provenance, maintenance, licence and vulnerability findings stand. What is new: the definition of "the
controlled maintenance environment" that `AGENTS.md` and ADR 0007 name but never defined, and the one npm command it
may run.

**Method.** On 2026-11-07 I read:

- npm's own definitions of the options used here, at tag `v11.20.0`
  (`workspaces/config/lib/definitions/definitions.js` and `docs/lib/content/configuring-npm/npmrc.md` in npm/cli);
- the verification of the Node.js tree (`security/verifications/2026-11-07-frontend-toolchain.md`);
- advisories for `npm@11.20.0` in OSV, GitHub's advisory database and deps.dev.

Nothing was installed and npm was not run.

## 1. Verdict

**APPROVE-WITH-CONDITIONS** for lock-only generation. `--package-lock-only` writes a lockfile from the registry's
metadata without creating `node_modules`. With `--ignore-scripts`, no package code runs. The conditions take away
everything else that could leak in or out:

- **Tokens:** an empty environment and an empty home directory, so npm reads no `~/.npmrc` and sees no token.
- **Hosts:** one registry, checked afterwards in the http log.
- **The result:** a field-by-field comparison of the lock with the allowlist before anyone proposes it.

## 2. What it is and why it is needed

ADR 0007 decides that "locks and verification metadata are regenerated only in a separate, controlled maintenance
environment and submitted as a pull request". `AGENTS.md` repeats it, and `security/AGENTS.md` forbids adding lock
entries by hand. For npm, nothing said what that environment is. The workbench's first lockfile (TypeScript 5.9.3, the
three Playwright packages and the two axe packages) needs one. This record defines it:

| Part | What it is |
|---|---|
| Host | The owner's MacBook (macOS 26, Apple silicon) |
| Runtime | `~/.local/node/node-v26.13.0-darwin-arm64`, verified before first use and read-only since (`dr-xr-xr-x`) |
| npm | 11.20.0, the copy bundled in that tree (`lib/node_modules/npm`); never a separately installed npm (npm 12.2.1 is rejected) |
| Source | A fresh clone of `main` at a commit the lock pull request states |
| Environment | `env -i`, with a new empty `HOME`, the verified Node first on `PATH`, then `/usr/bin:/bin` |
| Network | `https://registry.npmjs.org/` only |
| Output | `package-lock.json`, reviewed against the allowlist and proposed in its own pull request |

## 3. Provenance

- **Node.js 26.13.0, darwin-arm64.** The archive's SHA-256 equals the pinned
  `6f5b3187d9df9fc007e881167905bc7d69ad26450ddc377c83d63256a2495b6e`.
  - gpgv checked `SHASUMS256.txt.asc` against the nodejs/release-keys active keyring: VALIDSIG with primary fingerprint
    `C82FA3AE1CBEDC6BE46B9360C43CEC45C17AB93C` (Richard Lau).
  - `bin/node` passes `codesign --verify --strict`, with TeamIdentifier `HX7739G8FX`.
  - Details: the 2026-11-07 verification record.
- **npm 11.20.0.** It arrives inside that archive, so the signed archive hash covers its bytes. It has no npm
  provenance of its own (Linux review, section 8).

## 4. Maintenance and adoption

Carried over: 26.x is Active LTS, and the approvals expire on 2026-11-25 because a HIGH-severity 26.x release is
announced for 2026-11-11. On 2026-11-07 the announcement still gives that date and no CVE IDs
(`https://nodejs.org/en/blog/vulnerability/november-2026-security-releases`). This record expires with them. A newer
Node needs its own review, and then this record names that build instead.

## 5. Known vulnerabilities at this version

- **npm 11.20.0: none on 2026-11-07.** OSV `POST /v1/query` returned no vulns, GitHub's
  `/advisories?ecosystem=npm&affects=npm@11.20.0` returned an empty list, and deps.dev shows `advisoryKeys: []`.
- **Node.js 26.13.0:** as in its reviews: nothing published yet, a HIGH fix pre-announced.

## 6. License

- **Node.js:** MIT, with bundled components under their own licences (unchanged).
- **npm 11.20.0:** **Artistic-2.0** (version document `license`). That is not on the list `security/AGENTS.md` accepts
  without conditions, and the earlier Node reviews did not name it. Here npm is an unmodified build tool that is never
  distributed. The new `bundled` entry for the darwin-arm64 copy carries this as a condition for the owner's explicit
  decision.

## 7. Execution privileges

- **What the command does.** npm's definition of `package-lock-only`: "the current operation will only use the
  `package-lock.json`, ignoring `node_modules`". So npm resolves the dependency tree from registry metadata and writes
  `package-lock.json`. It creates no `node_modules`, so no package's code is extracted or run.
- **The flags.**
  - `--ignore-scripts` stops lifecycle scripts in any case.
  - `--no-audit` stops the audit submission to the registry: by default `audit` is true and "submit[s] audit reports
    alongside the current npm command".
  - `--no-fund` drops the funding message.
  - `--no-update-notifier` stops the version check.
- **Where npm reads configuration.** From the command line, `npm_config_*` variables, the project `.npmrc`, the user
  `~/.npmrc`, the global `$PREFIX/etc/npmrc` and npm's built-in file (`npmrc.md`).
  - `env -i` removes every variable.
  - The empty `HOME`, `--userconfig` and `--globalconfig` remove the files.
  - The fresh clone has no project `.npmrc` unless the repository commits one, which is then reviewed in a pull
    request.
  - The registry and the cache are set explicitly, so no stale or poisoned cache entry is used.
- **What still runs.** Node.js and npm themselves, as the owner's user, with network access. `env -i` keeps the
  owner's credentials out of npm's reach through variables and configuration. It does not sandbox npm from the home
  directory's files; a compromised npm would still have the user's file access.
- **The resolved closure.** The six packages above declare no install scripts and no optional platform packages.
  `@axe-core/playwright` asks for `axe-core` `~4.14.0`, the one range in the closure. On 2026-11-07, 4.14.0 is the only
  stable 4.14.x, but a later 4.14.x would be resolved instead. The comparison condition catches it, and the manifest
  should pin it (an `overrides` entry or a direct exact dependency).

## 8. Direct dependencies as published

Unchanged: Node.js bundles V8 14.6.202.34, libuv 1.52.1, zlib 1.3.2.1-motley, OpenSSL 3.5.9 and npm 11.20.0.
The darwin-arm64 copy of npm had no allowlist entry of its own; this record adds one (kind `bundled`).

## 9. Alternatives considered

- **Generating the lock in CI.** ADR 0007 forbids it: CI never regenerates locks.
- **A container.** It adds an image and an engine to review, and the MacBook's engine is not usable today (the
  attended-demo record).
- **Writing the lock by hand.** `security/AGENTS.md` forbids it.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter. The run, as a sketch for the lock pull request to follow (`$NODE` is the verified
tree):

```bash
W=$(mktemp -d) && mkdir "$W/home" "$W/cache" "$W/logs" && : > "$W/empty-npmrc"
env -i HOME="$W/home" PATH=/usr/bin:/bin GIT_CONFIG_NOSYSTEM=1 GIT_TERMINAL_PROMPT=0 \
  git clone https://github.com/Stig-Johnny/shipping-reference-platform.git "$W/repo"
git -C "$W/repo" rev-parse HEAD                     # stated in the lock pull request
cd "$W/repo"   # the manifests of the lock pull request are added here first
env -i HOME="$W/home" PATH="$NODE/bin:/usr/bin:/bin" TMPDIR="$W" \
  npm install --package-lock-only --ignore-scripts --no-audit --no-fund \
  --registry=https://registry.npmjs.org/ --userconfig="$W/empty-npmrc" --globalconfig="$W/empty-npmrc" \
  --cache="$W/cache" --logs-dir="$W/logs" --no-update-notifier --loglevel=http 2> "$W/npm-http.log"
test ! -e node_modules && git status --porcelain
```

**The lock entries this review set expects** (verified against the downloaded tarballs on 2026-11-07):

| Lock path | Version | `resolved` | `integrity` |
|---|---|---|---|
| `node_modules/typescript` | 5.9.3 | `https://registry.npmjs.org/typescript/-/typescript-5.9.3.tgz` | `sha512-jl1vZzPDinLr9eUt3J/t7V6FgNEw9QjvBPdysz9KfQDD41fQrC2Y4vKQdiaUpFT4bXlb1RHhLpp8wtm6M5TgSw==` |
| `node_modules/@playwright/test` | 1.65.0 | `https://registry.npmjs.org/@playwright/test/-/test-1.65.0.tgz` | `sha512-iGURLGuKDLakSEl7Pgfv8h7f+XLT+V861Wfhz0zlEl5nyFuKUopdkFBPKqTllTUp06aOwPuH8iuUEb3yp3MpbQ==` |
| `node_modules/playwright` | 1.65.0 | `https://registry.npmjs.org/playwright/-/playwright-1.65.0.tgz` | `sha512-F3AMWcR44qMBt4kq+6Kb6mX7bjGXshktKFOPULd7reED6ZJWevjbNRqysP7q+AATE9kdPEZBibex1BYsvpVoeA==` |
| `node_modules/playwright-core` | 1.65.0 | `https://registry.npmjs.org/playwright-core/-/playwright-core-1.65.0.tgz` | `sha512-vNSKgH6YqtN7cdT/L334zo0a+YwkrpicyrHJlWWPIm/Q+Nn2SWInil01oT0mbu96U4GgoRtCYQD9CzJQgju+lA==` |
| `node_modules/@axe-core/playwright` | 4.14.0 | `https://registry.npmjs.org/@axe-core/playwright/-/playwright-4.14.0.tgz` | `sha512-SGUhG5PIHd2eMdpgBF2PyQLOhX+qyDR2PT4cmoS4Qk2ak+jX/rrSvst9l0PGv5WTHolKpUMvVmoHpr4cZgf1RQ==` |
| `node_modules/axe-core` | 4.14.0 | `https://registry.npmjs.org/axe-core/-/axe-core-4.14.0.tgz` | `sha512-9WTZxEjsZ7b13TH8JPmbV2z8CHbl80/2hm3XPEG4JgNdQLK81IBRXmSxHfMAOkSqQeRxT/0dwNDz2GOm3zzpcQ==` |

Apart from the root and its workspace links, any other `packages` entry stops the lock pull request.

**Residual risk.**

- **A personal laptop, not a disposable host.** `env -i` keeps tokens out of npm's environment, not out of reach of
  the user's files.
- **One maintainer account.** npm 11.20.0 rests on the Node release signature, and the metadata it resolves rests on
  the registry's TLS. The comparison with integrity values recorded in reviews from downloaded tarballs is what
  catches a substituted package.
- **Checked by agents.** The comparison and the PolicyCheck run are done by AI agents. The owner accepts the result in
  the lock pull request.

## 11. Not verified

- **npm's network behaviour** with `--package-lock-only` was read in its definition and will be shown by the first
  run's http log, not by running it here.
- **Whether the macOS `env -i` shell** leaves any variable set by the login session. The first run records
  `env -i … /usr/bin/env` output (variable names only).
- **The owner's MacBook beyond the Node tree:** FileVault, other software running as the same user.
