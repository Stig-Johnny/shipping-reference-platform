---
component: "Eclipse Temurin JDK, macOS aarch64 HotSpot archive: OpenJDK25U-jdk_aarch64_mac_hotspot_25.0.5_8.tar.gz (adoptium/temurin25-binaries)"
kind: jdk
version: "25.0.5+8"
verdict: APPROVE-WITH-CONDITIONS
scope: "Local build hosts only (macOS arm64): extracted by the developer into a user-owned directory and used through JAVA_HOME and org.gradle.java.installations.paths for local Gradle builds, javac and tests. Never CI, never release evidence. The .pkg installer, the JRE and every other asset are out of scope"
conditions: [
  "Only the archive OpenJDK25U-jdk_aarch64_mac_hotspot_25.0.5_8.tar.gz, 136,446,056 bytes, SHA-256 36dc14d797aaff6d89aab302cd053fdba6207dfdcf772052fa7b023a94b0e857, downloaded with curl from the GitHub release into a new empty user-owned directory and checked with shasum -a 256 against this allowlist value (not only against the downloaded .sha256.txt) before extraction",
  "Before first use, a human verifies the .sig against the archive in a fresh GNUPGHOME, using key 3B04D753C9050D9A5D343F39843C48A565F8F04B fetched from keyserver.ubuntu.com, and records the result; the VALIDSIG primary-key fingerprint must equal that key, and a signature from any other key stops the install",
  "Extract as the developer with tar (no sudo), after tar -tzf shows only relative paths under one top-level directory, into a user-owned directory outside /Library/Java/JavaVirtualMachines; then make the tree read-only (chmod -R a-w); nothing writes into it afterwards, including keytool -importcert into its cacerts",
  "JAVA_HOME and org.gradle.java.installations.paths point at <dir>/jdk-25.0.5+8/Contents/Home (or the top-level directory the listing shows); org.gradle.java.installations.auto-detect=false and auto-download=false",
  "Gatekeeper is never disabled system-wide (no spctl --master-disable or its successors). A com.apple.quarantine attribute may be removed only from the extracted tree, and only after the hash and GPG checks pass",
  "At first install, record bin/java -version (it must equal the full_version_output in the release .json), plus codesign -dv --verbose=4 and codesign -d --entitlements - for bin/java and lib/server/libjvm.dylib, in this review's evidence",
  "Local results are not release evidence: CI on the Linux x64 Temurin 25.0.5+8 is authoritative, and code compiles with --release 25",
  "Approval lapses if the asset's SHA-256 changes (a respin under the same name) or when Adoptium publishes a Temurin 25 release fixing a vulnerability that affects 25.0.5+8; either is a new review"
]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Eclipse Temurin 25.0.5+8 (macOS aarch64 archive): a delta review

**Delta.** This review covers only what differs from the approved Linux x64 review of the same release (`inputs/approved-temurin-linux.md`). Both builds share the same release, the same source commit and the same build-scripts commit (§3), so the facts that are the same on both platforms are cited from there: release cadence, the October 2026 CVE status and the license. **Method:** on 2026-11-06 I read the Adoptium API, GitHub release pages, three release assets (`.sha256.txt`, `.json`, `.sig`) and both SBOMs, `temurin-build` and `ci-jenkins-pipelines` source on GitHub, adoptium.net documentation and `adoptium-support` issues. I used a GET-only fetch tool. The archive was **not** downloaded, extracted or run.

## 1. Verdict
**APPROVE-WITH-CONDITIONS**, for local macOS arm64 hosts. Four independent records give the same SHA-256 for the archive:
- the release's `.sha256.txt`;
- the Adoptium API;
- the release `.json` metadata;
- GitHub's own asset digest.

The archive also has a GPG signature, and it is self-contained, with its own FreeType and zlib bundled. That avoids the six Homebrew library formulae the `openjdk@25` route needs. Several things weigh against it:
- The Linux x64 review's gaps apply here too: there is no authenticated provenance, no AQAvit link and the release is mutable.
- macOS is outside Adoptium's reproducible-build and SLSA L3 coverage.
- The release SBOM hashes an **earlier, pre-signing** archive, not the bytes published.
- I could not establish that the archive's contents are Apple-notarized.

Integrity therefore rests on the SHA-256 and GPG checks, not on Gatekeeper.

## 2. What it is and why it is needed
It is the Java 25 JDK for local builds (`v01-design.md` §4: one review per platform binary, exact pins, no toolchain auto-download). The Homebrew review recommends this archive in its §9, because `brew install openjdk@25` cannot run until six dependency formulae have been reviewed (`inputs/homebrew-openjdk-review.md`).

## 3. Provenance
- **Release.** The release is `adoptium/temurin25-binaries` `jdk-25.0.5+8`, published by `eclipse-temurin-bot` on 2026-10-22T08:18:33Z. Its tag commit, `790405b2…`, is GitHub-verified, and the release page shows no "Immutable" badge ([tag page](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.5%2B8)). It is the newest Temurin 25 GA ([release_names](https://api.adoptium.net/v3/info/release_names?release_type=ga&vendor=eclipse&version=%5B25%2C26%29&page_size=20&sort_order=DESC)).
- **SHA-256: all four sources agree on `36dc14d797aaff6d89aab302cd053fdba6207dfdcf772052fa7b023a94b0e857`.**
  1. The `.sha256.txt` asset, 117 bytes, reads `36dc14d7…e857  OpenJDK25U-jdk_aarch64_mac_hotspot_25.0.5_8.tar.gz` ([asset](https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/OpenJDK25U-jdk_aarch64_mac_hotspot_25.0.5_8.tar.gz.sha256.txt)).
  2. The Adoptium API's `package.checksum` gives the same value, with size 136,446,056 bytes and binary `updated_at` 2026-10-22T08:22:11Z ([API](https://api.adoptium.net/v3/assets/release_name/eclipse/jdk-25.0.5%2B8?architecture=aarch64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=mac&project=jdk&vendor=eclipse)). The API's `checksum_link` points to the same `.sha256.txt`, so sources 1 and 2 are both Adoptium-produced, but they reach us by different paths (GitHub asset vs. api.adoptium.net).
  3. The release `.json` metadata's `sha256` field gives the same value ([asset](https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/OpenJDK25U-jdk_aarch64_mac_hotspot_25.0.5_8.tar.gz.json)).
  4. GitHub's server-side digest for the asset (`sha256:36dc14d7…e857`, dated 2026-10-22T08:22:02Z) gives the same value ([expanded assets](https://github.com/adoptium/temurin25-binaries/releases/expanded_assets/jdk-25.0.5%2B8)).
- **Source and build.**
  - The `.json` gives `scmRef` `jdk-25.0.5+8_adopt`, `openjdk_source` `adoptium/jdk25u@7ec19fb1eda7e42498b7ee4ebde29f77aa961a1c` and `buildRef` `temurin-build@1cf43d7a3e77886bfea7d550a902e151a87d724d`. The Linux x64 SBOM lists the same two commits.
  - Build host: Darwin 23.1.0 arm64 (`build-orka-macos14-arm64-…`), with Xcode 15.2 clang, the macOS 14.2 SDK and boot JDK Temurin-24.0.2+12.
  - Configure flags: `--with-freetype=bundled --with-zlib=bundled`, `--use-jep319-certs`, and `CUSTOM_CACERTS=true`, the same trust-store mechanism as the Linux build.
- **Apple code signing.**
  - At build time, configure logged `macosx code signing mode... debug` and `codesign with hardened runtime is possible... no`, with `SIGN="false"` (`.json`).
  - The pipeline then sends each `OpenJDK*_mac_*.tar.gz` through stage `'sign zip/tgz'` (job `sign_build`, `SIGN_TOOL=eclipse`). It replaces the archive with the signed one and recomputes `.sha256.txt` ([pipeline](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/pipelines/build/common/openjdk_build_pipeline.groovy)).
  - `sign.sh` sends `Contents/MacOS/libjli.dylib`, with `entitlements.plist`, and then the whole JDK directory, zipped and without entitlements, to `https://cbi.eclipse.org/macos/codesign/sign`. It then re-creates the tar.gz ([sign.sh](https://github.com/adoptium/temurin-build/blob/master/sign.sh)). I read this at `master`, not at the build's `BUILD_REF v2026.10.01`.
  - **No notarization command** appears in `sign.sh`, in the visible part of the pipeline, or in the `.json`. The pipeline's comments say "signs and notarizes" and point to a `sign_verification` job that I could not read.
- **SBOM mismatch (explained, not alarming).** The macOS SBOM's "JDK Component" SHA-256 is `431b3bc94113076f7767a07b82261533ce53e0f82c492834f0ca833b82fc6412`, timestamped 2026-10-21T15:55:17Z. That is not the published digest ([SBOM](https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/OpenJDK25U-sbom_aarch64_mac_hotspot_25.0.5_8.json)). The Linux x64 SBOM's JDK hash **does** equal its published digest `68982529…d7fb`. This is consistent with the macOS-only re-pack after signing described above, but it means **the SBOM does not bind the published macOS bytes**.
- **GPG.**
  - The signature asset is `OpenJDK25U-jdk_aarch64_mac_hotspot_25.0.5_8.tar.gz.sig`: 310 bytes, a binary OpenPGP detached signature, with GitHub digest `sha256:51fd43099f1c534fca7cd104c513ea2201146e513fd2b0e3b86d2338c4e6a108`.
  - Adoptium documents a single key, `3B04D753C9050D9A5D343F39843C48A565F8F04B`, UID "Adoptium GPG Key (DEB/RPM Signing Key)", fetched from `keyserver.ubuntu.com` ([GPG docs](https://adoptium.net/installation/gpg-verification)). actions/setup-java bundles the same fingerprint (see the Linux review).
  - To verify:
    ```
    export GNUPGHOME="$(mktemp -d)"   # fresh, empty keyring
    gpg --keyserver keyserver.ubuntu.com --recv-keys 3B04D753C9050D9A5D343F39843C48A565F8F04B
    gpg --status-fd 1 --verify OpenJDK25U-jdk_aarch64_mac_hotspot_25.0.5_8.tar.gz.sig OpenJDK25U-jdk_aarch64_mac_hotspot_25.0.5_8.tar.gz
    ```
  - The output must contain `GOODSIG` and a `VALIDSIG` line whose last field is `3B04D753C9050D9A5D343F39843C48A565F8F04B`. The "not certified with a trusted signature" warning is expected; do **not** assign the key ultimate trust.
- **SLSA and reproducibility.**
  - Authenticated provenance is still "TODO", and SLSA L3 ephemeral builders cover only Linux ([SLSA](https://adoptium.net/docs/slsa)).
  - Reproducibility instructions exist only for Linux x64/aarch64 and Windows x64 ([docs](https://adoptium.net/docs/reproducible-verification-builds)), and this build has `ENABLE_REPRODUCIBLE_COMPARE: false`.
  - No CDXA attestation exists for macOS or for 25.0.5+8 ([temurin-cdxa](https://github.com/adoptium/temurin-cdxa/tree/main/25)).
  - The API gives no `aqavit_results_link`.

## 4. Maintenance and adoption
- **Release history.** macOS aarch64 follows the same release train as Linux: 7 GA builds from 25+36 (2025-09-17) to 25.0.5+8 (2026-10-22) ([feature_releases](https://api.adoptium.net/v3/assets/feature_releases/25/ga?architecture=aarch64&image_type=jdk&os=mac&vendor=eclipse&page_size=10&sort_order=DESC)).
- **Downloads.** This archive has 97,640 downloads; the `.pkg` has 54,356 (API).
- **Scorecard.** `adoptium/temurin-build` scores 8.1 ([Scorecard](https://api.securityscorecards.dev/projects/github.com/adoptium/temurin-build); value as read for the Linux review on the same day).

## 5. Known vulnerabilities at 25.0.5+8
The status is the same as for Linux. 25.0.5+8 is the October 2026 update, and Adoptium lists it as fixed for CVE-2026-114820 (eMudhra) ([announcement](https://adoptium.net/news/2026/10/eclipse-temurin-17021-21013-2505-2701-available), [eMudhra](https://adoptium.net/news/2026/10/temurin-october-2026-emudhra)). **No publisher-acknowledged open vulnerability was found on 2026-11-06.** Gaps:
- OSV has no ecosystem for JDK binaries, and my tooling cannot POST, so I made no OSV query.
- The full October CVE list was not retrieved.
- Nothing macOS-specific was checked beyond the release announcement.

## 6. License
GPLv2 with the Classpath Exception (plus the Assembly Exception), the same as Linux ([FAQ](https://adoptium.net/docs/faq/)).

## 7. Execution privileges
- **Install.** The developer runs `tar` with no install scripts and no network. Unlike the `.pkg` ([macOS docs](https://adoptium.net/installation/macOS)), it needs no admin rights and writes nothing to `/Library/Java`, so `/usr/libexec/java_home` and `/usr/bin/java` will not find it.
- **Run time.** This happens in the build and test phases, as the developer: Gradle daemon, javac, JUnit, PIT and example tests. That code has the full file system, network, process execution, JNI and reflection, as on Linux.
- **macOS entitlements.** If they are applied as `entitlements.plist` lists them ([plist](https://github.com/adoptium/temurin-build/blob/master/entitlements.plist)), the JVM may load unsigned libraries and honours `DYLD_*` variables: `allow-jit`, `allow-unsigned-executable-memory`, `disable-library-validation`, `allow-dyld-environment-variables` and `debugger`. Code running as the user can therefore inject into it. This is no stronger than any unhardened user process. Which entitlements the shipped binaries actually carry is not verified (condition 6).
- **Gatekeeper and quarantine.** Gatekeeper assesses quarantined files. Adoptium maintainers report that a tarball fetched with curl/wget runs without the attribute ("As long as I download it with curl/wget or remove the quarantine attribute, it does its job", [#144](https://github.com/adoptium/adoptium-support/issues/144)). They direct users who hit "developer cannot be verified" to the signed PKG ([#808](https://github.com/adoptium/adoptium-support/issues/808)).
- **What this means for us.** With a curl download, Gatekeeper does not take part, and Apple signing is **not** a control this approval relies on. A browser download may trigger blocking, which the conditions handle.

## 8. Dependencies as published
The archive is self-contained. FreeType 2.14.3 (SBOM `metadata.tools`) and zlib are bundled, so it uses no Homebrew dylibs. The boot JDK (Temurin-24.0.2+12) and Xcode were used at build time only. Bundled third-party code is listed in the macOS SBOM, which I read only partly and which describes the pre-signing archive. Linked system frameworks (`otool -L`) were not inspected. Nothing is flagged for a separate review.

## 9. Alternatives considered
- **Homebrew `openjdk@25` 25.0.5.0.3.** It is blocked on six unreviewed library formulae, and it differs from CI in version and trust store.
- **The `.pkg`.** It needs admin rights and writes to `/Library/Java`.
- **Gradle toolchain auto-download.** Excluded by the design.

## 10. Conditions and residual risk
The conditions are in the frontmatter. Residual risks:
- Trust rests on Adoptium's macOS build host and the Eclipse CBI signing service, with no authenticated provenance or reproducibility for macOS.
- The SBOM cannot be matched to the published bytes.
- Local and CI JDKs share version, source and build scripts, but they are different binaries, so local results remain non-authoritative.

## 11. Not verified
- I did not run the GPG signature check. The fetch tool reported issuer-fingerprint bytes resembling `…5D343F39843C48A565F8F04B`, but I could not parse the packets locally.
- Whether the archive's Mach-O binaries carry a Developer ID signature, hardened runtime, the listed entitlements, or a notarization ticket on Apple's servers. I also did not verify the Eclipse Apple team ID.
- Whether macOS `tar` propagates quarantine from a quarantined tarball.
- The archive's top-level directory name, its listing, and its minimum macOS version.
- The CBI service's behaviour, and `sign.sh` at the exact `BUILD_REF`.
- The full SBOM, `cacerts` equality with the Linux build, and the October CVE list.
