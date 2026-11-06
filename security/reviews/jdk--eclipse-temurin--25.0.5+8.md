---
component: "Eclipse Temurin JDK, Linux x64 HotSpot: OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz (adoptium/temurin25-binaries)"
kind: jdk
version: "25.0.5+8"
verdict: APPROVE-WITH-CONDITIONS
scope: "CI only: GitHub-hosted ubuntu-24.04 x64 runners, as the JDK that runs Gradle, javac (--release 25), tests, PIT and the example app's tests; provisioned through actions/setup-java"
conditions: [
  "Only the archive OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz with SHA-256 68982529869257d0ee6b824ec5aa270ff8b118bb438670da0b97ebdab178d7fb, checked against the allowlist before extraction (staged by the controller, then actions/setup-java distribution: jdkfile)",
  "The runner image's preinstalled JDK 25 (JAVA_HOME_25_X64 / hosted tool cache) is not used: its bytes are not covered by this review",
  "Before first use a human verifies the archive's .sig with the Adoptium key 3B04D753C9050D9A5D343F39843C48A565F8F04B and records the result",
  "Gradle runs on and compiles with this JDK only (no toolchain auto-download; see the Gradle 9.8.2 review)",
  "Approval lapses if the asset's SHA-256 changes (a respin under the same name) or when Adoptium publishes a Temurin 25 release fixing a vulnerability that affects 25.0.5+8; either is a new review"
]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Eclipse Temurin 25.0.5+8 (Linux x64)

**Method.** On 2026-11-06 I read the Adoptium API, the GitHub release, adoptium.net documentation and news, OpenSSF Scorecard and the upstream `openjdk/jdk25u` tags, using a GET-only fetch tool. Nothing was downloaded or executed.

## 1. Verdict
**APPROVE-WITH-CONDITIONS.** This is the newest Temurin 25 GA ([release_names](https://api.adoptium.net/v3/info/release_names?release_type=ga&vendor=eclipse&version=%5B25%2C26%29&page_size=20&sort_order=DESC)). It was published by `eclipse-temurin-bot`, Adoptium publishes its SHA-256 in two places that agree, and it has a GPG signature. Four things weigh against it:
- There is no verifiable build provenance; Adoptium's own SLSA page lists authenticated provenance as "TODO".
- No independent reproducibility attestation exists for this build yet.
- No AQAvit results link is published.
- The release is mutable.

The conditions therefore pin the exact bytes, keep the runner's unverified copy out, and require a one-time signature check.

## 2. What it is and why it is needed
It is the Java 25 JDK for CI, provisioned through `actions/setup-java`. The design requires exact versions, no automatic toolchain downloads and a separate review for each platform binary. Local hosts use the Homebrew build, which has its own review (`v01-design.md` §4).

## 3. Provenance
- **Publisher and release.** The publisher is Eclipse Adoptium (Eclipse Foundation), GitHub org `adoptium`, repository `temurin25-binaries`. Release `jdk-25.0.5+8` was published on 2026-10-22T08:18:33Z by `eclipse-temurin-bot` with `"immutable":false` ([GitHub API](https://api.github.com/repos/adoptium/temurin25-binaries/releases/tags/jdk-25.0.5%2B8)). The tag commit `790405b2b35f68f615679082036cbeb1ae58144a` is shown as verified with GitHub's web-flow key ([release](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.5%2B8)).
- **Source.** The build's `scm_ref` is `jdk-25.0.5+8_adopt` ([Adoptium API](https://api.adoptium.net/v3/assets/release_name/eclipse/jdk-25.0.5%2B8?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=linux&project=jdk&vendor=eclipse)). Upstream tag `jdk-25.0.5+8` (2026-10-20) is commit `fda231468e56663992785ec199a6efb8eca3cc7a` ([jdk25u tags](https://github.com/openjdk/jdk25u/tags)).
- **Hash.** The archive is 141,341,188 bytes. Adoptium's `package.checksum` matches the GitHub asset `digest`: `sha256:68982529869257d0ee6b824ec5aa270ff8b118bb438670da0b97ebdab178d7fb`. The asset was uploaded at 2026-10-22T12:28:42Z and has not been replaced since. The release kept receiving other assets until 10-29.
- **Signing.** The archive ships with a `.sig`, `.sha256.txt`, `.json` metadata and a CycloneDX SBOM (which has its own `.sig`). The documented GPG key is `3B04D753C9050D9A5D343F39843C48A565F8F04B`; its UID reads "DEB/RPM Signing Key" ([verification](https://adoptium.net/installation/gpg-verification)). setup-java bundles the same fingerprint ([adoptium-key.ts](https://raw.githubusercontent.com/actions/setup-java/77dd5b2ce225ea11469928ac317850245a74ae78/src/distributions/temurin/adoptium-key.ts)).
- **SLSA.** Adoptium claims Level 2, but lists "Provenance – Authenticated" as "TODO" and Level 3 as "In progress" ([SLSA](https://adoptium.net/docs/slsa)). The release carries no attestation or in-toto files.
- **Reproducibility.** Adoptium says JDK 21+ is fully reproducible on Linux x64 ([Aug 2026 update](https://adoptium.net/news/2026/09/temurin-reproducible-verification-builds-2026)). Third-party CDXA attestations exist for 25.0.2+10, 25.0.3+9, 25.0.4+7 and 25.0.4.1+1, but **not for 25.0.5+8** ([temurin-cdxa](https://github.com/adoptium/temurin-cdxa/tree/main/25)). The verification procedure leaves `cacerts` out of the comparison ([procedure](https://adoptium.net/docs/reproducible-verification-builds/reproduce-linux-x64)).
- **Testing.** The FAQ requires every release to pass AQAvit and the JCK ([FAQ](https://adoptium.net/docs/faq/)). The TCK statement is blanket, not per release ([affidavit](https://adoptium.net/temurin/tck-affidavit)). The API publishes no `aqavit_results_link` for 25.0.5+8, 25.0.4+7 or 25.0.4.1+1.

## 4. Maintenance and adoption
- **Release history** ([feature_releases](https://api.adoptium.net/v3/assets/feature_releases/25/ga?architecture=x64&image_type=jdk&os=linux&vendor=eclipse&page_size=10&sort_order=DESC)). Updates are quarterly, with out-of-band releases in between. 25 is the most recent LTS ([available_releases](https://api.adoptium.net/v3/info/available_releases)).
  - 25+36: 2025-09-17
  - 25.0.1+8: 2025-10-24
  - 25.0.2+10: 2026-01-22
  - 25.0.3+9: 2026-04-22
  - 25.0.4+7: 2026-07-27
  - 25.0.4.1+1: 2026-08-19
  - 25.0.5+8: 2026-10-22
- **Scorecard.** `adoptium/temurin-build` scores 8.1 ([scorecard](https://api.securityscorecards.dev/projects/github.com/adoptium/temurin-build)): Code-Review 10, Maintained 10, Vulnerabilities 10, Token-Permissions 0, Signed-Releases −1. `temurin25-binaries` is not scored (404).
- **Usage.** This archive has been downloaded 979,851 times. GitHub's ubuntu-24.04 image 20261101.360.1 preinstalls "25.0.5+8" ([runner image](https://raw.githubusercontent.com/actions/runner-images/main/images/ubuntu/Ubuntu2404-Readme.md)).

## 5. Known vulnerabilities at 25.0.5+8
25.0.5 is the October 2026 update.
- **CVE-2026-114820 (eMudhra CA compromise).** Adoptium's announcement says it "is fixed as we did a respin" ([announcement](https://adoptium.net/news/2026/10/eclipse-temurin-17021-21013-2505-2701-available)). Its eMudhra post lists "JDK 25.0.5+8" as fixed: the trust store is built from Mozilla's NSS 3.131.1 data, which drops `emSign ECC Root CA - G3` and `AAA Certificate Services` ([eMudhra](https://adoptium.net/news/2026/10/temurin-october-2026-emudhra)).
- **Later upstream tags.** `jdk-25.0.5.0.2-ga` and `jdk-25.0.5.0.3-ga` add 6 commits: two CA backports (8395142, 8395690) and version bumps ([compare](https://github.com/openjdk/jdk25u/compare/jdk-25.0.5-ga...jdk-25.0.5.0.3-ga)). Adoptium says these are "not relevant to our distribution".

**Result: no newer Temurin 25 GA, and no publisher-acknowledged open vulnerability on 2026-11-06.** This check has gaps:
- The full October CVE list is not covered: the release notes render with JavaScript and openjdk.org was not reachable.
- I did not run an OSV POST query, because the tooling is GET-only.
- deps.dev does not index JDK binaries.

## 6. License
GPLv2 with the Classpath Exception ([FAQ](https://adoptium.net/docs/faq/)). The OpenJDK code also carries the GPLv2 Assembly Exception, and the build scripts are Apache-2.0 ([about](https://adoptium.net/about/)).

## 7. Execution privileges
- **Install.** Extraction with `tar`: no install scripts and no network.
- **Run time.** It runs all JVM code in CI: Gradle and javac in the build phase, and JUnit, PIT and the example's tests in the test phase. That code has the file system, network, process execution, JNI/native code and reflection.
- **Bundled tools.** `jshell`, `jwebserver`, `jdb`, `jcmd` (Attach API) and `keytool`.
- **Trust store.** `cacerts` (Mozilla NSS 3.131.1) governs TLS for every dependency download. Dependency verification (see the Gradle review) limits what a mis-issued certificate could do.

## 8. Dependencies as published
The archive is self-contained and declares no package dependencies. It needs the runner's glibc; I did not check the version. OpenJDK's bundled third-party code and `cacerts` are listed in the CycloneDX SBOM asset `OpenJDK25U-sbom_x64_linux_hotspot_25.0.5_8.json`, which this review did **not** read.

## 9. Alternatives considered
- **The runner's preinstalled 25.0.5+8.** Same label, but its bytes are unverified, so it is rejected as a source.
- **Other OpenJDK builds** (Corretto, Zulu, Microsoft). No advantage, and the design chose Temurin.
- **Homebrew `openjdk@25`.** Reviewed separately, for local hosts only.

## 10. Conditions and residual risk
The conditions are in the frontmatter. setup-java checks the checksum and signature against what the Adoptium API returns at run time. Only the controller's comparison with the allowlist hash binds CI to the bytes reviewed here.

Residual risk:
- There is no authenticated provenance, so trust rests on Adoptium's CI and signing key.
- Temurin still trusts `emSign Root CA - G1`, following Mozilla bug 2083449 ([eMudhra](https://adoptium.net/news/2026/10/temurin-october-2026-emudhra)). I did not verify the OpenJDK advisory's own list of removed roots.

## 11. Not verified
- The GPG signature, the SBOM contents and `.sha256.txt`. All three redirect to release-assets.githubusercontent.com, which I could not reach.
- Reproducibility and AQAvit results for this build.
- The October CVE list.
- Whether the runner image's copy is byte-identical.
- The binary's run-time behaviour, including any network activity.
