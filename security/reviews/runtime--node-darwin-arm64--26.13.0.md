---
component: "Node.js (official darwin-arm64 binary archive)"
kind: runtime
version: "26.13.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "Local build hosts only (Apple Silicon, macOS >= 13.5): node-v26.13.0-darwin-arm64.tar.xz from nodejs.org/dist, including its bundled npm 11.20.0, extracted without sudo into a user-owned directory and put first on PATH only for this repository's commands. Not CI; local results are not release evidence"
conditions: ["Download node-v26.13.0-darwin-arm64.tar.xz with curl from https://nodejs.org/dist/v26.13.0/ (no browser, no .pkg, no Homebrew, no version manager) into a new empty directory, and check it with shasum -a 256 --check against 6f5b3187d9df9fc007e881167905bc7d69ad26450ddc377c83d63256a2495b6e before extracting", "Before first use, the orchestrator verifies SHASUMS256.txt.asc with gpgv against the nodejs/release-keys active keyring, requires VALIDSIG primary fingerprint C82FA3AE1CBEDC6BE46B9360C43CEC45C17AB93C (Richard Lau), checks the darwin-arm64 line of the gpgv-extracted body equals the pinned hash, and records the result", "Extract as the host user with no sudo into a user-owned, not group- or world-writable directory outside /usr/local and /opt/homebrew; never strip com.apple.quarantine, add Gatekeeper exceptions or change Gatekeeper settings: if bin/node is quarantined or blocked, delete the tree and download again with curl", "Prepend <dir>/bin to PATH only in the environment of this repository's commands, never in shell profiles; no npm install -g and no corepack enable", "Before use, assert node --version is v26.13.0, npm --version is 11.20.0 and codesign --verify --strict bin/node succeeds; record the TeamIdentifier shown by codesign -dv", "npm runs with --ignore-scripts, as the blueprint mandates for CI", "Local test or build results are not release evidence; CI on the approved linux-x64 build is", "Approval expires 2026-11-25: the HIGH-severity 26.x security release announced for 2026-11-11 needs its own review"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2026-11-25"
---

# Security review: Node.js 26.13.0, darwin-arm64 archive, local build hosts (delta)

**Delta** of `approved-node-linux.md` (same release; linux-x64, CI). Publisher, metadata, bundled npm and vulnerability
status carry over. New here: artifact and hash, macOS signing and quarantine, key verification, local scope.
**Method.** On 2026-11-06 the checksum and signature files, dist listing, index.json, release notes, README and macOS
build scripts at tag v26.13.0 were read via a web fetcher. Nothing was downloaded, hashed or run; no gpgv/codesign/spctl.

## 1. Verdict

**APPROVE-WITH-CONDITIONS** for the plain `darwin-arm64` `.tar.xz` on local build hosts. The publisher's SHA-256 is
pinned and comes from the same clearsigned SHASUMS256.txt as the Linux approval. The release build code-signs `bin/node`
with hardened runtime, but its entitlements relax it heavily and **nothing in the release tooling notarizes the
tarball**. So it must arrive via curl (no quarantine), and Gatekeeper must not be bypassed. Same expiry as Linux.

## 2. What it is and why it is needed

The runtime for the frontend toolchain (Vite, Vitest, Playwright, TypeScript, openapi-typescript, VitePress), pinned at
"Node 26.13.0 LTS" by the blueprint (Linux review §2). v01-design has no frontend. This runs the same commands locally.

## 3. Provenance

- **Publisher and release.** The Node.js project; released 2026-10-28 by Richard Lau (@richardlau)
  (https://nodejs.org/en/blog/release/v26.13.0). index.json lists `osx-arm64-tar`, npm 11.20.0, V8 14.6.202.34,
  OpenSSL 3.5.9, `security: false` (https://nodejs.org/dist/index.json).
- **Published SHA-256** (signed body of https://nodejs.org/dist/v26.13.0/SHASUMS256.txt.asc, 34 lines; same lines in
  the release notes):

  | File | SHA-256 |
  |---|---|
  | node-v26.13.0-darwin-arm64.tar.xz (**approved**, 31 MB) | `6f5b3187d9df9fc007e881167905bc7d69ad26450ddc377c83d63256a2495b6e` |
  | node-v26.13.0-darwin-arm64.tar.gz (same tar, gzip; not approved) | `d38bd5e5a2ddce6bbb5ce45e46e208337da4d51fffe611e567fd32073d6439f6` |
  | node-v26.13.0.pkg (not approved) | `445b202997c959dddbd54ad8c82956371b495b03c73d56044a6f82dfb3249c46` |

- **PGP.** Clearsigned `Hash: SHA256`; issuer decoded by hand as `C82FA3AE1CBEDC6BE46B9360C43CEC45C17AB93C`, created
  2026-10-28 13:00:48 UTC. The v26.13.0 README lists eight primary release keys
  (https://raw.githubusercontent.com/nodejs/node/v26.13.0/README.md, "Release keys"):
  Antoine du Hamel `5BE8A3F6C8A5C01D106C0AD820B1A390B168D356`, Juan José Arboleda
  `DD792F5973C6DE52C432CBDAC77ABFA00DDBF2B7`, Marco Ippolito `CC68F5A3106FF448322E48ED27F5E38D5B0A215F`, Rafael Gonzaga
  `890C08DB8579162FEE0DF9DB8BEAB4DFCF555EF4`, **Richard Lau `C82FA3AE1CBEDC6BE46B9360C43CEC45C17AB93C` (expected
  signer)**, Ruy Adorno `108F52B48DB57BB0CC439B2997B01419BD92F80A`, Stewart X Addison
  `655F3B5C1FB3FA8D1A0CA6BDE4A7D232B936D2FD`, Ulises Gascón `A363A499291CBBC940DD62E41F10027AF002F8B0`.
  https://github.com/nodejs/release-keys lists the same eight.
- **How the orchestrator verifies** (once, in a new empty directory with a throwaway `GNUPGHOME`; keyring from
  `gpg-only-active-keys/pubring.kbx` of nodejs/release-keys, fetched at a recorded commit). The keyring path needs a
  slash, or gpgv looks for it inside `GNUPGHOME`:

  ```bash
  gpgv --status-fd 1 --keyring ./nodejs-active.kbx --output SHASUMS256.signed.txt < SHASUMS256.txt.asc | grep VALIDSIG
  # pass only if the LAST field (primary key fingerprint) is C82FA3AE1CBEDC6BE46B9360C43CEC45C17AB93C
  grep '  node-v26.13.0-darwin-arm64.tar.xz$' SHASUMS256.signed.txt   # must equal the pinned hash
  ```
  Also check the keyring's `fpr` lines are exactly the eight README fingerprints (two separate repositories). gpgv
  accepts *any* key in the keyring, hence the fingerprint check.
- **Anomaly (carried over).** Bare `SHASUMS256.txt` is dated 17:41, after `.asc`/`.sig` (13:01); its lines match the
  signed body by eye. Use the gpgv-extracted body; `gpgv ... SHASUMS256.txt.sig SHASUMS256.txt` shows if bytes changed.
- **macOS code signing** (build scripts at the tag): the `$(BINARYTAR)` Makefile target runs `tools/osx-codesign.sh` on
  `bin/node` before `tar`, with `codesign --options runtime --timestamp --entitlements tools/osx-entitlements.plist`.
  Only `bin/node` is signed; the script exits 0 without signing if `CODESIGN_CERT` is empty
  (https://raw.githubusercontent.com/nodejs/node/v26.13.0/Makefile, `.../tools/osx-codesign.sh`).
- **Notarization: the package only.** `tools/osx-notarize.sh` is called only from the `$(PKG)` target and submits
  `node-<version>.pkg` (`.../tools/osx-notarize.sh`). Nothing in these sources notarizes the tarball.
- **No SLSA/Sigstore provenance** for the binaries was found. Reproducibility was not checked.

## 4. Maintenance and adoption

Carried over from the Linux review: 26.x is Active LTS ("Lithium") from 2026-10-28, maintenance from 2027-10-20, end of
life 2029-04-30. 26.13.0 is the newest 26.x. macOS arm64 is a **Tier 1** platform, macOS >= 13.5, built on macOS 15 with
Xcode 16 (https://raw.githubusercontent.com/nodejs/node/v26.13.0/BUILDING.md). Scorecard: the fetch was not permitted.

## 5. Known vulnerabilities at this version

- **None published against 26.13.0 on 2026-11-06.** It equals 26.12.1 apart from LTS metadata (release notes).
- **Pre-announced:** HIGH for 26.x, "on or shortly after, Wednesday, November 11, 2026", with no CVE IDs yet; the page
  was last updated 2026-11-03 (https://nodejs.org/en/blog/vulnerability/november-2026-security-releases).
- **OSV not queried:** `POST /v1/query` was not possible with the available tools (GET returned 405).

## 6. License

MIT text ("Copyright Node.js contributors") plus a Joyent MIT block; bundled components keep their own licenses (Acorn
MIT, c-ares MIT, ICU Unicode V3, swc Apache-2.0, ...) (https://raw.githubusercontent.com/nodejs/node/v26.13.0/LICENSE).

## 7. Execution privileges

- **Everything the host user can do**, in build, test and dev-server phases: home directory (SSH keys, tokens, other
  repositories), network, child processes, native addons, WASM, plus the **TCC grants of the launching terminal or IDE**
  (e.g. Full Disk Access).
- **Hardened runtime mostly relaxed.** Entitlements: `allow-jit`, `allow-unsigned-executable-memory`,
  `disable-executable-page-protection`, `allow-dyld-environment-variables`, `disable-library-validation`,
  `get-task-allow` (https://raw.githubusercontent.com/nodejs/node/v26.13.0/tools/osx-entitlements.plist). So
  `DYLD_INSERT_LIBRARIES` works, any dylib loads, and same-user processes can get its task port.
- **The bundled npm** contacts registry.npmjs.org and runs lifecycle scripts unless `--ignore-scripts` is given.
- **Quarantine.** Browsers set `com.apple.quarantine`; curl does not. A quarantined, non-notarized `bin/node` would meet
  Gatekeeper, and the tempting fix (`xattr -dr`, "Open anyway") is the bypass the conditions forbid.

## 8. Direct dependencies as published

The binary bundles V8 14.6.202.34, libuv 1.52.1, zlib 1.3.2.1-motley and OpenSSL 3.5.9, plus **npm 11.20.0** (index.json).
npm has no provenance attestation of its own (Linux review §8); its bytes are covered by the pinned tarball hash.

## 9. Alternatives considered

- **The `.pkg`:** notarized, but installs system-wide into `/usr/local` with admin rights. **Homebrew `node`:** a
  different, Homebrew-built bottle that moves with autobumps. **nvm/fnm/volta:** more third-party code for the same file.

## 10. Conditions and residual risk

**Conditions:** see frontmatter. The download and check:

```bash
curl -fsSLO --proto '=https' https://nodejs.org/dist/v26.13.0/node-v26.13.0-darwin-arm64.tar.xz
echo "6f5b3187d9df9fc007e881167905bc7d69ad26450ddc377c83d63256a2495b6e  node-v26.13.0-darwin-arm64.tar.xz" | shasum -a 256 -c
```

**Residual risk.** The trust root is the release key and build machine. The entitlements ease injection, but only for
code already running as the user. A laptop is less controlled than an ephemeral runner, hence "not release evidence".

## 11. Not verified

- **The PGP signature:** gpgv not run; the release-keys keyring not fetched.
- **The archive:** not downloaded, hashed, listed or extracted. `bin/node`'s actual signature, Team ID, notarization
  status (`spctl --assess`), linked dylibs and file modes are unchecked. The build scripts show intent, not the result.
- **macOS:** whether macOS `tar` propagates quarantine from the archive to extracted files.
- **Also open:** whether Corepack is bundled, OSV, deps.dev, Scorecard, and the content of the 2026-11-11 fixes.
