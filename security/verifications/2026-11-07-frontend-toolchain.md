---
title: "Frontend toolchain verification, 2026-11-07"
description: "The pre-use checks of Node.js 26.13.0 (darwin-arm64 on the owner's MacBook, and the hash that CI pins for linux-x64), and the one-time recording of the Chrome Headless Shell 158.0.8127.18 SHA-256."
status: recorded
updated: 2026-11-07
---

# Frontend toolchain verification, 2026-11-07

The reviews of Node.js 26.13.0 and of Chrome Headless Shell 158.0.8127.18 require checks before first use, recorded
here. The format follows `2026-11-06-toolchain.md`.

**Who checked:**

- **Node.js:** the orchestrator, an AI agent, on the owner's MacBook (macOS arm64), from 2026-11-07T21:03:58Z to
  21:04:18Z.
- **The keyring's contents and the Chrome download:** the security-review agent, on the same MacBook, with
  `/usr/bin/curl`, `/usr/bin/shasum` and `/usr/bin/python3`, in new empty directories, sending no credentials.

These checks are mechanical and reproducible. They become the owner's decision when the owner accepts the reviewed set
that cites this record. That acceptance is pending.

## Node.js 26.13.0, darwin-arm64 (local build hosts and the maintenance environment)

Review: `security/reviews/runtime--node-darwin-arm64--26.13.0.md`, conditions 1 to 5.

| Check | Result |
|---|---|
| Download | `curl --proto '=https'` from `https://nodejs.org/dist/v26.13.0/` into a new empty directory: `node-v26.13.0-darwin-arm64.tar.xz` (30,647,096 bytes) and `SHASUMS256.txt.asc` (4,053 bytes, SHA-256 `1670dd3eebeb6be820e78faf68b9caf33b7434d55701249138fb426103bca1a0`). No browser, `.pkg`, Homebrew or version manager |
| SHA-256 | `shasum -a 256 --check` against the pinned `6f5b3187d9df9fc007e881167905bc7d69ad26450ddc377c83d63256a2495b6e`: OK |
| Signature | `gpgv` 2.4.9 (GnuPG, from Homebrew) with the keyring `gpg-only-active-keys/pubring.kbx` of nodejs/release-keys: exit 0, `GOODSIG C43CEC45C17AB93C Richard Lau`, `VALIDSIG` with primary-key fingerprint `C82FA3AE1CBEDC6BE46B9360C43CEC45C17AB93C`, signature made 2026-10-28 13:00:48 UTC |
| Signed body | The darwin-arm64 line of the gpgv-extracted body (34 lines) equals the pinned hash |
| Keyring | 21,011 bytes, SHA-256 `140f2ad5260fd62773b6243ce8e1d3009645d558f121b8262c55e383dc285932`. Its git blob hash `bae91a9ce595d5775a2e2a998af04dee044c865d` equals the file at nodejs/release-keys commit `481637f813e912c4aa3622d7964ab426c97b8e8d` (2026-09-01, the HEAD on 2026-11-07 and the last commit to change the file) |
| Keyring contents | Read from the keybox file's eight OpenPGP blobs without gpg: the primary keys are exactly the eight release keys of the v26.13.0 README, none missing and none extra (the review's section 3 asks for this, because gpgv accepts any key in the keyring) |
| Install | Extracted with `tar` as the host user, without `sudo`, into `~/.local/node/node-v26.13.0-darwin-arm64` (owned by the user, outside `/usr/local` and `/opt/homebrew`), then made read-only (`chmod -R a-w`). `bin/node` has no `com.apple.quarantine` attribute, and no Gatekeeper setting was touched |
| Versions | With `<dir>/bin` first on `PATH` for the command only: `node --version` `v26.13.0`, `npm --version` `11.20.0` |
| Code signing | `codesign --verify --strict bin/node`: OK. `codesign -dv`: `Identifier=node`, `TeamIdentifier=HX7739G8FX` |

Not done, as the review requires: no shell profile was changed, nothing was installed globally with npm, and corepack
was not enabled. Local results are not release evidence. The approval expires on 2026-11-25.

## Node.js 26.13.0, linux-x64 (CI)

Review: `security/reviews/runtime--node--26.13.0.md`, condition 2.

The same gpgv-verified `SHASUMS256.txt` body lists `node-v26.13.0-linux-x64.tar.xz` as
`d28a96af6c38596f6d9d97c0ee531c13d238d757742387d1928de822d959e997`, equal to the hash pinned in that review. The
signature check above covers it. No linux-x64 archive was downloaded here: CI downloads its own copy and checks it with
`sha256sum --check` on every run, once the workflow exists.

## Chrome Headless Shell 158.0.8127.18, linux64 (CI)

Review: `security/reviews/browser-binary--chrome-headless-shell--158.0.8127.18.md`, condition 2. Recorded from
2026-11-07T21:33:57Z to 21:34:15Z.

| Check | Result |
|---|---|
| Playwright's URL | `https://cdn.playwright.dev/builds/cft/158.0.8127.18/linux64/chrome-headless-shell-linux64.zip` answers `HTTP/2 307` with `location: https://storage.googleapis.com/chrome-for-testing-public/158.0.8127.18/linux64/chrome-headless-shell-linux64.zip`. The CDN hosts no copy of its own; it sends the client to Google's Chrome for Testing bucket |
| Google's download | The same `storage.googleapis.com` URL, fetched directly into a new empty directory: `HTTP/2 200`, 131,998,580 bytes, `last-modified: Wed, 04 Nov 2026 03:30:49 GMT` |
| Through Playwright's URL | `curl -fsSL --proto '=https' --proto-redir '=https'` into another new empty directory: one redirect, to the same URL, 131,998,580 bytes |
| SHA-256 | `18352d22cb0cdf520cbff2ad8c41391f0e02a72886cb53b0fde4bdfc4bd075d6` for both copies; `cmp` finds them byte-identical. Both routes lead to Google's bucket, so this is identity, not independent corroboration |
| Google's stored hash | The response header `x-goog-hash: md5=gfw3cKBYhEVZUf2X14DAug==` equals the MD5 of the download. That is only a transfer check |
| Google's index | `known-good-versions-with-downloads.json` (timestamp 2026-11-07T10:10:51Z) lists 158.0.8127.18, revision 1727472, with exactly this headless-shell linux64 URL. `last-known-good-versions.json` names it the **Beta** channel build; Stable is 157.0.8103.38 |
| Archive listing | 287 entries under one top-level directory `chrome-headless-shell-linux64/`, with no absolute path, no `..` segment and no symbolic link. Five executables: `chrome-headless-shell` (216,599,888 bytes), `libEGL.so`, `libGLESv2.so`, `libvk_swiftshader.so` and `libvulkan.so.1` |

The zip was neither extracted nor run. Three text members were read into memory to record them: `ABOUT`, `deb.deps`
and the start of `LICENSE.headless_shell`. Nothing was written to disk from the archive.

**Still open:** whether the runner image's libraries suffice to start the shell without `install-deps`. Only the first
CI run can show that, by launching the shell and passing a smoke test (the review's condition 6); `ldd` alone does
not.
