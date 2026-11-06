---
component: "Testcontainers 2.0.5 internals: com.github.docker-java:docker-java-api, docker-java-transport, docker-java-transport-zerodep; net.java.dev.jna:jna; org.apache.commons:commons-compress; org.rnorth.duct-tape:duct-tape; commons-codec:commons-codec; commons-io:commons-io"
kind: maven
version: "docker-java-* 3.7.1; jna 5.18.1; commons-compress 1.28.0; duct-tape 1.0.8; commons-codec 1.21.0; commons-io 2.20.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "apps/shipping-api test configurations only, as locked: testCompileClasspath and testRuntimeClasspath (commons-io 2.20.0 on testCompileClasspath only; testRuntimeClasspath resolves 2.21.0 through Structurizr, covered by that review). Same hosts and Docker endpoint as the Testcontainers 2.0.5 review"
conditions: ["Exact versions and jar SHA-256 values as in allowlist-entries.json; any other version or hash is a new review", "Test configurations only, and every condition of the Testcontainers 2.0.5 review holds (local daemon over its Unix socket, Ryuk disabled, no registry credentials in the test JVM, pinned postgres image)", "Linux and macOS hosts only: the Windows named-pipe transport (JNA kernel32) is not used", "The test JVM sets none of jna.boot.library.path, jna.library.path, jna.nosys=false or jna.nounpack; JNA's native stub is the one bundled in the pinned jar", "Re-review on the next Testcontainers or docker-java release (the zerodep jar embeds a relocated httpclient5 5.5.1 that is affected by GHSA-hjcp-jmpx-g3qm, and it cannot be upgraded independently)", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: Testcontainers internals (docker-java 3.7.1, JNA, commons-compress, duct-tape, commons-codec, commons-io)

**Method.** Read on 2026-11-06 with WebFetch: Central listings, sidecars and POMs; deps.dev (versions and dependency
graphs); OSV; Scorecard; and docker-java, JNA and Testcontainers sources at their tags. Nothing was downloaded, built or
run. **Correction to the brief:** the unshaded Apache HttpClient 5 on our classpath does not belong to docker-java. It
is reviewed separately (HttpComponents review).

## 1. Verdict
**APPROVE-WITH-CONDITIONS, test-only, under the Testcontainers 2.0.5 review's conditions.**
- **These jars carry Testcontainers' privileged reach:** docker-java speaks the Docker Engine API, which is
  root-equivalent on Linux, and JNA loads native code.
- **No advisory applies to these versions** as published. Two weak points remain:
  - The zerodep jar bundles a relocated httpclient5 5.5.1, which is inside GHSA-hjcp-jmpx-g3qm. That is a connection
    leak (DoS), triggered only by a misbehaving daemon.
  - duct-tape is archived upstream.
- Neither justifies rejection for test code that talks only to the local daemon.

## 2. What it is and why it is needed
Testcontainers 2.0.5 declares duct-tape 1.0.8, commons-compress 1.28.0, docker-java-api 3.7.1 and
docker-java-transport-zerodep 3.7.1 as compile dependencies
([POM](https://repo1.maven.org/maven2/org/testcontainers/testcontainers/2.0.5/testcontainers-2.0.5.pom)).
- zerodep brings docker-java-transport and JNA.
- commons-compress brings commons-io 2.20.0 and commons-codec, which it declares at 1.19.0; Boot's BOM lifts it to 1.21.0
  ([compress POM](https://repo1.maven.org/maven2/org/apache/commons/commons-compress/1.28.0/commons-compress-1.28.0.pom),
  [Boot BOM](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom)).

The design requires real PostgreSQL in tests (`v01-design.md` §4).

## 3. Provenance
| Artifact | Publisher → source | Central date | Publisher SHA-256 |
|---|---|---|---|
| docker-java-api, -transport, -transport-zerodep 3.7.1 | docker-java maintainers → docker-java/docker-java ([parent](https://repo1.maven.org/maven2/com/github/docker-java/docker-java-parent/3.7.1/docker-java-parent-3.7.1.pom)) | 2026-03-18 | all three sidecars **match** ([zerodep dir](https://repo1.maven.org/maven2/com/github/docker-java/docker-java-transport-zerodep/3.7.1/)) |
| jna 5.18.1 | Timothy Wall, Matthias Bläsing → java-native-access/jna ([POM](https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.18.1/jna-5.18.1.pom)) | 2025-09-30 | **none** (md5/sha1 only) |
| commons-compress 1.28.0 | ASF Commons | 2025-07-26 | **none** |
| duct-tape 1.0.8 | Richard North → rnorth/duct-tape ([POM](https://repo1.maven.org/maven2/org/rnorth/duct-tape/duct-tape/1.0.8/duct-tape-1.0.8.pom)) | 2019-04-28 | **none** |
| commons-codec 1.21.0 | ASF Commons ([POM](https://repo1.maven.org/maven2/commons-codec/commons-codec/1.21.0/commons-codec-1.21.0.pom)) | 2026-01-23 | **none** |
| commons-io 2.20.0 | ASF Commons ([POM](https://repo1.maven.org/maven2/commons-io/commons-io/2.20.0/commons-io-2.20.0.pom)) | 2025-07-14 | **none** |

- **All have `.asc`. Five of the eight have no publisher SHA-256** (trust-on-first-download).
- **Attestations:** none; deps.dev shows `slsaProvenances: []` for all eight.
- **docker-java releases** are published by GitHub Actions; 3.7.1 was signed by a maintainer
  ([releases](https://github.com/docker-java/docker-java/releases)).

## 4. Maintenance and adoption
- **docker-java:**
  - 3.7.1 is the newest release; there has been none for about 7.5 months.
  - Scorecard 3.8 (2026-11-02): Maintained 0, Code-Review 2, Token-Permissions 0
    ([SC](https://api.securityscorecards.dev/projects/github.com/docker-java/docker-java)).
- **JNA:** 5.19.1 (2026-06-12) is newer. Scorecard 3.7, Binary-Artifacts 0
  ([SC](https://api.securityscorecards.dev/projects/github.com/java-native-access/jna)).
- **duct-tape:** **archived by its owner on 2022-11-05**; the last release was 2019, and there is no Scorecard
  ([repo](https://github.com/rnorth/duct-tape)).
- **ASF Commons** (all Maintained 10):
  - commons-compress 1.28.0 is the newest; Scorecard 8.3.
  - commons-codec 1.23.0 is newer; Scorecard 7.8.
  - commons-io 2.22.0 is newer; Scorecard 7.8.

## 5. Known vulnerabilities at these versions
**None found on 2026-11-06 for the eight artifacts as published:**
- `inputs/osv.json` is empty and deps.dev `advisoryKeys: []` for all eight.
- commons-compress CVE-2024-25710 and CVE-2024-26308 were fixed in 1.26.0
  ([GHSA-4g9r-vxhx-9pgx](https://api.osv.dev/v1/vulns/GHSA-4g9r-vxhx-9pgx), [GHSA-4265-ccf5-phj5](https://api.osv.dev/v1/vulns/GHSA-4265-ccf5-phj5)).
- commons-io CVE-2024-47554 was fixed in 2.14.0 ([GHSA-78wr-2p64-hpwj](https://api.osv.dev/v1/vulns/GHSA-78wr-2p64-hpwj)).

**Bundled inside zerodep (invisible to scanners):**
- **The bundled code.** The zerodep POM shades docker-java-transport-httpclient5 and relocates `org.apache` to
  `com.github.dockerjava.zerodep.shaded.org.apache`
  ([pom.xml](https://raw.githubusercontent.com/docker-java/docker-java/3.7.1/docker-java-transport-zerodep/pom.xml)).
  That module depends on httpclient5 **5.5.1**
  ([POM](https://repo1.maven.org/maven2/com/github/docker-java/docker-java-transport-httpclient5/3.7.1/docker-java-transport-httpclient5-3.7.1.pom)).
- **The advisory.** httpclient5 5.5.1 is inside [GHSA-hjcp-jmpx-g3qm](https://api.osv.dev/v1/vulns/GHSA-hjcp-jmpx-g3qm)
  (MODERATE, a pool-exhaustion DoS on a bad `Content-Encoding`), fixed in 5.6.3.
- **Reachability.** The only server it talks to is our local Docker daemon. This matches the Testcontainers review §5.

## 6. License
- **docker-java, commons-*:** Apache-2.0.
- **duct-tape:** MIT.
- **jna:** LGPL-2.1-or-later or Apache-2.0 (dual; we take Apache-2.0).

All from the POMs. None is distributed.

## 7. Execution privileges (test phase only)
- **docker-java (Docker API).** It connects to `unix://`, `npipe://` or `tcp://`, as configured by Testcontainers
  ([ApacheDockerHttpClientImpl](https://raw.githubusercontent.com/docker-java/docker-java/3.7.1/docker-java-transport-httpclient5/src/main/java/com/github/dockerjava/httpclient5/ApacheDockerHttpClientImpl.java)).
  - **Unix sockets:** `UnixSocket` first uses the JDK's `UnixDomainSocketAddress` and falls back to JNA libc calls only
    if that fails ([UnixSocket](https://raw.githubusercontent.com/docker-java/docker-java/3.7.1/docker-java-transport/src/main/java/com/github/dockerjava/transport/UnixSocket.java)).
    On JDK 25, JNA should therefore stay unused on Linux and macOS (inference).
  - **Windows named pipes:** use JNA `kernel32` (condition 3 excludes them).
  - It maps daemon responses with Jackson. That Jackson is shaded inside the Testcontainers jar; see the Testcontainers
    review.
- **JNA (native code).**
  - It loads its `jnidispatch` stub in this order: `jna.boot.library.path`, then the system library path (only if
    `jna.nosys=false`), then by **extracting the bundled stub to a cache or temp directory** (`~/Library/Caches/JNA/temp`
    or `~/.cache/JNA/temp`).
  - There is **no checksum check** on the extracted file
    ([Native.java](https://raw.githubusercontent.com/java-native-access/jna/5.18.1/src/com/sun/jna/Native.java)).
  - The jar carries stubs for some 40 platforms.
  - Condition 4 keeps loading to the pinned jar's own copy.
- **commons-compress.** Testcontainers writes tar archives to copy files into containers, and **reads tar streams from
  the daemon** in `copyFileFromContainer`
  ([ContainerState](https://raw.githubusercontent.com/testcontainers/testcontainers-java/2.0.5/core/src/main/java/org/testcontainers/containers/ContainerState.java)).
- **duct-tape.** Retry, timeout and rate-limit helpers used while containers start
  ([GenericContainer](https://raw.githubusercontent.com/testcontainers/testcontainers-java/2.0.5/core/src/main/java/org/testcontainers/containers/GenericContainer.java)).
- **commons-codec, commons-io.** Encoding and IO utilities. commons-io 2.20.0 is compile-only and never loads.
- **None of them:** execution at build time.

## 8. Direct dependencies as published
- **docker-java-api:** jackson-annotations 2.20 (resolved to 2.21), slf4j-api.
- **docker-java-transport:** provided dependencies only (jna, immutables, findbugs).
- **docker-java-transport-zerodep:** docker-java-transport, jna 5.18.1, slf4j-api; shaded httpclient5 5.5.1 and
  httpcore5 (§5).
- **commons-compress:** commons-codec, commons-io, commons-lang3 3.18.0 (resolved to 3.20.0; covered by the Structurizr
  transitives review). Several are optional (zstd, brotli, xz, asm) and not resolved.
- **duct-tape:** org.jetbrains:annotations 17.0.0 (annotation-only review); slf4j-api is provided.
- **jna, commons-codec, commons-io:** none.
- **POM metadata only:** docker-java-parent 3.7.1, commons-parent 85.

## 9. Alternatives considered
- **A CI service container instead of Testcontainers** would remove this whole tree; the Testcontainers review §9
  rejected it for per-test isolation.
- **docker-java-transport-httpclient5 (unshaded):** would let Gradle upgrade httpclient5, but Testcontainers does not
  offer that transport.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:**
- **Root-equivalent Docker API access** in the test JVM.
- **Native code** extracted without a checksum.
- **Libraries that are dormant or archived** (duct-tape) or have low scores (docker-java 3.8, JNA 3.7).
- **A bundled httpclient5 with a known DoS** that we cannot upgrade.

## 11. Not verified
- **Jar contents:** the zerodep jar was not listed, so the shaded httpclient5/httpcore5 versions are inferred from the
  POMs; nor was the native stub list in the JNA jar checked.
- **Signatures:** no `.asc` was checked.
- **JNA on JDK 25:** whether JNA stays unused on macOS and Linux.
- **duct-tape's last commit.**
- **OSV:** not re-run by me; `inputs/osv.json` and deps.dev stand in.
