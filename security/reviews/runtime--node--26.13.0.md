---
component: "Node.js"
kind: runtime
version: "26.13.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "CI only: GitHub-hosted ubuntu-24.04 x64 runners, official nodejs.org build node-v26.13.0-linux-x64, including the npm 11.20.0 it bundles"
conditions: ["Install node-v26.13.0-linux-x64.tar.xz from nodejs.org/dist and check it with sha256sum --check against d28a96af6c38596f6d9d97c0ee531c13d238d757742387d1928de822d959e997 pinned in the workflow; if actions/setup-node is used instead, check the SHA-256 of the installed bin/node against the bin/node of that verified tarball (setup-node checks nothing)", "Before first use, the lock-maintenance environment verifies SHASUMS256.txt.asc with gpgv against the nodejs/release-keys keyring and records the result; the expected signer is Richard Lau, C82FA3AE1CBEDC6BE46B9360C43CEC45C17AB93C", "The job asserts node --version is v26.13.0 and npm --version is 11.20.0", "runs-on pins ubuntu-24.04, not ubuntu-latest", "Approval expires 2026-11-25: the HIGH-severity 26.x security release announced for 2026-11-11 needs its own review"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2026-11-25"
---

# Security review: Node.js 26.13.0 (runtime, CI)

**Method.** Release index, checksum file, signature file, release notes, README and schedule were read on 2026-11-06.
No binary was downloaded, hashed or run, and gpg was not run. **`expiresAt` is deliberately short** (see §5).

## 1. Verdict

**APPROVE-WITH-CONDITIONS** for the official `linux-x64` build in CI. The publisher's SHA-256 is pinned. The checksum
file is PGP-clearsigned; its issuer fingerprint matches a listed release key, but the signature still has to be
verified with gpgv before first use. **npm 12.2.1 is not bundled:** Node 26.13.0 ships npm **11.20.0**, which this
review covers as part of the tarball. npm 12.2.1 is rejected separately (`npm--npm--12.2.1.md`). A HIGH-severity
26.x security release is pre-announced for 2026-11-11, so this approval expires on 2026-11-25.

## 2. What it is and why it is needed

The JavaScript runtime for the whole frontend toolchain: Vite, Vitest, Playwright, TypeScript, openapi-typescript
and VitePress, in the `web`, `contract-e2e` and `docs-brain` jobs. The blueprint pins "Node 26.13.0 LTS"
(`inputs/blueprint.md` §4). v01-design has no frontend.

## 3. Provenance

- **Publisher.** The Node.js project, at https://nodejs.org/dist/v26.13.0/. It was released by Richard Lau
  (@richardlau): "2026-10-28, Version 26.13.0 'Lithium' (LTS)" (https://nodejs.org/en/blog/release/v26.13.0).
- **Index entry** (https://nodejs.org/dist/index.json): date 2026-10-28, `npm` 11.20.0, `v8` 14.6.202.34, `uv`
  1.52.1, `openssl` 3.5.9, `lts` "Lithium", `security` false.
- **Published SHA-256** (https://nodejs.org/dist/v26.13.0/SHASUMS256.txt, 34 lines):

  | File | SHA-256 |
  |---|---|
  | node-v26.13.0-linux-x64.tar.xz (**approved**) | `d28a96af6c38596f6d9d97c0ee531c13d238d757742387d1928de822d959e997` |
  | node-v26.13.0-linux-x64.tar.gz (same binaries) | `5848553868b402d3fabb5b09270649977780a390f832155721025a08b5104455` |
  | node-v26.13.0.tar.xz (source, reference) | `263f639ecfa7a17b49438d76cdbf9aff73179e3c7cb789da69b900718427fa2e` |

- **Signing.**
  - `SHASUMS256.txt.asc` (clearsigned, `Hash: SHA256`) and `SHASUMS256.txt.sig` exist (directory listing).
  - Decoding the signature packet by hand gives issuer `C82FA3AE1CBEDC6BE46B9360C43CEC45C17AB93C`, created
    2026-10-28 13:00:48 UTC.
  - The v26.13.0 README lists that fingerprint as Richard Lau's primary release key, and documents verification with
    `gpgv --keyring=<nodejs/release-keys pubring.kbx> --output SHASUMS256.txt < SHASUMS256.txt.asc`
    (https://raw.githubusercontent.com/nodejs/node/v26.13.0/README.md).
- **Anomaly to note.** The listing shows `SHASUMS256.txt` modified at 17:41, after `.asc`/`.sig` (13:01). Its lines
  matched the signed body by eye. Treat the body extracted by gpgv as the authority, not the bare `.txt`.
- **No build provenance** (SLSA/Sigstore) for the binaries was found. Reproducibility was not checked.

## 4. Maintenance and adoption

- **Schedule** (https://raw.githubusercontent.com/nodejs/Release/main/schedule.json): start 2026-05-05, LTS
  2026-10-28, maintenance 2027-10-20, end 2029-04-30.
- **Cadence.** 26.13.0 is the newest 26.x. Earlier 26.x security releases were 26.3.1 (06-17), 26.5.1 (07-28) and
  26.12.1 (10-21) (index.json).
- **The runner image does not cache 26.x.** ubuntu-24.04 image 20261101.360.1 caches 22.23.4 and 24.22.2, and the
  readme says `ubuntu-latest` moves to Ubuntu 26.04 in November 2026
  (https://raw.githubusercontent.com/actions/runner-images/main/images/ubuntu/Ubuntu2404-Readme.md).
- **Scorecard** for nodejs/node was not queried.

## 5. Known vulnerabilities at this version

- **None published against 26.13.0 on 2026-11-06.** The notes say it has "no further changes from Node.js 26.12.1"
  apart from release metadata, so it carries the 2026-10-21 security fixes.
- **Pre-announced:** "The highest severity issue fixed in the 26.x release line is HIGH". Releases come "on or
  shortly after, Wednesday, November 11, 2026". No CVE IDs are given yet
  (https://nodejs.org/en/blog/vulnerability/november-2026-security-releases). This is the reason for the short expiry.
- OSV was not queried for the runtime.

## 6. License

MIT, with bundled components under their own licenses, per https://github.com/nodejs/node/blob/v26.13.0/LICENSE.
That file was not re-read for this review.

## 7. Execution privileges

- **Everything, in every phase.** Every frontend build, test and docs step runs inside this runtime as the runner user,
  with full file system, network, child-process, native-addon and WASM access.
- **The bundled npm** contacts registry.npmjs.org. It runs package lifecycle scripts unless `--ignore-scripts` is
  given, which the blueprint mandates.
- **libatomic.** Linux binaries link it since Node 25 (release notes), so the runner image must provide it.

## 8. Direct dependencies as published

The binary bundles V8 14.6.202.34, libuv 1.52.1, zlib 1.3.2.1-motley and OpenSSL 3.5.9 (index.json). It also bundles
**npm 11.20.0**:

- Published 2026-09-22 by the personal account `reggi`, with no npm provenance attestation
  (https://registry.npmjs.org/npm/11.20.0), and `advisoryKeys: []` on deps.dev.
- Its bytes are covered by the signed tarball hash above, which is why it is preferred over a separately installed npm.

## 9. Alternatives considered

- **Node 24.22.2**, which is preinstalled on the runner. It needs no download, but it is not the blueprint's pin.
- **A `node:26` container image.** That is another artifact to review.
- **actions/setup-node.** Reviewed separately: it installs GitHub's repackaged archive, not this one.

## 10. Conditions and residual risk

**Conditions** (frontmatter). The direct download is preferred:

```bash
curl -fsSLO --proto '=https' https://nodejs.org/dist/v26.13.0/node-v26.13.0-linux-x64.tar.xz
echo "d28a96af6c38596f6d9d97c0ee531c13d238d757742387d1928de822d959e997  node-v26.13.0-linux-x64.tar.xz" | sha256sum --check --strict -
```

**Residual risk.**

- A pinned hash proves identity, not safety. The trust root is the Node release team's key and build machines.
- The approval lapses quickly because a HIGH fix is due five days after review.
- npm 11.20.0 has no build provenance of its own.

## 11. Not verified

- **The PGP signature.** gpgv was not run; the issuer was decoded by hand.
- **The binaries.** No tarball was downloaded, hashed or run.
- **Also open:** libatomic on the runner image, whether Corepack is bundled, the LICENSE text, and the content of the
  2026-11-11 fixes, which is not yet published.
