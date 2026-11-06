---
component: "Chrome Headless Shell (Playwright chromium-headless-shell, revision 1254)"
kind: browser-binary
version: "158.0.8127.18"
verdict: APPROVE-WITH-CONDITIONS
scope: "CI only: GitHub-hosted ubuntu-24.04 x64, headless E2E/accessibility tests of the locally started application, launched by @playwright/test 1.65.0 via executablePath"
conditions: ["The workflow downloads https://cdn.playwright.dev/builds/cft/158.0.8127.18/linux64/chrome-headless-shell-linux64.zip with curl --proto '=https' (no redirects to other schemes) and checks sha256sum --check against a SHA-256 pinned in the workflow", "That SHA-256 is recorded once in the lock-maintenance environment and, where Google's Chrome for Testing download for 158.0.8127.18 is available, confirmed byte-identical to it; until it is recorded this approval is not usable", "Extract into a fresh directory; pass chrome-headless-shell-linux64/chrome-headless-shell as launchOptions.executablePath; PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1", "Navigate only to the local application (the browser runs without Chromium's sandbox by Playwright default)", "Not approved: the full chromium (Chrome for Testing) build, ffmpeg, other browsers, any local or headed use"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: Chrome Headless Shell 158.0.8127.18 (Playwright revision 1254)

**Method.** On 2026-11-06 I read Playwright v1.65.0's `browsers.json`, `registry/index.ts`, `browserFetcher.ts` and
`oopDownloadBrowserMain.ts`, plus https://playwright.dev/docs/browsers. **The zip was not downloaded**, so it has no
hash yet.

## 1. Verdict

**APPROVE-WITH-CONDITIONS, but not usable until its SHA-256 is pinned.**

- **What Playwright 1.65.0 uses.** It pins this build in `browsers.json`. It downloads it from a single Playwright CDN
  host and checks **no hash or signature**: only HTTP status and length.
- **The approved artifact is the zip, fetched by the workflow itself.** The workflow downloads it, checks it against
  a recorded SHA-256 and hands it to Playwright through `executablePath`. Playwright's own downloader is not used.
- **Headless shell only.** The headless shell is enough for headless CI. The full Chrome for Testing build is not
  needed.

## 2. What it is and why it is needed

The headless browser that Playwright drives for the E2E and axe accessibility checks (blueprint §4, §7: "Cover …
browser binaries", §5).

## 3. Provenance

- **The pin.** `browsers.json` at v1.65.0 has
  `{ "name": "chromium-headless-shell", "revision": "1254", "installByDefault": true, "browserVersion": "158.0.8127.18", "title": "Chrome Headless Shell" }`.
  The full browser entry is titled "Chrome for Testing"
  (https://raw.githubusercontent.com/microsoft/playwright/v1.65.0/packages/playwright-core/browsers.json).
- **The source URL.** `cftUrl()` builds `builds/cft/${browserVersion}/${suffix}` with only one mirror,
  `https://cdn.playwright.dev`. For ubuntu24.04-x64 the suffix is `linux64/chrome-headless-shell-linux64.zip`
  (`registry/index.ts`). The full URL is derived from that and was not fetched.
- **Who builds it.** The binary is Google's Chrome for Testing build, per the title, re-hosted by Playwright.
  Playwright's docs still say it uses "open source Chromium builds" (https://playwright.dev/docs/browsers). That
  re-hosting and the docs inconsistency are why the conditions ask for a byte comparison with Google's own download.
- **Integrity.** There is **no hash, checksum or signature** anywhere in the install path:
  - `index.ts` passes none.
  - The fetcher checks `statusCode !== 200` and `downloadedBytes !== totalBytes` (skipped for chunked responses),
    then extracts the zip and writes a marker file.
  - No `rejectUnauthorized` is set in those files, so Node's default TLS verification applies.
- **The publisher publishes no SHA-256** that was found.

## 4. Maintenance and adoption

- **Release coupling.** The build is tied to the Playwright release, and it changes only when Playwright is upgraded,
  which is a new review.
- **Not checked.** Chrome's own release cadence for 158.x, and whether 158.0.8127.18 is still current.

## 5. Known vulnerabilities at this version

**Not assessed.** Chrome CVEs are not in OSV for this artifact, and Chrome release notes were outside the fetchable
sources.

Treat it as a browser that may lag security fixes. That is acceptable only because it loads nothing but our own local
app.

## 6. License

Chromium is BSD-3-Clause with bundled third-party licences, and Chrome for Testing has its own terms. **Not verified.**

## 7. Execution privileges

- **What it is.** A native executable (C++), started by Playwright at test time with the runner user's rights.
- **No sandbox by default.** `chromiumSandbox` "Defaults to `false`" (https://playwright.dev/docs/api/class-browsertype).
- **What it can do.** Arbitrary network access from page content, and GPU, software-rendering and file access within
  the user's rights.

## 8. Direct dependencies as published

- **Bundled.** The zip is self-contained, holding Chrome's bundled libraries.
- **From the host.** System libraries come from the runner image. Playwright lists 21 Ubuntu 24.04 packages for
  Chromium (`nativeDeps.ts`); they are not installed by us.
- **ffmpeg is not needed.** Playwright adds ffmpeg (revision 1013) to every chromium install, but it is only needed
  for video, and video is not approved.

## 9. Alternatives considered

- **Playwright's own `install --only-shell chromium`:** simpler, but unverifiable (no hash) and it also pulls ffmpeg.
- **A Chrome preinstalled on the runner image, if there is one** (not checked): another unpinned binary. Playwright
  says it "only works with the bundled Chromium" (`executablePath` docs).
- **The full CfT `chromium`:** larger, and not needed headless.

## 10. Conditions and residual risk

```bash
z=chrome-headless-shell-linux64.zip
curl -fsSL --proto '=https' --proto-redir '=https' -o "$RUNNER_TEMP/$z" \
  "https://cdn.playwright.dev/builds/cft/158.0.8127.18/linux64/$z"
echo "${CHS_SHA256}  $RUNNER_TEMP/$z" | sha256sum --check --strict -
```

**Residual risk.**

- **First fetch is trusted.** The recorded hash proves identity from that fetch onwards, not safety. Without Google's
  copy for comparison, the first fetch is trusted on TLS alone.
- **Unsandboxed.** The browser runs without its sandbox.
- **Patch lag.** It may lag Chrome security fixes.

## 11. Not verified

- **The zip.** Its existence at the derived URL, its size, hash and contents.
- **Identity with Google's CfT artifact.**
- **Chrome 158 advisories.**
- **The licence.**
- **Whether all runtime libraries are present** on ubuntu-24.04.
