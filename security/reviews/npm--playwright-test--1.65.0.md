---
component: "@playwright/test (with playwright and playwright-core)"
kind: npm
version: "1.65.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "devDependency of apps/shipping-web: E2E and accessibility tests in the contract-e2e job on GitHub-hosted ubuntu-24.04, against the locally started application only; installed by npm ci --ignore-scripts from the reviewed lockfile"
conditions: ["Lockfile pins @playwright/test, playwright and playwright-core 1.65.0 with the integrity values in section 3", "No `playwright install`, `install-deps`, `--with-deps` or branded channels (chrome, msedge) in CI; PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1; the browser comes only via browser-binary--chrome-headless-shell--158.0.8127.18 and launchOptions.executablePath", "No PLAYWRIGHT_DOWNLOAD_HOST or PLAYWRIGHT_*_DOWNLOAD_HOST overrides", "Tests navigate only to the application started by webServer on 127.0.0.1; webServer.command is a CODEOWNERS-protected repository command", "No --ui, show-report or trace-viewer servers in CI (reports are uploaded as artifacts); no video recording (it needs the unreviewed ffmpeg download)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: @playwright/test 1.65.0 (plus playwright 1.65.0 and playwright-core 1.65.0)

**Method.** Read on 2026-11-06:

- registry docs for all three packages, deps.dev, osv.dev (GHSA-7mvr-c777-76hp);
- `browsers.json`, `registry/index.ts`, `browserFetcher.ts`, `oopDownloadBrowserMain.ts`, `dependencies.ts`,
  `packages/utils/network.ts` and `packages/utils/third_party/extractZip.ts` at tag v1.65.0;
- playwright.dev (`/docs/browsers`, `/docs/test-webserver`, `/docs/api/class-browsertype`).

Nothing was downloaded or run.

## 1. Verdict

**APPROVE-WITH-CONDITIONS.** All three packages were built from the same commit through trusted publishing, with
verified provenance, and none has install scripts. The risks are in what the runner **does**:

- **Browser downloads.** They are not hash-checked, and the downloader follows redirects to any host, including
  https→http.
- **`install-deps`** runs `apt-get` as root through `sudo`.
- **Chromium** launches without its sandbox by default.

The conditions remove the downloads from Playwright entirely and confine the browser to our own app.

## 2. What it is and why it is needed

The E2E and accessibility runner ("E2E/accessibility: Quote→book→label, denial, flag states", blueprint §4, §7).

## 3. Provenance

| Package | Published | Integrity | Unpacked | Dependencies |
|---|---|---|---|---|
| `@playwright/test` | 2026-11-05T18:30:18Z | `sha512-iGURLGuKDLakSEl7Pgfv8h7f+XLT+V861Wfhz0zlEl5nyFuKUopdkFBPKqTllTUp06aOwPuH8iuUEb3yp3MpbQ==` | 28,544 B | `playwright` 1.65.0 |
| `playwright` | 2026-11-05T18:28:14Z | `sha512-F3AMWcR44qMBt4kq+6Kb6mX7bjGXshktKFOPULd7reED6ZJWevjbNRqysP7q+AATE9kdPEZBibex1BYsvpVoeA==` | 5,063,827 B | `playwright-core` 1.65.0 |
| `playwright-core` | 2026-11-05T20:17:40Z | `sha512-vNSKgH6YqtN7cdT/L334zo0a+YwkrpicyrHJlWWPIm/Q+Nn2SWInil01oT0mbu96U4GgoRtCYQD9CzJQgju+lA==` | 13,969,978 B | none (third-party code vendored) |

- **Publisher.** All three show `_npmUser` "GitHub Actions" (trustedPublisher `github`).
- **npm maintainers:** pavelfeldman, yurys, dgozman-ms, microsoft-oss-releases, microsoft1es.
- **SLSA provenance.** microsoft/playwright commit `a1f35e1955976e84411f9c10358228a80b763ebe`, `verified: true`
  (deps.dev, all three).
- **Install scripts:** none. There are no `scripts` at all in `playwright` or `playwright-core`, and `@playwright/test`
  has `{}`.
- **Native binaries:** none in the packages. Browsers are fetched separately (§7).

## 4. Maintenance and adoption

- **Releases.** 1.65.0 is `latest`. The previous stable was 1.64.1 (2026-10-14), and 1.66 alphas are already
  published (https://api.deps.dev/v3/systems/npm/packages/playwright).
- **Scorecard 5.6** (2026-08-24) for microsoft/playwright, 98,981 stars:
  - Code-Review 10, Maintained 10
  - Token-Permissions 0, Branch-Protection 0
  - Pinned-Dependencies 5, Binary-Artifacts 7

## 5. Known vulnerabilities at this version

**None found on 2026-11-06.** deps.dev has `advisoryKeys: []` for all three.

GHSA-7mvr-c777-76hp is the browser install scripts' `curl -k`. It affects `playwright` before 1.55.1, so 1.65.0 is
not affected (https://api.osv.dev/v1/vulns/GHSA-7mvr-c777-76hp). osv.dev list pagination failed, so deps.dev is the
source for anything older.

## 6. License

Apache-2.0.

## 7. Execution privileges

- **Test time.**
  - It executes `playwright.config.*` and test files in Node workers.
  - It spawns browser processes. `chromiumSandbox` defaults to `false` ("Enable Chromium sandboxing. Defaults to
    `false`", https://playwright.dev/docs/api/class-browsertype).
  - `webServer.command` is a "Shell command" whose process inherits `process.env` (https://playwright.dev/docs/test-webserver).
- **`playwright install`, from `registry/index.ts`, `browserFetcher.ts` and `network.ts`.**
  - It downloads zips into `~/.cache/ms-playwright` and validates only HTTP 200 and `content-length`. **There is no
    hash or signature check.**
  - It follows any 3xx `Location`, with no limit and no host or protocol check.
  - Download-host environment overrides replace every mirror, whatever the scheme.
  - `extractZip` rejects parent-directory escapes but creates symlinks without checking their targets.
  - Installing `chromium` or the headless shell always adds **ffmpeg** (`resolveBrowsers`).
- **`install-deps` / `--with-deps`** run `sudo -- sh -c "apt-get update && apt-get install -y --no-install-recommends …"`.
  On ubuntu24.04 that is 21 packages for chromium (`dependencies.ts`, `nativeDeps.ts`).
- **Servers.** UI mode, `show-report` and the trace viewer start HTTP servers.

## 8. Direct dependencies as published

- **The chain:** `@playwright/test` → `playwright` 1.65.0 (exact) → `playwright-core` 1.65.0 (exact).
- **No other npm dependencies.** `playwright-core` vendors its third-party code: `yauzl`, `proxy-from-env` and
  `https-proxy-agent` are referenced in the source read. **The tarball audit should inventory these bundled
  modules**, because the lockfile cannot see them.
- **Engines:** `node >=20`.

## 9. Alternatives considered

- **Cypress:** larger, and it has its own binary download.
- **WebdriverIO:** needs drivers.

Playwright is the blueprint's choice, and axe integrates with it.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk.**

- **Release age.** The release was one day old at review.
- **No Chromium sandbox.** Chromium runs unsandboxed, so it must only ever load our own app.
- **Vendored code.** It is invisible to lockfile-based checks.

## 11. Not verified

- **Tarball contents.** The tarballs and the vendored module list were not read.
- **TLS for downloads.** No `rejectUnauthorized` is set in the download files read, so Node's default certificate
  verification applies. The remaining TLS code paths were not read.
- **Runtime libraries.** Whether the runner image already has every library Chromium needs without `install-deps`.
