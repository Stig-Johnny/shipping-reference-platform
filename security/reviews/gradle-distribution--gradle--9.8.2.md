---
component: "Gradle Build Tool distribution gradle-9.8.2-bin.zip and its wrapper JAR gradle-wrapper.jar (services.gradle.org)"
kind: gradle-distribution
version: "9.8.2"
verdict: APPROVE-WITH-CONDITIONS
scope: "Build tool for every project of this repository (root build and the included build-logic/ build), in CI and on local build hosts, started only through the wrapper pinned below"
conditions: [
  "gradle/wrapper/gradle-wrapper.properties sets distributionUrl to https://services.gradle.org/distributions/gradle-9.8.2-bin.zip and distributionSha256Sum=21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607",
  "gradle/wrapper/gradle-wrapper.jar has SHA-256 3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98, checked by the trusted controller before any gradlew run; CI never regenerates the wrapper",
  "CI starts from an empty GRADLE_USER_HOME: no restored ~/.gradle/wrapper or ~/.gradle/caches (actions/setup-java cache input unset), because the wrapper checks the SHA-256 only on first download",
  "Runs on the reviewed JDK 25 only: org.gradle.java.installations.auto-download=false, no toolchain resolver plugin (e.g. foojay) applied",
  "Dependency verification stays strict from a reviewed gradle/verification-metadata.xml; CI never runs --write-verification-metadata",
  "org.gradle.mirror.maven.settings stays unset (false); no Develocity/build-scan or other plugin without its own review",
  "Before first use a human verifies gradle-9.8.2-bin.zip.asc and gradle-9.8.2-wrapper.jar.asc against signing subkey F3FF33E96F18AA62DD580F9651FBF517CE6D6B80 and records the result",
  "Any other Gradle version, including a Dependabot wrapper bump, is a new review"
]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Gradle 9.8.2 distribution and wrapper JAR

**Method.** I read the publisher pages, the GitHub repository at tag `v9.8.2`, OpenSSF Scorecard, Gradle's advisory pages, osv.dev and deps.dev on 2026-11-06, using a GET-only fetch tool. Nothing was downloaded, installed or executed.

## 1. Verdict
**APPROVE-WITH-CONDITIONS.** 9.8.2 is the current release ([versions/current](https://services.gradle.org/versions/current)). It falls outside the affected range of every Gradle advisory I read, including three High advisories that were fixed in 9.8.1 ([release notes](https://docs.gradle.org/9.8.2/release-notes.html)). Gradle publishes a SHA-256 and a PGP signature for both artifacts, but no build provenance. The bigger risk is what Gradle runs rather than Gradle itself: every settings script, build script and plugin executes with the build user's full rights. The conditions therefore pin both artifacts, the JDK and dependency verification.

## 2. What it is and why it is needed
Gradle builds `:core`, `:junit5`, `:example-app` and `build-logic/`. Java 25 support starts at 9.1.0. The build runs on JDK 25 and compiles with `--release 25`. The design verifies the wrapper JAR and the distribution separately and defers a Maven reference build (`v01-design.md` §3, §4).

## 3. Provenance
- **Publisher and source.** Gradle Inc. serves the distribution from `services.gradle.org`. The source is `github.com/gradle/gradle`, where tag `v9.8.2` points at commit `8de7bcf76e785507ad92f0741ab9235a430c95e6` ([release](https://github.com/gradle/gradle/releases/tag/v9.8.2)). The same commit is the `commitId` in [versions/all](https://services.gradle.org/versions/all), with `buildTime` `20261019162926+0000`.
- **The GitHub release.** `bot-gradle` published it on 2026-10-19T17:09:50Z. It has no assets and is not marked immutable. The commit shows "Verified" with GitHub's web-flow key `B5690EEEBB952194`. That signature proves GitHub created the merge commit, not who wrote the code. I could not retrieve the tag object: the API returned 403.
- **Published SHA-256.** The values on [release-checksums](https://gradle.org/release-checksums/) match the `checksum` and `wrapperChecksum` fields of [versions/all](https://services.gradle.org/versions/all) character for character:
  - `gradle-9.8.2-bin.zip` (144.60 MB): `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`
  - `gradle-9.8.2-wrapper.jar`: `3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98`, the same value as 9.8.1
- **PGP signatures.** `.asc` files are listed for the zip and the wrapper JAR, dated 19-Oct-2026 16:46 ([listing](https://services.gradle.org/distributions/)). Gradle says it signs "all distribution artifacts, including the Wrapper JAR" ([wrapper docs](https://docs.gradle.org/9.8.2/userguide/gradle_wrapper.html)).
- **Signing keys** ([keys](https://gradle.org/keys/)). The primary key is `EA96F38569C044AAEF7FCF732887F479B0B9771A`, created 2026-08-14, and the signing subkey is `F3FF33E96F18AA62DD580F9651FBF517CE6D6B80`. The previous key, `1BD97A6A…5E3D`, was "Revoked 2026-08-08". Gradle's documentation is inconsistent in two places. The UID is `info@gradle.com` in the wrapper docs but `maven-publishing@gradle.com` on the keys page. The revocation date is also earlier than the new key's creation date.
- **No provenance or SBOM** was found. Gradle says wrapper JARs are reproducible, except for versions 3.3–4.0.2 ([wrapper docs](https://docs.gradle.org/9.8.2/userguide/gradle_wrapper.html)).

## 4. Maintenance and adoption
- **Release cadence** ([releases](https://gradle.org/releases/)). 9.5.0 shipped on 04-28, 9.6.0 on 06-18, 9.7.0 on 08-06, 9.8.0 on 09-24, 9.8.1 on 10-07 and 9.8.2 on 2026-10-19. That is a minor release roughly every 6–7 weeks, with patch releases in between. The 8.14.x line is still patched (8.14.6, 10-08), and `9.9.0-rc-1` is active.
- **Scorecard: 4.6** on 2026-11-02, scored on commit `3ce94cb…` rather than the tag ([scorecard](https://api.securityscorecards.dev/projects/github.com/gradle/gradle)).
  - High: Maintained 10, Security-Policy 10, Code-Review 6, CII 5.
  - Low: Token-Permissions 0, Pinned-Dependencies 0, SAST 0, Binary-Artifacts 0 (the committed wrapper JAR) and Branch-Protection 3.
- **Security process.** Vulnerabilities are reported to security@gradle.com ([policy](https://github.com/gradle/gradle/security/policy)), and 21 advisories have been published ([advisories](https://github.com/gradle/gradle/security/advisories)). I did not check usage figures.

## 5. Known vulnerabilities at 9.8.2
GHSA records Gradle as the package `maven org.gradle:gradle-core` ([advisories](https://github.com/gradle/gradle/security/advisories)).

| Advisory | Affected | Patched | 9.8.2 |
|---|---|---|---|
| [GHSA-xwqc-3h47-hg64](https://github.com/gradle/gradle/security/advisories/GHSA-xwqc-3h47-hg64), [GHSA-mvvg-497x-hmj8](https://github.com/gradle/gradle/security/advisories/GHSA-mvvg-497x-hmj8), GHSA-j5m7-59rp-24f5 (High, 2026-10-07): daemon and worker deserialisation before authentication; repository disabling | ≤ 8.14.5; 9.0.0–9.8.0 | 8.14.6, 9.8.1 | not affected |
| GHSA-mqwm-5m85-gmcv (CVE-2026-22865), GHSA-w78c-w6vf-rw82 (CVE-2026-22816) (High, 2026-01-16) | < 8.14.4; 9.0.0–9.2.1 | 8.14.4, ≥ 9.3.0 | not affected |
| GHSA-465q-w4mf-4f4r (CVE-2025-27148) | 8.12 only | 8.12.1 | not affected |

The 15 other repository advisories, and the two Gradle entries on [osv.dev](https://osv.dev/list?q=gradle&ecosystem=Maven) (GHSA-hhr2-f668-ff2w, GHSA-pprq-4488-wgqx), date from 2019–2023, before 9.0. I did not read their affected ranges individually. **Result: none found affecting 9.8.2 on 2026-11-06.** That result has two gaps: I could not issue OSV's POST query, and the GitHub advisory API returned 403. Bundled libraries are covered in §8.

## 6. License
Gradle itself is Apache-2.0 ([LICENSE](https://raw.githubusercontent.com/gradle/gradle/v9.8.2/LICENSE)). The bundled libraries keep their own licences, which I did not read. deps.dev reports LGPL-2.1 for jcifs, and "non-standard" (unmapped) licences for Bouncy Castle, Kryo, jsoup, JSch and JUnit 4. This repository uses the distribution; it does not redistribute it.

## 7. Execution privileges
All of the following runs in the build phase, in CI and locally.
- **Wrapper.** The JAR runs on Java 8 or later. It downloads `distributionUrl` into `GRADLE_USER_HOME/wrapper/dists`. It checks `distributionSha256Sum` "only … if the configured Wrapper distribution hasn't been downloaded yet" ([wrapper docs](https://docs.gradle.org/9.8.2/userguide/gradle_wrapper.html)).
- **Build logic.** Settings scripts, build scripts, `build-logic/` and every plugin run as ordinary JVM code. They have file-system access, network access, process execution, reflection and native code (through native-platform, JNA and file-events).
  - Gradle itself advises "Do not Run `./gradlew` on Untrusted Projects" ([best practices](https://docs.gradle.org/9.8.2/userguide/best_practices_security.html)).
  - Gradle provides no sandbox. The only isolation is the design's controller and network-restricted worker.
- **Daemon.** A long-lived JVM, reached over a loopback TCP socket that is authenticated with a secret token. Since 9.8.1, the token is checked before any message is deserialised, and workers use a per-connection secret ([GHSA-xwqc](https://github.com/gradle/gradle/security/advisories/GHSA-xwqc-3h47-hg64)). The daemon exits after 3 h idle ([daemon](https://docs.gradle.org/9.8.2/userguide/gradle_daemon.html)).
- **Network.** Gradle contacts the declared repositories and the wrapper URL. The bundled S3, GCS, SFTP and SMB clients run only if a build declares such a repository.
- **Toolchains** ([toolchains](https://docs.gradle.org/9.8.2/userguide/toolchains.html)).
  - JDK auto-download is on by default, but it needs a resolver plugin.
  - Auto-detection reads `JAVA_HOME`, OS locations, SDKMAN, asdf, Jabba, Maven toolchains and IntelliJ.
  - setup-java writes `~/.m2/toolchains.xml`, so auto-detection can pick that file up.
- **Test phase.** Gradle forks test JVMs and runs PIT via `JavaExec`.

## 8. Bundled libraries (the distribution's `lib/`)
Versions are taken from `gradle/dependency-management/distribution.versions.toml` and `shared-versions.properties` at `v9.8.2` ([catalog](https://raw.githubusercontent.com/gradle/gradle/v9.8.2/gradle/dependency-management/distribution.versions.toml)). I did not list the zip's `lib/` directory itself.

| Area | Libraries |
|---|---|
| Languages | Groovy 4.0.33; Kotlin 2.4.10 (compiler-embeddable, scripting, stdlib); kotlinx-coroutines 1.10.2; kotlinx-serialization 1.11.0 |
| Parsing | ASM 9.10.1; javaparser-core 3.27.1; jsoup 1.23.2; tomlj 1.0.0; SnakeYAML 2.6; Gson 2.13.1; Jackson 2.22.3 |
| Repositories and VCS | httpclient 4.5.14 / httpcore 4.4.16; AWS SDK 1.12.780; google-api-client 1.34.0 / google-http-client 1.42.2; sshd 2.15.0; JSch 0.2.16; jcifs 1.3.17; JGit 7.2.1.202505142326-r |
| Crypto, Maven, Ivy, Ant | Bouncy Castle bcprov/bcpg 1.85; maven-model/settings 3.9.5; plexus-utils 3.6.1; plexus-sec-dispatcher 2.0; Ivy 2.5.3; Ant 1.10.17 |
| Native | native-platform 0.22-milestone-29; gradle-fileevents 0.2.8; JNA 5.17.0; jansi 2.4.2 |
| Utilities | Guava 33.4.6-jre; commons-compress 1.28.0; commons-io 2.20.0; commons-lang3 3.20.0; fastutil 8.5.2; Kryo 2.24.0; slf4j 2.0.17 |
| "Provided" (used only if a build selects them) | TestNG 6.3.1; JUnit 4.13.2; bsh 2.0b6; jcommander 1.78; maven-core 3.9.5; zinc 1.12.0 |

- **Advisories.** I checked 40 of these libraries on deps.dev.
  - **Guava 33.4.6-jre** has [GHSA-xxph-c9ww-hj94](https://api.deps.dev/v3/advisories/GHSA-xxph-c9ww-hj94) (CVE-2026-102554, CVSS 5.9): deserialising `CompactHashMap` and related classes allocates memory without bound, a DoS. I did not trace whether Gradle can reach it.
  - The other 39 have no advisories.
- **Flagged for age**, though they have no advisories: jcifs (2012), Kryo (2014), TestNG (2011) and bsh (2016).
- **Coverage.** These JARs ship inside the zip, so `distributionSha256Sum` covers them. Dependency verification does not.

## 9. Alternatives considered
Maven is deferred by the design. Gradle 8.14.x does not support Java 25, which needs 9.1.0 or later ([compatibility](https://docs.gradle.org/9.8.2/userguide/compatibility.html)). 9.9.0-rc-1 is not GA.

## 10. Conditions and residual risk
The conditions are in the frontmatter. One needs explaining: CI must start from an empty `GRADLE_USER_HOME`, because a restored cache bypasses the wrapper's checksum.

Residual risks:
- Build logic is unrestricted, and only the design's isolation contains it.
- The zip has no provenance.
- The Guava DoS advisory.
- Gradle's key documentation is inconsistent.

## 11. Not verified
- PGP signatures, and the `.sha256` files themselves. Both redirect to `downloads.gradle.org`, which I could not reach.
- The zip's actual `lib/` against the catalog, and the bundled libraries' licences.
- The tag object.
- Reproducibility of the zip.
- Affected ranges of the older advisories.
- An OSV POST query.
- Usage metrics.
