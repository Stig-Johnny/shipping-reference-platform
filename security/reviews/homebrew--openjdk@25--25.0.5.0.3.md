---
component: "Homebrew formula openjdk@25 (homebrew/core), macOS arm64 bottles"
kind: homebrew
version: "25.0.5.0.3"
verdict: APPROVE-WITH-CONDITIONS
scope: "Local build hosts only (macOS arm64: Sequoia, Tahoe, macOS 27): JDK for local Gradle builds and tests; never CI, never release evidence"
conditions: [
  "Bottle only, installed only if brew info --json=v2 openjdk@25 shows 25.0.5.0.3 revision 0 and the host's bottle SHA-256 matches: arm64_sequoia a589e88d2a284450b97a3890fbd226b9662ac5229b6466511fa55e91df165466, arm64_tahoe 8dcb068655f82c9418265c58313178f291b27e75f0d653b41a7ea8cc172480da, arm64_golden_gate 61b59bcdc1d545cb59a401a29e7462e5843fc8cdc4db5f9f36ce3413a1bae277",
  "Install with HOMEBREW_VERIFY_ATTESTATIONS=1 and the attestation check must pass (not verified in this review)",
  "The runtime dependency formulae freetype 2.15.0, giflib 6.1.3, harfbuzz 15.1.0, jpeg-turbo 3.2.1, libpng 1.6.59, little-cms2 2.19.1 and their own dependencies are not covered: each needs its own recorded review before brew install openjdk@25 runs",
  "brew pin openjdk@25 and every dependency formula; any upgrade, including a formula autobump, is a new review",
  "No sudo symlink into /Library/Java/JavaVirtualMachines: point JAVA_HOME or org.gradle.java.installations.paths at $(brew --prefix openjdk@25)/libexec/openjdk.jdk/Contents/Home",
  "No --build-from-source (it downloads and runs a boot JDK from download.java.net that this review does not cover)",
  "Local results are not release evidence: CI on Temurin 25.0.5+8 is authoritative and code compiles with --release 25"
]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Homebrew `openjdk@25` 25.0.5.0.3 (macOS arm64 bottles)

**Method.** I read the following on 2026-11-06 with a GET-only fetch tool: the formula API JSON, the formula source, the homebrew-core history and PR #321726, the upstream `openjdk/jdk25u` tags and compare view, and OpenSSF Scorecard. `docs.brew.sh` refused the fetch, and the GitHub attestation API returned 403. Nothing was downloaded, installed or run.

## 1. Verdict
**APPROVE-WITH-CONDITIONS, for the bottle itself.** homebrew-core CI builds it from the newest upstream GA tag, from a source tarball pinned by SHA-256. The merging PR had 23 passing checks and a human maintainer's approval. I could not verify the bottle's attestation, though. Installing it also pulls six runtime dependency formulae, each with its own native-code dependency tree, and this review does not cover them. **Until those formulae are reviewed, this approval does not authorise `brew install openjdk@25`.** §9 recommends a self-contained alternative.

## 2. What it is and why it is needed
It is the JDK 25 for local build hosts (`v01-design.md` §4). The design requires a separate review per platform bottle and exact pinned versions. CI uses Temurin 25.0.5+8, which has its own review.

## 3. Provenance
- **Formula.** `homebrew/core` `openjdk@25`, version 25.0.5.0.3, revision 0 ([API](https://formulae.brew.sh/api/formula/openjdk@25.json)).
  - `tap_git_head`: `719f8ef7e1ef6753ebcee6933592df6c84aa79ab`.
  - Formula source sha256: `6d73346465966e59f70d886354b65528c5b156852163d4a3ab2d766224d843d9`.
  - Bottles are served from `ghcr.io/v2/homebrew/core/openjdk/25/blobs/sha256:<sha>`, cellar `:any`.
- **Bottle SHA-256 values,** as published by Homebrew in the formula and the API:
  - arm64_sequoia: `a589e88d2a284450b97a3890fbd226b9662ac5229b6466511fa55e91df165466`
  - arm64_tahoe: `8dcb068655f82c9418265c58313178f291b27e75f0d653b41a7ea8cc172480da`
  - arm64_golden_gate: `61b59bcdc1d545cb59a401a29e7462e5843fc8cdc4db5f9f36ce3413a1bae277`
- **Source** ([formula](https://github.com/Homebrew/homebrew-core/blob/719f8ef7e1ef6753ebcee6933592df6c84aa79ab/Formula/o/openjdk@25.rb)). The tarball is `github.com/openjdk/jdk25u/archive/refs/tags/jdk-25.0.5.0.3-ga.tar.gz`, SHA-256 `b4dfab0e1a41ed6cc8595555323102d4b3910a7e6e14bc9ec59d945028030072`.
  - That tag is commit `275848b087112429235f4f235822e294a1a288ef`, the newest `-ga` tag on 2026-11-06 ([tags](https://github.com/openjdk/jdk25u/tags)).
  - Against `jdk-25.0.5-ga` it adds 6 commits: two CA-certificate backports (8395142, 8395690) and version bumps ([compare](https://github.com/openjdk/jdk25u/compare/jdk-25.0.5-ga...jdk-25.0.5.0.3-ga)).
- **Build.** `make images`, with a boot JDK 25.0.2 from `download.java.net` pinned by SHA-256. It links system libraries (`--with-freetype=system`, `--with-libpng=system`, …) and sets the vendor to the tap user. There is no `post_install` step and no service.
- **Pipeline.** BrewTestBot opened PR [#321726](https://github.com/Homebrew/homebrew-core/pull/321726). It passed "23 checks" and `cho-m` approved it on 2026-10-28. It was merged via the merge queue as `02e642fb33630603a58f53d14d9f3d1e93fc56a7`, and a CI workflow run published the bottles. The commits show no signature ([history](https://github.com/Homebrew/homebrew-core/commits/main/Formula/o/openjdk@25.rb)).
- **Attestation: not verified.** `api.github.com/…/attestations/sha256:<bottle>` returned 403.

## 4. Maintenance and adoption
- **Version history.** The formula was created on 2026-05-19 at 25.0.3. It then moved to 25.0.4, 25.0.4.1, 25.0.5 (10-20), 25.0.5.0.2 (10-22) and 25.0.5.0.3 (10-27), each within days of the upstream tag ([PRs](https://github.com/Homebrew/homebrew-core/pulls?q=openjdk%4025+is%3Apr)).
- **Lifecycle.** It is scheduled to be deprecated on 2030-09-30 and disabled on 2033-09-30 ("unmaintained").
- **Installs.** 8,503 / 17,370 / 26,586 over 30 / 90 / 365 days ([formula page](https://formulae.brew.sh/formula/openjdk@25)).
- **Scorecard.** homebrew-core scores 8 (Code-Review 10, Maintained 10, Branch-Protection 5, Token-Permissions 6) ([homebrew-core](https://api.securityscorecards.dev/projects/github.com/Homebrew/homebrew-core)). brew scores 5.9 ([brew](https://api.securityscorecards.dev/projects/github.com/Homebrew/brew)).

## 5. Known vulnerabilities at 25.0.5.0.3
25.0.5.0.3 is the newest upstream GA. It includes the October 2026 update and JDK-8395690, which Adoptium cites as the fix for CVE-2026-114820 ([eMudhra](https://adoptium.net/news/2026/10/temurin-october-2026-emudhra)). **Result: none known on 2026-11-06.** Gaps: no OSV POST query was possible, the full October CVE list was not retrieved (openjdk.org was unreachable), and the dependency formulae were **not** checked.

## 6. License
- openjdk@25: `GPL-2.0-only WITH Classpath-exception-2.0` ([API](https://formulae.brew.sh/api/formula/openjdk@25.json)).
- Dependencies: FTL (freetype), MIT, IJG/Zlib/BSD-3-Clause (jpeg-turbo) and libpng-2.0.

## 7. Execution privileges
- **Install.** brew extracts the bottle and links it keg-only. No `post_install` or service runs.
- **Run time.** The JDK runs Gradle, javac, tests and PIT locally as the developer, with full file-system, network, process, native-code and reflection rights.
- **Shared libraries.** Cellar `:any` plus system libraries means the JDK loads Homebrew's `freetype`, `libpng`, `giflib`, `jpeg-turbo`, `little-cms2` and `harfbuzz` whenever `java.desktop` (fonts, imaging, colour) is used. A later `brew upgrade` of any of them therefore changes code inside this JDK without changing its bottle hash, which is why the conditions pin them.
- **Out of scope.** The `brew` client itself (auto-update, analytics), because docs.brew.sh could not be read.

## 8. Dependencies as published
- **Runtime** ([API](https://formulae.brew.sh/api/formula/openjdk@25.json), [formula page](https://formulae.brew.sh/formula/openjdk@25)):
  - freetype 2.15.0 (needs brotli, libpng)
  - giflib 6.1.3
  - harfbuzz 15.1.0 (needs cairo, freetype, glib, graphite2, icu4c@79, libpng)
  - jpeg-turbo 3.2.1
  - libpng 1.6.59
  - little-cms2 2.19.1 (needs jpeg-turbo, libtiff)
- **Build only,** not installed with the bottle: autoconf, pkgconf and Xcode.
- **Flag:** every runtime formula, and its own transitive tree, needs its own review. harfbuzz's (cairo, glib, icu4c) is the largest.

## 9. Alternatives considered
**Recommended: Temurin 25.0.5+8 for macOS aarch64.** It is a single self-contained archive with an Adoptium SHA-256 and GPG signature. It needs no Homebrew dependency tree and matches CI's vendor and version; it needs only one new platform review. The unversioned `openjdk` formula is 27.0.1.0.3, the wrong major version ([openjdk](https://formulae.brew.sh/api/formula/openjdk.json)).

## 10. Conditions and residual risk
The conditions are in the frontmatter. Residual risks:
- Trust in homebrew-core CI and GHCR, with the attestation unverified.
- The formula build declares no network sandbox: the PR is labelled "missing network sandbox".
- Version skew with CI. Locally, 25.0.5.0.3 runs with OpenJDK's own trust store (the configure arguments set no `cacerts` override). CI runs 25.0.5+8 with Mozilla's.

## 11. Not verified
- Bottle attestations and their SLSA predicate.
- Bottle contents (`java -version`, vendor string), or that a rebuild matches.
- The dependency formulae's sources, bottles and advisories.
- What `HOMEBREW_VERIFY_ATTESTATIONS` checks: docs.brew.sh was denied.
