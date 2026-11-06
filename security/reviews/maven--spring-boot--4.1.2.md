---
component: "org.springframework.boot:spring-boot-dependencies (BOM) and starters"
kind: maven
version: "4.1.2"
verdict: APPROVE-WITH-CONDITIONS
scope: ":example-app only. Main: the BOM as a Gradle platform, spring-boot-starter-webmvc, -jdbc, -validation, -actuator, -flyway. Test configurations only: spring-boot-starter-test, spring-boot-testcontainers. Includes the org.springframework.boot 4.1.2 modules these pull in (listed in section 8). spring-boot-starter-web is not approved. Not :core, not :junit5."
conditions: ["Gradle platform(org.springframework.boot:spring-boot-dependencies:4.1.2) on :example-app only; every org.springframework.boot artifact resolves at 4.1.2 and matches its published SHA-256", "Use spring-boot-starter-webmvc; the deprecated spring-boot-starter-web is not approved", "Every non-Boot artifact the starters pull in (section 8) needs its own approved allowlist entry; this review does not approve them", "Actuator stays at its defaults: only health exposed over HTTP and JMX; never expose heapdump, env, configprops, loggers, threaddump or shutdown (the app has no Spring Security); set server.address=127.0.0.1 when the app runs on a developer host", "spring-boot-starter-test and spring-boot-testcontainers only on test configurations", "No Spring Boot Gradle plugin, executable packaging, devtools or spring-boot-docker-compose (not reviewed)", "Re-review on any Boot version change, which moves every managed version"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: Spring Boot 4.1.2 BOM and starters (example application only)

## 1. Verdict

**APPROVE-WITH-CONDITIONS.** 4.1.2 (2026-10-22) is the newest 4.1.x; 4.2 exists only as RC2. deps.dev lists no
advisory for the Boot artifacts, or for the main versions the BOM manages: Spring Framework 7.0.10, Tomcat 11.0.27,
Jackson 3.1.7, Hibernate Validator 9.1.5, Logback 1.5.38, SnakeYAML 2.6, HikariCP 7.0.2 and Micrometer 1.17.2. The
twelve Spring Framework advisories of 2026-08-20 are fixed in 7.0.9. Three conditions carry the approval. Use
`spring-boot-starter-webmvc`, because `-web` is deprecated in 4.1. Keep Actuator at its health-only default, because
the app has no Spring Security. Treat the BOM as a version source only: every third-party artifact it brings in is
reviewed on its own.

## 2. What it is and why it is needed

v01-design.md §4: "Spring Boot 4.1.2 … Example only. Use native Gradle BOM support/application plugin; defer
executable packaging plugins. Boot manages JUnit 6.0.3". The starters cover HTTP endpoints, JDBC transactions,
validation, health, Flyway migrations, and tests against real PostgreSQL.

## 3. Provenance

- **Publisher.** Maven Central, `org.springframework.boot`. The POMs give Apache-2.0, developer "Spring"
  (organization "VMware, Inc.") and scm `https://github.com/spring-projects/spring-boot`. Every file is dated
  2026-10-22 and has `.asc`, `.sha256` and `.sha512`. The jars also have a Gradle `.module` (Gradle 9.8.1), but the BOM
  directory does not.
- **SHA-256.** The `sha256` values in `allowlist-entries.json` are copied verbatim from the publisher's sidecar files,
  `https://repo1.maven.org/maven2/org/springframework/boot/<a>/4.1.2/<a>-4.1.2.jar.sha256` (for the BOM, `.pom.sha256`).
  That covers the BOM, the eight starters named in the scope, `spring-boot-starter-web`, and five modules pulled in
  (`-starter`, `-starter-tomcat`, `-starter-logging`, `-starter-jackson`, `spring-boot-flyway`). The other modules'
  values were not fetched, so they are `null`.
- **Build.** `release-train-build.yml` at `v4.1.2`
  (https://github.com/spring-projects/spring-boot/blob/v4.1.2/.github/workflows/release-train-build.yml) runs on
  `workflow_dispatch` with `contents: read`, SHA-pinned actions and runner label `ubuntu22-2-8`. It publishes to a
  local repository, runs a "Repeat Build", then hands off through a callback to a release-train repository the file
  does not name. **Signing and the Central upload happen there, out of view.** There is no attestation, and deps.dev
  shows `slsaProvenances: []`. GitHub release `v4.1.2` was made by `spring-builds` at commit `634e6fd`.

## 4. Maintenance and adoption

4.1.0 (2026-06-10), 4.1.1 (08-20), 4.1.2 (10-22); 4.0.9 the same day; 4.2.0-RC2 on 11-05
(https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/). The repository dates from 2012,
with 81,786 stars and 43,451 forks, last pushed 2026-11-06. **Scorecard 5.6** (2026-10-19;
https://api.securityscorecards.dev/projects/github.com/spring-projects/spring-boot): Maintained, Fuzzing (OSS-Fuzz),
Dangerous-Workflow and Security-Policy score 10; Code-Review 2, Branch-Protection 3, Token-Permissions 0, SAST 0.
Vulnerabilities are reported through `spring-projects/security-advisories` and published at https://spring.io/security.
The OSS support end date for 4.1.x was not found.

## 5. Known vulnerabilities at 4.1.2

**Method.** OSV `POST /v1/query` is unavailable here, so deps.dev v3 (which carries OSV data) and spring.io/security
were used on 2026-11-06.

- **Boot artifacts.** spring-boot, -autoconfigure, -actuator, -actuator-autoconfigure and -webmvc 4.1.2 all show
  `advisoryKeys: []` (https://api.deps.dev/v3/systems/maven/packages/org.springframework.boot%3Aspring-boot/versions/4.1.2,
  and the same pattern for the others).
- **Managed versions, all `[]`:** spring-core, -web, -webmvc and -jdbc 7.0.10; tomcat-embed-core 11.0.27; tools.jackson
  jackson-databind and jackson-core 3.1.7; hibernate-validator 9.1.5.Final; logback 1.5.38; snakeyaml 2.6; HikariCP
  7.0.2; micrometer-core 1.17.2.
- **Spring advisories of 2026-08-20.** CVE-2026-47883, -47884, -47885, -47886, -47887, -47889, -47890, -47891, -47892,
  -47893, -59280 and -59281 (https://spring.io/security/cve-2026-47883 and siblings) affect 7.0.0–7.0.8, with 7.0.9 as
  the OSS fix, so **none applies to 7.0.10**. The feed's newest items are from 2026-08-20; its JavaScript "load more"
  was not run. **None found on 2026-11-06.**

## 6. License

**Apache-2.0** for all Boot artifacts (POMs, deps.dev). Transitive Logback is "EPL-2.0, LGPL-2.1-only"; that is flagged
for Logback's own review.

## 7. Execution privileges

- **Build time: none.** The BOM is metadata, but it decides the version of every library it manages (it imports 46
  other BOMs). No starter here contains an annotation processor.
- **Runtime.** Auto-configuration creates beans from what is on the classpath. Embedded Tomcat 11.0.27 listens on
  `server.port` (8080); `server.address` has no default
  (https://docs.spring.io/spring-boot/appendix/application-properties/index.html). Boot reads `application.*` (YAML via
  SnakeYAML), environment variables and system properties. HikariCP connects to the database, Flyway migrates at
  startup, and Hibernate Validator evaluates EL message templates through `tomcat-embed-el`.
- **Actuator.** "By default, only the health endpoint is exposed over HTTP and JMX", and "access to all endpoints except
  for `shutdown` and `heapdump` is unrestricted" (https://docs.spring.io/spring-boot/reference/actuator/endpoints.html).
  Boot advises a firewall or Spring Security for exposed endpoints; the example has no Spring Security.
- **Test.** Mockito 5.23.0 and Byte Buddy generate and instrument classes in the test JVM. `spring-boot-testcontainers`
  drives the Docker API (see the Testcontainers review).

## 8. Direct dependencies as published (POMs at 4.1.2; all compile scope)

- **webmvc:** `-starter`, `-starter-jackson`, `-starter-tomcat` (+ `-starter-tomcat-runtime`), `-http-converter`,
  `-webmvc`, `-servlet`, `-web-server`, `-tomcat`, `-jackson`. Third-party: spring-web/-webmvc 7.0.10;
  tomcat-embed-core/-el/-websocket 11.0.27; jackson-databind 3.1.7; jakarta.annotation-api 3.0.0.
- **starter:** `-starter-logging` (logback-classic 1.5.38, log4j-to-slf4j 2.25.5, jul-to-slf4j 2.0.20),
  `-autoconfigure`, `spring-boot` (spring-core/-context 7.0.10), snakeyaml 2.6.
- **jdbc:** `-jdbc`, `-sql`, `-transaction`, `-persistence` (spring-jdbc, spring-tx 7.0.10), HikariCP 7.0.2.
- **validation:** `-validation` (hibernate-validator 9.1.5.Final, tomcat-embed-el 11.0.27).
- **actuator:** `-starter-micrometer-metrics`, `-micrometer-metrics`, `-micrometer-observation`, `-actuator`,
  `-actuator-autoconfigure`, `-health`. Third-party: micrometer-core/-observation/-commons/-jakarta9 1.17.2,
  HdrHistogram 2.2.2 (runtime), jspecify 1.0.1.
- **flyway:** `-starter-jdbc`, `spring-boot-flyway` (flyway-core 12.4.0, reviewed separately).
- **test:** `-test`, `-test-autoconfigure`. Third-party: spring-test 7.0.10, junit-jupiter 6.0.3, assertj-core 3.27.7,
  mockito-core/-junit-jupiter 5.23.0, hamcrest 3.0, awaitility 4.3.0, json-path 2.10.0, json-smart 2.6.0, jsonassert
  1.5.3, xmlunit-core 2.11.0, jakarta.xml.bind-api 4.0.5.
- **testcontainers:** `-autoconfigure` and `org.testcontainers:testcontainers` 2.0.5; no optional dependencies in the
  POM or `.module`.
- **starter-web:** the same content as webmvc; its description says "(deprecated in favor of
  spring-boot-starter-webmvc)".

**Transitives that deserve their own review, most exposed first:** Spring Framework 7.0.10; Tomcat 11.0.27; Jackson
3.1.7; Hibernate Validator 9.1.5 with EL; Logback; SnakeYAML; HikariCP; Micrometer and HdrHistogram; Mockito and Byte
Buddy; JUnit 6.0.3; then the rest of the test set. **Not in scope:** the Boot 4 slice test starters (`-webmvc-test`,
`-jdbc-test`, `-flyway-test`). Their POMs were read, but they are not approved here.

## 9. Alternatives considered

Spring Framework without Boot means more wiring, the same core transitives, and no Modulith integration. Boot 4.0.9
is the older line, and 4.2 exists only as RC2. Other frameworks are out, because Spring Modulith requires Spring.

## 10. Conditions and residual risk

The conditions are those in the frontmatter. **Residual risk:** one BOM upgrade moves dozens of versions, so every
upgrade is a new review. Signing happens in a release-train repository that cannot be seen. Code-Review scores 2.
Until every artifact in section 8 has its own entry, the classpath is only partly reviewed; the allowlist checker fails
closed on those artifacts.

## 11. Not verified

The `.asc` signatures, and the jars' hashes computed locally: nothing was downloaded. The `.module` files against the
POMs, for most artifacts; Gradle resolves from the `.module` files. The release-train repository, and whether the
"Repeat Build" outputs are compared. Tomcat's bind interface when `server.address` is empty, and OSS support end dates.
Spring advisories from before August 2026, one by one; deps.dev covers the exact versions.
