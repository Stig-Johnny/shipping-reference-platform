---
component: "io.micrometer:micrometer-core, micrometer-commons, micrometer-observation, micrometer-jakarta9; org.hdrhistogram:HdrHistogram; com.zaxxer:HikariCP"
kind: maven
version: "micrometer-* 1.17.2; HdrHistogram 2.2.2; HikariCP 7.0.2"
verdict: APPROVE-WITH-CONDITIONS
scope: "apps/shipping-api as locked: micrometer-* and HikariCP on compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath; HdrHistogram on runtimeClasspath and testRuntimeClasspath. All ship in the application (pulled in by spring-boot-starter-actuator and spring-boot-starter-jdbc 4.1.2)"
conditions: ["Exact versions and jar SHA-256 values as in allowlist-entries.json; any other version or hash is a new review", "No Micrometer registry or exporter module (Prometheus, OTLP, StatsD or other) and no push registry is added without its own review; metrics leave the process only through Actuator, under the Spring Boot review's health-only exposure condition", "micrometer-jakarta9 stays inert: no Jakarta JMS or Jakarta Mail API on any classpath without a new review", "HikariCP is configured only through the repository's application properties and environment set by the deployer: jdbcUrl, credentials and driver come from trusted configuration, never from request data; spring.datasource.hikari.data-source-j-n-d-i and the hikaricp.configurationFile system property are not used; register-mbeans stays false", "Re-review when Spring Boot moves HikariCP (7.1.0 exists) or Micrometer, or when either publishes an advisory", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: Micrometer 1.17.2, HdrHistogram 2.2.2 and HikariCP 7.0.2

**Method.** Read on 2026-11-06 with WebFetch: Central listings, POMs and sidecars; deps.dev (including its hash
index); OSV; Scorecard; GitHub releases, tags, workflows and source at the tags. Nothing was downloaded, built or run.

## 1. Verdict
**APPROVE-WITH-CONDITIONS for all six.** All six jar hashes match a publisher record (sidecars for Micrometer and
HdrHistogram; deps.dev's hash index for HikariCP), and no advisory applies to these versions.

The weak spot is **HikariCP**, the connection pool that holds the database credentials:
- one maintainer;
- releases built and signed on that maintainer's machine (inferred);
- no `.sha256` on Central;
- Scorecard 3.8;
- Boot 4.1.2 pins 7.0.2, although 7.1.0 has been out since 2026-06-14.

It is still the default pool of Spring Boot, and the alternatives need their own reviews. The conditions keep its
configuration trusted and its JMX and JNDI paths closed. Micrometer is a mature Spring-adjacent project, and
HdrHistogram is a small, stable data-structure library.

## 2. What it is and why it is needed
- **Micrometer:** the metrics and observation API that Actuator uses (`spring-boot-starter-actuator`, top-level Boot
  review §8). It covers the health and metrics the design wants for the example (`v01-design.md` §4).
- **HdrHistogram:** a runtime dependency of micrometer-core, for percentile histograms.
- **HikariCP:** the JDBC connection pool behind `spring-boot-starter-jdbc`, used for the real PostgreSQL transactions
  of slice 5.

## 3. Provenance
| Artifact | Publisher → source | Central date | `.asc` | Publisher SHA-256 vs ours |
|---|---|---|---|---|
| micrometer-commons, -core, -observation, -jakarta9 1.17.2 | `io.micrometer`, developers Tommy Ludwig and Jonatan Ivanov → micrometer-metrics/micrometer ([POM](https://repo1.maven.org/maven2/io/micrometer/micrometer-core/1.17.2/micrometer-core-1.17.2.pom)) | 2026-10-22 | yes | all four `.jar.sha256` sidecars **match** ([dir](https://repo1.maven.org/maven2/io/micrometer/micrometer-core/1.17.2/)) |
| HdrHistogram 2.2.2 | `org.hdrhistogram`, Gil Tene → HdrHistogram/HdrHistogram ([POM](https://repo1.maven.org/maven2/org/hdrhistogram/HdrHistogram/2.2.2/HdrHistogram-2.2.2.pom)) | 2024-05-30 | yes | sidecar **matches** ([dir](https://repo1.maven.org/maven2/org/hdrhistogram/HdrHistogram/2.2.2/)) |
| HikariCP 7.0.2 | `com.zaxxer`, Brett Wooldridge (Zaxxer.com) → brettwooldridge/HikariCP ([POM](https://repo1.maven.org/maven2/com/zaxxer/HikariCP/7.0.2/HikariCP-7.0.2.pom)) | 2025-08-19 | yes | **no `.sha256`** on Central (only `.md5`, `.sha1`). deps.dev's hash index returns HikariCP 7.0.2 for our SHA-256 ([query](https://api.deps.dev/v3/query?hash.type=SHA256&hash.value=8eYS%2Bic0W%2BMQeoVDHoqK6yBcFTZKsvLUEeQKnXuwgJU%3D)); a one-character change returns 404. That is a third-party index, not the publisher |

**Micrometer's build.** `release-train-build.yml` at `v1.17.2` runs on `workflow_dispatch` with `contents: read` and
SHA-pinned actions. It builds twice ("Repeat Build"), then hands off to the external `spring-io/release-train`
repository, which returns 404, so the signing and the Central upload cannot be seen
([workflow](https://raw.githubusercontent.com/micrometer-metrics/micrometer/v1.17.2/.github/workflows/release-train-build.yml)).
- `build.gradle` signs with in-memory PGP keys and disables Gradle module metadata, which is why there is no `.module`
  ([build.gradle](https://raw.githubusercontent.com/micrometer-metrics/micrometer/v1.17.2/build.gradle)).
- GitHub release `v1.17.2` was published by `shakuzen` on 2026-10-23.

**HikariCP's build.** The only workflow is CI on JDK 11, with tag-pinned actions and no publish step
([ci.yml](https://raw.githubusercontent.com/brettwooldridge/HikariCP/HikariCP-7.0.2/.github/workflows/ci.yml)). The POM
signs with GPG in a `release` profile, and the tag commit is a maven-release-plugin commit with no Verified badge
([commit](https://github.com/brettwooldridge/HikariCP/commit/80c46ae)). So the release was most likely built on the
maintainer's machine (inference).

**Attestations.** deps.dev shows `slsaProvenances: []` for all six
([micrometer-core](https://api.deps.dev/v3/systems/maven/packages/io.micrometer%3Amicrometer-core/versions/1.17.2),
[HikariCP](https://api.deps.dev/v3/systems/maven/packages/com.zaxxer%3AHikariCP/versions/7.0.2)).

## 4. Maintenance and adoption
- **Micrometer:**
  - Releases come every one to two months: 1.17.0 (2026-06-08), 1.17.1 (08-20), 1.17.2 (10-22). 1.17.2 is the newest 1.17.x; 1.18.0-RC2 is out ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/io.micrometer%3Amicrometer-core)).
  - Scorecard 6 (2026-10-19): Maintained 10, SAST 10, Code-Review 2, Token-Permissions 0 ([scorecard](https://api.securityscorecards.dev/projects/github.com/micrometer-metrics/micrometer)).
- **HdrHistogram:**
  - 2.2.2 is the newest release. The one before it was 2.2.1 (2024-05-05), and before that 2.1.12 (2019).
  - Scorecard 3.4, with Maintained 0 ([scorecard](https://api.securityscorecards.dev/projects/github.com/HdrHistogram/HdrHistogram)). It is a finished data structure, not an abandoned one.
- **HikariCP:**
  - 7.1.0 (2026-06-14) is the newest release ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/com.zaxxer%3AHikariCP)). There are no GitHub Releases, only [tags](https://github.com/brettwooldridge/HikariCP/tags).
  - 2,480 of the commits are the maintainer's; the next contributor has 241 ([contributors](https://api.github.com/repos/brettwooldridge/HikariCP/contributors?per_page=10)).
  - Scorecard 3.8: Maintained 0, Code-Review 0, Security-Policy 0 ([scorecard](https://api.securityscorecards.dev/projects/github.com/brettwooldridge/HikariCP)).
  - The 7.0.x changes are pool fixes, with no security items ([CHANGES](https://raw.githubusercontent.com/brettwooldridge/HikariCP/HikariCP-7.0.2/CHANGES)).

## 5. Known vulnerabilities at these versions
**None found on 2026-11-06:**
- `inputs/osv.json` is empty for all six, and deps.dev `advisoryKeys: []` for all six.
- The two micrometer-core advisories, GHSA-g3pr-3p32-fp23 (HTTP server instrumentation DoS) and GHSA-w737-wx49-qj23
  (gRPC), have OSV ranges that end at 1.16.5 and 1.15.11. 1.17.x is outside them
  ([OSV](https://api.osv.dev/v1/vulns/GHSA-g3pr-3p32-fp23), [OSV](https://api.osv.dev/v1/vulns/GHSA-w737-wx49-qj23)).

## 6. License
- **Micrometer and HikariCP:** Apache-2.0 (POMs).
- **HdrHistogram:** "Public Domain, per Creative Commons CC0" and BSD-2-Clause (POM); deps.dev reads it as "non-standard, BSD-2-Clause". Both are permissive.

## 7. Execution privileges (runtime)
- **micrometer-core: JMX and reflection, not files or network.**
  - The binders read platform MXBeans and call `com.sun.management` getters by reflection ([ProcessorMetrics](https://raw.githubusercontent.com/micrometer-metrics/micrometer/v1.17.2/micrometer-core/src/main/java/io/micrometer/core/instrument/binder/system/ProcessorMetrics.java)).
  - `JvmGcMetrics` adds GC notification listeners.
  - `DiskSpaceMetrics` calls `File.getUsableSpace` on a configured path.
  - Core contains HTTP senders (`ipc/http`), but they are used only by push registries in separate modules. `PushMeterRegistry` starts a thread only on `start()` ([ipc/http](https://github.com/micrometer-metrics/micrometer/tree/v1.17.2/micrometer-core/src/main/java/io/micrometer/core/ipc/http)). Condition 2 keeps those registries out. Not every class in core was read.
- **micrometer-jakarta9:** instruments only Jakarta JMS and Mail ([source tree](https://github.com/micrometer-metrics/micrometer/tree/v1.17.2/micrometer-jakarta9/src/main/java/io/micrometer/jakarta9/instrument)). Both APIs are optional and absent here, so the module is inert.
- **micrometer-commons and -observation:** in-process API types.
- **HdrHistogram:** in-memory arrays. Its source was not read.
- **HikariCP:**
  - It opens JDBC connections through the PostgreSQL driver and holds the credentials in memory.
  - It runs housekeeping and network-timeout threads.
  - It instantiates `driverClassName` and `exceptionOverrideClassName` by reflection.
  - `dataSourceJNDI` triggers `InitialContext.lookup` ([PoolBase](https://raw.githubusercontent.com/brettwooldridge/HikariCP/HikariCP-7.0.2/src/main/java/com/zaxxer/hikari/pool/PoolBase.java)).
  - MBeans are registered only if `registerMbeans` is set; it defaults to false ([HikariConfig](https://raw.githubusercontent.com/brettwooldridge/HikariCP/HikariCP-7.0.2/src/main/java/com/zaxxer/hikari/HikariConfig.java)).
  - The `hikaricp.configurationFile` system property loads a properties file.
  - Condition 4 closes the JNDI, file and URL-injection paths.
  - Javassist is used only at HikariCP's own build time, to generate proxies, and is optional in the POM.
- **Build time (ours):** none of the six runs.

## 8. Direct dependencies as published
- **micrometer-core:** jspecify 1.0.1, micrometer-commons, micrometer-observation (compile); HdrHistogram 2.2.2 (runtime). 31 dependencies are marked `optional` and none is resolved; the lockfile confirms this.
- **micrometer-jakarta9:** micrometer-core, -commons, -observation, jspecify; jakarta.jms-api and jakarta.mail-api are optional ([POM](https://repo1.maven.org/maven2/io/micrometer/micrometer-jakarta9/1.17.2/micrometer-jakarta9-1.17.2.pom)).
- **micrometer-observation → micrometer-commons → jspecify.**
- **HdrHistogram:** none at runtime.
- **HikariCP:** slf4j-api 2.0.17 (compile; resolved to 2.0.20, see the logging review). javassist and micrometer-core 1.5.10 are optional; hibernate-core, dropwizard and prometheus are provided.

## 9. Alternatives considered
- **Pools other than HikariCP** (Tomcat JDBC pool, commons-dbcp2, or the driver's own `PGSimpleDataSource` without a pool) would each need a review, and none is Boot's default.
- **Dropping Actuator** would remove Micrometer and HdrHistogram, at the cost of the health endpoint.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:**
- **HikariCP's single maintainer and off-CI releases**, with its hash anchored only by deps.dev's index and our own
  first download.
- **A lagging pin:** fixes in 7.1.0 (a virtual-thread spin) are not ours until Boot moves.
- **Micrometer's opaque signing:** signing happens in a release-train repository that cannot be seen.

## 11. Not verified
- **Signatures:** no `.asc` checked. HikariCP's `.asc` files are smaller (481 B) than Micrometer's (833 B); the key
  type was not checked.
- **The build path:** where Micrometer signs and uploads; HikariCP's local release process.
- **Full source scans:** micrometer-core was not scanned for socket use beyond the files cited; HikariCP runtime classes
  were not scanned for javassist imports; HdrHistogram's source was not read.
- **OSV:** not re-run by me; `inputs/osv.json` and deps.dev stand in.
