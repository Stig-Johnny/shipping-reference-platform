---
component: "ch.qos.logback:logback-classic, logback-core; org.slf4j:slf4j-api, jul-to-slf4j, jcl-over-slf4j; org.apache.logging.log4j:log4j-api, log4j-to-slf4j; commons-logging:commons-logging"
kind: maven
version: "logback 1.5.38; slf4j 2.0.20; log4j 2.25.5; commons-logging 1.3.6"
verdict: APPROVE-WITH-CONDITIONS
scope: "apps/shipping-api as locked. logback-classic, logback-core, slf4j-api, jul-to-slf4j, log4j-api, log4j-to-slf4j: compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath (ship in the application). commons-logging 1.3.6: compileClasspath, runtimeClasspath, testCompileClasspath (testRuntimeClasspath resolves 1.4.0, covered by the Structurizr transitives review). jcl-over-slf4j 2.0.20: REJECTED (see conditions)"
conditions: ["Exact versions and jar SHA-256 values as in allowlist-entries.json; any other version or hash is a new review", "jcl-over-slf4j 2.0.20 is REJECTED: exclude org.slf4j:jcl-over-slf4j from spring-modulith-events-core (or from all configurations) so that only commons-logging provides org.apache.commons.logging; the lockfile must no longer list it, and the application and test suites pass without it", "Logging configuration is a repository-owned logback-spring.xml (or Boot defaults) only: no SocketAppender, SSL/socket receivers, SMTPAppender, DBAppender, insertFromJNDI or other network/JNDI elements; no Janino on any classpath; logback.configurationFile and logging.config are not set from outside the repository", "No log4j-core, log4j-slf4j*-impl, slf4j-reload4j or other second logging backend on any classpath; no commons-logging.properties, org.apache.commons.logging.* system property or slf4j.provider override (same rule as the Structurizr transitives review)", "The 2026 low-severity Logback fixes and Log4j 2.26.x are not ours until Spring Boot moves; re-review then, or on any advisory against these versions", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: logging stack (Logback 1.5.38, SLF4J 2.0.20 bridges, Log4j API 2.25.5, Commons Logging 1.3.6)

**Method.** Read on 2026-11-06 with WebFetch: Central listings, sidecars, POMs and `.module` files; deps.dev
(including its hash index); OSV and the GitHub advisory API; Scorecard; the Logback and SLF4J site sources on GitHub
(logback.qos.ch, slf4j.org and logging.apache.org were refused); and release scripts and workflows. Nothing was
downloaded, built or run.

## 1. Verdict
**APPROVE-WITH-CONDITIONS for seven artifacts. REJECT jcl-over-slf4j 2.0.20.**
- All hashes match a publisher record, and every known advisory ends below these versions. log4j-api 2.25.5 is itself
  the fixed version for GHSA-qv9r-c865-cp47.
- **The rejection is about correctness, not a CVE.** Spring Framework 7 replaced `spring-jcl` with commons-logging 1.3,
  which routes to SLF4J by itself. `spring-modulith-events-core` 2.1.2 still adds `jcl-over-slf4j` at runtime scope
  ([POM](https://repo1.maven.org/maven2/org/springframework/modulith/spring-modulith-events-core/2.1.2/spring-modulith-events-core-2.1.2.pom)).
- So **both jars on the shipped runtime classpath define `org.apache.commons.logging.*`.** Which copy loads depends on
  classpath order. Spring's lead says only one provider of that namespace should be present
  ([#32459](https://github.com/spring-projects/spring-framework/issues/32459)). Boot's own logging starter does not
  include jcl-over-slf4j
  ([POM](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-logging/4.1.2/spring-boot-starter-logging-4.1.2.pom)).
  The fix is an exclusion, not an upgrade.

## 2. What it is and why it is needed
- **spring-boot-starter-logging 4.1.2** brings logback-classic, log4j-to-slf4j and jul-to-slf4j (POM above). Logback is
  Boot's default backend. The two bridges route Log4j-API and JUL calls into SLF4J.
- **commons-logging 1.3.x** is a direct dependency of spring-core 7.0.10, which declares 1.3.5 (Spring review §8).
- **slf4j-api** is the shared API; HikariCP, Modulith, json-schema-validator and docker-java all use it.

## 3. Provenance
| Artifact | Publisher → source | Central date | `.asc` | Publisher SHA-256 vs ours |
|---|---|---|---|---|
| logback-classic, logback-core 1.5.38 | QOS.ch; Ceki Gülcü, Joern Huxhorn → qos-ch/logback ([parent](https://repo1.maven.org/maven2/ch/qos/logback/logback-parent/1.5.38/logback-parent-1.5.38.pom)) | 2026-07-09 | yes | `.jar.sha256` sidecars **match** ([dir](https://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.5.38/)) |
| slf4j-api, jul-to-slf4j, jcl-over-slf4j 2.0.20 | QOS.ch; Ceki Gülcü → qos-ch/slf4j ([bom](https://repo1.maven.org/maven2/org/slf4j/slf4j-bom/2.0.20/slf4j-bom-2.0.20.pom)) | 2026-09-22 | yes | sidecars **match** ([dir](https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.20/)) |
| log4j-api, log4j-to-slf4j 2.25.5 | Apache Software Foundation → apache/logging-log4j2 ([bom](https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-bom/2.25.5/log4j-bom-2.25.5.pom)) | 2026-07-01 | yes | no `.sha256`; the **signed `.module` lists the jar SHA-256, which matches** ([api module](https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.25.5/log4j-api-2.25.5.module), [to-slf4j module](https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-to-slf4j/2.25.5/log4j-to-slf4j-2.25.5.module)) |
| commons-logging 1.3.6 | ASF Commons → gitbox commons-logging ([POM](https://repo1.maven.org/maven2/commons-logging/commons-logging/1.3.6/commons-logging-1.3.6.pom)) | 2026-03-04 | yes | no `.sha256`; deps.dev's hash index returns 1.3.6 for our SHA-256 ([query](https://api.deps.dev/v3/query?hash.type=SHA256&hash.value=%2BOrYlDQBCB3qCqgktbG6QKDk7Sl6VyoPAiWBUKC2I1c%3D)), a third-party index |

**Release builds:**
- **QOS (Logback and SLF4J)** releases are built and signed on a local machine with `mvn deploy -P javadocjar,sign-artifacts`
  ([release.sh](https://raw.githubusercontent.com/qos-ch/logback/master/release.sh)). The signing key fingerprint is
  published in [SECURITY.md](https://raw.githubusercontent.com/qos-ch/slf4j/master/SECURITY.md).
- **Log4j** deploys from GitHub Actions through `apache/logging-parent`. A `verify-reproducibility-release` job rebuilds
  the release on Ubuntu and macOS and compares it with the staged repository
  ([workflow](https://raw.githubusercontent.com/apache/logging-parent/gha/v0/.github/workflows/verify-reproducibility-reusable.yaml)).
  Signed CycloneDX SBOMs are on Central.
- **Attestations:** none for any of the eight; deps.dev shows `slsaProvenances: []` for all.

## 4. Maintenance and adoption
- **Logback:**
  - 1.5.38 is the newest 1.5.x; the 1.6 line has reached 1.6.6 (2026-10-25) ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/ch.qos.logback%3Alogback-classic)).
  - Scorecard 7.0 (2026-11-02), Code-Review 1 ([SC](https://api.securityscorecards.dev/projects/github.com/qos-ch/logback)).
- **SLF4J:**
  - 2.0.20 is the newest 2.0.x.
  - Scorecard 5.8, Code-Review 0 ([SC](https://api.securityscorecards.dev/projects/github.com/qos-ch/slf4j)).
  - Only Ceki Gülcü is listed, so it is effectively a one-person project (inferred).
- **Log4j:** 2.25.5 is the newest 2.25.x; 2.26.1 came out on 2026-06-29. Its Scorecard is stale: 8.3, from 2024-04-22
  ([SC](https://api.securityscorecards.dev/projects/github.com/apache/logging-log4j2)).
- **commons-logging:** 1.4.0 (2026-06-11) is the newest. Scorecard 7.2 (2026-11-03)
  ([SC](https://api.securityscorecards.dev/projects/github.com/apache/commons-logging)).

## 5. Known vulnerabilities at these versions
**None found on 2026-11-06.** `inputs/osv.json` is empty and deps.dev `advisoryKeys: []` for all eight. The advisory
ranges checked:
- **Logback:** every OSV range ends below 1.5.38.
  - [GHSA-vmq6-5m68-f53m](https://api.osv.dev/v1/vulns/GHSA-vmq6-5m68-f53m) and GHSA-gm62-rw4g-vrc4 (serialization) end at 1.4.x.
  - [GHSA-pr98-23f8-jwxv](https://api.osv.dev/v1/vulns/GHSA-pr98-23f8-jwxv) and GHSA-6v67-2wr5-gvf4 end below 1.5.13.
  - [GHSA-25qh-j22f-pwp8](https://api.osv.dev/v1/vulns/GHSA-25qh-j22f-pwp8) (CVE-2025-11226, Janino) ends below 1.5.19.
  - The low-severity GHSA-qqpg-mvqg-649v, -p47f-322f-whfh and [-jhq6-gfmj-v8fx](https://api.osv.dev/v1/vulns/GHSA-jhq6-gfmj-v8fx) end below 1.5.34.
- **log4j-api:** [GHSA-qv9r-c865-cp47](https://api.osv.dev/v1/vulns/GHSA-qv9r-c865-cp47) covers `[2.13.1, 2.25.5)`, so
  2.25.5 is the fix. Log4Shell ([GHSA-jfh8-c2jp-5v3q](https://api.osv.dev/v1/vulns/GHSA-jfh8-c2jp-5v3q)) is in
  log4j-core, which is absent here (condition 4).
- **SLF4J, log4j-to-slf4j and commons-logging:** no advisories.

## 6. License
- **Logback:** EPL-2.0 or LGPL-2.1-only (dual). It ships in the application; EPL-2.0 can be chosen.
- **slf4j-api and jul-to-slf4j:** MIT.
- **jcl-over-slf4j, Log4j and commons-logging:** Apache-2.0.

## 7. Execution privileges (runtime and tests)
- **Logback** reads its configuration at startup. The search order is Configurator providers via ServiceLoader, then
  the `logback.configurationFile` property, then `logback-test.xml`/`logback.xml`; Boot prefers `logback-spring.xml`
  ([configuration](https://raw.githubusercontent.com/qos-ch/logback-site/main/src/site/pages2/manual/configuration.html),
  [Boot logging](https://docs.spring.io/spring-boot/reference/features/logging.html)).
  - Configuration can open sockets: `SocketAppender` and receivers, with events in clear text
    ([network](https://raw.githubusercontent.com/qos-ch/logback-site/main/src/site/pages2/manual/appenders-network.html)).
  - It can perform JNDI lookups through `insertFromJNDI`
    ([conditional](https://raw.githubusercontent.com/qos-ch/logback-site/main/src/site/pages2/manual/configuration-conditional.html)).
  - Groovy config, JMX configurator and the Janino `condition` attribute are removed; the last went in 1.5.37 (same
    pages).
  - Condition 3 keeps configuration repository-owned and free of network and JNDI elements.
  - It writes to the console, and to files only if configured.
- **jul-to-slf4j:** Boot installs `SLF4JBridgeHandler` and a `LevelChangePropagator`. This was read on Boot's `main`
  branch ([LogbackLoggingSystem](https://raw.githubusercontent.com/spring-projects/spring-boot/main/core/spring-boot/src/main/java/org/springframework/boot/logging/logback/LogbackLoggingSystem.java)).
- **commons-logging 1.3.6** picks its implementation in this order: the system property
  `org.apache.commons.logging.LogFactory`, then ServiceLoader, then `commons-logging.properties`, then SLF4J
  (log4j-to-slf4j present), then the Log4j API, then the legacy implementation
  ([LogFactory](https://raw.githubusercontent.com/apache/commons-logging/rel/commons-logging-1.3.6/src/main/java/org/apache/commons/logging/LogFactory.java)).
  The classpath and system properties therefore choose the code that runs (condition 4).
- **log4j-api and log4j-to-slf4j:** an API and a bridge, with no lookups or plugins; those are in log4j-core.
- **Build time:** none.

## 8. Direct dependencies as published
- **logback-classic:** logback-core, slf4j-api. Mail, activation and servlet are optional; **Janino is not declared**.
- **logback-core:** only optional dependencies.
- **jul-to-slf4j, jcl-over-slf4j:** slf4j-api. **slf4j-api:** none.
- **log4j-to-slf4j:** log4j-api and slf4j-api at runtime.
- **log4j-api:** no runtime dependencies. At compile time only, it pulls jspecify, bnd.annotation, error_prone_annotations
  2.38.0 and the OSGi annotations (annotation-only review).
- **commons-logging:** only optional dependencies (slf4j-api, log4j-api and others).
- **Versions resolve higher than declared:** slf4j 2.0.17, which several artifacts declare, resolves to 2.0.20, and
  spring-core's commons-logging 1.3.5 resolves to 1.3.6.

## 9. Alternatives considered
- **Log4j-core as the backend:** a larger surface (lookups, plugins).
- **Plain JUL:** loses Boot's logging configuration.
- **Keeping jcl-over-slf4j and excluding commons-logging instead:** this would replace Apache's maintained 1.3 line with
  a JCL 1.1.1 clone, against Spring 7's direction.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:**
- **QOS releases** are built on one maintainer's machine, with low GitHub code-review scores. Logback has had eight
  advisories since 2023, all fixed by 1.5.34.
- **Logback's configuration is a capability surface** (sockets, JNDI); the control is that the repository owns it.
- **Tests run commons-logging 1.4.0 while production runs 1.3.6,** so the suites do not exercise the shipped
  commons-logging.

## 11. Not verified
- **Signatures:** no `.asc` was checked.
- **Boot's JUL-bridge code:** read on `main`, not at `v4.1.2`.
- **`insertFromJNDI` scope:** whether lookups are limited to `java:`.
- **Classpath order:** which `org.apache.commons.logging` copy wins today.
- **OSV:** not re-run by me; `inputs/osv.json`, deps.dev and the GitHub advisory API stand in.
