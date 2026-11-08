---
component: "Chrome Headless Shell (Playwright chromium-headless-shell, revision 1254)"
kind: browser-binary
version: "158.0.8127.18"
verdict: APPROVE-WITH-CONDITIONS
scope: "CI only: GitHub-hosted ubuntu-24.04 x64, headless E2E/accessibility tests of the locally started application, launched by @playwright/test 1.65.0 via executablePath"
conditions: ["The workflow downloads https://cdn.playwright.dev/builds/cft/158.0.8127.18/linux64/chrome-headless-shell-linux64.zip, or the Google URL it redirects to (https://storage.googleapis.com/chrome-for-testing-public/158.0.8127.18/linux64/chrome-headless-shell-linux64.zip), with curl --proto '=https' --proto-redir '=https', and checks it with sha256sum --check against 18352d22cb0cdf520cbff2ad8c41391f0e02a72886cb53b0fde4bdfc4bd075d6 pinned in the workflow", "That SHA-256 was recorded on 2026-11-07 (security/verifications/2026-11-07-frontend-toolchain.md): 131,998,580 bytes, byte-identical through Playwright's URL and Google's Chrome for Testing URL. Playwright's URL redirects to Google's, so this establishes identity, not independent corroboration by a second publisher; the first fetch is trusted on Google's TLS. Any other hash is a new review", "Extract into a fresh directory; pass chrome-headless-shell-linux64/chrome-headless-shell as launchOptions.executablePath; PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1", "Navigate only to the local application (the browser runs without Chromium's sandbox by Playwright default)", "Not approved: the full chromium (Chrome for Testing) build, ffmpeg, other browsers, any local or headed use", "Before the job becomes a required check, its first run launches the shell through Playwright on the runner, without install-deps, and passes a smoke test against the local application (one page loads and renders); ldd output alone does not satisfy this. The run is recorded as an amendment", "Licence: a Google Chrome for Testing build (its ABOUT file names Google's Terms of Service) with Chromium's BSD-3-Clause licence and about 700 bundled licences (LICENSE.headless_shell); used in CI only, never uploaded or redistributed, which needs the owner's explicit decision (security/AGENTS.md)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: Chrome Headless Shell 158.0.8127.18 (Playwright revision 1254)

**Method.** On 2026-11-06 I read Playwright v1.65.0's `browsers.json`, `registry/index.ts`, `browserFetcher.ts` and
`oopDownloadBrowserMain.ts`, plus https://playwright.dev/docs/browsers. **The zip was not downloaded**, so it has no
hash yet.

## 1. Verdict

**APPROVE-WITH-CONDITIONS.** Its SHA-256 was recorded on 2026-11-07, byte-identical through Playwright's URL and
Google's Chrome for Testing URL (amendment below). Both routes lead to Google, so that shows identity, not independent
corroboration. Until then it was not usable. One condition stays open: the first CI run must launch it and pass a
smoke test on the runner.

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

- **The zip.** Its existence at the derived URL, its size, hash and contents. *Closed by the 2026-11-07 amendment.*
- **Identity with Google's CfT artifact.** *Closed by the 2026-11-07 amendment.*
- **Chrome 158 advisories.** Still open.
- **The licence.** *Read on 2026-11-07 (amendment); it needs the owner's decision (condition 7).*
- **Whether all runtime libraries are present** on ubuntu-24.04. Still open: only the first CI run can show it
  (condition 6).

## Amendment, 2026-11-07: the hash, recorded

**What was done.** The security-review agent worked on the owner's MacBook with `/usr/bin/curl`, in three new empty
directories, sending no credentials:

- the URL Playwright uses, without following redirects;
- Google's Chrome for Testing URL;
- Playwright's URL again, with `-L --proto '=https' --proto-redir '=https'`, the workflow's form.

The zip was hashed and its central directory listed. It was neither extracted nor run; three text members were read
into memory. Evidence: `security/verifications/2026-11-07-frontend-toolchain.md`.

**What was found.**

- **Playwright's CDN holds no copy.** `cdn.playwright.dev` answers `HTTP/2 307` to
  `https://storage.googleapis.com/chrome-for-testing-public/158.0.8127.18/linux64/chrome-headless-shell-linux64.zip`.
  So the bytes come from Google's Chrome for Testing bucket either way. The "Playwright re-hosting" in section 3 is a
  redirect, which answers the documentation inconsistency noted there.
- **Hash.** Both downloads are 131,998,580 bytes, with SHA-256
  `18352d22cb0cdf520cbff2ad8c41391f0e02a72886cb53b0fde4bdfc4bd075d6`, and `cmp` finds them identical. Google's
  `x-goog-hash` MD5 header matches the download, which checks only the transfer.
- **Identity, not corroboration.** Playwright's URL redirects to Google's, so the two downloads are one object from one
  publisher. That establishes identity; it is not a second, independent source. No publisher hash or signature exists,
  so the first fetch is trusted on Google's TLS (residual risk, section 10).
- **Google's index.** `known-good-versions-with-downloads.json` lists 158.0.8127.18 (revision 1727472) with exactly
  this URL. `last-known-good-versions.json` (2026-11-07T10:10:51Z) shows it is Chrome's **Beta** channel build; Stable
  is 157.0.8103.38. Playwright 1.65.0 pins a beta browser. That matters little while the browser loads only our own
  application (condition 4).
- **Contents.**
  - 287 entries under `chrome-headless-shell-linux64/`, with no absolute path, no `..` segment and no symbolic link,
    so extracting it cannot create a link that points outside its directory.
  - Five executables: `chrome-headless-shell` (216,599,888 bytes), `libEGL.so`, `libGLESv2.so`,
    `libvk_swiftshader.so` and `libvulkan.so.1`.
  - The rest is data: `icudtl.dat`, `v8_context_snapshot.bin`, `.pak` resources, `hyphen-data/`, and the
    `deb.deps`/`rpm.deps` package lists.
- **Licence.** `ABOUT` reads "Google Chrome … Copyright 2026 Google LLC … See the Terms of Service at chrome://terms".
  `LICENSE.headless_shell` (2,342,403 bytes) opens with The Chromium Project's BSD-style licence and holds about 700
  further sections for bundled components. The build is not under one SPDX licence, so condition 7 asks the owner to
  decide on CI-only, non-redistributed use.
- **Runtime libraries: still open.** `deb.deps` lists the 30 Debian dependencies of the full Chrome package (GTK,
  CUPS, `wget`, `xdg-utils` among them). That is not a statement of what the headless shell needs. Condition 6 leaves
  it to the first CI run, which must launch the shell and pass a smoke test. `ldd` output alone does not settle it.

**Conditions changed:**

- 1 pins the hash and names Google's URL.
- 2 records the hash, and says what the comparison shows: identity, not corroboration.
- 6 (a launch smoke test on the runner) and 7 (licence) are new.
- 3 to 5 are unchanged.
